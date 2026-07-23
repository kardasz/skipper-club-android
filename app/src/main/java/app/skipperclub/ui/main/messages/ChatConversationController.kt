package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatUser
import app.skipperclub.data.ChatsError
import app.skipperclub.data.SortOrder
import app.skipperclub.data.WebSocketChatRealtimeClient
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatConversationUiState(
    val chat: Chat? = null,
    /** Oldest first; the screen renders it bottom-anchored. */
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    /** User IDs currently typing in this chat, per `chat:typing`; cleared on inactivity timeout. */
    val typingUserIds: Set<String> = emptySet(),
    /**
     * Delivery state of own sends, keyed by the `clientMessageId` of the message they belong to.
     * The bubble itself is a normal entry in [messages] — this map only decorates it, so a status
     * for a message that is not (or no longer) rendered simply goes unused.
     */
    val sendStatusByClientMessageId: Map<String, MessageSendStatus> = emptyMap(),
)

/**
 * Delivery state of an own message, cross-client parity with web's `MessageSendStatus`
 * (`lib/messages/types.ts`) and iOS's `MessageStatus`. iOS additionally models `delivered`; the
 * backend emits no distinct delivery event, so Android stays on these three.
 */
enum class MessageSendStatus { Sending, Sent, Failed }

sealed interface ChatConversationEvent {
    data class OperationFailed(val error: Exception) : ChatConversationEvent
    data object SessionExpired : ChatConversationEvent
    data object MessageSent : ChatConversationEvent

    /**
     * A send was rejected by the server (or never reached it) — carries the text back so the
     * screen can put the draft into the input again. Keeps [ChatConversationUiState] free of the
     * input state, which stays owned by the screen.
     */
    data class SendFailed(val clientMessageId: String, val text: String) : ChatConversationEvent
}

/**
 * State holder for a single conversation: paginated history, sending, and [catchUp] as the
 * reconciliation path — run once the chat room is live again after a reconnect, and on a fixed
 * interval as the REST fallback while the WebSocket realtime channel is disconnected. Newest
 * messages are fetched descending and kept ascending in [ChatConversationUiState.messages].
 *
 * Paging and catch-up follow `task_shared_catchup_contract.md`, the cross-client specification the
 * web and iOS clients implement too; the constants below are the greppable half of that contract.
 */
class ChatConversationController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val chatId: String,
    private val currentUserId: String? = null,
    /**
     * Author of optimistic bubbles. Left null in production: the chat — and with it the
     * participant this [currentUserId] refers to — is not loaded yet when the screen constructs
     * the controller, so [optimisticMessage] resolves the author from
     * [ChatConversationUiState.chat] at send time instead. Kept as a parameter so a caller that
     * already holds the [ChatUser] (and tests) can pin it.
     */
    private val currentUser: ChatUser? = null,
    private val gateway: ChatsGateway = RealChatsGateway,
    private val pageSize: Int = HISTORY_PAGE_SIZE,
    /** Catch-up page size; injectable so tests do not have to build 50-message pages. */
    private val catchUpLimit: Int = CATCHUP_LIMIT,
    /** Hard cap on catch-up pages before falling back to a full reload; see [catchUp]. */
    private val catchUpMaxPages: Int = CATCHUP_MAX_PAGES,
    /** Safety net for a lost `isTyping:false`: clears a user's typing state if nothing follows. */
    private val typingExpiryMillis: Long = TYPING_RECEIVE_EXPIRY_MS,
    /**
     * How long an optimistic bubble may stay unconfirmed before it is shown as failed; injectable
     * so tests do not have to wait out [SEND_CONFIRM_TIMEOUT_MILLIS].
     */
    private val sendConfirmTimeoutMillis: Long = SEND_CONFIRM_TIMEOUT_MILLIS,
    /** How long arrivals are coalesced before one mark-read goes out; see [scheduleMarkRead]. */
    private val readReceiptDebounceMillis: Long = READ_RECEIPT_DEBOUNCE_MILLIS,
    /**
     * Upper bound on how long the debounce may keep postponing: a continuous stream of arrivals
     * faster than one per [readReceiptDebounceMillis] would otherwise reset the window forever and
     * starve mark-read; see [scheduleMarkRead].
     */
    private val readReceiptMaxLatencyMillis: Long = READ_RECEIPT_MAX_LATENCY_MILLIS,
    /**
     * Monotonic-enough clock for the max-latency bound and for the `createdAt` an optimistic
     * bubble carries until the server's own timestamp replaces it; injectable so tests are
     * deterministic.
     */
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * `message:read` over the socket for the newest visible message. Sent only from
     * [flushPendingMarkRead] — see [markRead] for why the REST bulk call is the sole receipt source
     * everywhere else. Injectable so tests don't need a real socket.
     */
    private val sendReadReceipt: (chatId: String, messageId: String) -> Unit = { id, messageId ->
        WebSocketChatRealtimeClient.sendMessageRead(id, messageId)
    },
    /**
     * Idempotency key per logical message ([java.util.UUID] by default; injectable so tests are
     * deterministic). Generated once per [send] and reused for any HTTP-level retry of that POST,
     * so a timeout-and-retransmit cannot create a duplicate message server-side.
     */
    private val clientMessageIdProvider: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(ChatConversationUiState())
    val state: StateFlow<ChatConversationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatConversationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ChatConversationEvent> = _events.asSharedFlow()

    private val typingExpiryJobs = mutableMapOf<String, Job>()

    /** Per-`clientMessageId` send-confirmation watchdogs; see [armSendWatchdog]. */
    private val sendWatchdogJobs = mutableMapOf<String, Job>()

    /**
     * How many history rows this controller has fetched through *paged* requests — the offset the
     * next [loadMore] must use. Deliberately not derived from [ChatConversationUiState.messages],
     * which also grows from realtime arrivals, catch-up and optimistic bubbles: the server
     * pages `created_at DESC` over its own list, so every row that entered ours by another route
     * shifts that window by one and silently skips an older message. Duplicates are filtered by id,
     * gaps have no signal at all (task_shared_catchup_contract.md §3.1).
     *
     * Advanced only by the initial load, [loadMore] and [reloadFromScratch]; reset by [retry].
     */
    private var historyOffset: Int = 0

    /** The catch-up loop in flight, so a second trigger coalesces into it instead of stacking. */
    private var catchUpJob: Job? = null

    /** In-flight debounced mark-read, cancelled and restarted by each new arrival. */
    private var markReadJob: Job? = null

    /**
     * When the oldest still-unflushed mark-read was scheduled; drives the max-latency bound and
     * doubles as the "work is pending" marker for [flushPendingMarkRead] — non-null from the first
     * coalesced arrival until the debounced job actually starts executing. Deliberately not
     * derived from [markReadJob]'s liveness: a scope teardown can cancel the job before the
     * screen's dispose gets to flush, and the flush must still see the work as pending.
     */
    private var firstPendingMarkReadAtMillis: Long? = null

    /**
     * Same dispatcher as [scope] but an independent [SupervisorJob], so the dispose-time flush in
     * [flushPendingMarkRead] survives the screen scope being torn down at that very moment. Only
     * short-lived flush work runs here; nothing long-lived can leak.
     */
    private val markReadFlushScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoading = false, loadFailed = true, hasLoadedOnce = true) }
                return@launch
            }
            try {
                val chat = gateway.getChat(token, chatId)
                val page = gateway.listMessages(
                    token,
                    chatId,
                    limit = pageSize,
                    offset = 0,
                    order = SortOrder.Desc,
                )
                historyOffset = page.messages.size
                _state.update {
                    it.copy(
                        chat = chat,
                        messages = page.messages.sortedByCreationOrder(),
                        hasMore = page.hasMore,
                        isLoading = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
                markRead(token, hadUnread = chat.unreadCount > 0)
            } catch (error: ChatsError) {
                _state.update { it.copy(isLoading = false, loadFailed = true, hasLoadedOnce = true) }
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
        }
    }

    fun retry() {
        historyOffset = 0
        _state.update { it.copy(hasLoadedOnce = false) }
        loadInitialIfNeeded()
    }

    /** Loads the next (older) history page and merges it in. */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            try {
                val page = gateway.listMessages(
                    token,
                    chatId,
                    limit = pageSize,
                    offset = historyOffset,
                    order = SortOrder.Desc,
                )
                // Advance by what the server actually returned, before any deduplication: the offset
                // counts rows on the server's list, not rows we chose to keep.
                historyOffset += page.messages.size
                mergeFetched(page.messages)
                _state.update { it.copy(hasMore = page.hasMore, isLoadingMore = false) }
            } catch (error: ChatsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Sends [text] optimistically: the bubble is appended to [ChatConversationUiState.messages]
     * before the request goes out and converges onto the server's message once it is confirmed,
     * so a slow link no longer looks like the message was never sent.
     *
     * Returns the `clientMessageId` the send was accepted under, or `null` when there was nothing
     * to send. The screen clears its input on a non-null result and leaves the draft alone
     * otherwise, so a rejected send never destroys what the user typed.
     *
     * Deliberately **not** gated on [ChatConversationUiState.isSending] any more: with a bubble
     * rendering per send, a second message typed while the first is still in flight is legitimate,
     * and the old early return swallowed it without a trace. `isSending` survives only as the
     * derived "at least one send in flight" flag that drives the input bar.
     */
    fun send(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // One key per logical message, minted before the request goes out: if OkHttp (or any proxy)
        // retransmits the POST after a timeout, the server dedupes on it and returns the message it
        // already created instead of a duplicate. It doubles as the optimistic bubble's identity —
        // isDuplicate already reconciles on it, so the bubble rides that existing dedup path.
        val clientMessageId = clientMessageIdProvider()
        _state.update { state ->
            val optimistic = optimisticMessage(state, clientMessageId, trimmed)
            state
                .copy(
                    messages = if (optimistic == null) {
                        state.messages
                    } else {
                        state.messages + optimistic
                    },
                )
                .withSendStatus(clientMessageId, MessageSendStatus.Sending)
        }
        armSendWatchdog(clientMessageId)
        dispatchSend(clientMessageId, trimmed)
        return clientMessageId
    }

    /**
     * Re-issues a send that ended up [MessageSendStatus.Failed], reusing the **same**
     * `clientMessageId`: the backend dedupes on it (docs/api/messages/websocket.md, "Transport
     * parity"), so a retry of a request that did reach the server returns the message it already
     * created and emits no second event. Ignores anything that is not currently failed.
     */
    fun retrySend(clientMessageId: String) {
        val current = _state.value
        if (current.sendStatusByClientMessageId[clientMessageId] != MessageSendStatus.Failed) return
        val text = current.messages.firstOrNull { it.clientMessageId == clientMessageId }?.text ?: return
        _state.update { it.withSendStatus(clientMessageId, MessageSendStatus.Sending) }
        armSendWatchdog(clientMessageId)
        dispatchSend(clientMessageId, text)
    }

    /**
     * The bubble rendered between the tap and the server's `201`. Built only when the author can be
     * resolved — [currentUserId] alone is not enough to render an avatar and a name, and a bubble
     * attributed to the wrong participant is worse than no bubble at all, so an unresolvable author
     * falls back to the previous non-optimistic behaviour.
     */
    private fun optimisticMessage(
        state: ChatConversationUiState,
        clientMessageId: String,
        text: String,
    ): ChatMessage? {
        val author = currentUser
            ?: state.chat?.participants?.firstOrNull { it.id == currentUserId }
            ?: return null
        val createdAt = Instant.ofEpochMilli(nowMillis()).toString()
        return ChatMessage(
            id = OPTIMISTIC_ID_PREFIX + clientMessageId,
            chatId = chatId,
            text = text,
            read = false,
            user = author,
            createdAt = createdAt,
            updatedAt = createdAt,
            clientMessageId = clientMessageId,
        )
    }

    /** The POST itself, shared by [send] and [retrySend] so a retry is byte-for-byte the same call. */
    private fun dispatchSend(clientMessageId: String, text: String) {
        scope.launch {
            val token = requireToken() ?: run {
                failSend(clientMessageId, text)
                return@launch
            }
            try {
                val message = gateway.sendMessage(token, chatId, text, clientMessageId)
                reconcileSent(clientMessageId, message)
                _events.tryEmit(ChatConversationEvent.MessageSent)
            } catch (error: ChatsError) {
                failSend(clientMessageId, text)
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Converges the optimistic bubble onto the server's message. Replaced **in place** rather than
     * removed and re-appended: the two `createdAt` values differ by the round trip, and re-sorting
     * would make the bubble visibly jump. The next full load re-sorts anyway.
     */
    private fun reconcileSent(clientMessageId: String, message: ChatMessage) {
        cancelSendWatchdog(clientMessageId)
        _state.update { state ->
            // The placeholder first: the server row for this same send may already sit in the list
            // (WS echo, catch-up page) carrying the very same key, and that row must not be the one
            // we rewrite or drop.
            val pendingIndex = state.messages
                .indexOfFirst { it.isOptimistic() && it.clientMessageId == clientMessageId }
                .takeIf { it >= 0 }
                ?: state.messages.indexOfFirst { it.clientMessageId == clientMessageId }
            val heldElsewhere = state.messages
                .filterIndexed { index, _ -> index != pendingIndex }
                .any { isDuplicate(it, message) }
            val messages = when {
                // Already delivered by another route: drop the placeholder instead of leaving the
                // same message in the list twice.
                pendingIndex >= 0 && heldElsewhere && state.messages[pendingIndex].isOptimistic() ->
                    state.messages.filterIndexed { index, _ -> index != pendingIndex }

                pendingIndex >= 0 && !heldElsewhere ->
                    state.messages.toMutableList().also { it[pendingIndex] = message }

                pendingIndex >= 0 || heldElsewhere -> state.messages
                else -> state.messages + message
            }
            state.copy(messages = messages).withSendStatus(clientMessageId, MessageSendStatus.Sent)
        }
    }

    /**
     * Marks a send as failed, keeping its bubble in the conversation so the user can retry from it,
     * and hands the text back for the draft. Only a send still in flight can fail: a `Sent` entry
     * whose REST call errors out afterwards was already confirmed over the socket.
     */
    private fun failSend(clientMessageId: String, text: String) {
        cancelSendWatchdog(clientMessageId)
        var failed = false
        _state.update { state ->
            if (state.sendStatusByClientMessageId[clientMessageId] != MessageSendStatus.Sending) {
                state
            } else {
                failed = true
                state.withSendStatus(clientMessageId, MessageSendStatus.Failed)
            }
        }
        if (failed) _events.tryEmit(ChatConversationEvent.SendFailed(clientMessageId, text))
    }

    /**
     * Flips a still-unconfirmed send to [MessageSendStatus.Failed] after
     * [sendConfirmTimeoutMillis]. Without it a request that neither succeeds nor throws — a socket
     * black-holed by a captive portal, a proxy holding the connection open — leaves the bubble
     * spinning forever with no way back.
     *
     * Deliberately does not emit [ChatConversationEvent.SendFailed]: 12 seconds on, the input holds
     * whatever the user has typed since, and the recovery affordance is the bubble's own retry.
     */
    private fun armSendWatchdog(clientMessageId: String) {
        sendWatchdogJobs.remove(clientMessageId)?.cancel()
        sendWatchdogJobs[clientMessageId] = scope.launch {
            delay(sendConfirmTimeoutMillis)
            sendWatchdogJobs.remove(clientMessageId)
            _state.update { state ->
                if (state.sendStatusByClientMessageId[clientMessageId] != MessageSendStatus.Sending) {
                    state
                } else {
                    state.withSendStatus(clientMessageId, MessageSendStatus.Failed)
                }
            }
        }
    }

    private fun cancelSendWatchdog(clientMessageId: String) {
        sendWatchdogJobs.remove(clientMessageId)?.cancel()
    }

    /**
     * Records [status] for [clientMessageId] and re-derives [ChatConversationUiState.isSending]
     * from the map, which is what keeps the flag honest now that concurrent sends are allowed: it
     * means "at least one send in flight", never "a send is in flight, so refuse the next one".
     */
    private fun ChatConversationUiState.withSendStatus(
        clientMessageId: String,
        status: MessageSendStatus,
    ): ChatConversationUiState {
        val statuses = sendStatusByClientMessageId + (clientMessageId to status)
        return copy(
            sendStatusByClientMessageId = statuses,
            isSending = statuses.containsValue(MessageSendStatus.Sending),
        )
    }

    /**
     * Applies a message pushed over the socket for this chat: appends it when unseen and
     * immediately marks incoming messages as read (the user is looking at the conversation).
     *
     * When the arrival is the echo of one of our own optimistic sends, the placeholder is
     * **replaced** by it rather than the echo being discarded — discarding leaves the
     * `optimistic-…` id in the list, so the row never picks up the server id and the send stays
     * unconfirmed until the watchdog fires.
     */
    fun onRealtimeMessage(message: ChatMessage) {
        if (message.chatId != chatId) return
        if (!_state.value.hasLoadedOnce) return
        var appended = false
        var confirmedClientMessageId: String? = null
        _state.update { state ->
            val existingIndex = state.messages.indexOfFirst { isDuplicate(it, message) }
            val existing = state.messages.getOrNull(existingIndex)
            when {
                existing == null -> {
                    appended = true
                    confirmedClientMessageId = null
                    state.copy(messages = state.messages + message)
                }

                existing.isOptimistic() -> {
                    appended = false
                    confirmedClientMessageId = existing.clientMessageId
                    val messages = state.messages.toMutableList().also { it[existingIndex] = message }
                    val replaced = state.copy(messages = messages)
                    existing.clientMessageId
                        ?.let { replaced.withSendStatus(it, MessageSendStatus.Sent) }
                        ?: replaced
                }

                else -> {
                    appended = false
                    confirmedClientMessageId = null
                    state
                }
            }
        }
        confirmedClientMessageId?.let { cancelSendWatchdog(it) }
        if (appended && message.user.id != currentUserId) {
            scheduleMarkRead()
        }
    }

    /**
     * Same logical message? Matches by server id, or — when the incoming payload carries the
     * echoed `clientMessageId` idempotency key (backend v1.5.0+) — by that key too. The second
     * match closes the tiny window where the WS echo of our own REST send arrives before the REST
     * response: both carry the same key, so whichever lands second is dropped instead of showing
     * the message twice.
     */
    private fun isDuplicate(existing: ChatMessage, incoming: ChatMessage): Boolean =
        existing.id == incoming.id ||
            (incoming.clientMessageId != null && existing.clientMessageId == incoming.clientMessageId)

    /**
     * Coalesces mark-read work over a short window instead of firing it per arriving message.
     *
     * Read receipts cascade — a receipt for the newest message already means every earlier one is
     * read — so a burst of arrivals (a chat opened with a backlog, a lively group) only ever needs
     * one receipt and one bulk mark-read. Firing per message instead sent one WS frame plus one
     * REST call each, which on 20 unread walks straight through the server's 10 events/second
     * inbound limit and comes back as `Rate limit exceeded`.
     *
     * The window is bounded by [readReceiptMaxLatencyMillis]: each arrival restarts the debounce,
     * so a stream faster than one message per [readReceiptDebounceMillis] would otherwise postpone
     * the flush indefinitely — the peer would never see "seen" while the conversation stays busy.
     * The first unflushed arrival starts the latency clock; once it runs out the flush fires
     * regardless of what keeps landing.
     */
    private fun scheduleMarkRead() {
        val now = nowMillis()
        val firstPendingAt = firstPendingMarkReadAtMillis ?: now.also { firstPendingMarkReadAtMillis = it }
        val remainingUntilForced = (firstPendingAt + readReceiptMaxLatencyMillis - now).coerceAtLeast(0)
        markReadJob?.cancel()
        markReadJob = scope.launch {
            delay(minOf(readReceiptDebounceMillis, remainingUntilForced))
            firstPendingMarkReadAtMillis = null
            val token = runCatching { accessToken() }.getOrNull() ?: return@launch
            markRead(token, hadUnread = true)
        }
    }

    /**
     * Cancels a pending debounced mark-read and executes it immediately. The screen calls this
     * from its dispose, **before** sending `chat:leave`: closing the conversation inside the
     * debounce window used to cancel [markReadJob] silently, losing both the WS receipt (the peer
     * never saw "seen") and the REST bulk mark-read (the unread badge resurfaced for a message
     * that was actually read).
     *
     * The WS receipt is sent synchronously so it reaches the server before the caller's
     * `chat:leave` frame (receipts for a room we already left are dropped). The token fetch and
     * REST call run on [markReadFlushScope] because the screen's scope — the [scope] this
     * controller launches everything in — is being torn down at that very moment.
     */
    fun flushPendingMarkRead() {
        if (firstPendingMarkReadAtMillis == null) return
        firstPendingMarkReadAtMillis = null
        markReadJob?.cancel()
        markReadJob = null
        sendNewestReadReceiptFromOthers()
        markReadFlushScope.launch {
            val token = runCatching { accessToken() }.getOrNull() ?: return@launch
            markChatsReadViaRest(token)
        }
    }

    /**
     * Reconciles the conversation with the server: the bounded page-back catch-up of
     * task_shared_catchup_contract.md §3.2.
     *
     * Pages the newest history in [catchUpLimit]-sized batches, merging as it goes, until a batch
     * reaches back to the newest message already held (the gap is closed) or the start of history is
     * reached. A single page — what this used to do — leaves a permanent hole whenever more than one
     * page arrived during an outage: the merge appends the newest page, and [loadMore] pages
     * backwards from the *oldest* row, so nothing ever fetches the middle.
     *
     * Runs on `chat:joined` for this chat and on the screen's REST poll while the socket is down; a
     * poll tick is just a catch-up whose first page overlaps immediately. Concurrent triggers
     * coalesce into the loop in flight rather than stacking a second one.
     *
     * Deliberately **not** guarded on [ChatConversationUiState.isSending]: skipping the catch-up
     * because a send happened to be in flight dropped that reconnect's reconciliation entirely, and
     * nothing re-armed it. The race that guard was for — the fetched page racing the send — is
     * already handled by [isDuplicate] matching on the echoed `clientMessageId`.
     */
    fun catchUp() {
        val current = _state.value
        if (!current.hasLoadedOnce || current.isLoading) return
        if (catchUpJob?.isActive == true) return
        catchUpJob = scope.launch {
            val token = runCatching { accessToken() }.getOrNull() ?: return@launch
            try {
                runCatchUp(token)
            } catch (_: ChatsError) {
                // Background reconciliation: stay quiet, the next poll tick or rejoin retries.
            }
        }
    }

    private suspend fun runCatchUp(token: String) {
        // Anchor on the newest message we hold; the loop stops as soon as a page reaches back to it.
        // Unconfirmed optimistic bubbles are skipped: their `optimistic-…` id exists only here, so
        // no page could ever overlap on it and the loop would page back to the cap every time.
        val anchorId = _state.value.messages.lastOrNull { !it.isOptimistic() }?.id
            ?: return reloadFromScratch(token)
        var gainedMessages = false
        repeat(catchUpMaxPages) { pageIndex ->
            val page = gateway.listMessages(
                token,
                chatId,
                limit = catchUpLimit,
                offset = pageIndex * catchUpLimit,
                order = SortOrder.Desc,
            )
            gainedMessages = mergeFetched(page.messages) || gainedMessages
            // Overlap reached, or the start of history — the gap is closed either way. Deliberately
            // no `hasMore` write: an offset-0 page describes the newest window, not the older
            // history the user is paging through.
            if (page.messages.any { it.id == anchorId } || page.messages.size < catchUpLimit) {
                if (gainedMessages) scheduleMarkRead()
                return
            }
        }
        // The outage ran deeper than the page cap. Merging what we fetched would leave an invisible
        // hole between that window and the local history, so throw the window away and reload.
        reloadFromScratch(token)
    }

    /**
     * Discards the local window and re-reads page 0 as a fresh load — the `NOT_CLOSED` branch of the
     * contract, and the cold path when nothing local is left to anchor on. Replaces the list
     * atomically instead of flipping [ChatConversationUiState.isLoading]: the 5s poll reaches this on
     * an empty conversation, and a spinner flashing every tick would be worse than a silent swap.
     * Being a full load, this is one of the two paths allowed to write `hasMore` and [historyOffset].
     */
    private suspend fun reloadFromScratch(token: String) {
        val page = gateway.listMessages(
            token,
            chatId,
            limit = pageSize,
            offset = 0,
            order = SortOrder.Desc,
        )
        val known = _state.value.messages
        val gainedMessages = page.messages.any { candidate -> known.none { isDuplicate(it, candidate) } }
        historyOffset = page.messages.size
        val confirmed = mutableSetOf<String>()
        _state.update { state ->
            // Optimistic bubbles exist only here, so a wholesale swap would silently drop a send
            // that is still in flight. Carry over the ones this page does not already confirm.
            val (reconciled, confirmedKeys) = reconcileOptimistic(state.messages, page.messages)
            confirmed.clear()
            confirmed += confirmedKeys
            val pending = reconciled.filter { it.isOptimistic() }
            confirmedKeys
                .fold(state.copy(messages = (page.messages + pending).sortedByCreationOrder())) { acc, key ->
                    acc.withSendStatus(key, MessageSendStatus.Sent)
                }
                .copy(hasMore = page.hasMore)
        }
        confirmed.forEach { cancelSendWatchdog(it) }
        if (gainedMessages) scheduleMarkRead()
    }

    /**
     * Merges a fetched batch into the conversation; returns whether anything new landed.
     *
     * Entries already held win over incoming duplicates — except optimistic ones, which the
     * incoming server row *replaces*: a send confirmed by a catch-up page must pick up its server
     * id here, or the placeholder would linger until the watchdog declared it failed.
     */
    private fun mergeFetched(incoming: List<ChatMessage>): Boolean {
        var gainedMessages = false
        val confirmed = mutableSetOf<String>()
        _state.update { state ->
            val (reconciled, confirmedKeys) = reconcileOptimistic(state.messages, incoming)
            val fresh = incoming.filterNot { candidate -> reconciled.any { isDuplicate(it, candidate) } }
            gainedMessages = fresh.isNotEmpty()
            confirmed.clear()
            confirmed += confirmedKeys
            if (fresh.isEmpty() && confirmedKeys.isEmpty()) {
                state
            } else {
                confirmedKeys
                    .fold(state.copy(messages = (reconciled + fresh).sortedByCreationOrder())) { acc, key ->
                        acc.withSendStatus(key, MessageSendStatus.Sent)
                    }
            }
        }
        confirmed.forEach { cancelSendWatchdog(it) }
        return gainedMessages
    }

    /**
     * Swaps every optimistic entry of [existing] for the [incoming] server row that carries its
     * `clientMessageId`, and reports the keys that were confirmed so their watchdogs can be
     * cancelled and their status flipped to [MessageSendStatus.Sent].
     */
    private fun reconcileOptimistic(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): Pair<List<ChatMessage>, Set<String>> {
        if (existing.none { it.isOptimistic() }) return existing to emptySet()
        val confirmed = mutableSetOf<String>()
        val reconciled = existing.map { held ->
            if (!held.isOptimistic()) return@map held
            val server = incoming.firstOrNull { isDuplicate(held, it) } ?: return@map held
            held.clientMessageId?.let { confirmed += it }
            server
        }
        return reconciled to confirmed
    }

    /** A bubble inserted by [send] that the server has not confirmed yet. */
    private fun ChatMessage.isOptimistic(): Boolean = id.startsWith(OPTIMISTIC_ID_PREFIX)

    /**
     * Clears the unread state for this chat over **one** transport: the REST bulk mark-read.
     *
     * This used to fire the WS `message:read` frame as well, for "parity". It is not parity — per
     * the transport-parity table in docs/api/messages/websocket.md the REST `mark-read` broadcasts
     * a `message:read` receipt to the chat room for every chat where something was newly marked,
     * so the peers' "seen" indicators already update from it. Sending both delivered two identical
     * receipts per read and spent two of the server's 10 events/second inbound slots. The REST call
     * is the one kept because it is also what actually clears the unread counters.
     *
     * The gap the WS frame covered — "nothing was newly marked, so REST emits no receipt" — is not
     * a gap: with nothing newly marked, no receipt is the correct outcome. The single place the
     * synchronous frame is still required is [flushPendingMarkRead], where it has to reach the
     * server before the caller's `chat:leave`.
     */
    private fun markRead(token: String, hadUnread: Boolean) {
        if (!hadUnread) return
        scope.launch { markChatsReadViaRest(token) }
    }

    /**
     * The live per-message receipt for the newest visible message from *another* participant.
     *
     * Used only by [flushPendingMarkRead]: everywhere else the REST bulk mark-read's own broadcast
     * is the receipt (see [markRead]), but at dispose time the REST call cannot be awaited before
     * `chat:leave` goes out, so the frame is sent synchronously as well. Skipping our own newest
     * message avoids emitting a nonsensical read receipt for a message we authored.
     */
    private fun sendNewestReadReceiptFromOthers() {
        _state.value.messages.lastOrNull { it.user.id != currentUserId }?.let { newest ->
            runCatching { sendReadReceipt(chatId, newest.id) }
        }
    }

    private suspend fun markChatsReadViaRest(token: String) {
        try {
            gateway.markChatsRead(token, listOf(chatId))
        } catch (error: ChatsError) {
            _events.tryEmit(ChatConversationEvent.OperationFailed(error))
        }
    }

    /**
     * Applies a `chat:typing` event pushed over the socket. Guards on [chatId] the same way
     * [onRealtimeMessage] does, since the socket delivers events for every joined room. A fresh
     * `isTyping = true` (re)starts a [typingExpiryMillis] safety-net timer per user: the server
     * does not auto-expire typing state, so a lost `isTyping:false` would otherwise leave the
     * indicator stuck forever.
     */
    fun onRealtimeTyping(chatId: String, userId: String, isTyping: Boolean) {
        if (chatId != this.chatId) return
        if (userId == currentUserId) return
        typingExpiryJobs.remove(userId)?.cancel()
        if (isTyping) {
            _state.update { it.copy(typingUserIds = it.typingUserIds + userId) }
            typingExpiryJobs[userId] = scope.launch {
                delay(typingExpiryMillis)
                _state.update { it.copy(typingUserIds = it.typingUserIds - userId) }
                typingExpiryJobs.remove(userId)
            }
        } else {
            _state.update { it.copy(typingUserIds = it.typingUserIds - userId) }
        }
    }

    /**
     * Applies a `message:read` receipt pushed over the socket. Receipts are cumulative: reading
     * [messageId] means the participant has read it **and every earlier message** in the chat, so
     * every own message up to and including that position (list order == createdAt order) flips
     * [ChatMessage.read] for the "seen" indicator — not just the exact match, which would leave
     * older bubbles stuck on "sent" when the receipt only targets the newest message. Only own
     * messages are flipped (the indicator renders on own bubbles only, and the flag on the other
     * side's messages means *we* read them). Our own read receipts are echoed back to the room too,
     * so they are ignored here.
     *
     * The anchor is frequently a message we never loaded — the peer read a backlog older than our
     * first page, or the receipt targets a message that arrived while we were scrolled into
     * history. Ignoring those (the previous behaviour) left every own bubble on "sent" forever, so
     * an absent anchor falls back to [readAt]: every own message created no later than the receipt
     * is flipped. A `readAt` that does not parse is dropped rather than throwing inside the socket
     * dispatch loop, and a message whose own `createdAt` does not parse is left alone rather than
     * being flipped on a timestamp we could not compare.
     */
    fun onRealtimeMessageRead(messageId: String, userId: String, readAt: String) {
        if (userId == currentUserId) return
        _state.update { state ->
            val readUpToIndex = state.messages.indexOfFirst { it.id == messageId }
            if (readUpToIndex >= 0) {
                state.copy(
                    messages = state.messages.mapIndexed { index, message ->
                        if (index <= readUpToIndex && message.isUnreadOwn()) {
                            message.copy(read = true)
                        } else {
                            message
                        }
                    },
                )
            } else {
                val readAtInstant = parseMessageInstantOrNull(readAt) ?: return@update state
                state.copy(
                    messages = state.messages.map { message ->
                        val createdAt = parseMessageInstantOrNull(message.createdAt)
                        if (message.isUnreadOwn() && createdAt != null && !createdAt.isAfter(readAtInstant)) {
                            message.copy(read = true)
                        } else {
                            message
                        }
                    },
                )
            }
        }
    }

    /** An own bubble the "seen" indicator has not been switched on for yet. */
    private fun ChatMessage.isUnreadOwn(): Boolean = !read && user.id == currentUserId

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(ChatConversationEvent.SessionExpired)
        return token
    }

    companion object {
        /**
         * History page size. One of the three constants pinned by
         * task_shared_catchup_contract.md §3.5 so web/iOS/Android page identically — a bug
         * reproduced on one client reproduces on all three.
         */
        const val HISTORY_PAGE_SIZE = 30

        /** Catch-up page size (§3.5). Well inside the API's `limit` maximum of 100. */
        const val CATCHUP_LIMIT = 50

        /**
         * Hard cap on catch-up pages (§3.5): at most [CATCHUP_LIMIT] × this many messages are pulled
         * per reconnect, so a flapping connection cannot turn the loop into a fetch storm. Beyond it
         * the conversation is reloaded from page 0 instead of partially merged.
         */
        const val CATCHUP_MAX_PAGES = 5

        /**
         * A received typing indicator auto-clears if no fresh `isTyping:true` arrives within this
         * window. Kept in sync with the sender's keepalive (2s) + idle-stop (3s) on web/iOS so a
         * still-typing peer's keepalive always lands before this expiry fires.
         */
        const val TYPING_RECEIVE_EXPIRY_MS = 5_000L

        /**
         * How long an optimistic bubble may stay unconfirmed before it is shown as failed. Pinned
         * across the three clients — web's `SEND_CONFIRM_TIMEOUT_MS = 12000`
         * (`lib/messages/constants.ts`) and iOS's `sendConfirmationTimeout = .seconds(12)` — so a
         * flaky link reports the same way everywhere.
         */
        const val SEND_CONFIRM_TIMEOUT_MILLIS = 12_000L

        /**
         * Id prefix of a bubble that exists only on this device. The suffix is the send's
         * `clientMessageId`, which is what actually reconciles it with the server's message; the
         * prefix just makes an unconfirmed row recognisable wherever a server id is expected.
         */
        const val OPTIMISTIC_ID_PREFIX = "optimistic-"

        /**
         * How long arrivals are coalesced before one mark-read goes out. Short enough that the
         * sender's "seen" indicator still feels immediate; long enough that a burst collapses into
         * a single receipt. Mirrors the same window on web.
         */
        const val READ_RECEIPT_DEBOUNCE_MILLIS = 400L

        /**
         * Hard ceiling on mark-read latency under a continuous message stream. A conversation
         * receiving faster than one message per [READ_RECEIPT_DEBOUNCE_MILLIS] restarts the
         * debounce on every arrival; without this bound the flush would be starved for as long as
         * the burst lasts.
         */
        const val READ_RECEIPT_MAX_LATENCY_MILLIS = 1_500L
    }
}

/**
 * Orders messages oldest-first by the `(createdAt, id)` tuple required by
 * task_shared_catchup_contract.md §3.3 — the same tuple the backend uses for its read pointer.
 *
 * The timestamp is parsed rather than compared as text: the API omits fractional seconds when they
 * are zero, and `"…:00.500Z"` sorts *before* `"…:00Z"` lexicographically ('.' < 'Z'). The id breaks
 * the ties that second-granularity timestamps make common; ids are UUIDv7, so their order matches
 * creation order. A timestamp that fails to parse sorts to the far past rather than the far future,
 * so a malformed row can never masquerade as the newest message [ChatConversationController]
 * anchors its catch-up on.
 */
internal fun List<ChatMessage>.sortedByCreationOrder(): List<ChatMessage> =
    map { it to parseMessageInstant(it.createdAt) }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
        .map { it.first }

private fun parseMessageInstant(isoTimestamp: String): Instant =
    parseMessageInstantOrNull(isoTimestamp) ?: Instant.EPOCH

/**
 * Strict variant for callers that must distinguish "unparseable" from "very old" — the cascading
 * read-receipt fallback in [ChatConversationController.onRealtimeMessageRead], where the
 * [Instant.EPOCH] substitution above would silently mark a malformed row as read.
 */
private fun parseMessageInstantOrNull(isoTimestamp: String): Instant? =
    try {
        Instant.parse(isoTimestamp)
    } catch (_: DateTimeParseException) {
        null
    }
