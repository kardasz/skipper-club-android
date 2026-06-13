package app.skipperclub.ui.main.cruises

import app.skipperclub.data.ChatUser
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseParticipant
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseUserRole
import app.skipperclub.data.CruisesError
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

/** Which detail variant the current user should see (`docs/ux/flows/cruises.md`). */
enum class CruiseViewerRole { Organizer, Participant, Visitor }

data class CruiseDetailUiState(
    val cruise: Cruise? = null,
    val participants: List<CruiseParticipant> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isActing: Boolean = false,
    val inviteQuery: String = "",
    val inviteResults: List<ChatUser> = emptyList(),
    val isSearchingUsers: Boolean = false,
) {
    val viewerRole: CruiseViewerRole
        get() = when {
            cruise?.currentUserRole == CruiseUserRole.Organizer -> CruiseViewerRole.Organizer
            cruise?.currentUserParticipation?.state == CruiseParticipantState.Accepted ->
                CruiseViewerRole.Participant

            else -> CruiseViewerRole.Visitor
        }

    /** Crew tab of the manage screen: active members first, then past cancellations. */
    val crewMembers: List<CruiseParticipant>
        get() = participants
            .filter {
                it.state == CruiseParticipantState.Accepted ||
                    it.state == CruiseParticipantState.CanceledByParticipant ||
                    it.state == CruiseParticipantState.CanceledByOrganizer
            }
            .sortedByDescending { it.state == CruiseParticipantState.Accepted }

    /** Invitations tab of the manage screen: actionable requests/invites first. */
    val invitations: List<CruiseParticipant>
        get() = participants
            .filter {
                it.state == CruiseParticipantState.Pending ||
                    it.state == CruiseParticipantState.Invited ||
                    it.state == CruiseParticipantState.RejectedByParticipant ||
                    it.state == CruiseParticipantState.RejectedByOrganizer ||
                    it.state == CruiseParticipantState.WithdrawnByParticipant ||
                    it.state == CruiseParticipantState.WithdrawnByOrganizer
            }
            .sortedByDescending {
                it.state == CruiseParticipantState.Pending || it.state == CruiseParticipantState.Invited
            }
}

sealed interface CruiseDetailEvent {
    data class OperationFailed(val error: Exception) : CruiseDetailEvent
    data object SessionExpired : CruiseDetailEvent
    data object Deleted : CruiseDetailEvent
    /** Fired after any mutation so the list screen can sync its card. */
    data class CruiseChanged(val cruise: Cruise) : CruiseDetailEvent
}

/**
 * State holder for the cruise detail + participant management flows. Owns the
 * participant state machine transitions; the screen only decides which buttons
 * to render for [CruiseDetailUiState.viewerRole].
 */
class CruiseDetailController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val currentUserId: () -> String?,
    private val cruiseId: String,
    private val gateway: CruisesGateway = RealCruisesGateway,
    private val userSearchDebounceMillis: Long = 300,
) {
    private val _state = MutableStateFlow(CruiseDetailUiState())
    val state: StateFlow<CruiseDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CruiseDetailEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CruiseDetailEvent> = _events.asSharedFlow()

    private var userSearchJob: Job? = null

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            try {
                refreshFrom(token)
                _state.update { it.copy(isLoading = false) }
            } catch (error: CruisesError) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                _events.tryEmit(CruiseDetailEvent.OperationFailed(error))
            }
        }
    }

    /** Replaces the cruise after the edit wizard publishes an update. */
    fun onCruiseEdited(cruise: Cruise) {
        if (cruise.id != cruiseId) return
        _state.update { it.copy(cruise = cruise) }
        _events.tryEmit(CruiseDetailEvent.CruiseChanged(cruise))
    }

    /** Visitor sends a join request (`pending`). */
    fun join() {
        val userId = currentUserId() ?: return
        mutate { token ->
            gateway.addParticipant(token, cruiseId, userId)
        }
    }

    /** Visitor withdraws their own `pending` join request. */
    fun withdrawJoinRequest() {
        transitionOwnParticipation(CruiseParticipantState.WithdrawnByParticipant)
    }

    /** Invited user confirms participation. */
    fun acceptInvitation() {
        transitionOwnParticipation(CruiseParticipantState.Accepted)
    }

    /** Invited user declines the invitation. */
    fun rejectInvitation() {
        transitionOwnParticipation(CruiseParticipantState.RejectedByParticipant)
    }

    /** Accepted crew member leaves the cruise. */
    fun leave() {
        transitionOwnParticipation(CruiseParticipantState.CanceledByParticipant)
    }

    /** Organizer accepts a `pending` join request. */
    fun acceptRequest(participant: CruiseParticipant) {
        transitionParticipant(participant, CruiseParticipantState.Accepted)
    }

    /** Organizer rejects a `pending` join request. */
    fun rejectRequest(participant: CruiseParticipant) {
        transitionParticipant(participant, CruiseParticipantState.RejectedByOrganizer)
    }

    /** Organizer cancels an `invited` invitation. */
    fun cancelInvitation(participant: CruiseParticipant) {
        transitionParticipant(participant, CruiseParticipantState.WithdrawnByOrganizer)
    }

    /** Organizer removes an `accepted` crew member. */
    fun removeParticipant(participant: CruiseParticipant) {
        transitionParticipant(participant, CruiseParticipantState.CanceledByOrganizer)
    }

    /** Organizer invites a user found via [updateInviteQuery] (`invited`). */
    fun invite(user: ChatUser) {
        mutate { token ->
            gateway.addParticipant(token, cruiseId, user.id)
            _state.update { it.copy(inviteQuery = "", inviteResults = emptyList()) }
        }
    }

    fun deleteCruise() {
        if (_state.value.isActing) return
        _state.update { it.copy(isActing = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isActing = false) }
                return@launch
            }
            try {
                gateway.delete(token, cruiseId)
                _state.update { it.copy(isActing = false) }
                _events.tryEmit(CruiseDetailEvent.Deleted)
            } catch (error: CruisesError) {
                _state.update { it.copy(isActing = false) }
                _events.tryEmit(CruiseDetailEvent.OperationFailed(error))
            }
        }
    }

    fun updateInviteQuery(value: String) {
        _state.update { it.copy(inviteQuery = value) }
        userSearchJob?.cancel()
        if (value.trim().length < 2) {
            _state.update { it.copy(inviteResults = emptyList(), isSearchingUsers = false) }
            return
        }
        _state.update { it.copy(isSearchingUsers = true) }
        userSearchJob = scope.launch {
            delay(userSearchDebounceMillis)
            val token = requireToken() ?: run {
                _state.update { it.copy(isSearchingUsers = false) }
                return@launch
            }
            try {
                val page = gateway.searchUsers(token, UserSearchQuery(search = value.trim()))
                _state.update { state ->
                    val excluded = state.excludedInviteUserIds()
                    state.copy(
                        inviteResults = page.users.filterNot { it.id in excluded },
                        isSearchingUsers = false,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(inviteResults = emptyList(), isSearchingUsers = false) }
            }
        }
    }

    private fun transitionOwnParticipation(target: CruiseParticipantState) {
        val participationId = _state.value.cruise?.currentUserParticipation?.id ?: return
        mutate { token ->
            gateway.updateParticipantState(token, cruiseId, participationId, target)
        }
    }

    private fun transitionParticipant(participant: CruiseParticipant, target: CruiseParticipantState) {
        mutate { token ->
            gateway.updateParticipantState(token, cruiseId, participant.id, target)
        }
    }

    /** Runs a mutation, then re-fetches the cruise (and crew when organizer). */
    private fun mutate(block: suspend (token: String) -> Unit) {
        if (_state.value.isActing) return
        _state.update { it.copy(isActing = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isActing = false) }
                return@launch
            }
            try {
                block(token)
                refreshFrom(token)
                _state.update { it.copy(isActing = false) }
            } catch (error: CruisesError) {
                _state.update { it.copy(isActing = false) }
                _events.tryEmit(CruiseDetailEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun refreshFrom(token: String) {
        val cruise = gateway.get(token, cruiseId)
        val participants = if (cruise.currentUserRole == CruiseUserRole.Organizer) {
            gateway.participants(token, cruiseId).participants
        } else {
            emptyList()
        }
        _state.update { it.copy(cruise = cruise, participants = participants) }
        _events.tryEmit(CruiseDetailEvent.CruiseChanged(cruise))
    }

    /** Users already involved in the cruise are filtered out of invite search results. */
    private fun CruiseDetailUiState.excludedInviteUserIds(): Set<String> =
        buildSet {
            cruise?.organizer?.id?.let { add(it) }
            participants.forEach { participant ->
                if (!participant.state.isTerminal) add(participant.userId)
            }
        }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(CruiseDetailEvent.SessionExpired)
        return token
    }
}
