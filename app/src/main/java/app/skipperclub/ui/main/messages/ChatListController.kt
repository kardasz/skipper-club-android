package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatListQuery
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatsError
import app.skipperclub.data.UnreadMessagesStore
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

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val searchQuery: String = "",
    val typeFilter: ChatType? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
) {
    fun toQuery(limit: Int, offset: Int): ChatListQuery =
        ChatListQuery(
            type = typeFilter,
            search = searchQuery.trim().takeIf { it.isNotEmpty() },
            limit = limit,
            offset = offset,
        )
}

sealed interface ChatListEvent {
    data class OperationFailed(val error: Exception) : ChatListEvent
    data object SessionExpired : ChatListEvent
    data object ChatDeleted : ChatListEvent
}

/**
 * State holder for the chat list: pagination, server-side search/type filter
 * and chat mutations. Plain class (no ViewModel/DI yet — see CLAUDE.md §State);
 * owned by the composable via `remember` and unit-tested with a fake [ChatsGateway].
 */
class ChatListController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: ChatsGateway = RealChatsGateway,
    private val pageSize: Int = 20,
    private val searchDebounceMillis: Long = 300,
    private val reloadDebounceMillis: Long = 250,
) {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatListEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ChatListEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var reloadJob: Job? = null

    /**
     * How many rows this controller has fetched through paged requests — the offset the next
     * [loadMore] must use. Not derived from [ChatListUiState.chats]: that list also grows from
     * [onRealtimeMessage] and [onChatCreated] prepends, and every such row would shift the server's
     * `updatedAt DESC` window by one and silently skip a chat on the next page. Same rule as the
     * conversation's history offset (task_shared_catchup_contract.md §3.1).
     */
    private var listOffset: Int = 0

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
    }

    fun setSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(searchDebounceMillis)
            reload(showAsRefreshing = false)
        }
    }

    fun setTypeFilter(type: ChatType?) {
        if (type == _state.value.typeFilter) return
        _state.update { it.copy(typeFilter = type) }
        reload(showAsRefreshing = false)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isRefreshing || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        loadJob = scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            try {
                val page = gateway.listChats(
                    token,
                    _state.value.toQuery(limit = pageSize, offset = listOffset),
                )
                // Advance by the raw row count, before deduplication: the offset counts rows on the
                // server's list, not the ones we chose to keep.
                listOffset += page.chats.size
                _state.update { state ->
                    val knownIds = state.chats.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        chats = state.chats + page.chats.filterNot { it.id in knownIds },
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: ChatsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(ChatListEvent.OperationFailed(error))
            }
        }
    }

    /** Hides the chat for the current user (server keeps it for other participants). */
    fun deleteChat(chat: Chat) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.deleteChat(token, chat.id)
                _state.update { state ->
                    state.copy(chats = state.chats.filterNot { it.id == chat.id })
                }
                _events.tryEmit(ChatListEvent.ChatDeleted)
            } catch (error: ChatsError) {
                _events.tryEmit(ChatListEvent.OperationFailed(error))
            }
        }
    }

    fun markChatRead(chat: Chat) {
        if (chat.unreadCount == 0) return
        // Optimistic: clearing the badge must not wait for the round-trip.
        _state.update { state ->
            state.copy(
                chats = state.chats.map {
                    if (it.id == chat.id) it.copy(unreadCount = 0) else it
                },
            )
        }
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.markChatsRead(token, listOf(chat.id))
                // Reconcile the app-wide badge with the server once the read has committed.
                UnreadMessagesStore.refresh()
            } catch (error: ChatsError) {
                _events.tryEmit(ChatListEvent.OperationFailed(error))
            }
        }
    }

    /** Clears the unread badge locally after the conversation marked itself read. */
    fun onChatOpened(chatId: String) {
        _state.update { state ->
            state.copy(
                chats = state.chats.map {
                    if (it.id == chatId) it.copy(unreadCount = 0) else it
                },
            )
        }
    }

    /**
     * Applies a message pushed over the socket: bumps the chat to the top with
     * the new preview and unread count. A message for a chat not in the list
     * (new or previously hidden) triggers a reload.
     *
     * A recipient viewing the open chat receives the same message twice — `message:new`
     * on the joined room and `message:received` on their personal room. The server sends
     * `message:received` to every participant except the sender, regardless of room
     * membership, so the update is idempotent by [ChatMessage.id]: a message already shown
     * as the last one is re-applied without incrementing the unread count again.
     */
    fun onRealtimeMessage(message: ChatMessage, isChatOpen: Boolean) {
        val snapshot = _state.value
        if (!snapshot.hasLoadedOnce) return
        val existing = snapshot.chats.firstOrNull { it.id == message.chatId }
        if (existing == null) {
            // Respect an active search/type filter by asking the server again. The server emits both
            // message:new (chat room) and message:received (personal room) for the same first message
            // in a new chat, so coalesce the pair into a single reload instead of firing two.
            scheduleReload()
            return
        }
        val alreadyApplied = existing.lastMessage?.id == message.id
        _state.update { state ->
            val updated = existing.copy(
                lastMessage = message,
                updatedAt = message.createdAt,
                unreadCount = when {
                    isChatOpen -> 0
                    alreadyApplied -> existing.unreadCount
                    else -> existing.unreadCount + 1
                },
            )
            state.copy(chats = listOf(updated) + state.chats.filterNot { it.id == message.chatId })
        }
    }

    /**
     * Catch up the list after the socket reconnects. Messages missed while it was down can leave
     * stale previews and unread counts on rows other than the open conversation (which refreshes
     * itself), so we re-read the list. No-op before the first load — [loadInitialIfNeeded] handles
     * the cold start.
     */
    fun onRealtimeReconnected() {
        if (!_state.value.hasLoadedOnce) return
        reload(showAsRefreshing = true)
    }

    /** Surfaces a freshly created chat at the top without a round-trip. */
    fun onChatCreated(chat: Chat) {
        _state.update { state ->
            state.copy(chats = listOf(chat) + state.chats.filterNot { it.id == chat.id })
        }
    }

    private fun reload(showAsRefreshing: Boolean) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = !showAsRefreshing,
                isRefreshing = showAsRefreshing,
                isLoadingMore = false,
                loadFailed = false,
            )
        }
        loadJob = scope.launch {
            val token = requireToken() ?: run {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                return@launch
            }
            try {
                val page = gateway.listChats(
                    token,
                    _state.value.toQuery(limit = pageSize, offset = 0),
                )
                // A reload replaces `chats` wholesale, so the paging cursor restarts from this page.
                listOffset = page.chats.size
                _state.update {
                    it.copy(
                        chats = page.chats,
                        hasMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: ChatsError) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                _events.tryEmit(ChatListEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Coalesces realtime-triggered reloads for an unlisted chat: the first trigger arms a short
     * debounce, and further triggers while it is pending are no-ops, so a burst of events (the
     * message:new + message:received pair for one message) results in a single [reload]. Only this
     * realtime path is debounced — user-driven reloads ([refresh], [loadInitialIfNeeded]) stay
     * immediate. The job lives on [scope], so it is cancelled with the controller on teardown; a
     * reload lost to teardown mid-window only leaves a stale list until the next screen entry, which
     * reloads anyway.
     */
    private fun scheduleReload() {
        if (reloadJob?.isActive == true) return
        reloadJob = scope.launch {
            delay(reloadDebounceMillis)
            reload(showAsRefreshing = true)
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(ChatListEvent.SessionExpired)
        return token
    }
}
