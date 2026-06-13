package app.skipperclub.ui.main.invitations

import app.skipperclub.data.Invitation
import app.skipperclub.data.InvitationListQuery
import app.skipperclub.data.InvitationsError
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

data class InvitationsUiState(
    val invitations: List<Invitation> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    /** Id of the invitation currently being resent, so its row/detail can show progress. */
    val resendingId: String? = null,
    /** True while a new invitation is being sent from the create form. */
    val isSending: Boolean = false,
)

sealed interface InvitationsEvent {
    data class OperationFailed(val error: Exception) : InvitationsEvent
    data class InvitationCreated(val email: String) : InvitationsEvent
    data class InvitationResent(val email: String) : InvitationsEvent
    data object SessionExpired : InvitationsEvent
}

/**
 * State holder for the invitations screen: pagination plus delete/resend
 * mutations. Plain class (no ViewModel/DI yet — see CLAUDE.md §State); owned by
 * the composable via `remember` and unit-tested with a fake [InvitationsGateway].
 */
class InvitationsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: InvitationsGateway = RealInvitationsGateway,
    private val pageSize: Int = 20,
) {
    private val _state = MutableStateFlow(InvitationsUiState())
    val state: StateFlow<InvitationsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InvitationsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<InvitationsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
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
                val snapshot = _state.value
                val page = gateway.list(
                    token,
                    InvitationListQuery(limit = pageSize, offset = snapshot.invitations.size),
                )
                _state.update { state ->
                    val knownIds = state.invitations.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        invitations = state.invitations + page.invitations.filterNot { it.id in knownIds },
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: InvitationsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(InvitationsEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Sends a brand-new invitation to [email]. On success reloads the list so the
     * new row appears, and emits [InvitationsEvent.InvitationCreated] so the UI can
     * dismiss the form and confirm.
     */
    fun createInvitation(email: String) {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSending = false) }
                return@launch
            }
            try {
                gateway.send(token, email)
                _state.update { it.copy(isSending = false) }
                _events.tryEmit(InvitationsEvent.InvitationCreated(email))
                reload(showAsRefreshing = true)
            } catch (error: InvitationsError) {
                _state.update { it.copy(isSending = false) }
                _events.tryEmit(InvitationsEvent.OperationFailed(error))
            }
        }
    }

    fun delete(invitation: Invitation) {
        val previous = _state.value.invitations
        _state.update { state ->
            state.copy(invitations = state.invitations.filterNot { it.id == invitation.id })
        }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(invitations = previous) }
                return@launch
            }
            try {
                gateway.delete(token, invitation.id)
            } catch (error: InvitationsError) {
                _state.update { it.copy(invitations = previous) }
                _events.tryEmit(InvitationsEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Re-sends the invitation to its email. The server soft-deletes the old
     * invitation and creates a fresh one (new code + expiry), so we reload the
     * list on success to surface the updated row.
     */
    fun resend(invitation: Invitation) {
        if (_state.value.resendingId != null) return
        _state.update { it.copy(resendingId = invitation.id) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(resendingId = null) }
                return@launch
            }
            try {
                gateway.send(token, invitation.email)
                _events.tryEmit(InvitationsEvent.InvitationResent(invitation.email))
                reload(showAsRefreshing = true, clearResending = true)
            } catch (error: InvitationsError) {
                _state.update { it.copy(resendingId = null) }
                _events.tryEmit(InvitationsEvent.OperationFailed(error))
            }
        }
    }

    private fun reload(showAsRefreshing: Boolean, clearResending: Boolean = false) {
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
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = true,
                        hasLoadedOnce = true,
                        resendingId = if (clearResending) null else it.resendingId,
                    )
                }
                return@launch
            }
            try {
                val page = gateway.list(token, InvitationListQuery(limit = pageSize, offset = 0))
                _state.update {
                    it.copy(
                        invitations = page.invitations,
                        hasMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                        resendingId = if (clearResending) null else it.resendingId,
                    )
                }
            } catch (error: InvitationsError) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = true,
                        hasLoadedOnce = true,
                        resendingId = if (clearResending) null else it.resendingId,
                    )
                }
                _events.tryEmit(InvitationsEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(InvitationsEvent.SessionExpired)
        return token
    }
}
