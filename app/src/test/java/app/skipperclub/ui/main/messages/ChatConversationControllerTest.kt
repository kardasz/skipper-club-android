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
    private val readReceipts = mutableListOf<Pair<String, String>>()

    private var nextClientMessageId = 0

    private fun controller(
        token: String? = "token",
        typingExpiryMillis: Long = ChatConversationController.TYPING_RECEIVE_EXPIRY_MS,
    ): ChatConversationController {
        val controller = ChatConversationController(
            scope = scope,
            accessToken = { token },
            chatId = "chat-1",
            currentUserId = "me",
            gateway = gateway,
            pageSize = 2,
            typingExpiryMillis = typingExpiryMillis,
            sendReadReceipt = { chatId, messageId -> readReceipts += chatId to messageId },
            clientMessageIdProvider = { "client-id-${nextClientMessageId++}" },
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
    fun sendPassesOneClientMessageIdPerLogicalMessage() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.send("First")
        controller.send("Second")

        // Every send carries the idempotency key; a fresh one is minted per logical message so two
        // distinct messages can never collapse into one server-side.
        assertEquals(listOf("client-id-0", "client-id-1"), gateway.sentClientMessageIds)
    }

    @Test
    fun sendGeneratesRandomUuidClientMessageIdsByDefault() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = ChatConversationController(
            scope = scope,
            accessToken = { "token" },
            chatId = "chat-1",
            currentUserId = "me",
            gateway = gateway,
            sendReadReceipt = { _, _ -> },
        )
        controller.loadInitialIfNeeded()

        controller.send("Ahoy!")
        controller.send("Ahoy again!")

        assertEquals(2, gateway.sentClientMessageIds.size)
        assertEquals(2, gateway.sentClientMessageIds.toSet().size)
        // Parseable UUIDs (any version) — the wire contract for `clientMessageId`.
        gateway.sentClientMessageIds.forEach { java.util.UUID.fromString(it) }
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

    @Test
    fun initialLoadSendsWsReadReceiptForNewestMessageWhenUnread() {
        gateway.chat = testChat("chat-1", unreadCount = 2)
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:05:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:00:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller()

        controller.loadInitialIfNeeded()

        // Messages are kept oldest-first, so "m2" (the API's newest) is the last/most recent one.
        assertEquals(listOf("chat-1" to "m2"), readReceipts)
    }

    @Test
    fun initialLoadSkipsWsReadReceiptWhenNothingUnread() {
        gateway.chat = testChat("chat-1", unreadCount = 0)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun initialLoadWsReadReceiptSkipsOwnNewestMessageAndTargetsNewestFromOthers() {
        gateway.chat = testChat("chat-1", unreadCount = 2)
        // The API's newest ("m2") is our own; the receipt must target "m1", the newest from someone
        // else, never our own message.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", userId = "me", createdAt = "2026-06-12T10:05:00Z"),
                    testMessage("m1", userId = "other", createdAt = "2026-06-12T10:00:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertEquals(listOf("chat-1" to "m1"), readReceipts)
    }

    @Test
    fun initialLoadSkipsWsReadReceiptWhenAllMessagesAreOwnButStillBulkMarksRead() {
        gateway.chat = testChat("chat-1", unreadCount = 1)
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1", userId = "me")), total = 1),
        )
        val controller = controller()

        controller.loadInitialIfNeeded()

        // No non-own message exists, so no per-message WS receipt is emitted...
        assertTrue(readReceipts.isEmpty())
        // ...but the REST bulk mark-read is unchanged.
        assertTrue(gateway.calls.contains("markChatsRead:chat-1"))
    }

    @Test
    fun typingReceiveExpiryDefaultsToFiveSeconds() {
        assertEquals(5_000L, ChatConversationController.TYPING_RECEIVE_EXPIRY_MS)
    }

    @Test
    fun realtimeTypingAddsUserToTypingSet() {
        val controller = controller()

        controller.onRealtimeTyping("chat-1", "other", isTyping = true)

        assertTrue(controller.state.value.typingUserIds.contains("other"))
    }

    @Test
    fun realtimeTypingFalseRemovesUser() {
        val controller = controller()
        controller.onRealtimeTyping("chat-1", "other", isTyping = true)

        controller.onRealtimeTyping("chat-1", "other", isTyping = false)

        assertFalse(controller.state.value.typingUserIds.contains("other"))
    }

    @Test
    fun realtimeTypingAutoExpiresAsASafetyNetForALostStopEvent() {
        // Zero-length expiry + the Unconfined test scope runs the safety-net timer to completion
        // synchronously, proving a lost `isTyping:false` does not leave the indicator stuck.
        val controller = controller(typingExpiryMillis = 0)

        controller.onRealtimeTyping("chat-1", "other", isTyping = true)

        assertFalse(controller.state.value.typingUserIds.contains("other"))
    }

    @Test
    fun realtimeTypingForOtherChatIsIgnored() {
        val controller = controller()

        controller.onRealtimeTyping("other-chat", "other", isTyping = true)

        assertTrue(controller.state.value.typingUserIds.isEmpty())
    }

    @Test
    fun realtimeTypingFromSelfIsIgnored() {
        val controller = controller()

        controller.onRealtimeTyping("chat-1", "me", isTyping = true)

        assertTrue(controller.state.value.typingUserIds.isEmpty())
    }

    @Test
    fun realtimeMessageReadMarksMatchingOwnMessageAsRead() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1", userId = "me"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("m1", userId = "other")

        assertTrue(controller.state.value.messages.first { it.id == "m1" }.read)
    }

    @Test
    fun realtimeMessageReadCascadesToAllEarlierOwnMessages() {
        // Receipts are cumulative: reading the newest message means everything before it was read
        // too, so a single receipt for "m3" must flip the earlier own bubbles as well.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m3", userId = "me", createdAt = "2026-06-12T10:03:00Z"),
                    testMessage("m2", userId = "me", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", userId = "me", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 3,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("m3", userId = "other")

        assertTrue(controller.state.value.messages.all { it.read })
    }

    @Test
    fun realtimeMessageReadDoesNotMarkOwnMessagesAfterTheReceiptPosition() {
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m3", userId = "me", createdAt = "2026-06-12T10:03:00Z"),
                    testMessage("m2", userId = "me", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", userId = "me", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 3,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("m2", userId = "other")

        val byId = controller.state.value.messages.associateBy { it.id }
        assertTrue(byId.getValue("m1").read)
        assertTrue(byId.getValue("m2").read)
        assertFalse(byId.getValue("m3").read)
    }

    @Test
    fun realtimeMessageReadCascadePastAnotherParticipantsMessageFlipsOnlyOwnOnes() {
        // The receipt position may be another participant's message (they read up to their own
        // newest); earlier own messages still flip, but the other side's bubbles keep their flag
        // (it means *we* read them, not that they were seen).
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m3", userId = "other", createdAt = "2026-06-12T10:03:00Z"),
                    testMessage("m2", userId = "other", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", userId = "me", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 3,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("m3", userId = "other")

        val byId = controller.state.value.messages.associateBy { it.id }
        assertTrue(byId.getValue("m1").read)
        assertFalse(byId.getValue("m2").read)
        assertFalse(byId.getValue("m3").read)
    }

    @Test
    fun realtimeMessageReadForUnknownMessageIsIgnored() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1", userId = "me"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("not-loaded", userId = "other")

        assertFalse(controller.state.value.messages.first { it.id == "m1" }.read)
    }

    @Test
    fun realtimeMessageReadFromSelfIsIgnored() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1", userId = "me"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("m1", userId = "me")

        assertFalse(controller.state.value.messages.first { it.id == "m1" }.read)
    }
}
