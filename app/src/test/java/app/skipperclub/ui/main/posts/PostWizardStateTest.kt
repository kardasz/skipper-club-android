package app.skipperclub.ui.main.posts

import app.skipperclub.data.PostType
import app.skipperclub.data.PostsError
import app.skipperclub.ui.main.posts.wizard.PostWizardError
import app.skipperclub.ui.main.posts.wizard.PostWizardEvent
import app.skipperclub.ui.main.posts.wizard.PostWizardState
import app.skipperclub.ui.main.posts.wizard.PostWizardStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostWizardStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakePostsGateway()
    private val events = mutableListOf<PostWizardEvent>()

    private fun wizard(token: String? = "token"): PostWizardState {
        val state = PostWizardState(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            locationSearchDebounceMillis = 0,
        )
        scope.launch { state.events.collect { events += it } }
        return state
    }

    @Test
    fun stepsIncludeRouteStopsOnlyForRouteType() {
        val state = wizard()

        state.selectType(PostType.Photo)
        assertEquals(
            listOf(
                PostWizardStep.Type,
                PostWizardStep.Details,
                PostWizardStep.Media,
                PostWizardStep.Summary,
            ),
            state.steps,
        )

        state.selectType(PostType.Route)
        assertEquals(
            listOf(
                PostWizardStep.Type,
                PostWizardStep.Details,
                PostWizardStep.RouteStops,
                PostWizardStep.Media,
                PostWizardStep.Summary,
            ),
            state.steps,
        )
    }

    @Test
    fun cannotLeaveTypeStepWithoutSelection() {
        val state = wizard()

        assertFalse(state.canGoNext)
        state.selectType(PostType.Tips)
        assertTrue(state.canGoNext)
        state.next()
        assertEquals(PostWizardStep.Details, state.step)
    }

    @Test
    fun detailsStepValidatesPerTypeRequirements() {
        val state = wizard()
        state.selectType(PostType.Marina)
        state.next()

        state.next() // nothing filled in

        assertEquals(PostWizardStep.Details, state.step)
        assertEquals(
            setOf(
                PostWizardError.DescriptionRequired,
                PostWizardError.LocationRequired,
                PostWizardError.RegionRequired,
            ),
            state.visibleErrors,
        )

        state.updateDescription("Great marina")
        state.selectLocation(geocoded("ACI Marina Split"))
        state.selectRegion("ADR-HR")
        state.next()

        assertEquals(PostWizardStep.Media, state.step)
        assertTrue(state.visibleErrors.isEmpty())
    }

    @Test
    fun photoSkipsDescriptionAndLocationButRequiresMedia() {
        val state = wizard()
        state.selectType(PostType.Photo)
        state.next()
        state.selectRegion("ADR-HR")
        state.next()

        assertEquals(PostWizardStep.Media, state.step)
        state.next()

        // photo requires at least one uploaded media item
        assertEquals(PostWizardStep.Media, state.step)
        assertEquals(setOf(PostWizardError.MediaRequired), state.visibleErrors)

        state.uploadMedia("a.jpg", "image/jpeg", byteArrayOf(1), 10, 10)
        state.next()
        assertEquals(PostWizardStep.Summary, state.step)
    }

    @Test
    fun routeRequiresAtLeastOneStop() {
        val state = wizard()
        state.selectType(PostType.Route)
        state.next()
        state.updateDescription("Trip")
        state.selectLocation(geocoded("Split"))
        state.selectRegion("ADR-HR")
        state.next()

        assertEquals(PostWizardStep.RouteStops, state.step)
        state.next()
        assertEquals(PostWizardStep.RouteStops, state.step)
        assertEquals(setOf(PostWizardError.StopsRequired), state.visibleErrors)

        state.addStop(geocoded("Hvar"))
        state.next()
        assertEquals(PostWizardStep.Media, state.step)
    }

    @Test
    fun stopsCanBeReorderedAndRemoved() {
        val state = wizard()
        state.selectType(PostType.Route)
        state.addStop(geocoded("Split"))
        state.addStop(geocoded("Hvar"))
        state.addStop(geocoded("Vis"))

        state.moveStop(2, -1)
        assertEquals(listOf("Split", "Vis", "Hvar"), state.stops.map { it.name })

        state.removeStop(0)
        assertEquals(listOf("Vis", "Hvar"), state.stops.map { it.name })

        state.moveStop(0, -1) // no-op at boundary
        assertEquals(listOf("Vis", "Hvar"), state.stops.map { it.name })
    }

    @Test
    fun backWalksStepsAndReturnsFalseAtStart() {
        val state = wizard()
        state.selectType(PostType.Tips)
        state.next()

        assertTrue(state.back())
        assertEquals(PostWizardStep.Type, state.step)
        assertFalse(state.back())
    }

    @Test
    fun buildRequestForRouteIncludesRouteFields() {
        val state = wizard()
        state.selectType(PostType.Route)
        state.updateDescription("Island hopping")
        state.selectLocation(geocoded("Split", 43.5, 16.4))
        state.selectRegion("ADR-HR")
        state.addStop(geocoded("Hvar", 43.1, 16.4))
        state.updateDurationDays("7")
        state.updateLengthNm("120.5")

        val request = state.buildRequest()!!

        assertEquals("route", request.type)
        assertEquals("ADR-HR", request.regionCode)
        assertEquals("Island hopping", request.description)
        assertEquals("Split", request.locationName)
        assertEquals(43.5, request.coordinates!!.lat, 0.0)
        assertEquals(1, request.stops!!.size)
        assertEquals(7, request.durationDays)
        assertEquals(120.5, request.lengthNm!!, 0.0)
        assertNull(request.mediaIds)
    }

    @Test
    fun buildRequestForTipsOmitsRouteFields() {
        val state = wizard()
        state.selectType(PostType.Tips)
        state.updateDescription("Watch the shallows")
        state.selectRegion("ADR-HR")

        val request = state.buildRequest()!!

        assertEquals("tips", request.type)
        assertNull(request.stops)
        assertNull(request.durationDays)
        assertNull(request.lengthNm)
        assertNull(request.locationName)
        assertNull(request.coordinates)
    }

    @Test
    fun uploadMediaTracksLifecycleAndCollectsIds() {
        val state = wizard()
        state.selectType(PostType.Photo)
        state.selectRegion("ADR-HR")

        state.uploadMedia("a.jpg", "image/jpeg", byteArrayOf(1, 2), 100, 80)

        val item = state.media.single()
        assertFalse(item.isUploading)
        assertEquals("media-a.jpg", item.mediaId)
        assertEquals("https://cdn/a.jpg", item.publicUrl)

        val request = state.buildRequest()!!
        assertEquals(listOf("media-a.jpg"), request.mediaIds)
    }

    @Test
    fun failedUploadMarksItemAndEmitsEvent() {
        gateway.mutationError = PostsError.Validation("too large")
        val state = wizard()
        state.selectType(PostType.Photo)

        state.uploadMedia("a.jpg", "image/jpeg", byteArrayOf(1), null, null)

        assertTrue(state.media.single().failed)
        assertTrue(events.any { it is PostWizardEvent.MediaUploadFailed })
    }

    @Test
    fun publishSendsRequestAndEmitsPublished() {
        gateway.createdPost = testPost("created")
        val state = wizard()
        state.selectType(PostType.Tips)
        state.updateDescription("Tip")
        state.selectRegion("ADR-HR")
        state.next()
        state.next()
        state.next()

        assertEquals(PostWizardStep.Summary, state.step)
        state.publish()

        assertTrue(gateway.calls.contains("create:tips"))
        assertTrue(events.any { it is PostWizardEvent.Published })
        assertFalse(state.isPublishing)
    }

    @Test
    fun publishBlocksWhenValidationFails() {
        val state = wizard()
        state.selectType(PostType.Tips)
        // description missing

        state.publish()

        assertFalse(gateway.calls.any { it.startsWith("create") })
        assertTrue(PostWizardError.DescriptionRequired in state.visibleErrors)
    }

    @Test
    fun publishFailureEmitsPublishFailed() {
        gateway.mutationError = PostsError.Server(500, null)
        val state = wizard()
        state.selectType(PostType.Tips)
        state.updateDescription("Tip")
        state.selectRegion("ADR-HR")

        state.publish()

        assertTrue(events.any { it is PostWizardEvent.PublishFailed })
        assertFalse(state.isPublishing)
    }

    @Test
    fun locationSearchPopulatesResultsAndSelectionFillsFields() {
        gateway.locations = listOf(geocoded("Hvar"))
        val state = wizard()
        state.selectType(PostType.Place)

        state.updateLocationQuery("Hva") // 3 chars triggers search
        assertEquals(1, state.locationResults.size)

        state.selectLocation(state.locationResults.single())
        assertEquals("Hvar", state.locationName)
        assertEquals(43.5, state.coordinates!!.lat, 0.0)
        assertTrue(state.locationResults.isEmpty())
    }

    @Test
    fun shortLocationQueryClearsResults() {
        gateway.locations = listOf(geocoded("Hvar"))
        val state = wizard()
        state.updateLocationQuery("Hvar")
        assertEquals(1, state.locationResults.size)

        state.updateLocationQuery("H")

        assertTrue(state.locationResults.isEmpty())
        assertFalse(state.isSearchingLocation)
    }

    @Test
    fun descriptionIsCappedAtMaxLength()  {
        val state = wizard()
        state.updateDescription("x".repeat(3000))
        assertEquals(2200, state.description.length)
    }
}
