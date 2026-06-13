package app.skipperclub.data

import app.skipperclub.BuildConfig
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Client for the `/v1/alerts` endpoints. Modeled on [CheckInsApi] (raw OkHttp +
 * manual RFC 7807 mapping) to stay consistent until the codebase grows enough to
 * justify Retrofit + DI (see CLAUDE.md §Networking).
 */
object AlertsApi {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    /**
     * Creates a navigation alert owned by the caller. The body's `content` is
     * stored as-is; [AlertGeometry] is the optional GeoJSON `Point`. The stored
     * language is taken from the `Content-Language` header (primary tag).
     */
    internal fun createRequest(
        accessToken: String,
        category: AlertCategory,
        content: String,
        geometry: AlertGeometry?,
    ): Request {
        val payload = CreateAlertRequest(
            category = category,
            content = content.trim(),
            geometry = geometry,
        )
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/alerts")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Content-Language", Locale.getDefault().language.ifBlank { "en" })
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    suspend fun create(
        accessToken: String,
        category: AlertCategory,
        content: String,
        geometry: AlertGeometry?,
    ): Alert {
        execute(createRequest(accessToken, category, content, geometry)).use { response ->
            if (!response.isSuccessful) throw response.toAlertError()
            val responseBody = response.body.string()
            return try {
                json.decodeFromString<Alert>(responseBody)
            } catch (_: SerializationException) {
                throw AlertError.Server(response.code, "Malformed response")
            }
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(AlertError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toAlertError(): AlertError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401, 403 -> AlertError.AuthenticationRequired(detail)
            429 -> AlertError.RateLimited(detail)
            400, 422 -> {
                val fieldErrors = problem?.violations.orEmpty()
                    .mapNotNull { violation ->
                        val path = violation.propertyPath?.substringBefore('.') ?: return@mapNotNull null
                        path to (violation.message ?: "")
                    }
                    .toMap()
                AlertError.Validation(detail, fieldErrors)
            }
            else -> AlertError.Server(code, detail)
        }
    }
}

sealed class AlertError(message: String) : Exception(message) {
    class Network(cause: Throwable) : AlertError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : AlertError(detail ?: "Authentication required")
    class RateLimited(detail: String?) : AlertError(detail ?: "Too many requests")

    /**
     * DTO-level validation rejection. [fieldErrors] maps each offending property
     * path (e.g. `content`) to the server-provided message, so the form can show
     * inline errors.
     */
    class Validation(
        detail: String?,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AlertError(detail ?: "Validation failed")

    class Server(val statusCode: Int, detail: String?) : AlertError(detail ?: "Server error ($statusCode)")
}
