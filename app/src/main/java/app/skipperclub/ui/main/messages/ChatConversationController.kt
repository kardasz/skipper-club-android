package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatsError
import app.skipperclub.data.SortOrder
import kotlinx.coroutines.CoroutineScope
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
) {
    private val _state = MutableStateFlow(ChatConversationUiState())
    val state: StateFlow<ChatConversationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatConversationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ChatConversationEvent> = _events.asSharedFlow()

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
        scope.launch {
            try {
                gateway.markChatsRead(token, listOf(chatId))
            } catch (error: ChatsError) {
                _events.tryEmit(ChatConversationEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(ChatConversationEvent.SessionExpired)
        return token
    }
}
