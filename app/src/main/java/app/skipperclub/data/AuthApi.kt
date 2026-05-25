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
        .let { HttpLoggingProvider.apply(it) }
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

    suspend fun login(email: String, password: String, turnstileToken: String): SessionResponse {
        val body = json.encodeToString(LoginRequest(email, password))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/auth/login")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header(HEADER_TURNSTILE, turnstileToken)
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toAuthError()
            val payload = response.body.string()
            return try {
                json.decodeFromString<SessionResponse>(payload)
            } catch (_: SerializationException) {
                throw AuthError.Server(response.code, "Malformed response")
            }
        }
    }

    suspend fun requestPasswordReset(email: String, turnstileToken: String) {
        val body = json.encodeToString(PasswordResetRequest(email)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/auth/password-reset-request")
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

    suspend fun resetPassword(email: String, code: String, password: String) {
        val body = json.encodeToString(PasswordResetConfirmRequest(email, code, password))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/auth/password-reset")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toAuthError()
        }
    }

    suspend fun registerByInvitation(
        code: String,
        name: String,
        email: String,
        password: String,
        turnstileToken: String,
    ): SessionResponse {
        val body = json.encodeToString(InvitationRegisterRequest(code, name, email, password))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/invitations/register")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header(HEADER_TURNSTILE, turnstileToken)
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toAuthError()
            val payload = response.body.string()
            return try {
                json.decodeFromString<SessionResponse>(payload)
            } catch (_: SerializationException) {
                throw AuthError.Server(response.code, "Malformed response")
            }
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
            val payload = response.body.string()
            return try {
                json.decodeFromString<SessionResponse>(payload)
            } catch (_: SerializationException) {
                throw AuthError.Server(response.code, "Malformed response")
            }
        }
    }

    internal suspend fun refreshSession(sessionId: String, refreshToken: String): RefreshSessionResponse {
        val body = json.encodeToString(RefreshSessionRequest(refreshToken))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/sessions/$sessionId/refresh")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toAuthError()
            val payload = response.body.string()
            return try {
                json.decodeFromString<RefreshSessionResponse>(payload)
            } catch (_: SerializationException) {
                throw AuthError.Server(response.code, "Malformed response")
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
                        continuation.resumeWithException(AuthError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    private fun Response.toAuthError(): AuthError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        val validationFields = problem?.violations.orEmpty()
            .asSequence()
            .mapNotNull { it.propertyPath?.substringBefore('.') }
            .toSet()
        return when (code) {
            400 -> when (problem?.type) {
                "/errors/invalid-invitation" -> AuthError.InvalidInvitation(detail)
                "/errors/invitation-email-mismatch" -> AuthError.InvitationEmailMismatch(detail)
                else -> AuthError.Validation(detail, validationFields)
            }
            401 -> when (problem?.type) {
                "/errors/invalid-credentials" -> AuthError.InvalidCredentials(detail)
                "/errors/invalid-otp-code" -> AuthError.InvalidOtpCode(detail)
                "/errors/invalid-password-reset-code" -> AuthError.InvalidPasswordResetCode(detail)
                "/errors/invalid-refresh-token" -> AuthError.InvalidRefreshToken(detail)
                "/errors/refresh-token-expired" -> AuthError.RefreshTokenExpired(detail)
                "/errors/authentication-required" -> AuthError.AuthenticationRequired(detail)
                else -> AuthError.AuthenticationRequired(detail)
            }
            403 -> AuthError.CaptchaFailed(detail)
            409 -> AuthError.EmailAlreadyRegistered(detail)
            422 -> AuthError.Validation(detail, validationFields)
            429 -> AuthError.RateLimited(detail)
            else -> AuthError.Server(code, detail)
        }
    }
}
