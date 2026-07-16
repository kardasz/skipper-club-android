package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends, so an Unconfined scope runs every launched
 * coroutine to completion synchronously; the search debounce is set to zero.
 */
class ChatListControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeChatsGateway()
    private val events = mutableListOf<ChatListEvent>()

    private fun controller(token: String? = "token"): ChatListController {
        val controller = ChatListController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            pageSize = 2,
            searchDebounceMillis = 0,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesChatsAndPagingState() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"), testChat("c2")), total = 5))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("c1", "c2"), state.chats.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertEquals(0, gateway.listChatsQueries.single().offset)
    }

    @Test
    fun loadInitialIsIdempotent() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"))))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "listChats" })
    }

    @Test
    fun searchQueryIsTrimmedAndSentToServer() {
        gateway.chatPages = listOf(chatsPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.setSearchQuery("  jan ")

        assertEquals("jan", gateway.listChatsQueries.last().search)
        assertEquals("  jan ", controller.state.value.searchQuery)
    }

    @Test
    fun blankSearchQueryIsOmittedFromQuery() {
        gateway.chatPages = listOf(chatsPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.setSearchQuery("jan")
        controller.setSearchQuery("")

        assertNull(gateway.listChatsQueries.last().search)
    }

    @Test
    fun typeFilterReloadsWithTypeParameter() {
        gateway.chatPages = listOf(chatsPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.setTypeFilter(ChatType.Group)

        assertEquals(ChatType.Group, gateway.listChatsQueries.last().type)
        assertEquals(ChatType.Group, controller.state.value.typeFilter)

        controller.setTypeFilter(null)
        assertNull(gateway.listChatsQueries.last().type)
    }

    @Test
    fun loadMoreAppendsNextPageWithOffsetAndDeduplicates() {
        gateway.chatPages = listOf(
            chatsPage(listOf(testChat("c1"), testChat("c2")), total = 3),
            chatsPage(listOf(testChat("c2"), testChat("c3")), total = 3, offset = 2),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf("c1", "c2", "c3"), state.chats.map { it.id })
        assertEquals(2, gateway.listChatsQueries.last().offset)
    }

    @Test
    fun loadMoreIsNoopWithoutMorePages() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        assertEquals(1, gateway.calls.count { it == "listChats" })
    }

    @Test
    fun deleteChatRemovesItAndEmitsEvent() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"), testChat("c2"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.deleteChat(controller.state.value.chats.first())

        assertEquals(listOf("c2"), controller.state.value.chats.map { it.id })
        assertTrue(gateway.calls.contains("deleteChat:c1"))
        assertTrue(events.contains(ChatListEvent.ChatDeleted))
    }

    @Test
    fun deleteChatFailureKeepsChatAndEmitsError() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = ChatsError.NotFound(null)

        controller.deleteChat(controller.state.value.chats.first())

        assertEquals(listOf("c1"), controller.state.value.chats.map { it.id })
        assertTrue(events.any { it is ChatListEvent.OperationFailed })
    }

    @Test
    fun markChatReadClearsBadgeOptimisticallyAndCallsGateway() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1", unreadCount = 4))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.markChatRead(controller.state.value.chats.first())

        assertEquals(0, controller.state.value.chats.first().unreadCount)
        assertTrue(gateway.calls.contains("markChatsRead:c1"))
    }

    @Test
    fun markChatReadSkipsAlreadyReadChats() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1", unreadCount = 0))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.markChatRead(controller.state.value.chats.first())

        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun onChatOpenedClearsUnreadLocally() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1", unreadCount = 7))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onChatOpened("c1")

        assertEquals(0, controller.state.value.chats.first().unreadCount)
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun onChatCreatedPrependsWithoutDuplicates() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onChatCreated(testChat("c1"))
        controller.onChatCreated(testChat("c2"))

        assertEquals(listOf("c2", "c1"), controller.state.value.chats.map { it.id })
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.listChatsError = ChatsError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertTrue(state.loadFailed)
        assertTrue(state.hasLoadedOnce)
        assertTrue(events.any { it is ChatListEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(ChatListEvent.SessionExpired))
    }

    @Test
    fun realtimeMessageBumpsChatWithPreviewAndUnread() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1"), testChat("c2", unreadCount = 1))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        val message = testMessage("m9", chatId = "c2", text = "Ahoy!", createdAt = "2026-06-12T11:00:00Z")
        controller.onRealtimeMessage(message, isChatOpen = false)

        val state = controller.state.value
        assertEquals(listOf("c2", "c1"), state.chats.map { it.id })
        val updated = state.chats.first()
        assertEquals("Ahoy!", updated.lastMessage?.text)
        assertEquals("2026-06-12T11:00:00Z", updated.updatedAt)
        assertEquals(2, updated.unreadCount)
    }

    @Test
    fun realtimeMessageIsIdempotentAcrossDoubleDelivery() {
        // The open chat is delivered twice for one message (message:new + message:received);
        // the second application must not increment unread again.
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1", unreadCount = 2))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        val message = testMessage("m9", chatId = "c1", text = "Ahoy!")
        controller.onRealtimeMessage(message, isChatOpen = false)
        controller.onRealtimeMessage(message, isChatOpen = false)

        val updated = controller.state.value.chats.first()
        assertEquals(3, updated.unreadCount)
        assertEquals("m9", updated.lastMessage?.id)
    }

    @Test
    fun realtimeMessageForOpenChatKeepsUnreadAtZero() {
        gateway.chatPages = listOf(chatsPage(listOf(testChat("c1", unreadCount = 0))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m9", chatId = "c1"), isChatOpen = true)

        assertEquals(0, controller.state.value.chats.first().unreadCount)
        assertEquals("m9", controller.state.value.chats.first().lastMessage?.id)
    }

    @Test
    fun realtimeMessageForUnknownChatTriggersReload() {
        gateway.chatPages = listOf(
            chatsPage(listOf(testChat("c1"))),
            chatsPage(listOf(testChat("c-new"), testChat("c1"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m9", chatId = "c-new"), isChatOpen = false)

        assertEquals(2, gateway.calls.count { it == "listChats" })
        assertEquals(listOf("c-new", "c1"), controller.state.value.chats.map { it.id })
    }

    @Test
    fun realtimeMessageBeforeInitialLoadIsIgnored() {
        val controller = controller()

        controller.onRealtimeMessage(testMessage("m9", chatId = "c1"), isChatOpen = false)

        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun reconnectReloadsListToCatchUpMissedMessages() {
        gateway.chatPages = listOf(
            chatsPage(listOf(testChat("c1"))),
            chatsPage(listOf(testChat("c2"), testChat("c1"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeReconnected()

        assertEquals(2, gateway.calls.count { it == "listChats" })
        assertEquals(listOf("c2", "c1"), controller.state.value.chats.map { it.id })
    }

    @Test
    fun reconnectBeforeInitialLoadIsIgnored() {
        val controller = controller()

        controller.onRealtimeReconnected()

        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun refreshReplacesChats() {
        gateway.chatPages = listOf(
            chatsPage(listOf(testChat("c1"))),
            chatsPage(listOf(testChat("c2"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.refresh()

        assertEquals(listOf("c2"), controller.state.value.chats.map { it.id })
    }
}
