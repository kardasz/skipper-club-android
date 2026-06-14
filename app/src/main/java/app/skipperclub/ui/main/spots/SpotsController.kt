package app.skipperclub.ui.main.spots

import app.skipperclub.data.Spot
import app.skipperclub.data.SpotListQuery
import app.skipperclub.data.SpotsError
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

data class SpotsUiState(
    val spots: List<Spot> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    /** True while a create or update is being submitted from the form. */
    val isSaving: Boolean = false,
)

sealed interface SpotsEvent {
    data class OperationFailed(val error: Exception) : SpotsEvent
    data class SpotCreated(val spot: Spot) : SpotsEvent
    data class SpotUpdated(val spot: Spot) : SpotsEvent
    data class SpotDeleted(val name: String) : SpotsEvent
    data object SessionExpired : SpotsEvent
}

/**
 * State holder for the admin spots screen: paginated list plus create / update /
 * delete mutations. Plain class (no ViewModel/DI yet — see CLAUDE.md §State);
 * owned by the composable via `remember` and unit-tested with a fake gateway.
 */
class SpotsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: SpotsGateway = RealSpotsGateway,
    private val pageSize: Int = 20,
) {
    private val _state = MutableStateFlow(SpotsUiState())
    val state: StateFlow<SpotsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SpotsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SpotsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
    }

    /** Updates the active name filter and reloads the list from the first page. */
    fun search(name: String) {
        _state.update { it.copy(query = name) }
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
                val snapshot = _state.value
                val page = gateway.list(
                    token,
                    SpotListQuery(name = snapshot.query.trim().ifBlank { null }, limit = pageSize, offset = snapshot.spots.size),
                )
                _state.update { state ->
                    val knownIds = state.spots.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        spots = state.spots + page.spots.filterNot { it.id in knownIds },
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: SpotsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(SpotsEvent.OperationFailed(error))
            }
        }
    }

    fun createSpot(form: SpotForm) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            try {
                val created = gateway.create(token, form.toCreateRequest())
                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(SpotsEvent.SpotCreated(created))
                reload(showAsRefreshing = true)
            } catch (error: SpotsError) {
                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(SpotsEvent.OperationFailed(error))
            }
        }
    }

    fun updateSpot(original: Spot, form: SpotForm) {
        if (_state.value.isSaving) return
        // No-op edit: nothing changed, just acknowledge so the form can close.
        if (!hasChanges(original, form)) {
            _events.tryEmit(SpotsEvent.SpotUpdated(original))
            return
        }
        _state.update { it.copy(isSaving = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            try {
                val updated = gateway.update(token, original.id, buildUpdateRequest(original, form))
                _state.update { state ->
                    state.copy(
                        isSaving = false,
                        spots = state.spots.map { if (it.id == updated.id) updated else it },
                    )
                }
                _events.tryEmit(SpotsEvent.SpotUpdated(updated))
            } catch (error: SpotsError) {
                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(SpotsEvent.OperationFailed(error))
            }
        }
    }

    fun delete(spot: Spot) {
        val previous = _state.value.spots
        _state.update { state ->
            state.copy(spots = state.spots.filterNot { it.id == spot.id })
        }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(spots = previous) }
                return@launch
            }
            try {
                gateway.delete(token, spot.id)
                _events.tryEmit(SpotsEvent.SpotDeleted(spot.name))
            } catch (error: SpotsError) {
                _state.update { it.copy(spots = previous) }
                _events.tryEmit(SpotsEvent.OperationFailed(error))
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
                val snapshot = _state.value
                val page = gateway.list(
                    token,
                    SpotListQuery(name = snapshot.query.trim().ifBlank { null }, limit = pageSize, offset = 0),
                )
                _state.update {
                    it.copy(
                        spots = page.spots,
                        hasMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: SpotsError) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = true,
                        hasLoadedOnce = true,
                    )
                }
                _events.tryEmit(SpotsEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(SpotsEvent.SessionExpired)
        return token
    }
}
