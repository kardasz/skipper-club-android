package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatsError
import app.skipperclub.data.SortOrder
import app.skipperclub.data.WebSocketChatRealtimeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * State holder for a single conversation: paginated history, sending, and
 * [refreshNewMessages] as the REST fallback while the WebSocket realtime
 * channel is disconnected. Newest messages are fetched descending and kept
 * ascending in [ChatConversationUiState.messages].
 */
class ChatConversationController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val chatId: String,
    private val currentUserId: String? = null,
    private val gateway: ChatsGateway = RealChatsGateway,
    private val pageSize: Int = 30,
    /** Safety net for a lost `isTyping:false`: clears a user's typing state if nothing follows. */
    private val typingExpiryMillis: Long = TYPING_RECEIVE_EXPIRY_MS,
    /** How long arrivals are coalesced before one mark-read goes out; see [scheduleMarkRead]. */
    private val readReceiptDebounceMillis: Long = READ_RECEIPT_DEBOUNCE_MILLIS,
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

    /** In-flight debounced mark-read, cancelled and restarted by each new arrival. */
    private var markReadJob: Job? = null

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
                _state.update {
                    it.copy(
                        chat = chat,
                        messages = page.messages.asReversed(),
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
        _state.update { it.copy(hasLoadedOnce = false) }
        loadInitialIfNeeded()
    }

    /** Loads the next (older) history page and prepends it. */
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
                val snapshot = _state.value
                val page = gateway.listMessages(
                    token,
                    chatId,
                    limit = pageSize,
                    offset = snapshot.messages.size,
                    order = SortOrder.Desc,
                )
                _state.update { state ->
                    val knownIds = state.messages.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        messages = page.messages.asReversed().filterNot { it.id in knownIds } +
                            state.messages,
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
                        messages = if (state.messages.any { it.id == message.id }) {
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
            if (state.messages.any { it.id == message.id }) {
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
     * Coalesces mark-read work over a short window instead of firing it per arriving message.
     *
     * Read receipts cascade — a receipt for the newest message already means every earlier one is
     * read — so a burst of arrivals (a chat opened with a backlog, a lively group) only ever needs
     * one receipt and one bulk mark-read. Firing per message instead sent one WS frame plus one
     * REST call each, which on 20 unread walks straight through the server's 10 events/second
     * inbound limit and comes back as `Rate limit exceeded`.
     */
    private fun scheduleMarkRead() {
        markReadJob?.cancel()
        markReadJob = scope.launch {
            delay(readReceiptDebounceMillis)
            val token = runCatching { accessToken() }.getOrNull() ?: return@launch
            markRead(token, hadUnread = true)
        }
    }

    /**
     * Fetches the newest page and appends messages we haven't seen. The screen
     * calls this on a fixed interval while the realtime socket is down.
     */
    fun refreshNewMessages() {
        val current = _state.value
        if (!current.hasLoadedOnce || current.isLoading || current.isSending) return
        scope.launch {
            val token = runCatching { accessToken() }.getOrNull() ?: return@launch
            try {
                val page = gateway.listMessages(
                    token,
                    chatId,
                    limit = pageSize,
                    offset = 0,
                    order = SortOrder.Desc,
                )
                var receivedNew = false
                _state.update { state ->
                    val knownIds = state.messages.mapTo(mutableSetOf()) { it.id }
                    val fresh = page.messages.asReversed().filterNot { it.id in knownIds }
                    receivedNew = fresh.isNotEmpty()
                    if (fresh.isEmpty()) state else state.copy(messages = state.messages + fresh)
                }
                if (receivedNew) scheduleMarkRead()
            } catch (_: ChatsError) {
                // Background poll: stay quiet, the next tick retries.
            }
        }
    }

    private fun markRead(token: String, hadUnread: Boolean) {
        if (!hadUnread) return
        // WS parity: send a live per-message receipt for the newest visible message from *another*
        // participant so their "seen" indicators update immediately, in addition to the REST bulk
        // mark-read below (which the backend does not broadcast per-message events for). Skipping our
        // own newest message avoids emitting a nonsensical read receipt for a message we authored.
        _state.value.messages.lastOrNull { it.user.id != currentUserId }?.let { newest ->
            runCatching { sendReadReceipt(chatId, newest.id) }
        }
        scope.launch {
            try {
                gateway.markChatsRead(token, listOf(chatId))
            } catch (error: ChatsError) {
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
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
    }
}
