package app.skipperclub.ui.main.profile

import app.skipperclub.data.ChatsError
import app.skipperclub.data.CreateChatRequest
import app.skipperclub.data.FriendsError
import app.skipperclub.data.FriendshipStatus
import app.skipperclub.data.ProfileError
import app.skipperclub.ui.main.friends.FriendsGateway
import app.skipperclub.ui.main.friends.RealFriendsGateway
import app.skipperclub.ui.main.messages.ChatsGateway
import app.skipperclub.ui.main.messages.RealChatsGateway
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

/** Context-menu state for another member's profile (friend request + open chat). */
data class PublicProfileActionState(
    val friendshipStatus: FriendshipStatus = FriendshipStatus.None,
    val isSendingFriendRequest: Boolean = false,
    val isOpeningChat: Boolean = false,
) {
    /** The "Add to friends" action is available only with no existing relationship. */
    val canSendFriendRequest: Boolean
        get() = friendshipStatus == FriendshipStatus.None && !isSendingFriendRequest

    val friendRequestPending: Boolean get() = friendshipStatus == FriendshipStatus.Pending

    val alreadyFriends: Boolean get() = friendshipStatus == FriendshipStatus.Accepted
}

sealed interface PublicProfileEvent {
    data class LoadFailed(val error: Exception) : PublicProfileEvent
    data object SessionExpired : PublicProfileEvent
    data object FriendRequestSent : PublicProfileEvent
    data class FriendRequestFailed(val error: Exception) : PublicProfileEvent
    /** A one-to-one chat with the target user is ready; the screen opens the conversation. */
    data class OpenChat(val chatId: String) : PublicProfileEvent
    data class ChatFailed(val error: Exception) : PublicProfileEvent
}

/**
 * State holder for the read-only public profile of another member: a single fetch
 * of `GET /v1/users/{userId}` (reusing [ProfileUiState] + [ProfileScreenContent])
 * plus the two context-menu actions — sending a friend request
 * (`POST /friend-requests`) and starting a conversation (`POST /chats`). Plain
 * class owned by the composable via `remember`; unit-tested with fake gateways.
 */
class PublicProfileController(
    private val scope: CoroutineScope,
    private val userId: String,
    private val accessToken: suspend () -> String?,
    private val gateway: ProfileGateway = RealProfileGateway,
    private val friendsGateway: FriendsGateway = RealFriendsGateway,
    private val chatsGateway: ChatsGateway = RealChatsGateway,
) {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _actionState = MutableStateFlow(PublicProfileActionState())
    val actionState: StateFlow<PublicProfileActionState> = _actionState.asStateFlow()

    private val _events = MutableSharedFlow<PublicProfileEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PublicProfileEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
    }

    fun sendFriendRequest() {
        if (!_actionState.value.canSendFriendRequest) return
        _actionState.update { it.copy(isSendingFriendRequest = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _actionState.update { it.copy(isSendingFriendRequest = false) }
                return@launch
            }
            try {
                friendsGateway.sendFriendRequest(token, userId)
                _actionState.update {
                    it.copy(isSendingFriendRequest = false, friendshipStatus = FriendshipStatus.Pending)
                }
                _events.tryEmit(PublicProfileEvent.FriendRequestSent)
            } catch (error: FriendsError) {
                if (error is FriendsError.Conflict) {
                    // Request already exists / already friends — settle into a pending state
                    // and treat it as success rather than surfacing a confusing error.
                    _actionState.update {
                        it.copy(isSendingFriendRequest = false, friendshipStatus = FriendshipStatus.Pending)
                    }
                    _events.tryEmit(PublicProfileEvent.FriendRequestSent)
                } else {
                    _actionState.update { it.copy(isSendingFriendRequest = false) }
                    _events.tryEmit(PublicProfileEvent.FriendRequestFailed(error))
                }
            }
        }
    }

    fun openChat() {
        if (_actionState.value.isOpeningChat) return
        _actionState.update { it.copy(isOpeningChat = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _actionState.update { it.copy(isOpeningChat = false) }
                return@launch
            }
            try {
                val chat = chatsGateway.createChat(token, CreateChatRequest(participantIds = listOf(userId)))
                _actionState.update { it.copy(isOpeningChat = false) }
                _events.tryEmit(PublicProfileEvent.OpenChat(chat.id))
            } catch (error: ChatsError) {
                _actionState.update { it.copy(isOpeningChat = false) }
                _events.tryEmit(PublicProfileEvent.ChatFailed(error))
            }
        }
    }

    private fun reload(showAsRefreshing: Boolean) {
        loadJob?.cancel()
        _state.update {
            it.copy(isLoading = !showAsRefreshing, isRefreshing = showAsRefreshing, loadFailed = false)
        }
        loadJob = scope.launch {
            val token = requireToken() ?: run {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                return@launch
            }
            try {
                val profile = gateway.getUser(token, userId)
                _state.update {
                    it.copy(
                        profile = profile,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
                _actionState.update { it.copy(friendshipStatus = profile.currentUserFriendshipStatus) }
            } catch (error: ProfileError) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                _events.tryEmit(PublicProfileEvent.LoadFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(PublicProfileEvent.SessionExpired)
        return token
    }
}
