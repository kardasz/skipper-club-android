package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnreadMessagesStoreTest {

    private fun message(chatId: String = "c1") = ChatMessage(
        id = "m1",
        chatId = chatId,
        text = "hi",
        read = false,
        user = ChatUser(id = "u1", name = "User"),
        createdAt = "2026-06-12T10:00:00Z",
        updatedAt = "2026-06-12T10:00:00Z",
    )

    @Test
    fun messageReceivedBumpsTheCount() {
        assertEquals(1, unreadCountAfter(0, ChatRealtimeEvent.MessageReceived(message())))
        assertEquals(6, unreadCountAfter(5, ChatRealtimeEvent.MessageReceived(message())))
    }

    @Test
    fun messageNewDoesNotBumpTheCount() {
        // The open room's message:new is not a personal-room notification; it must not
        // increment the app-wide badge (that chat is being read).
        assertNull(unreadCountAfter(3, ChatRealtimeEvent.MessageNew(message())))
    }

    @Test
    fun connectedTriggersReconcileNotAnOptimisticChange() {
        // Connected returns null (no optimistic change) — the store reconciles from REST instead.
        assertNull(unreadCountAfter(3, ChatRealtimeEvent.Connected))
    }

    @Test
    fun messageReceivedForTheOpenChatDoesNotBumpTheCount() {
        // The user is looking at this conversation and the screen marks it read on arrival, so a
        // badge here counts up for a message being read and then clears itself moments later.
        assertNull(
            unreadCountAfter(3, ChatRealtimeEvent.MessageReceived(message("c1")), activeChatId = "c1"),
        )
    }

    @Test
    fun messageReceivedForAnotherChatStillBumpsTheCountWhileAChatIsOpen() {
        assertEquals(
            4,
            unreadCountAfter(3, ChatRealtimeEvent.MessageReceived(message("c2")), activeChatId = "c1"),
        )
    }

    @Test
    fun otherEventsLeaveTheCountUnchanged() {
        assertNull(unreadCountAfter(2, ChatRealtimeEvent.Disconnected))
        assertNull(unreadCountAfter(2, ChatRealtimeEvent.NotificationNew(testAppNotification("n1"))))
    }
}
