package app.skipperclub.ui.main.friends

import app.skipperclub.data.FriendListQuery
import app.skipperclub.data.FriendUser
import app.skipperclub.data.FriendsError
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

data class FriendSearchUiState(
    val searchQuery: String = "",
    val results: List<FriendUser> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val hasSearchedOnce: Boolean = false,
    /** Users a request has been successfully sent to this session. */
    val sentUserIds: Set<String> = emptySet(),
    /** Users with a send-request mutation in flight. */
    val sendingUserIds: Set<String> = emptySet(),
)

sealed interface FriendSearchEvent {
    data class RequestSent(val user: FriendUser) : FriendSearchEvent
    data class OperationFailed(val error: Exception) : FriendSearchEvent
    data object SessionExpired : FriendSearchEvent
}

/**
 * State holder for the "invite a friend" flow: debounced community-member search
 * against `GET /v1/users`, and sending friend requests (`POST /friend-requests`)
 * with per-row in-flight/sent tracking. Mirrors [app.skipperclub.ui.main.messages]'s
 * NewChatController.
 */
class FriendSearchController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val currentUserId: String?,
    private val gateway: FriendsGateway = RealFriendsGateway,
    private val pageSize: Int = 20,
    private val searchDebounceMillis: Long = 300,
) {
    private val _state = MutableStateFlow(FriendSearchUiState())
    val state: StateFlow<FriendSearchUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FriendSearchEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<FriendSearchEvent> = _events.asSharedFlow()

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

    fun sendRequest(user: FriendUser) {
        val current = _state.value
        if (user.id in current.sendingUserIds || user.id in current.sentUserIds) return
        _state.update { it.copy(sendingUserIds = it.sendingUserIds + user.id) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(sendingUserIds = it.sendingUserIds - user.id) }
                return@launch
            }
            try {
                gateway.sendFriendRequest(token, user.id)
                _state.update {
                    it.copy(
                        sendingUserIds = it.sendingUserIds - user.id,
                        sentUserIds = it.sentUserIds + user.id,
                    )
                }
                _events.tryEmit(FriendSearchEvent.RequestSent(user))
            } catch (error: FriendsError) {
                _state.update { it.copy(sendingUserIds = it.sendingUserIds - user.id) }
                // Treat "already exists / already friends" as a sent state so the row settles.
                if (error is FriendsError.Conflict) {
                    _state.update { it.copy(sentUserIds = it.sentUserIds + user.id) }
                }
                _events.tryEmit(FriendSearchEvent.OperationFailed(error))
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
                    FriendListQuery(
                        search = _state.value.searchQuery.trim().takeIf { it.length >= 2 },
                        limit = pageSize,
                        offset = 0,
                    ),
                )
                _state.update { state ->
                    state.copy(
                        results = page.friends.filterNot { it.id == currentUserId },
                        isSearching = false,
                        searchFailed = false,
                        hasSearchedOnce = true,
                    )
                }
            } catch (error: FriendsError) {
                _state.update { it.copy(isSearching = false, searchFailed = true, hasSearchedOnce = true) }
                _events.tryEmit(FriendSearchEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(FriendSearchEvent.SessionExpired)
        return token
    }
}
