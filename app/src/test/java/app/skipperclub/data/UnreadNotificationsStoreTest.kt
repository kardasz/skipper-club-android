package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal fun testAppNotification(
    id: String,
    status: NotificationStatus = NotificationStatus.Unread,
) = AppNotification(
    id = id,
    eventType = NotificationEventType.CruiseInvitationSent,
    sourceType = NotificationSourceType.Cruise,
    sourceId = "src-$id",
    relationId = "rel-$id",
    status = status,
    metadata = mapOf("actorName" to "Anna", "cruiseTitle" to "Mazury"),
    createdAt = "2026-06-13T09:00:00Z",
    readAt = if (status == NotificationStatus.Read) "2026-06-13T09:30:00Z" else null,
)

class UnreadNotificationsStoreTest {

    @Test
    fun unreadNotificationNewBumpsTheCount() {
        assertEquals(
            1,
            unreadNotificationsCountAfter(0, ChatRealtimeEvent.NotificationNew(testAppNotification("n1"))),
        )
        assertEquals(
            6,
            unreadNotificationsCountAfter(5, ChatRealtimeEvent.NotificationNew(testAppNotification("n2"))),
        )
    }

    @Test
    fun readNotificationNewDoesNotBumpTheCount() {
        // Defensive: the server only pushes fresh (unread) notifications, but if a read one ever
        // arrives it must not inflate the badge.
        val event = ChatRealtimeEvent.NotificationNew(
            testAppNotification("n1", status = NotificationStatus.Read),
        )

        assertNull(unreadNotificationsCountAfter(3, event))
    }

    @Test
    fun connectedTriggersReconcileNotAnOptimisticChange() {
        // Connected returns null (no optimistic change) — the store reconciles from REST instead.
        assertNull(unreadNotificationsCountAfter(3, ChatRealtimeEvent.Connected))
    }

    @Test
    fun otherEventsLeaveTheCountUnchanged() {
        val message = ChatMessage(
            id = "m1",
            chatId = "c1",
            text = "hi",
            read = false,
            user = ChatUser(id = "u1", name = "User"),
            createdAt = "2026-06-12T10:00:00Z",
            updatedAt = "2026-06-12T10:00:00Z",
        )

        assertNull(unreadNotificationsCountAfter(2, ChatRealtimeEvent.Disconnected))
        // A new chat message is the messages badge's concern (UnreadMessagesStore), not this one's.
        assertNull(unreadNotificationsCountAfter(2, ChatRealtimeEvent.MessageReceived(message)))
        assertNull(unreadNotificationsCountAfter(2, ChatRealtimeEvent.MessageNew(message)))
    }
}
