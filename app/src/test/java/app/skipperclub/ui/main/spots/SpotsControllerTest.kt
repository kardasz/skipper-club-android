package app.skipperclub.ui.main.spots

import app.skipperclub.data.SpotsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends, so an Unconfined scope runs every launched
 * coroutine to completion synchronously.
 */
class SpotsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeSpotsGateway()
    private val events = mutableListOf<SpotsEvent>()

    private fun controller(token: String? = "token"): SpotsController {
        val controller = SpotsController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            pageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesSpotsAndPagingState() {
        gateway.pages = listOf(spotsPage(listOf(testSpot("s1"), testSpot("s2")), total = 5))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("s1", "s2"), state.spots.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadInitialIsIdempotent() {
        gateway.pages = listOf(spotsPage(listOf(testSpot("s1"))))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun loadMoreAppendsNextPageDeduplicated() {
        gateway.pages = listOf(
            spotsPage(listOf(testSpot("s1"), testSpot("s2")), total = 3),
            spotsPage(listOf(testSpot("s2"), testSpot("s3")), total = 3, offset = 2),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        assertEquals(listOf("s1", "s2", "s3"), controller.state.value.spots.map { it.id })
        assertEquals(2, gateway.listQueries.last().offset)
    }

    @Test
    fun searchSetsQueryAndReloadsWithNameFilter() {
        gateway.pages = listOf(
            spotsPage(listOf(testSpot("s1"), testSpot("s2"))),
            spotsPage(listOf(testSpot("s2", name = "Neptun"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.search("nept")

        assertEquals("nept", controller.state.value.query)
        assertEquals("nept", gateway.listQueries.last().name)
        assertEquals(listOf("s2"), controller.state.value.spots.map { it.id })
    }

    @Test
    fun createSpotSendsBuildsRequestReloadsAndEmitsCreated() {
        gateway.pages = listOf(
            spotsPage(emptyList()),
            spotsPage(listOf(testSpot("new", name = "Neptun"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.createSpot(SpotForm(name = "Neptun", lat = "54.35", lng = "18.65"))

        assertEquals("Neptun", gateway.lastCreate?.name)
        assertEquals(54.35, gateway.lastCreate?.coordinates?.lat)
        assertFalse(controller.state.value.isSaving)
        assertTrue(events.any { it is SpotsEvent.SpotCreated })
    }

    @Test
    fun createSpotDuplicateEmitsOperationFailed() {
        gateway.pages = listOf(spotsPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = SpotsError.Duplicate("dup", emptyList())

        controller.createSpot(SpotForm(name = "Neptun", lat = "54.35", lng = "18.65"))

        assertFalse(controller.state.value.isSaving)
        assertTrue(events.any { it is SpotsEvent.OperationFailed })
        assertFalse(events.any { it is SpotsEvent.SpotCreated })
    }

    @Test
    fun updateSpotChangedNamePatchesAndReplacesRow() {
        val original = testSpot("s1", name = "Old")
        gateway.pages = listOf(spotsPage(listOf(original)))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.updateSpot(original, SpotForm.fromSpot(original).copy(name = "New"))

        assertEquals("s1", gateway.lastUpdate?.first)
        assertEquals("New", gateway.lastUpdate?.second?.name)
        assertEquals("New", controller.state.value.spots.first().name)
        assertTrue(events.any { it is SpotsEvent.SpotUpdated })
    }

    @Test
    fun updateSpotWithNoChangesSkipsGatewayButEmitsUpdated() {
        val original = testSpot("s1", name = "Same")
        gateway.pages = listOf(spotsPage(listOf(original)))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.updateSpot(original, SpotForm.fromSpot(original))

        assertFalse(gateway.calls.any { it.startsWith("update:") })
        assertTrue(events.any { it is SpotsEvent.SpotUpdated })
    }

    @Test
    fun deleteRemovesSpotAndEmitsDeleted() {
        gateway.pages = listOf(spotsPage(listOf(testSpot("s1"), testSpot("s2"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.delete(controller.state.value.spots.first())

        assertEquals(listOf("s2"), controller.state.value.spots.map { it.id })
        assertTrue(gateway.calls.contains("delete:s1"))
        assertTrue(events.any { it is SpotsEvent.SpotDeleted })
    }

    @Test
    fun deleteFailureRestoresSpotAndEmitsError() {
        gateway.pages = listOf(spotsPage(listOf(testSpot("s1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = SpotsError.Server(500, null)

        controller.delete(controller.state.value.spots.first())

        assertEquals(listOf("s1"), controller.state.value.spots.map { it.id })
        assertTrue(events.any { it is SpotsEvent.OperationFailed })
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.listError = SpotsError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(controller.state.value.hasLoadedOnce)
        assertTrue(events.any { it is SpotsEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(SpotsEvent.SessionExpired))
    }
}
