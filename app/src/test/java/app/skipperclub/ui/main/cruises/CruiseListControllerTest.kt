package app.skipperclub.ui.main.cruises

import app.skipperclub.data.CruiseScope
import app.skipperclub.data.CruisesError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unconfined scope + zero debounce run launched coroutines to completion synchronously. */
class CruiseListControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeCruisesGateway()
    private val events = mutableListOf<CruiseListEvent>()

    private fun controller(token: String? = "token"): CruiseListController {
        val controller = CruiseListController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            pageSize = 2,
            searchDebounceMillis = 0,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesCruisesAndPaging() {
        gateway.cruisePages = listOf(cruisesPage(listOf(testCruise("c1"), testCruise("c2")), total = 5))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("c1", "c2"), state.cruises.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertEquals(0, gateway.listQueries.single().offset)
    }

    @Test
    fun loadInitialIsIdempotent() {
        gateway.cruisePages = listOf(cruisesPage(listOf(testCruise("c1"))))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun selectScopeReloadsWithNewScope() {
        gateway.cruisePages = listOf(cruisesPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.selectScope(CruiseScope.Mine)

        assertEquals(CruiseScope.Mine, gateway.listQueries.last().scope)
        assertEquals(CruiseScope.Mine, controller.state.value.scope)
    }

    @Test
    fun searchQueryIsTrimmedBeforeSending() {
        gateway.cruisePages = listOf(cruisesPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.updateSearch("  adriatic ")

        assertEquals("adriatic", gateway.listQueries.last().search)
        assertEquals("  adriatic ", controller.state.value.search)
    }

    @Test
    fun loadMoreAppendsNextPageWithOffsetAndDedup() {
        gateway.cruisePages = listOf(
            cruisesPage(listOf(testCruise("c1"), testCruise("c2")), total = 3),
            cruisesPage(listOf(testCruise("c2"), testCruise("c3")), total = 3, offset = 2),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        assertEquals(listOf("c1", "c2", "c3"), controller.state.value.cruises.map { it.id })
        assertEquals(2, gateway.listQueries.last().offset)
    }

    @Test
    fun onCruiseCreatedPrependsWithoutDuplicates() {
        gateway.cruisePages = listOf(cruisesPage(listOf(testCruise("c1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onCruiseCreated(testCruise("c2"))
        controller.onCruiseCreated(testCruise("c1"))

        assertEquals(listOf("c1", "c2"), controller.state.value.cruises.map { it.id })
    }

    @Test
    fun onCruiseChangedReplacesMatchingCard() {
        gateway.cruisePages = listOf(cruisesPage(listOf(testCruise("c1", title = "Old"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onCruiseChanged(testCruise("c1", title = "New"))

        assertEquals("New", controller.state.value.cruises.single().title)
    }

    @Test
    fun onCruiseDeletedRemovesCard() {
        gateway.cruisePages = listOf(cruisesPage(listOf(testCruise("c1"), testCruise("c2"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onCruiseDeleted("c1")

        assertEquals(listOf("c2"), controller.state.value.cruises.map { it.id })
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.listError = CruisesError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is CruiseListEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(CruiseListEvent.SessionExpired))
    }
}
