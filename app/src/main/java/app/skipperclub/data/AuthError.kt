package app.skipperclub.data

sealed class AuthError(message: String) : Exception(message) {
    class Network(cause: Throwable) : AuthError(cause.message ?: "Network error")
    class InvalidCredentials(detail: String?) : AuthError(detail ?: "Invalid credentials")
    class InvalidOtpCode(detail: String?) : AuthError(detail ?: "Invalid or expired code")
    class RateLimited(detail: String?) : AuthError(detail ?: "Too many requests")
    class CaptchaFailed(detail: String?) : AuthError(detail ?: "CAPTCHA verification failed")
    class Validation(detail: String?) : AuthError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : AuthError(detail ?: "Server error ($statusCode)")
}
