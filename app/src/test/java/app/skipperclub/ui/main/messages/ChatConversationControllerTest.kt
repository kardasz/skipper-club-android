package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConversationControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeChatsGateway()
    private val events = mutableListOf<ChatConversationEvent>()

    private fun controller(token: String? = "token"): ChatConversationController {
        val controller = ChatConversationController(
            scope = scope,
            accessToken = { token },
            chatId = "chat-1",
            currentUserId = "me",
            gateway = gateway,
            pageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadFetchesChatAndMessagesAscending() {
        gateway.chat = testChat("chat-1")
        // The API returns newest first; the controller keeps them oldest first.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:05:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:00:00Z"),
                ),
                total = 5,
            ),
        )
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals("chat-1", state.chat?.id)
        assertEquals(listOf("m1", "m2"), state.messages.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
    }

    @Test
    fun initialLoadMarksUnreadChatAsRead() {
        gateway.chat = testChat("chat-1", unreadCount = 3)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(gateway.calls.contains("markChatsRead:chat-1"))
    }

    @Test
    fun initialLoadSkipsMarkReadWhenNothingUnread() {
        gateway.chat = testChat("chat-1", unreadCount = 0)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun loadMorePrependsOlderMessages() {
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m4", createdAt = "2026-06-12T10:04:00Z"),
                    testMessage("m3", createdAt = "2026-06-12T10:03:00Z"),
                ),
                total = 4,
            ),
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 4,
                offset = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf("m1", "m2", "m3", "m4"), state.messages.map { it.id })
        assertFalse(state.hasMore)
        assertEquals(listOf(0, 2), gateway.listMessagesOffsets)
    }

    @Test
    fun sendAppendsReturnedMessage() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.send("  Ahoy!  ")

        val state = controller.state.value
        assertEquals(listOf("m1", "sent"), state.messages.map { it.id })
        assertFalse(state.isSending)
        assertTrue(gateway.calls.contains("sendMessage:chat-1:Ahoy!"))
        assertTrue(events.contains(ChatConversationEvent.MessageSent))
    }

    @Test
    fun sendIgnoresBlankText() {
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.send("   ")

        assertFalse(gateway.calls.any { it.startsWith("sendMessage") })
    }

    @Test
    fun sendFailureEmitsErrorAndResetsSendingFlag() {
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = ChatsError.Validation(null)

        controller.send("Hello")

        assertFalse(controller.state.value.isSending)
        assertTrue(events.any { it is ChatConversationEvent.OperationFailed })
    }

    @Test
    fun refreshNewMessagesAppendsOnlyUnseenAndMarksRead() {
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1")), total = 1),
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:06:00Z"),
                    testMessage("m1"),
                ),
                total = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.refreshNewMessages()

        assertEquals(listOf("m1", "m2"), controller.state.value.messages.map { it.id })
        assertTrue(gateway.calls.contains("markChatsRead:chat-1"))
    }

    @Test
    fun refreshNewMessagesWithoutNewsDoesNotMarkRead() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.refreshNewMessages()

        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun refreshNewMessagesBeforeInitialLoadIsNoop() {
        val controller = controller()

        controller.refreshNewMessages()

        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun realtimeMessageAppendsAndMarksRead() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m2", userId = "other"))

        assertEquals(listOf("m1", "m2"), controller.state.value.messages.map { it.id })
        assertTrue(gateway.calls.contains("markChatsRead:chat-1"))
    }

    @Test
    fun realtimeOwnMessageDoesNotMarkRead() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m2", userId = "me"))

        assertEquals(listOf("m1", "m2"), controller.state.value.messages.map { it.id })
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun realtimeMessageIsDeduplicatedById() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m1"))

        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun realtimeMessageForOtherChatIsIgnored() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m2", chatId = "other-chat"))

        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })
    }

    @Test
    fun realtimeMessageBeforeInitialLoadIsIgnored() {
        val controller = controller()

        controller.onRealtimeMessage(testMessage("m1"))

        assertTrue(controller.state.value.messages.isEmpty())
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.getChatError = ChatsError.NotFound(null)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is ChatConversationEvent.OperationFailed })
    }

    @Test
    fun retryAfterFailureReloads() {
        gateway.getChatError = ChatsError.Network(Exception("offline"))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.getChatError = null

        controller.retry()

        val state = controller.state.value
        assertFalse(state.loadFailed)
        assertEquals("chat-1", state.chat?.id)
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(ChatConversationEvent.SessionExpired))
    }
}
