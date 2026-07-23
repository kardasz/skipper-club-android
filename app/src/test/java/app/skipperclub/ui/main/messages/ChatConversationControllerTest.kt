package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatsError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
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
        // Scaled-down mirrors of the shared contract's 30 / 50 / 5 so pages stay readable here.
        pageSize: Int = 2,
        catchUpLimit: Int = 2,
        catchUpMaxPages: Int = 3,
        typingExpiryMillis: Long = ChatConversationController.TYPING_RECEIVE_EXPIRY_MS,
        sendConfirmTimeoutMillis: Long = ChatConversationController.SEND_CONFIRM_TIMEOUT_MILLIS,
        // No debounce by default: `delay(0)` never suspends, so on Dispatchers.Unconfined the
        // mark-read runs inline and these tests stay synchronous. The coalescing behaviour itself
        // is covered by markReadCoalescesABurstOfArrivals below, which sets a real window.
        readReceiptDebounceMillis: Long = 0L,
        readReceiptMaxLatencyMillis: Long = ChatConversationController.READ_RECEIPT_MAX_LATENCY_MILLIS,
        nowMillis: () -> Long = System::currentTimeMillis,
    ): ChatConversationController {
        val controller = ChatConversationController(
            scope = scope,
            accessToken = { token },
            chatId = "chat-1",
            currentUserId = "me",
            gateway = gateway,
            pageSize = pageSize,
            catchUpLimit = catchUpLimit,
            catchUpMaxPages = catchUpMaxPages,
            typingExpiryMillis = typingExpiryMillis,
            sendConfirmTimeoutMillis = sendConfirmTimeoutMillis,
            readReceiptDebounceMillis = readReceiptDebounceMillis,
            readReceiptMaxLatencyMillis = readReceiptMaxLatencyMillis,
            nowMillis = nowMillis,
            sendReadReceipt = { chatId, messageId -> readReceipts += chatId to messageId },
            clientMessageIdProvider = { "client-id-${nextClientMessageId++}" },
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    /** Nothing outlives a test — in particular the send-confirmation watchdogs left armed by it. */
    @After
    fun cancelScope() {
        scope.cancel()
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
    fun sendAppendsAnOptimisticBubbleBeforeTheGatewayReturns() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        val sendGate = CompletableDeferred<Unit>()
        gateway.sendMessageGate = sendGate

        val clientMessageId = controller.send("  Ahoy!  ")

        assertEquals("client-id-0", clientMessageId)
        val state = controller.state.value
        val optimistic = state.messages.last()
        assertEquals("optimistic-client-id-0", optimistic.id)
        assertEquals("Ahoy!", optimistic.text)
        // Authored by the current user, resolved from the loaded chat's participants.
        assertEquals("me", optimistic.user.id)
        assertEquals("client-id-0", optimistic.clientMessageId)
        assertEquals(MessageSendStatus.Sending, state.sendStatusByClientMessageId["client-id-0"])
        assertTrue(state.isSending)

        sendGate.complete(Unit)
    }

    @Test
    fun sendSuccessReplacesTheOptimisticBubbleInPlace() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        gateway.sentMessage =
            testMessage("server-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0")
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.send("Ahoy!")

        val state = controller.state.value
        // Replaced, not removed-and-re-appended: the list is the same length and the bubble kept
        // its position instead of jumping when the server's createdAt took over.
        assertEquals(listOf("m1", "server-id"), state.messages.map { it.id })
        assertEquals(MessageSendStatus.Sent, state.sendStatusByClientMessageId["client-id-0"])
        assertFalse(state.isSending)
    }

    @Test
    fun wsEchoArrivingFirstReplacesTheOptimisticBubbleAndTheRestResponseAddsNoDuplicate() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        gateway.sentMessage =
            testMessage("server-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0")
        val controller = controller()
        controller.loadInitialIfNeeded()
        val sendGate = CompletableDeferred<Unit>()
        gateway.sendMessageGate = sendGate
        controller.send("Ahoy!")

        controller.onRealtimeMessage(
            testMessage("server-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0"),
        )

        // The echo *replaces* the placeholder rather than being discarded as a duplicate — the row
        // has to pick up the server id, or it would stay unconfirmed until the watchdog fired.
        assertEquals(listOf("m1", "server-id"), controller.state.value.messages.map { it.id })
        assertEquals(
            MessageSendStatus.Sent,
            controller.state.value.sendStatusByClientMessageId["client-id-0"],
        )

        sendGate.complete(Unit)
        assertEquals(listOf("m1", "server-id"), controller.state.value.messages.map { it.id })
        assertFalse(controller.state.value.isSending)
    }

    @Test
    fun sendFailureMarksTheBubbleFailedAndKeepsItInTheConversation() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = ChatsError.Network(Exception("offline"))

        controller.send("Ahoy!")

        val state = controller.state.value
        assertEquals(listOf("m1", "optimistic-client-id-0"), state.messages.map { it.id })
        assertEquals(MessageSendStatus.Failed, state.sendStatusByClientMessageId["client-id-0"])
        assertFalse(state.isSending)
        assertTrue(events.any { it is ChatConversationEvent.OperationFailed })
        // ...and the text comes back so the screen can restore the draft.
        assertEquals(
            listOf(ChatConversationEvent.SendFailed("client-id-0", "Ahoy!")),
            events.filterIsInstance<ChatConversationEvent.SendFailed>(),
        )
    }

    @Test
    fun retrySendReusesTheSameClientMessageIdAndFlipsBackToSending() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = ChatsError.Network(Exception("offline"))
        controller.send("Ahoy!")
        gateway.mutationError = null
        val retryGate = CompletableDeferred<Unit>()
        gateway.sendMessageGate = retryGate

        controller.retrySend("client-id-0")

        assertEquals(
            MessageSendStatus.Sending,
            controller.state.value.sendStatusByClientMessageId["client-id-0"],
        )
        // The *same* idempotency key goes back on the wire: the backend returns the message it
        // already created for a replay instead of a second one.
        assertEquals(listOf("client-id-0", "client-id-0"), gateway.sentClientMessageIds)

        gateway.sentMessage =
            testMessage("server-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0")
        retryGate.complete(Unit)

        assertEquals(listOf("m1", "server-id"), controller.state.value.messages.map { it.id })
        assertEquals(
            MessageSendStatus.Sent,
            controller.state.value.sendStatusByClientMessageId["client-id-0"],
        )
    }

    @Test
    fun retrySendIgnoresAnEntryThatIsNotFailed() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        controller.send("Ahoy!")

        controller.retrySend("client-id-0")
        controller.retrySend("never-sent")

        assertEquals(listOf("client-id-0"), gateway.sentClientMessageIds)
    }

    @Test
    fun sendConfirmWatchdogFlipsAnUnconfirmedEntryToFailed() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        // Zero timeout so the watchdog runs inline on the Unconfined scope instead of the test
        // sitting out the real 12 seconds.
        val controller = controller(sendConfirmTimeoutMillis = 0L)
        controller.loadInitialIfNeeded()
        // The request neither returns nor throws — the black-holed-connection case the watchdog is
        // the only recovery from.
        gateway.sendMessageGate = CompletableDeferred()

        controller.send("Ahoy!")

        val state = controller.state.value
        assertEquals(listOf("m1", "optimistic-client-id-0"), state.messages.map { it.id })
        assertEquals(MessageSendStatus.Failed, state.sendStatusByClientMessageId["client-id-0"])
        assertFalse(state.isSending)
    }

    @Test
    fun sendConfirmTimeoutMatchesWebAndIos() {
        // web: SEND_CONFIRM_TIMEOUT_MS = 12000; iOS: sendConfirmationTimeout = .seconds(12).
        assertEquals(12_000L, ChatConversationController.SEND_CONFIRM_TIMEOUT_MILLIS)
    }

    @Test
    fun aSecondSendWhileTheFirstIsInFlightIsNotSwallowed() {
        // The old isSending early-return dropped it silently; with a bubble per send, sending two
        // messages in a row is ordinary use.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.sendMessageGate = CompletableDeferred()

        controller.send("First")
        controller.send("Second")

        assertEquals(listOf("First", "Second"), controller.state.value.messages.drop(1).map { it.text })
        assertEquals(listOf("client-id-0", "client-id-1"), gateway.sentClientMessageIds)
        assertTrue(controller.state.value.isSending)
    }

    @Test
    fun sendWithoutAResolvableAuthorFallsBackToTheNonOptimisticBehaviour() {
        // The current user is not among the participants, so a bubble could only be attributed to
        // the wrong person — no bubble is the better failure mode.
        gateway.chat = testChat("chat-1", participants = listOf(testUser("other")))
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        val sendGate = CompletableDeferred<Unit>()
        gateway.sendMessageGate = sendGate

        controller.send("Ahoy!")

        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })
        assertTrue(controller.state.value.isSending)

        sendGate.complete(Unit)

        assertEquals(listOf("m1", "sent"), controller.state.value.messages.map { it.id })
    }

    @Test
    fun catchUpConfirmingASendReplacesTheOptimisticBubble() {
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1", createdAt = "2026-06-12T10:01:00Z")), total = 1),
            messagesPage(
                listOf(
                    testMessage(
                        "server-id",
                        userId = "me",
                        text = "Ahoy!",
                        createdAt = "2026-06-12T10:02:00Z",
                        clientMessageId = "client-id-0",
                    ),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.sendMessageGate = CompletableDeferred()
        controller.send("Ahoy!")

        controller.catchUp()

        assertEquals(listOf("m1", "server-id"), controller.state.value.messages.map { it.id })
        assertEquals(
            MessageSendStatus.Sent,
            controller.state.value.sendStatusByClientMessageId["client-id-0"],
        )
        assertFalse(controller.state.value.isSending)
        // The unconfirmed bubble is not a usable anchor — its id exists nowhere on the server — so
        // the loop anchors on "m1" and the first page already overlaps.
        assertEquals(listOf(0, 0), gateway.listMessagesOffsets)
    }

    @Test
    fun catchUpAppendsOnlyUnseenAndMarksRead() {
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

        controller.catchUp()

        assertEquals(listOf("m1", "m2"), controller.state.value.messages.map { it.id })
        assertTrue(gateway.calls.contains("markChatsRead:chat-1"))
    }

    @Test
    fun catchUpWithoutNewsDoesNotMarkRead() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.catchUp()

        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
    }

    @Test
    fun catchUpBeforeInitialLoadIsNoop() {
        val controller = controller()

        controller.catchUp()

        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun catchUpPagesBackUntilItOverlapsTheNewestKnownMessage() {
        // The outage left a gap of five messages — deeper than one page. A single-page catch-up
        // (the old behaviour) would append m5/m6 and leave m2..m4 unreachable forever, because
        // loadMore pages backwards from the *oldest* row.
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1", createdAt = "2026-06-12T10:01:00Z")), total = 1),
            messagesPage(
                listOf(
                    testMessage("m6", createdAt = "2026-06-12T10:06:00Z"),
                    testMessage("m5", createdAt = "2026-06-12T10:05:00Z"),
                ),
                total = 6,
            ),
            messagesPage(
                listOf(
                    testMessage("m4", createdAt = "2026-06-12T10:04:00Z"),
                    testMessage("m3", createdAt = "2026-06-12T10:03:00Z"),
                ),
                total = 6,
                offset = 2,
            ),
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 6,
                offset = 4,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.catchUp()

        val messages = controller.state.value.messages
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5", "m6"), messages.map { it.id })
        assertEquals(messages.size, messages.distinctBy { it.id }.size)
        // Initial load, then three catch-up pages walking back to the overlap.
        assertEquals(listOf(0, 0, 2, 4), gateway.listMessagesOffsets)
    }

    @Test
    fun catchUpStopsOnTheFirstOverlappingPage() {
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 2,
            ),
            messagesPage(
                listOf(
                    testMessage("m3", createdAt = "2026-06-12T10:03:00Z"),
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                ),
                total = 3,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.catchUp()

        assertEquals(listOf("m1", "m2", "m3"), controller.state.value.messages.map { it.id })
        // One page only: the batch reached back to "m2", so the gap is closed.
        assertEquals(2, gateway.calls.count { it.startsWith("listMessages") })
    }

    @Test
    fun catchUpUsesTheContractPageSizeNotTheHistoryPageSize() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1")), total = 1))
        val controller = controller(pageSize = 30, catchUpLimit = 50)
        controller.loadInitialIfNeeded()

        controller.catchUp()

        assertEquals(listOf(30, 50), gateway.listMessagesLimits)
    }

    @Test
    fun catchUpDeeperThanThePageCapReloadsInsteadOfMergingPartially() {
        // Nothing overlaps within catchUpMaxPages, so the merged window would be an island floating
        // above the local history. The contract calls for a visible full reload instead.
        val farPage = { first: String, second: String ->
            messagesPage(
                listOf(testMessage(first, createdAt = "2026-06-12T11:00:00Z"), testMessage(second)),
                total = 99,
            )
        }
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1", createdAt = "2026-06-12T10:01:00Z")), total = 1),
            farPage("m9", "m8"),
            farPage("m7", "m6"),
            farPage("m5", "m4"),
            messagesPage(
                listOf(
                    testMessage("m9", createdAt = "2026-06-12T10:09:00Z"),
                    testMessage("m8", createdAt = "2026-06-12T10:08:00Z"),
                ),
                total = 9,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.catchUp()

        // Only the fresh page survives — no partial merge, no hole.
        assertEquals(listOf("m8", "m9"), controller.state.value.messages.map { it.id })
        assertTrue(controller.state.value.hasMore)
        assertEquals(listOf(0, 0, 2, 4, 0), gateway.listMessagesOffsets)
    }

    @Test
    fun catchUpWithNothingLocalToAnchorOnReloadsFromPageZero() {
        gateway.messagePages = listOf(
            messagesPage(emptyList(), total = 0),
            messagesPage(listOf(testMessage("m1")), total = 1),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.catchUp()

        assertEquals(listOf("m1"), controller.state.value.messages.map { it.id })
        assertEquals(listOf(0, 0), gateway.listMessagesOffsets)
    }

    @Test
    fun catchUpDoesNotChangeHasMore() {
        // An offset-0 page describes the newest window, not the older history the user is paging
        // through: writing hasMore from it would strand pagination in the middle of the backlog.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 10,
            ),
            messagesPage(
                listOf(
                    testMessage("m3", createdAt = "2026-06-12T10:03:00Z"),
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()
        assertTrue(controller.state.value.hasMore)

        controller.catchUp()

        assertTrue(controller.state.value.hasMore)
    }

    @Test
    fun catchUpRunsWhileASendIsInFlightAndReconcilesItByClientMessageId() {
        // The old isSending guard silently dropped a reconnect's catch-up whenever a send happened
        // to be in flight, and nothing re-armed it. The race it guarded against is handled by the
        // clientMessageId match instead.
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1", createdAt = "2026-06-12T10:01:00Z")), total = 1),
            messagesPage(
                listOf(
                    testMessage(
                        "server-id",
                        userId = "me",
                        text = "Ahoy!",
                        createdAt = "2026-06-12T10:02:00Z",
                        clientMessageId = "client-id-0",
                    ),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 2,
            ),
        )
        gateway.sentMessage = testMessage(
            "server-id",
            userId = "me",
            text = "Ahoy!",
            createdAt = "2026-06-12T10:02:00Z",
            clientMessageId = "client-id-0",
        )
        val controller = controller()
        controller.loadInitialIfNeeded()
        val sendGate = CompletableDeferred<Unit>()
        gateway.sendMessageGate = sendGate
        controller.send("Ahoy!")
        assertTrue(controller.state.value.isSending)

        controller.catchUp()

        // The catch-up ran and delivered the very message still being sent.
        assertEquals(listOf("m1", "server-id"), controller.state.value.messages.map { it.id })

        // ...and the REST response, landing afterwards, is reconciled rather than duplicated.
        sendGate.complete(Unit)
        assertFalse(controller.state.value.isSending)
        assertEquals(listOf("m1", "server-id"), controller.state.value.messages.map { it.id })
    }

    @Test
    fun concurrentCatchUpTriggersCoalesceIntoTheLoopInFlight() {
        // A poll tick landing on top of a chat:joined must not stack a second page-back loop.
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1")), total = 1),
            messagesPage(listOf(testMessage("m1")), total = 1),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()
        val gate = CompletableDeferred<Unit>()
        gateway.listMessagesGate = gate

        controller.catchUp()
        controller.catchUp()
        gate.complete(Unit)

        assertEquals(2, gateway.calls.count { it.startsWith("listMessages") })
    }

    @Test
    fun loadMoreOffsetIgnoresRealtimeArrivalsAndCatchUp() {
        // The offset counts *history* rows this controller fetched, never the size of the rendered
        // list: every realtime arrival would otherwise shift the server-side window by one and skip
        // exactly one older message per arrival.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m5", createdAt = "2026-06-12T10:05:00Z"),
                    testMessage("m4", createdAt = "2026-06-12T10:04:00Z"),
                ),
                total = 10,
            ),
            // The catch-up page confirms both live arrivals, so it overlaps the anchor at once.
            messagesPage(
                listOf(
                    testMessage("m9", createdAt = "2026-06-12T10:09:00Z"),
                    testMessage("m8", createdAt = "2026-06-12T10:08:00Z"),
                ),
                total = 10,
            ),
            messagesPage(
                listOf(
                    testMessage("m3", createdAt = "2026-06-12T10:03:00Z"),
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                ),
                total = 10,
                offset = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m8", createdAt = "2026-06-12T10:08:00Z"))
        controller.onRealtimeMessage(testMessage("m9", createdAt = "2026-06-12T10:09:00Z"))
        controller.catchUp()
        controller.loadMore()

        // Two history rows fetched so far, so the older page starts at 2 — not at the list size (4).
        assertEquals(listOf(0, 0, 2), gateway.listMessagesOffsets)
        assertEquals(
            listOf("m2", "m3", "m4", "m5", "m8", "m9"),
            controller.state.value.messages.map { it.id },
        )
    }

    @Test
    fun retryResetsTheHistoryOffset() {
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 10,
            ),
            messagesPage(
                listOf(
                    testMessage("m0", createdAt = "2026-06-12T10:00:00Z"),
                    testMessage("m00", createdAt = "2026-06-11T10:00:00Z"),
                ),
                total = 10,
                offset = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()
        controller.loadMore()

        controller.retry()
        controller.loadMore()

        // The reload restarts the cursor: 0 (initial), 2 (loadMore), 0 (retry), 2 (loadMore again).
        assertEquals(listOf(0, 2, 0, 2), gateway.listMessagesOffsets)
    }

    @Test
    fun messagesWithTheSameTimestampKeepAStableIdOrderAcrossMerges() {
        // The API omits fractional seconds, so ties are common; the contract breaks them on the
        // (UUIDv7) id so repeated merges cannot reshuffle the conversation.
        val tie = "2026-06-12T10:00:00Z"
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("b", createdAt = tie)), total = 3),
            messagesPage(
                listOf(
                    testMessage("c", createdAt = tie),
                    testMessage("b", createdAt = tie),
                    testMessage("a", createdAt = tie),
                ),
                total = 3,
            ),
        )
        val controller = controller(catchUpLimit = 3)
        controller.loadInitialIfNeeded()

        controller.catchUp()
        controller.catchUp()

        assertEquals(listOf("a", "b", "c"), controller.state.value.messages.map { it.id })
    }

    @Test
    fun sharedCatchUpConstantsMatchTheCrossClientContract() {
        // task_shared_catchup_contract.md §3.5 — changing any of these means changing web and iOS
        // in the same sprint.
        assertEquals(30, ChatConversationController.HISTORY_PAGE_SIZE)
        assertEquals(50, ChatConversationController.CATCHUP_LIMIT)
        assertEquals(5, ChatConversationController.CATCHUP_MAX_PAGES)
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
    fun markReadCoalescesABurstOfArrivals() {
        // Receipts cascade, so a burst needs exactly one bulk mark-read. One per arrival — the old
        // behaviour — walks a busy chat straight through the server's 10 events/second inbound
        // limit and comes back as `Rate limit exceeded`.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller(readReceiptDebounceMillis = 50L)
        controller.loadInitialIfNeeded()
        gateway.calls.clear()
        readReceipts.clear()

        for (i in 2..11) {
            controller.onRealtimeMessage(
                testMessage("m$i", userId = "other", createdAt = "2026-06-12T10:0$i:00Z"),
            )
        }
        runBlocking { delay(300) }

        assertEquals(listOf("markChatsRead:chat-1"), gateway.calls.filter { it.startsWith("markChatsRead") })
        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun exactlyOneMarkReadTransportFiresPerRead() {
        // The REST bulk mark-read already broadcasts a `message:read` receipt to the chat room for
        // every chat where something was newly marked, so the WS frame on top of it delivered every
        // receipt to the peers twice and spent two of the server's 10 events/second inbound slots.
        gateway.chat = testChat("chat-1", unreadCount = 2)
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1", userId = "other"))))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.onRealtimeMessage(
            testMessage("m2", userId = "other", createdAt = "2026-06-12T10:02:00Z"),
        )

        // Two reads, two REST calls...
        assertEquals(
            listOf("markChatsRead:chat-1", "markChatsRead:chat-1"),
            gateway.calls.filter { it.startsWith("markChatsRead") },
        )
        // ...and not a single duplicate WS receipt alongside them.
        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun flushPendingMarkReadExecutesTheDebouncedWorkImmediately() {
        // Closing the conversation inside the debounce window must not lose the mark-read: the
        // flush cancels the pending job and fires the WS receipt + REST bulk call right away.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller(readReceiptDebounceMillis = 60_000L)
        controller.loadInitialIfNeeded()
        gateway.calls.clear()
        readReceipts.clear()

        controller.onRealtimeMessage(testMessage("m2", userId = "other"))
        // Still inside the debounce window — nothing has gone out yet.
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
        assertTrue(readReceipts.isEmpty())

        controller.flushPendingMarkRead()

        assertEquals(listOf("markChatsRead:chat-1"), gateway.calls.filter { it.startsWith("markChatsRead") })
        assertEquals(listOf("chat-1" to "m2"), readReceipts)
    }

    @Test
    fun flushPendingMarkReadWithoutPendingWorkIsNoop() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller(readReceiptDebounceMillis = 60_000L)
        controller.loadInitialIfNeeded()
        gateway.calls.clear()
        readReceipts.clear()

        controller.flushPendingMarkRead()

        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun flushPendingMarkReadSurvivesScreenScopeCancellation() {
        // The dispose-time reality: the screen's scope is being torn down while the flush runs.
        // Even if the pending debounce job was already cancelled with the scope, the flush must
        // still deliver the receipt and the REST mark-read (it runs on its own surviving scope).
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller(readReceiptDebounceMillis = 60_000L)
        controller.loadInitialIfNeeded()
        controller.onRealtimeMessage(testMessage("m2", userId = "other"))
        gateway.calls.clear()
        readReceipts.clear()

        scope.cancel()
        controller.flushPendingMarkRead()

        assertEquals(listOf("markChatsRead:chat-1"), gateway.calls.filter { it.startsWith("markChatsRead") })
        assertEquals(listOf("chat-1" to "m2"), readReceipts)
    }

    @Test
    fun flushPendingMarkReadDoesNotRepeatWorkThatAlreadyFlushed() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        // Zero debounce: the scheduled mark-read completes inline on the Unconfined scope.
        val controller = controller()
        controller.loadInitialIfNeeded()
        controller.onRealtimeMessage(testMessage("m2", userId = "other"))
        gateway.calls.clear()
        readReceipts.clear()

        controller.flushPendingMarkRead()

        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun markReadIsForcedAfterMaxLatencyUnderAContinuousStream() {
        // A stream arriving faster than the debounce window restarts it on every message; the
        // max-latency bound guarantees the flush still fires. Fake clock + Unconfined scope keep
        // this deterministic: once the elapsed time crosses the bound the scheduled delay is 0,
        // which never suspends and runs the mark-read inline.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        var now = 0L
        val controller = controller(
            readReceiptDebounceMillis = 1_000L,
            readReceiptMaxLatencyMillis = 300L,
            nowMillis = { now },
        )
        controller.loadInitialIfNeeded()
        gateway.calls.clear()
        readReceipts.clear()

        controller.onRealtimeMessage(testMessage("m2", userId = "other", createdAt = "2026-06-12T10:02:00Z"))
        now = 200L
        controller.onRealtimeMessage(testMessage("m3", userId = "other", createdAt = "2026-06-12T10:03:00Z"))
        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
        now = 320L
        controller.onRealtimeMessage(testMessage("m4", userId = "other", createdAt = "2026-06-12T10:04:00Z"))

        assertEquals(listOf("markChatsRead:chat-1"), gateway.calls.filter { it.startsWith("markChatsRead") })
        assertTrue(readReceipts.isEmpty())
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
    fun realtimeEchoIsDeduplicatedByClientMessageIdWhenRestResponseLandedFirst() {
        // REST send response landed first; the WS echo of the same logical message must be dropped
        // on the echoed clientMessageId even if the ids were to disagree.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        gateway.sentMessage = testMessage("rest-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0")
        val controller = controller()
        controller.loadInitialIfNeeded()
        controller.send("Ahoy!")

        controller.onRealtimeMessage(
            testMessage("ws-echo-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0"),
        )

        assertEquals(listOf("m1", "rest-id"), controller.state.value.messages.map { it.id })
    }

    @Test
    fun restResponseIsDeduplicatedByClientMessageIdWhenWsEchoLandedFirst() {
        // The tiny race the echoed key closes: the WS echo of our own REST send arrives before the
        // REST response. The echo is appended as it lands; when the REST response then resolves,
        // its message matches the echo on clientMessageId and must not be appended again.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        gateway.sentMessage = testMessage("rest-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0")
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(
            testMessage("ws-echo-id", userId = "me", text = "Ahoy!", clientMessageId = "client-id-0"),
        )
        controller.send("Ahoy!")

        assertEquals(listOf("m1", "ws-echo-id"), controller.state.value.messages.map { it.id })
        assertFalse(controller.state.value.isSending)
    }

    @Test
    fun realtimeMessagesWithoutClientMessageIdAreNotDeduplicatedAgainstEachOther() {
        // Distinct messages that both lack the echoed key must never collapse into one.
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessage(testMessage("m2", userId = "other"))
        controller.onRealtimeMessage(testMessage("m3", userId = "other"))

        assertEquals(listOf("m1", "m2", "m3"), controller.state.value.messages.map { it.id })
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
    fun initialLoadUsesTheRestBulkMarkReadAsItsOnlyReceiptTransport() {
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

        assertTrue(gateway.calls.contains("markChatsRead:chat-1"))
        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun initialLoadSkipsMarkReadEntirelyWhenNothingUnread() {
        gateway.chat = testChat("chat-1", unreadCount = 0)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertFalse(gateway.calls.any { it.startsWith("markChatsRead") })
        assertTrue(readReceipts.isEmpty())
    }

    @Test
    fun flushWsReceiptSkipsOwnNewestMessageAndTargetsNewestFromOthers() {
        // The dispose-time flush is the one place the synchronous WS frame survives (it has to beat
        // the caller's chat:leave). The newest message there is our own, so the receipt must target
        // "m1" — the newest from someone else — never a message we authored.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", userId = "me", createdAt = "2026-06-12T10:05:00Z"),
                    testMessage("m1", userId = "other", createdAt = "2026-06-12T10:00:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller(readReceiptDebounceMillis = 60_000L)
        controller.loadInitialIfNeeded()
        controller.onRealtimeMessage(testMessage("m3", userId = "other", createdAt = "2026-06-12T10:06:00Z"))
        // Our own reply lands last, so the newest message overall is one we authored.
        controller.onRealtimeMessage(testMessage("m4", userId = "me", createdAt = "2026-06-12T10:07:00Z"))
        readReceipts.clear()

        controller.flushPendingMarkRead()

        assertEquals(listOf("chat-1" to "m3"), readReceipts)
    }

    @Test
    fun flushSkipsTheWsReceiptWhenEveryMessageIsOwnButStillBulkMarksRead() {
        // A catch-up that gained messages schedules the mark-read whoever authored them. With
        // nothing from another participant there is no message a receipt could sensibly point at,
        // so only the REST bulk call goes out.
        gateway.messagePages = listOf(
            messagesPage(listOf(testMessage("m1", userId = "me")), total = 1),
            messagesPage(
                listOf(
                    testMessage("m2", userId = "me", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", userId = "me"),
                ),
                total = 2,
            ),
        )
        val controller = controller(readReceiptDebounceMillis = 60_000L)
        controller.loadInitialIfNeeded()
        controller.catchUp()
        gateway.calls.clear()
        readReceipts.clear()

        controller.flushPendingMarkRead()

        assertTrue(readReceipts.isEmpty())
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

        controller.onRealtimeMessageRead("m1", userId = "other", readAt = "2026-06-12T10:00:00Z")

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

        controller.onRealtimeMessageRead("m3", userId = "other", readAt = "2026-06-12T10:03:00Z")

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

        controller.onRealtimeMessageRead("m2", userId = "other", readAt = "2026-06-12T10:02:00Z")

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

        controller.onRealtimeMessageRead("m3", userId = "other", readAt = "2026-06-12T10:03:00Z")

        val byId = controller.state.value.messages.associateBy { it.id }
        assertTrue(byId.getValue("m1").read)
        assertFalse(byId.getValue("m2").read)
        assertFalse(byId.getValue("m3").read)
    }

    /** Own messages a minute apart, oldest first once loaded: m1 10:01, m2 10:02, m3 10:03. */
    private fun ownMessagesPage() = messagesPage(
        listOf(
            testMessage("m3", userId = "me", createdAt = "2026-06-12T10:03:00Z"),
            testMessage("m2", userId = "me", createdAt = "2026-06-12T10:02:00Z"),
            testMessage("m1", userId = "me", createdAt = "2026-06-12T10:01:00Z"),
        ),
        total = 3,
    )

    @Test
    fun realtimeMessageReadWithAnUnloadedAnchorCascadesByReadAt() {
        // The anchor is frequently a message we never loaded — the peer read a backlog older than
        // our first page, or the receipt targets something that arrived while we were scrolled into
        // history. Ignoring those (the old behaviour) left every own bubble on "sent" forever.
        // The boundary is inclusive: "m2" was created at exactly readAt.
        gateway.messagePages = listOf(ownMessagesPage())
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead(
            "never-loaded",
            userId = "other",
            readAt = "2026-06-12T10:02:00Z",
        )

        val byId = controller.state.value.messages.associateBy { it.id }
        assertTrue(byId.getValue("m1").read)
        assertTrue(byId.getValue("m2").read)
        assertFalse(byId.getValue("m3").read)
    }

    @Test
    fun realtimeMessageReadWithAnUnloadedAnchorFlipsOnlyOwnMessages() {
        // The flag on the other side's messages means *we* read them, so a peer's receipt must
        // never touch them — the fallback is no different from the anchored cascade here.
        gateway.messagePages = listOf(
            messagesPage(
                listOf(
                    testMessage("m2", userId = "other", createdAt = "2026-06-12T10:02:00Z"),
                    testMessage("m1", userId = "me", createdAt = "2026-06-12T10:01:00Z"),
                ),
                total = 2,
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead(
            "never-loaded",
            userId = "other",
            readAt = "2026-06-12T10:09:00Z",
        )

        val byId = controller.state.value.messages.associateBy { it.id }
        assertTrue(byId.getValue("m1").read)
        assertFalse(byId.getValue("m2").read)
    }

    @Test
    fun realtimeMessageReadWithAnUnparseableReadAtIsANoop() {
        // Dropped rather than thrown: this runs inside the socket's dispatch loop, and one
        // malformed receipt must not take the collector down with it.
        gateway.messagePages = listOf(ownMessagesPage())
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("never-loaded", userId = "other", readAt = "not-a-timestamp")

        assertTrue(controller.state.value.messages.none { it.read })
    }

    @Test
    fun realtimeMessageReadWithAnUnloadedAnchorOlderThanEverythingFlipsNothing() {
        gateway.messagePages = listOf(ownMessagesPage())
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead(
            "never-loaded",
            userId = "other",
            readAt = "2026-06-12T09:00:00Z",
        )

        assertTrue(controller.state.value.messages.none { it.read })
    }

    @Test
    fun realtimeMessageReadFromSelfIsIgnored() {
        gateway.messagePages = listOf(messagesPage(listOf(testMessage("m1", userId = "me"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onRealtimeMessageRead("m1", userId = "me", readAt = "2026-06-12T10:00:00Z")

        assertFalse(controller.state.value.messages.first { it.id == "m1" }.read)
    }
}
