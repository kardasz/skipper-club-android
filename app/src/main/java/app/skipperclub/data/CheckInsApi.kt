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
 * Client for the `/v1/check-ins` endpoints. Modeled on [AuthApi] (raw OkHttp + manual
 * RFC 7807 mapping) to stay consistent until the codebase grows enough to justify
 * Retrofit + DI (see CLAUDE.md §Networking).
 */
object CheckInsApi {
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
     * Upserts the caller's check-in. Pass `locationName = null` to let the backend
     * reverse-geocode the coordinates.
     */
    suspend fun upsert(
        accessToken: String,
        lat: Double,
        lng: Double,
        locationName: String?,
    ): CheckIn {
        val payload = CheckInRequest(
            lat = lat,
            lng = lng,
            locationName = locationName?.trim()?.takeIf { it.isNotEmpty() },
        )
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/check-ins")
            .put(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toCheckInError()
            val responseBody = response.body.string()
            return try {
                json.decodeFromString<CheckIn>(responseBody)
            } catch (_: SerializationException) {
                throw CheckInError.Server(response.code, "Malformed response")
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
                        continuation.resumeWithException(CheckInError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    private fun Response.toCheckInError(): CheckInError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            in 400..499 -> when (code) {
                401, 403 -> CheckInError.AuthenticationRequired(detail)
                429 -> CheckInError.RateLimited(detail)
                else -> CheckInError.Validation(detail)
            }
            else -> CheckInError.Server(code, detail)
        }
    }
}

sealed class CheckInError(message: String) : Exception(message) {
    class Network(cause: Throwable) : CheckInError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : CheckInError(detail ?: "Authentication required")
    class RateLimited(detail: String?) : CheckInError(detail ?: "Too many requests")
    class Validation(detail: String?) : CheckInError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : CheckInError(detail ?: "Server error ($statusCode)")
}
