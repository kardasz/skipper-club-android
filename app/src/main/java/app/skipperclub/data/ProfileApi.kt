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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Client for the `/v1/profile` endpoints backing the "My profile" screen.
 * Modeled on [NotificationsApi] (raw OkHttp + manual RFC 7807 mapping) to stay
 * consistent until the codebase grows enough to justify Retrofit + DI (see
 * CLAUDE.md §Networking).
 */
object ProfileApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Coalesce an explicit JSON `null` on a non-nullable, defaulted field (e.g. counts) to its default.
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    private fun profileUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/profile".toHttpUrl()

    internal fun getProfileRequest(accessToken: String): Request =
        baseRequest(accessToken).url(profileUrl()).get().build()

    suspend fun getProfile(accessToken: String): UserProfile =
        executeAndDecode<ProfileDto, UserProfile>(getProfileRequest(accessToken)) { it.toDomain() }

    private fun baseRequest(accessToken: String): Request.Builder =
        Request.Builder()
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")

    private suspend inline fun <reified DtoT, DomainT> executeAndDecode(
        request: Request,
        crossinline toDomain: (DtoT) -> DomainT,
    ): DomainT {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toProfileError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw ProfileError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(ProfileError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toProfileError(): ProfileError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> ProfileError.AuthenticationRequired(detail)
            404 -> ProfileError.NotFound(detail)
            429 -> ProfileError.RateLimited(detail)
            400, 422 -> ProfileError.Validation(detail)
            else -> ProfileError.Server(code, detail)
        }
    }
}

sealed class ProfileError(message: String) : Exception(message) {
    class Network(cause: Throwable) : ProfileError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : ProfileError(detail ?: "Authentication required")
    class NotFound(detail: String?) : ProfileError(detail ?: "Profile not found")
    class RateLimited(detail: String?) : ProfileError(detail ?: "Too many requests")
    class Validation(detail: String?) : ProfileError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : ProfileError(detail ?: "Server error ($statusCode)")
}
