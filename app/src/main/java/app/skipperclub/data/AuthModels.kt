package app.skipperclub.data

import kotlinx.serialization.Serializable

@Serializable
internal data class OtpRequest(val email: String)

@Serializable
internal data class OtpVerifyRequest(val email: String, val code: String)

@Serializable
internal data class LoginRequest(val email: String, val password: String)

@Serializable
internal data class PasswordResetRequest(val email: String)

@Serializable
internal data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    val password: String,
)

@Serializable
internal data class InvitationRegisterRequest(
    val code: String,
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
internal data class RefreshSessionRequest(val refreshToken: String)

@Serializable
internal data class RefreshSessionResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

@Serializable
data class SessionResponse(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: SessionUser,
)

@Serializable
data class SessionUser(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
)

@Serializable
internal data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val violations: List<ProblemViolation> = emptyList(),
)

@Serializable
internal data class ProblemViolation(
    val propertyPath: String? = null,
    val message: String? = null,
)
