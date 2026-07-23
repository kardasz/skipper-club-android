package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatMessage
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
)

sealed interface ChatConversationEvent {
    data class OperationFailed(val error: Exception) : ChatConversationEvent
    data object SessionExpired : ChatConversationEvent
    data object MessageSent : ChatConversationEvent
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
    private val gateway: ChatsGateway = RealChatsGateway,
    private val pageSize: Int = HISTORY_PAGE_SIZE,
    /** Catch-up page size; injectable so tests do not have to build 50-message pages. */
    private val catchUpLimit: Int = CATCHUP_LIMIT,
    /** Hard cap on catch-up pages before falling back to a full reload; see [catchUp]. */
    private val catchUpMaxPages: Int = CATCHUP_MAX_PAGES,
    /** Safety net for a lost `isTyping:false`: clears a user's typing state if nothing follows. */
    private val typingExpiryMillis: Long = TYPING_RECEIVE_EXPIRY_MS,
    /** How long arrivals are coalesced before one mark-read goes out; see [scheduleMarkRead]. */
    private val readReceiptDebounceMillis: Long = READ_RECEIPT_DEBOUNCE_MILLIS,
    /**
     * Upper bound on how long the debounce may keep postponing: a continuous stream of arrivals
     * faster than one per [readReceiptDebounceMillis] would otherwise reset the window forever and
     * starve mark-read; see [scheduleMarkRead].
     */
    private val readReceiptMaxLatencyMillis: Long = READ_RECEIPT_MAX_LATENCY_MILLIS,
    /** Monotonic-enough clock for the max-latency bound; injectable so tests are deterministic. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * `message:read` over the socket for the newest visible message, mirroring what iOS/Web send
     * (see the transport-parity table in docs/api/messages/websocket.md). Injectable so tests don't
     * need a real socket.
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

    /**
     * How many history rows this controller has fetched through *paged* requests — the offset the
     * next [loadMore] must use. Deliberately not derived from [ChatConversationUiState.messages],
     * which also grows from realtime arrivals, catch-up and (later) optimistic bubbles: the server
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
                _state.update { state ->
                    state.copy(
                        messages = mergeMessages(state.messages, page.messages),
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: ChatsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return
        // One key per logical message, minted before the request goes out: if OkHttp (or any proxy)
        // retransmits the POST after a timeout, the server dedupes on it and returns the message it
        // already created instead of a duplicate.
        val clientMessageId = clientMessageIdProvider()
        _state.update { it.copy(isSending = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSending = false) }
                return@launch
            }
            try {
                val message = gateway.sendMessage(token, chatId, trimmed, clientMessageId)
                _state.update { state ->
                    state.copy(
                        // The WS echo of this very send can land before the REST response does;
                        // isDuplicate matches it by server id or by the echoed clientMessageId.
                        messages = if (state.messages.any { isDuplicate(it, message) }) {
                            state.messages
                        } else {
                            state.messages + message
                        },
                        isSending = false,
                    )
                }
                _events.tryEmit(ChatConversationEvent.MessageSent)
            } catch (error: ChatsError) {
                _state.update { it.copy(isSending = false) }
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Applies a message pushed over the socket for this chat: appends it when
     * unseen and immediately marks incoming messages as read (the user is
     * looking at the conversation).
     */
    fun onRealtimeMessage(message: ChatMessage) {
        if (message.chatId != chatId) return
        if (!_state.value.hasLoadedOnce) return
        var appended = false
        _state.update { state ->
            if (state.messages.any { isDuplicate(it, message) }) {
                state
            } else {
                appended = true
                state.copy(messages = state.messages + message)
            }
        }
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
        // (Once optimistic bubbles land, unconfirmed entries must be excluded here — they carry no
        // server id for a page to overlap on.)
        val anchorId = _state.value.messages.lastOrNull()?.id ?: return reloadFromScratch(token)
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
        _state.update {
            it.copy(messages = page.messages.sortedByCreationOrder(), hasMore = page.hasMore)
        }
        if (gainedMessages) scheduleMarkRead()
    }

    /** Merges a fetched batch into the conversation; returns whether anything new landed. */
    private fun mergeFetched(incoming: List<ChatMessage>): Boolean {
        var gainedMessages = false
        _state.update { state ->
            val merged = mergeMessages(state.messages, incoming)
            gainedMessages = merged.size > state.messages.size
            if (merged == state.messages) state else state.copy(messages = merged)
        }
        return gainedMessages
    }

    /**
     * Union of what we hold and a fetched batch, in creation order. Entries already held win over
     * incoming duplicates, and [isDuplicate] matches the echoed `clientMessageId` as well as the
     * server id — so a message delivered by catch-up reconciles an in-flight send instead of
     * appearing twice next to it.
     */
    private fun mergeMessages(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): List<ChatMessage> {
        val fresh = incoming.filterNot { candidate -> existing.any { isDuplicate(it, candidate) } }
        if (fresh.isEmpty()) return existing
        return (existing + fresh).sortedByCreationOrder()
    }

    private fun markRead(token: String, hadUnread: Boolean) {
        if (!hadUnread) return
        sendNewestReadReceiptFromOthers()
        scope.launch { markChatsReadViaRest(token) }
    }

    /**
     * WS parity: send a live per-message receipt for the newest visible message from *another*
     * participant so their "seen" indicators update immediately, in addition to the REST bulk
     * mark-read (which the backend does not broadcast per-message events for). Skipping our own
     * newest message avoids emitting a nonsensical read receipt for a message we authored.
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
     * side's messages means *we* read them). A receipt for a message we have not loaded is ignored.
     * Our own read receipts are echoed back to the room too, so they are ignored here.
     */
    fun onRealtimeMessageRead(messageId: String, userId: String) {
        if (userId == currentUserId) return
        _state.update { state ->
            val readUpToIndex = state.messages.indexOfFirst { it.id == messageId }
            if (readUpToIndex < 0) return@update state
            state.copy(
                messages = state.messages.mapIndexed { index, message ->
                    if (index <= readUpToIndex && !message.read && message.user.id == currentUserId) {
                        message.copy(read = true)
                    } else {
                        message
                    }
                },
            )
        }
    }

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
    try {
        Instant.parse(isoTimestamp)
    } catch (_: DateTimeParseException) {
        Instant.EPOCH
    }
