package app.skipperclub.ui.auth

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver

sealed interface AuthDestination {
    data object Login : AuthDestination
    data class Password(val email: String) : AuthDestination
    data class PasswordResetRequest(val email: String = "", val linkSent: Boolean = false) : AuthDestination
    data class PasswordReset(val email: String, val code: String) : AuthDestination
    data class PasswordResetComplete(val email: String) : AuthDestination
    data class OtpVerify(val email: String) : AuthDestination
    data class JoinByInvitation(val code: String = "") : AuthDestination
}

private const val TYPE_KEY = "type"
private const val EMAIL_KEY = "email"
private const val CODE_KEY = "code"
private const val TYPE_LOGIN = "login"
private const val TYPE_PASSWORD = "password"
private const val TYPE_PASSWORD_RESET_REQUEST = "password-reset-request"
private const val TYPE_PASSWORD_RESET = "password-reset"
private const val TYPE_PASSWORD_RESET_COMPLETE = "password-reset-complete"
private const val TYPE_OTP = "otp"
private const val TYPE_INVITATION = "invitation"
private const val LINK_SENT_KEY = "linkSent"

val AuthDestinationSaver: Saver<AuthDestination, Any> = mapSaver(
    save = { destination ->
        when (destination) {
            AuthDestination.Login -> mapOf(TYPE_KEY to TYPE_LOGIN)
            is AuthDestination.Password -> mapOf(
                TYPE_KEY to TYPE_PASSWORD,
                EMAIL_KEY to destination.email,
            )
            is AuthDestination.PasswordResetRequest -> mapOf(
                TYPE_KEY to TYPE_PASSWORD_RESET_REQUEST,
                EMAIL_KEY to destination.email,
                LINK_SENT_KEY to destination.linkSent,
            )
            is AuthDestination.PasswordReset -> mapOf(
                TYPE_KEY to TYPE_PASSWORD_RESET,
                EMAIL_KEY to destination.email,
                CODE_KEY to destination.code,
            )
            is AuthDestination.PasswordResetComplete -> mapOf(
                TYPE_KEY to TYPE_PASSWORD_RESET_COMPLETE,
                EMAIL_KEY to destination.email,
            )
            is AuthDestination.OtpVerify -> mapOf(
                TYPE_KEY to TYPE_OTP,
                EMAIL_KEY to destination.email,
            )
            is AuthDestination.JoinByInvitation -> mapOf(
                TYPE_KEY to TYPE_INVITATION,
                CODE_KEY to destination.code,
            )
        }
    },
    restore = { saved ->
        when (saved[TYPE_KEY] as? String) {
            TYPE_PASSWORD -> AuthDestination.Password(saved[EMAIL_KEY] as? String ?: "")
            TYPE_PASSWORD_RESET_REQUEST -> AuthDestination.PasswordResetRequest(
                email = saved[EMAIL_KEY] as? String ?: "",
                linkSent = saved[LINK_SENT_KEY] as? Boolean ?: false,
            )
            TYPE_PASSWORD_RESET -> AuthDestination.PasswordReset(
                email = saved[EMAIL_KEY] as? String ?: "",
                code = saved[CODE_KEY] as? String ?: "",
            )
            TYPE_PASSWORD_RESET_COMPLETE -> AuthDestination.PasswordResetComplete(
                saved[EMAIL_KEY] as? String ?: "",
            )
            TYPE_OTP -> AuthDestination.OtpVerify(saved[EMAIL_KEY] as? String ?: "")
            TYPE_INVITATION -> AuthDestination.JoinByInvitation(saved[CODE_KEY] as? String ?: "")
            else -> AuthDestination.Login
        }
    },
)
