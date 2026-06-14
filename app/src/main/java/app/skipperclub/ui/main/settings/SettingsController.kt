package app.skipperclub.ui.main.settings

import app.skipperclub.data.NotificationSettings
import app.skipperclub.data.SettingsError
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

data class SettingsUiState(
    val notifications: NotificationSettings? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val hasLoadedOnce: Boolean = false,
)

sealed interface SettingsEvent {
    data class LoadFailed(val error: Exception) : SettingsEvent
    data class SaveFailed(val error: Exception) : SettingsEvent
    data object SessionExpired : SettingsEvent
}

/**
 * State holder for the "Settings" screen. Today it owns only the notification
 * channel preferences (`GET`/`PUT /v1/profile/notification-settings`); more
 * settings sections will hang off this controller over time. Toggling a channel
 * applies optimistically and `PUT`s the full pair (the endpoint uses
 * full-replacement semantics), reverting to the last confirmed value on failure.
 *
 * Plain class (no ViewModel/DI yet — see CLAUDE.md §State); owned by the
 * composable via `remember` and unit-tested with a fake [SettingsGateway].
 */
class SettingsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: SettingsGateway = RealSettingsGateway,
) {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var saveJob: Job? = null

    /** The last server-confirmed value, used to roll back a failed optimistic toggle. */
    private var confirmed: NotificationSettings? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = scope.launch {
            val token = requireToken { it.copy(isLoading = false, loadFailed = true, hasLoadedOnce = true) }
                ?: return@launch
            try {
                val settings = gateway.getNotificationSettings(token)
                confirmed = settings
                _state.update {
                    it.copy(
                        notifications = settings,
                        isLoading = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: SettingsError) {
                _state.update { it.copy(isLoading = false, loadFailed = true, hasLoadedOnce = true) }
                _events.tryEmit(SettingsEvent.LoadFailed(error))
            }
        }
    }

    fun setEmailEnabled(enabled: Boolean) {
        val current = _state.value.notifications ?: return
        save(current.copy(emailNotificationsEnabled = enabled))
    }

    fun setPushEnabled(enabled: Boolean) {
        val current = _state.value.notifications ?: return
        save(current.copy(pushNotificationsEnabled = enabled))
    }

    private fun save(target: NotificationSettings) {
        if (target == _state.value.notifications && target == confirmed) return
        // Optimistically reflect the toggle; the latest intent always wins.
        saveJob?.cancel()
        _state.update { it.copy(notifications = target, isSaving = true) }
        saveJob = scope.launch {
            val token = requireToken { it.copy(isSaving = false, notifications = confirmed) }
                ?: return@launch
            try {
                val saved = gateway.updateNotificationSettings(token, target)
                confirmed = saved
                _state.update { it.copy(notifications = saved, isSaving = false) }
            } catch (error: SettingsError) {
                // Revert to the last server-confirmed pair so the switches don't lie.
                _state.update { it.copy(notifications = confirmed, isSaving = false) }
                _events.tryEmit(SettingsEvent.SaveFailed(error))
            }
        }
    }

    private suspend inline fun requireToken(onMissing: (SettingsUiState) -> SettingsUiState): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) {
            _state.update(onMissing)
            _events.tryEmit(SettingsEvent.SessionExpired)
        }
        return token
    }
}
