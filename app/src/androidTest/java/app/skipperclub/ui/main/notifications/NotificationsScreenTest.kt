package app.skipperclub.ui.main.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.skipperclub.R
import app.skipperclub.data.AppNotification
import app.skipperclub.data.NotificationEventType
import app.skipperclub.data.NotificationSourceType
import app.skipperclub.data.NotificationStatus
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val nowMillis = 1_781_337_600_000L

    private val unread = previewNotification(
        "n1",
        NotificationEventType.CruiseInvitationSent,
        NotificationSourceType.Cruise,
        metadata = mapOf("actorName" to "Anna Nowak", "cruiseTitle" to "Mazury 2026"),
    )
    private val read = previewNotification(
        "n2",
        NotificationEventType.PostReacted,
        NotificationSourceType.Post,
        status = NotificationStatus.Read,
        metadata = mapOf("actorName" to "Jan Kowalski"),
    )

    @Test
    fun rendersNotificationRowsWithRenderedText() {
        content(NotificationsUiState(notifications = listOf(unread, read), hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.notifications_title)).assertExists()
        compose.onNodeWithTag("notification_item_n1").assertExists()
        compose.onNodeWithTag("notification_item_n2").assertExists()
    }

    @Test
    fun emptyStateShownWhenNoNotifications() {
        content(NotificationsUiState(hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.notifications_empty_title)).assertExists()
        compose.onNodeWithText(text(R.string.notifications_empty_subtitle)).assertExists()
    }

    @Test
    fun markAllReadVisibleOnlyWithUnreadAndEmitsCallback() {
        var marked = 0
        content(
            state = NotificationsUiState(notifications = listOf(unread, read), hasLoadedOnce = true),
            onMarkAllRead = { marked++ },
        )

        compose.onNodeWithTag("notifications_mark_all_read").performClick()
        assertEquals(1, marked)
    }

    @Test
    fun markAllReadHiddenWhenEverythingRead() {
        content(NotificationsUiState(notifications = listOf(read), hasLoadedOnce = true))

        compose.onNodeWithTag("notifications_mark_all_read").assertDoesNotExist()
    }

    @Test
    fun tappingRowEmitsOpenCallback() {
        var opened: AppNotification? = null
        content(
            state = NotificationsUiState(notifications = listOf(unread), hasLoadedOnce = true),
            onOpen = { opened = it },
        )

        compose.onNodeWithTag("notification_item_n1").performClick()
        assertEquals("n1", opened?.id)
    }

    @Test
    fun backButtonEmitsCloseCallback() {
        var closed = 0
        content(
            state = NotificationsUiState(notifications = listOf(unread), hasLoadedOnce = true),
            onClose = { closed++ },
        )

        compose.onNodeWithTag("notifications_back").performClick()
        assertEquals(1, closed)
    }

    private fun content(
        state: NotificationsUiState,
        onClose: () -> Unit = {},
        onMarkAllRead: () -> Unit = {},
        onOpen: (AppNotification) -> Unit = {},
    ) {
        compose.setContent {
            SkipperClubTheme {
                NotificationsScreenContent(
                    state = state,
                    nowMillis = nowMillis,
                    onClose = onClose,
                    onMarkAllRead = onMarkAllRead,
                    onOpen = onOpen,
                    onMarkRead = {},
                    onDelete = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
