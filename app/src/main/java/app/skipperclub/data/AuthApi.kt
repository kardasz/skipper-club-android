package app.skipperclub.data

import app.skipperclub.BuildConfig
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

object AuthApi {
    private const val HEADER_TURNSTILE = "X-Turnstile-Token"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendOtp(email: String, turnstileToken: String) {
        val body = json.encodeToString(OtpRequest(email)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/auth/otp")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header(HEADER_TURNSTILE, turnstileToken)
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toAuthError()
        }
    }

    suspend fun verifyOtp(email: String, code: String, turnstileToken: String): SessionResponse {
        val body = json.encodeToString(OtpVerifyRequest(email, code))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/auth/otp/verify")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header(HEADER_TURNSTILE, turnstileToken)
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toAuthError()
            val payload = response.body?.string().orEmpty()
            return try {
                json.decodeFromString<SessionResponse>(payload)
            } catch (e: SerializationException) {
                throw AuthError.Server(response.code, "Malformed response")
            }
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(AuthError.Network(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }

    private fun Response.toAuthError(): AuthError {
        val payload = body?.string().orEmpty()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> when (problem?.type) {
                "/errors/invalid-credentials" -> AuthError.InvalidCredentials(detail)
                "/errors/invalid-otp-code" -> AuthError.InvalidOtpCode(detail)
                else -> AuthError.InvalidOtpCode(detail)
            }
            403 -> AuthError.CaptchaFailed(detail)
            422 -> AuthError.Validation(detail)
            429 -> AuthError.RateLimited(detail)
            else -> AuthError.Server(code, detail)
        }
    }
}
