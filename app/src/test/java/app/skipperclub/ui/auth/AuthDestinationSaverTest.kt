package app.skipperclub.ui.auth

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthDestinationSaverTest {
    @Test
    fun savesAndRestoresEveryAuthDestination() {
        val destinations = listOf(
            AuthDestination.Login,
            AuthDestination.Password("sailor@example.com"),
            AuthDestination.PasswordResetRequest("sailor@example.com", linkSent = true),
            AuthDestination.PasswordReset("sailor@example.com", "123456"),
            AuthDestination.PasswordResetComplete("sailor@example.com"),
            AuthDestination.OtpVerify("sailor@example.com"),
            AuthDestination.JoinByInvitation("ABC12345"),
        )

        destinations.forEach { destination ->
            val saved = with(AuthDestinationSaver) { alwaysSaveable.save(destination) }
            assertEquals(destination, AuthDestinationSaver.restore(checkNotNull(saved)))
        }
    }

    private companion object {
        val alwaysSaveable = SaverScope { true }
    }
}
