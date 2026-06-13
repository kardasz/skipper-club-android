package app.skipperclub.ui.main.cruises

import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseListQuery
import app.skipperclub.data.CruiseScope
import app.skipperclub.data.CruiseSortField
import app.skipperclub.data.CruisesError
import app.skipperclub.data.SortOrder
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

data class CruiseListUiState(
    val cruises: List<Cruise> = emptyList(),
    val scope: CruiseScope = CruiseScope.All,
    val search: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
)

sealed interface CruiseListEvent {
    data class OperationFailed(val error: Exception) : CruiseListEvent
    data object SessionExpired : CruiseListEvent
}

/**
 * State holder for the cruise list: scope tabs, debounced search and pagination.
 * Plain class (no ViewModel/DI yet — see CLAUDE.md §State); owned by the
 * composable via `remember` and unit-tested with a fake [CruisesGateway].
 */
class CruiseListController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: CruisesGateway = RealCruisesGateway,
    private val pageSize: Int = 20,
    private val searchDebounceMillis: Long = 300,
) {
    private val _state = MutableStateFlow(CruiseListUiState())
    val state: StateFlow<CruiseListUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CruiseListEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CruiseListEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
    }

    fun selectScope(scope: CruiseScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope) }
        reload(showAsRefreshing = false)
    }

    fun updateSearch(value: String) {
        if (value == _state.value.search) return
        _state.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(searchDebounceMillis)
            reload(showAsRefreshing = false)
        }
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
                val page = gateway.list(token, snapshot.toQuery(offset = snapshot.cruises.size))
                _state.update { state ->
                    val knownIds = state.cruises.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        cruises = state.cruises + page.cruises.filterNot { it.id in knownIds },
                        hasMore = page.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: CruisesError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(CruiseListEvent.OperationFailed(error))
            }
        }
    }

    /** Prepends a freshly created cruise so it is visible without a round-trip. */
    fun onCruiseCreated(cruise: Cruise) {
        _state.update { state ->
            state.copy(cruises = listOf(cruise) + state.cruises.filterNot { it.id == cruise.id })
        }
    }

    /** Keeps the list card in sync after detail-screen mutations (join, edit, …). */
    fun onCruiseChanged(cruise: Cruise) {
        _state.update { state ->
            state.copy(cruises = state.cruises.map { if (it.id == cruise.id) cruise else it })
        }
    }

    fun onCruiseDeleted(cruiseId: String) {
        _state.update { state ->
            state.copy(cruises = state.cruises.filterNot { it.id == cruiseId })
        }
    }

    private fun CruiseListUiState.toQuery(offset: Int): CruiseListQuery =
        CruiseListQuery(
            scope = scope,
            search = search.trim().takeIf { it.isNotEmpty() },
            sort = CruiseSortField.DepartureDate,
            order = SortOrder.Desc,
            limit = pageSize,
            offset = offset,
        )

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
                val page = gateway.list(token, _state.value.toQuery(offset = 0))
                _state.update {
                    it.copy(
                        cruises = page.cruises,
                        hasMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: CruisesError) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                _events.tryEmit(CruiseListEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(CruiseListEvent.SessionExpired)
        return token
    }
}
