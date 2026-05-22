package app.skipperclub.data

import kotlinx.serialization.Serializable

@Serializable
internal data class OtpRequest(val email: String)

@Serializable
internal data class OtpVerifyRequest(val email: String, val code: String)

@Serializable
internal data class LoginRequest(val email: String, val password: String)

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
)
