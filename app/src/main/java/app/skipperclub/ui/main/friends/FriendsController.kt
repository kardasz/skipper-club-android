package app.skipperclub.ui.main.friends

import app.skipperclub.data.FriendListQuery
import app.skipperclub.data.FriendRequest
import app.skipperclub.data.FriendRequestListQuery
import app.skipperclub.data.FriendRequestState
import app.skipperclub.data.FriendUser
import app.skipperclub.data.FriendsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val receivedRequests: List<FriendRequest> = emptyList(),
    val sentRequests: List<FriendRequest> = emptyList(),
    val friends: List<FriendUser> = emptyList(),
    val friendsTotal: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreFriends: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    /** Requests with an accept/reject/cancel mutation in flight. */
    val busyRequestIds: Set<String> = emptySet(),
    /** Friends with a remove mutation in flight. */
    val removingFriendIds: Set<String> = emptySet(),
) {
    val hasRequests: Boolean get() = receivedRequests.isNotEmpty() || sentRequests.isNotEmpty()
    val isEmpty: Boolean get() = !hasRequests && friends.isEmpty()
}

sealed interface FriendsEvent {
    data class OperationFailed(val error: Exception) : FriendsEvent
    data object SessionExpired : FriendsEvent
}

/**
 * State holder for the "Friends" screen: pending friend requests (received and
 * sent), the friend list with pagination, and the accept/reject/cancel/remove
 * mutations with optimistic UI updates. Plain class (no ViewModel/DI yet — see
 * CLAUDE.md §State); owned by the composable via `remember` and unit-tested with
 * a fake [FriendsGateway].
 */
class FriendsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: FriendsGateway = RealFriendsGateway,
    private val friendsPageSize: Int = 20,
    private val requestsPageSize: Int = 50,
) {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
    }

    fun loadMoreFriends() {
        val current = _state.value
        if (!current.hasMoreFriends || current.isLoading || current.isRefreshing || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            try {
                val snapshot = _state.value
                val page = gateway.listFriends(
                    token,
                    FriendListQuery(limit = friendsPageSize, offset = snapshot.friends.size),
                )
                _state.update { state ->
                    val knownIds = state.friends.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        friends = state.friends + page.friends.filterNot { it.id in knownIds },
                        friendsTotal = page.total,
                        hasMoreFriends = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: FriendsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(FriendsEvent.OperationFailed(error))
            }
        }
    }

    /** Accept a received request: the other user becomes a friend. */
    fun acceptRequest(request: FriendRequest) {
        if (request.id in _state.value.busyRequestIds) return
        _state.update { it.copy(busyRequestIds = it.busyRequestIds + request.id) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(busyRequestIds = it.busyRequestIds - request.id) }
                return@launch
            }
            try {
                gateway.updateFriendRequest(token, request.id, FriendRequestState.Accepted)
                _state.update { state ->
                    val alreadyFriend = state.friends.any { it.id == request.user.id }
                    state.copy(
                        receivedRequests = state.receivedRequests.filterNot { it.id == request.id },
                        friends = if (alreadyFriend) state.friends else listOf(request.user) + state.friends,
                        friendsTotal = if (alreadyFriend) state.friendsTotal else state.friendsTotal + 1,
                        busyRequestIds = state.busyRequestIds - request.id,
                    )
                }
            } catch (error: FriendsError) {
                _state.update { it.copy(busyRequestIds = it.busyRequestIds - request.id) }
                _events.tryEmit(FriendsEvent.OperationFailed(error))
            }
        }
    }

    /** Reject a received request. */
    fun rejectRequest(request: FriendRequest) {
        mutateRequest(request) { token ->
            gateway.updateFriendRequest(token, request.id, FriendRequestState.Rejected)
        }
    }

    /** Withdraw a request the current user sent. */
    fun cancelRequest(request: FriendRequest) {
        mutateRequest(request) { token ->
            gateway.cancelFriendRequest(token, request.id)
        }
    }

    private inline fun mutateRequest(
        request: FriendRequest,
        crossinline action: suspend (String) -> Unit,
    ) {
        if (request.id in _state.value.busyRequestIds) return
        _state.update { it.copy(busyRequestIds = it.busyRequestIds + request.id) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(busyRequestIds = it.busyRequestIds - request.id) }
                return@launch
            }
            try {
                action(token)
                _state.update { state ->
                    state.copy(
                        receivedRequests = state.receivedRequests.filterNot { it.id == request.id },
                        sentRequests = state.sentRequests.filterNot { it.id == request.id },
                        busyRequestIds = state.busyRequestIds - request.id,
                    )
                }
            } catch (error: FriendsError) {
                _state.update { it.copy(busyRequestIds = it.busyRequestIds - request.id) }
                _events.tryEmit(FriendsEvent.OperationFailed(error))
            }
        }
    }

    fun removeFriend(friend: FriendUser) {
        if (friend.id in _state.value.removingFriendIds) return
        val previous = _state.value
        _state.update { state ->
            state.copy(
                friends = state.friends.filterNot { it.id == friend.id },
                friendsTotal = (state.friendsTotal - 1).coerceAtLeast(0),
                removingFriendIds = state.removingFriendIds + friend.id,
            )
        }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(friends = previous.friends, friendsTotal = previous.friendsTotal, removingFriendIds = it.removingFriendIds - friend.id) }
                return@launch
            }
            try {
                gateway.removeFriend(token, friend.id)
                _state.update { it.copy(removingFriendIds = it.removingFriendIds - friend.id) }
            } catch (error: FriendsError) {
                _state.update {
                    it.copy(
                        friends = previous.friends,
                        friendsTotal = previous.friendsTotal,
                        removingFriendIds = it.removingFriendIds - friend.id,
                    )
                }
                _events.tryEmit(FriendsEvent.OperationFailed(error))
            }
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
                val received = gateway.listFriendRequests(
                    token,
                    FriendRequestListQuery(state = FriendRequestState.Pending, limit = requestsPageSize),
                )
                val sent = gateway.listFriendRequests(
                    token,
                    FriendRequestListQuery(state = FriendRequestState.Sent, limit = requestsPageSize),
                )
                val friends = gateway.listFriends(token, FriendListQuery(limit = friendsPageSize, offset = 0))
                _state.update {
                    it.copy(
                        receivedRequests = received.requests,
                        sentRequests = sent.requests,
                        friends = friends.friends,
                        friendsTotal = friends.total,
                        hasMoreFriends = friends.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: FriendsError) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                _events.tryEmit(FriendsEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(FriendsEvent.SessionExpired)
        return token
    }
}
