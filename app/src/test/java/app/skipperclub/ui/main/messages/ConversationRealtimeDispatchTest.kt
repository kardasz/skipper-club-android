package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatRealtimeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the conversation's side of the shared socket ([applyConversationRealtimeEvent]) — in
 * particular the C-AN-1 regression: after a background→foreground cycle the client's deliberate
 * `disconnect()` has cleared its joined-room set, so the reconnect's onOpen replay is empty and
 * only the screen's own `Connected` → rejoin → `chat:joined` → catch-up chain can close the gap.
 */
class ConversationRealtimeDispatchTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeChatsGateway()
    private val joins = mutableListOf<String>()

    private fun controller(): ChatConversationController = ChatConversationController(
        scope = scope,
        accessToken = { "token" },
        chatId = "chat-1",
        currentUserId = "me",
        gateway = gateway,
        pageSize = 2,
        catchUpLimit = 2,
        readReceiptDebounceMillis = 0L,
        sendReadReceipt = { _, _ -> },
    )

    private fun dispatch(event: ChatRealtimeEvent, controller: ChatConversationController) {
        applyConversationRealtimeEvent(
            event = event,
            chatId = "chat-1",
            controller = controller,
            rejoinChat = { joins += it },
        )
    }

    @After
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun backgroundForegroundCycleRejoinsAndCatchesUpOnMissedMessages() {
        gateway.messagePages = listOf(
            // Initial page before backgrounding.
            messagesPage(listOf(testMessage("m1", createdAt = "2026-06-12T10:01:00Z")), total = 1),
            // Catch-up page after the foreground rejoin: m2 was sent while the app was in the
            // background; the page reaches back to the local anchor m1, closing the gap.
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()
        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })

        // Backgrounding: RealtimeConnectionManager disconnects deliberately, which also clears the
        // client's joined-room set — nothing but this screen can restore the room afterwards.
        dispatch(ChatRealtimeEvent.Disconnected, controller)
        // Foreground: the socket reconnects; the screen must re-join its room.
        dispatch(ChatRealtimeEvent.Connected, controller)
        assertEquals(listOf("chat-1"), joins)

        // The re-join's ack is what drives the catch-up (never Connected itself).
        dispatch(ChatRealtimeEvent.ChatJoined("chat-1"), controller)

        assertEquals(listOf("m1", "m2"), controller.state.value.messages.map { it.id })
    }

    @Test
    fun everyConnectedTriggersARejoin() {
        // Server-side drops reconnect too; the rejoin lambda itself decides (via the client's
        // joined-room membership) whether a frame actually goes out, so dispatch must never
        // swallow the trigger.
        val controller = controller()

        dispatch(ChatRealtimeEvent.Connected, controller)
        dispatch(ChatRealtimeEvent.Disconnected, controller)
        dispatch(ChatRealtimeEvent.Connected, controller)

        assertEquals(listOf("chat-1", "chat-1"), joins)
    }

    @Test
    fun chatJoinedForAnotherChatDoesNotCatchUp() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1")), total = 1))
        val controller = controller()
        controller.loadInitialIfNeeded()
        val callsAfterLoad = gateway.calls.count { it.startsWith("listMessages") }

        dispatch(ChatRealtimeEvent.ChatJoined("other-chat"), controller)

        assertEquals(callsAfterLoad, gateway.calls.count { it.startsWith("listMessages") })
    }

    @Test
    fun chatJoinedForThisChatCatchesUp() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1")), total = 1))
        val controller = controller()
        controller.loadInitialIfNeeded()

        dispatch(ChatRealtimeEvent.ChatJoined("chat-1"), controller)

        assertEquals(2, gateway.calls.count { it.startsWith("listMessages") })
    }

    @Test
    fun disconnectedClearsTheTypingIndicators() {
        val controller = controller()
        dispatch(
            ChatRealtimeEvent.TypingUpdate(chatId = "chat-1", userId = "other", isTyping = true),
            controller,
        )
        assertTrue(controller.state.value.typingUserIds.contains("other"))

        dispatch(ChatRealtimeEvent.Disconnected, controller)

        assertTrue(controller.state.value.typingUserIds.isEmpty())
    }

    @Test
    fun messagesRouteToTheControllerOnBothRoomAndPersonalEvents() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1")), total = 1))
        val controller = controller()
        controller.loadInitialIfNeeded()

        dispatch(
            ChatRealtimeEvent.MessageNew(testMessage("m2", createdAt = "2026-06-12T10:02:00Z")),
            controller,
        )
        dispatch(
            ChatRealtimeEvent.MessageReceived(testMessage("m3", createdAt = "2026-06-12T10:03:00Z")),
            controller,
        )

        assertEquals(listOf("m1", "m2", "m3"), controller.state.value.messages.map { it.id })
        assertFalse(joins.contains("chat-1"))
    }
}
