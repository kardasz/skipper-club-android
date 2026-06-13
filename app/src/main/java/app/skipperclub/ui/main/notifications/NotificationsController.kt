package app.skipperclub.ui.main.notifications

import app.skipperclub.data.AppNotification
import app.skipperclub.data.NotificationListQuery
import app.skipperclub.data.NotificationStatus
import app.skipperclub.data.NotificationsError
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

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
) {
    val unreadCount: Int get() = notifications.count { it.isUnread }
    val hasUnread: Boolean get() = unreadCount > 0
}

sealed interface NotificationsEvent {
    data class OperationFailed(val error: Exception) : NotificationsEvent
    data object SessionExpired : NotificationsEvent
}

/**
 * State holder for the notification center: pagination plus read/delete
 * mutations with optimistic UI updates. Plain class (no ViewModel/DI yet —
 * see CLAUDE.md §State); owned by the composable via `remember` and unit-tested
 * with a fake [NotificationsGateway].
 */
class NotificationsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: NotificationsGateway = RealNotificationsGateway,
    private val pageSize: Int = 20,
) {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NotificationsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NotificationsEvent> = _events.asSharedFlow()

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
                    NotificationListQuery(limit = pageSize, offset = snapshot.notifications.size),
                )
                _state.update { state ->
                    val knownIds = state.notifications.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        notifications = state.notifications + page.notifications.filterNot { it.id in knownIds },
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: NotificationsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(NotificationsEvent.OperationFailed(error))
            }
        }
    }

    /** Marks a single notification read; clears the badge optimistically. */
    fun markRead(notification: AppNotification) {
        if (!notification.isUnread) return
        _state.update { state -> state.updateStatus(notification.id, NotificationStatus.Read) }
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.updateStatus(token, notification.id, NotificationStatus.Read)
            } catch (error: NotificationsError) {
                _state.update { state -> state.updateStatus(notification.id, NotificationStatus.Unread) }
                _events.tryEmit(NotificationsEvent.OperationFailed(error))
            }
        }
    }

    fun markAllRead() {
        if (!_state.value.hasUnread) return
        val previous = _state.value.notifications
        _state.update { state ->
            state.copy(notifications = state.notifications.map { it.markedRead() })
        }
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.markAllRead(token)
            } catch (error: NotificationsError) {
                _state.update { it.copy(notifications = previous) }
                _events.tryEmit(NotificationsEvent.OperationFailed(error))
            }
        }
    }

    fun delete(notification: AppNotification) {
        val previous = _state.value.notifications
        _state.update { state ->
            state.copy(notifications = state.notifications.filterNot { it.id == notification.id })
        }
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.delete(token, notification.id)
            } catch (error: NotificationsError) {
                _state.update { it.copy(notifications = previous) }
                _events.tryEmit(NotificationsEvent.OperationFailed(error))
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
                val page = gateway.list(token, NotificationListQuery(limit = pageSize, offset = 0))
                _state.update {
                    it.copy(
                        notifications = page.notifications,
                        hasMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: NotificationsError) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                _events.tryEmit(NotificationsEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(NotificationsEvent.SessionExpired)
        return token
    }
}

private fun NotificationsUiState.updateStatus(id: String, status: NotificationStatus): NotificationsUiState =
    copy(
        notifications = notifications.map {
            if (it.id == id) it.copy(status = status, readAt = if (status == NotificationStatus.Read) it.readAt else null) else it
        },
    )

private fun AppNotification.markedRead(): AppNotification =
    if (isUnread) copy(status = NotificationStatus.Read) else this
