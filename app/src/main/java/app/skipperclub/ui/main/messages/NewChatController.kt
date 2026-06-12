package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatUser
import app.skipperclub.data.ChatsError
import app.skipperclub.data.CreateChatRequest
import app.skipperclub.data.UserSearchQuery
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

data class NewChatUiState(
    val searchQuery: String = "",
    val results: List<ChatUser> = emptyList(),
    val selected: List<ChatUser> = emptyList(),
    val groupName: String = "",
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val hasSearchedOnce: Boolean = false,
    val isCreating: Boolean = false,
) {
    val isGroup: Boolean
        get() = selected.size > 1

    val canCreate: Boolean
        get() = !isCreating && selected.isNotEmpty() && (!isGroup || groupName.isNotBlank())
}

sealed interface NewChatEvent {
    data class ChatCreated(val chat: Chat) : NewChatEvent
    data class OperationFailed(val error: Exception) : NewChatEvent
    data object SessionExpired : NewChatEvent
}

/**
 * State holder for the new-chat flow: debounced member search against
 * `GET /v1/users`, participant selection and chat creation. One participant
 * creates a 1:1 chat (server deduplicates), two or more require a group name.
 */
class NewChatController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val currentUserId: String?,
    private val gateway: ChatsGateway = RealChatsGateway,
    private val pageSize: Int = 20,
    private val searchDebounceMillis: Long = 300,
) {
    private val _state = MutableStateFlow(NewChatUiState())
    val state: StateFlow<NewChatUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NewChatEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NewChatEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null

    fun loadInitialIfNeeded() {
        if (_state.value.hasSearchedOnce || _state.value.isSearching) return
        search()
    }

    fun setSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(searchDebounceMillis)
            search()
        }
    }

    fun toggleUser(user: ChatUser) {
        _state.update { state ->
            val selected = if (state.selected.any { it.id == user.id }) {
                state.selected.filterNot { it.id == user.id }
            } else {
                state.selected + user
            }
            state.copy(selected = selected)
        }
    }

    fun setGroupName(name: String) {
        _state.update { it.copy(groupName = name) }
    }

    fun create() {
        val snapshot = _state.value
        if (!snapshot.canCreate) return
        _state.update { it.copy(isCreating = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isCreating = false) }
                return@launch
            }
            try {
                val chat = gateway.createChat(
                    token,
                    CreateChatRequest(
                        participantIds = snapshot.selected.map { it.id },
                        name = snapshot.groupName.trim().takeIf { snapshot.isGroup },
                    ),
                )
                _state.update { it.copy(isCreating = false) }
                _events.tryEmit(NewChatEvent.ChatCreated(chat))
            } catch (error: ChatsError) {
                _state.update { it.copy(isCreating = false) }
                _events.tryEmit(NewChatEvent.OperationFailed(error))
            }
        }
    }

    private fun search() {
        _state.update { it.copy(isSearching = true, searchFailed = false) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSearching = false, searchFailed = true, hasSearchedOnce = true) }
                return@launch
            }
            try {
                val page = gateway.searchUsers(
                    token,
                    UserSearchQuery(
                        search = _state.value.searchQuery.trim().takeIf { it.length >= 2 },
                        limit = pageSize,
                        offset = 0,
                    ),
                )
                _state.update { state ->
                    state.copy(
                        results = page.users.filterNot { it.id == currentUserId },
                        isSearching = false,
                        searchFailed = false,
                        hasSearchedOnce = true,
                    )
                }
            } catch (error: ChatsError) {
                _state.update { it.copy(isSearching = false, searchFailed = true, hasSearchedOnce = true) }
                _events.tryEmit(NewChatEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(NewChatEvent.SessionExpired)
        return token
    }
}
