package app.skipperclub.ui.auth

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver

sealed interface AuthDestination {
    data object Login : AuthDestination
    data class Password(val email: String) : AuthDestination
    data class OtpVerify(val email: String) : AuthDestination
}

private const val TYPE_KEY = "type"
private const val EMAIL_KEY = "email"
private const val TYPE_LOGIN = "login"
private const val TYPE_PASSWORD = "password"
private const val TYPE_OTP = "otp"

val AuthDestinationSaver: Saver<AuthDestination, Any> = mapSaver(
    save = { destination ->
        when (destination) {
            AuthDestination.Login -> mapOf(TYPE_KEY to TYPE_LOGIN)
            is AuthDestination.Password -> mapOf(
                TYPE_KEY to TYPE_PASSWORD,
                EMAIL_KEY to destination.email,
            )
            is AuthDestination.OtpVerify -> mapOf(
                TYPE_KEY to TYPE_OTP,
                EMAIL_KEY to destination.email,
            )
        }
    },
    restore = { saved ->
        when (saved[TYPE_KEY] as? String) {
            TYPE_PASSWORD -> AuthDestination.Password(saved[EMAIL_KEY] as? String ?: "")
            TYPE_OTP -> AuthDestination.OtpVerify(saved[EMAIL_KEY] as? String ?: "")
            else -> AuthDestination.Login
        }
    },
)
