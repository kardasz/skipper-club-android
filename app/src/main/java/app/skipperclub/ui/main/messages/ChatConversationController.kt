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
    private val typingExpiryMillis: Long = 3_000L,
    /**
     * `message:read` over the socket for the newest visible message, mirroring what iOS/Web send
     * (see the transport-parity table in docs/api/messages/websocket.md). Injectable so tests don't
     * need a real socket.
     */
    private val sendReadReceipt: (chatId: String, messageId: String) -> Unit = { id, messageId ->
        WebSocketChatRealtimeClient.sendMessageRead(id, messageId)
    },
) {
    private val _state = MutableStateFlow(ChatConversationUiState())
    val state: StateFlow<ChatConversationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatConversationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ChatConversationEvent> = _events.asSharedFlow()

    private val typingExpiryJobs = mutableMapOf<String, Job>()

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
        _state.update { it.copy(isSending = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSending = false) }
                return@launch
            }
            try {
                val message = gateway.sendMessage(token, chatId, trimmed)
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
            scope.launch {
                val token = runCatching { accessToken() }.getOrNull() ?: return@launch
                markRead(token, hadUnread = true)
            }
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
                if (receivedNew) markRead(token, hadUnread = true)
            } catch (_: ChatsError) {
                // Background poll: stay quiet, the next tick retries.
            }
        }
    }

    private fun markRead(token: String, hadUnread: Boolean) {
        if (!hadUnread) return
        // WS parity: send a live per-message receipt for the newest visible message so other
        // participants' "seen" indicators update immediately, in addition to the REST bulk
        // mark-read below (which the backend does not broadcast per-message events for).
        _state.value.messages.lastOrNull()?.let { newest ->
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
     * Applies a `message:read` receipt pushed over the socket: flips the matching message's
     * [ChatMessage.read] flag so a "seen" indicator can reflect another participant's read live.
     * Our own read receipts are echoed back to the room too, so they are ignored here.
     */
    fun onRealtimeMessageRead(messageId: String, userId: String) {
        if (userId == currentUserId) return
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(read = true) else it
                },
            )
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(ChatConversationEvent.SessionExpired)
        return token
    }
}
