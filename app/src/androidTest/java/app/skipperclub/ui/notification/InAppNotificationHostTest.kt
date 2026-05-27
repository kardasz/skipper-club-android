package app.skipperclub.ui.notification

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Rule
import org.junit.Test

class InAppNotificationHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hostShowsAndDismissesNotification() {
        val hostState = InAppNotificationHostState().apply {
            show("Saved", InAppNotificationType.Success)
        }

        compose.setContent {
            SkipperClubTheme {
                InAppNotificationHost(
                    hostState = hostState,
                    autoDismissMillis = 10_000L,
                )
            }
        }

        compose.onNodeWithText("Saved").assertExists()
        compose.onNodeWithContentDescription(text(R.string.notification_dismiss)).performClick()
        compose.onAllNodesWithText("Saved").assertCountEquals(0)
    }

    @Test
    fun hostAutoDismissesCurrentNotification() {
        val hostState = InAppNotificationHostState().apply {
            show("Failed", InAppNotificationType.Error)
        }

        compose.setContent {
            SkipperClubTheme {
                InAppNotificationHost(
                    hostState = hostState,
                    autoDismissMillis = 50L,
                )
            }
        }

        compose.onNodeWithText("Failed").assertExists()
        compose.waitUntil(timeoutMillis = 2_000) {
            hostState.current == null
        }
        compose.onAllNodesWithText("Failed").assertCountEquals(0)
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
