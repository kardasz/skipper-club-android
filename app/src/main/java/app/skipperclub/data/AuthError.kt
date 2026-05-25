package app.skipperclub.data

sealed class AuthError(message: String) : Exception(message) {
    class Network(cause: Throwable) : AuthError(cause.message ?: "Network error")
    class InvalidCredentials(detail: String?) : AuthError(detail ?: "Invalid credentials")
    class InvalidOtpCode(detail: String?) : AuthError(detail ?: "Invalid or expired code")
    class InvalidPasswordResetCode(detail: String?) : AuthError(detail ?: "Invalid or expired reset code")
    class InvalidRefreshToken(detail: String?) : AuthError(detail ?: "Invalid refresh token")
    class RefreshTokenExpired(detail: String?) : AuthError(detail ?: "Refresh token expired")
    class AuthenticationRequired(detail: String?) : AuthError(detail ?: "Authentication required")
    class RateLimited(detail: String?) : AuthError(detail ?: "Too many requests")
    class CaptchaFailed(detail: String?) : AuthError(detail ?: "CAPTCHA verification failed")
    class Validation(
        detail: String?,
        val fields: Set<String> = emptySet(),
    ) : AuthError(detail ?: "Validation failed")
    class InvalidInvitation(detail: String?) : AuthError(detail ?: "Invalid invitation")
    class InvitationEmailMismatch(detail: String?) : AuthError(detail ?: "Email doesn't match invitation")
    class EmailAlreadyRegistered(detail: String?) : AuthError(detail ?: "Email already registered")
    class Server(val statusCode: Int, detail: String?) : AuthError(detail ?: "Server error ($statusCode)")
}
