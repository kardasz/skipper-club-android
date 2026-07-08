package app.skipperclub.ui.main.posts

import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertSeverity
import app.skipperclub.data.FriendUser
import app.skipperclub.data.MediaUploadMeta
import app.skipperclub.data.Post
import app.skipperclub.data.PostAlert
import app.skipperclub.data.PostContent
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostLocation
import app.skipperclub.data.PostMedia
import app.skipperclub.data.PostRoute
import app.skipperclub.data.PostRouteStop
import app.skipperclub.data.PostStatus
import app.skipperclub.data.PostUser
import app.skipperclub.data.PostsError
import app.skipperclub.ui.main.posts.wizard.PostWizardError
import app.skipperclub.ui.main.posts.wizard.PostWizardEvent
import app.skipperclub.ui.main.posts.wizard.PostWizardState
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

    private fun wizard(token: String? = "token", editingPost: Post? = null): PostWizardState {
        val state = PostWizardState(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            locationSearchDebounceMillis = 0,
            editingPost = editingPost,
        )
        scope.launch { state.events.collect { events += it } }
        return state
    }

    private fun editPost(
        id: String = "p1",
        text: String = "old body",
        route: PostRoute? = null,
        alert: PostAlert? = null,
        location: PostLocation = PostLocation(),
        media: List<PostMedia> = emptyList(),
        taggedUsers: List<PostUser> = emptyList(),
    ) = Post(
        id = id,
        user = PostUser("author", "Author"),
        contentKeys = emptySet(),
        status = PostStatus.Published,
        content = PostContent(text = text, route = route, alert = alert),
        location = location,
        media = media,
        taggedUsers = taggedUsers,
        publishedAt = "2025-12-01T10:00:00Z",
        createdAt = "2025-12-01T10:00:00Z",
        updatedAt = "2025-12-01T10:00:00Z",
    )

    // --- Text (required) ---

    @Test
    fun textIsRequiredToPublish() {
        val state = wizard()

        assertFalse(state.canPublish)
        state.publish()

        assertTrue(PostWizardError.TextRequired in state.visibleErrors)
        assertFalse(gateway.calls.any { it.startsWith("create") })
        assertFalse(events.any { it is PostWizardEvent.Published })
    }

    @Test
    fun textIsCappedAtMaxLength() {
        val state = wizard()
        state.updateText("x".repeat(3000))
        assertEquals(2200, state.text.length)
    }

    @Test
    fun plainTextPublishSendsRequestAndEmitsPublished() {
        val state = wizard()
        state.updateText("Fair winds")

        assertTrue(state.canPublish)
        state.publish()

        assertTrue(gateway.calls.any { it.startsWith("create") })
        assertTrue(events.any { it is PostWizardEvent.Published })
        assertFalse(state.isPublishing)
    }

    @Test
    fun buildRequestPlainTextOmitsOptionalSections() {
        val state = wizard()
        state.updateText("  Hello world  ")

        val request = state.buildRequest()

        assertEquals("Hello world", request.content.text)
        assertNull(request.content.route)
        assertNull(request.content.alert)
        assertNull(request.location)
        assertNull(request.mediaIds)
        assertNull(request.taggedUserIds)
    }

    // --- Location ---

    @Test
    fun locationSearchPopulatesResultsAndSelectionFillsPoint() {
        gateway.locations = listOf(geocoded("Hvar", 43.1, 16.4))
        val state = wizard()
        state.updateText("Anchored here")

        state.updateLocationQuery("Hva")
        assertEquals(1, state.locationResults.size)

        state.selectLocation(state.locationResults.single())
        assertEquals("Hvar", state.locationName)
        assertEquals(43.1, state.coordinates!!.lat, 0.0)
        assertTrue(state.locationResults.isEmpty())

        val location = state.buildRequest().location!!
        assertEquals("Hvar", location.name)
        assertEquals(16.4, location.point!!.lng, 0.0)
        assertNull(location.area)
    }

    // --- Route section ---

    @Test
    fun routeRequiresAtLeastOneStop() {
        val state = wizard()
        state.updateText("Island hopping")
        state.updateRouteEnabled(true)

        state.publish()
        assertTrue(PostWizardError.StopsRequired in state.visibleErrors)
        assertFalse(events.any { it is PostWizardEvent.Published })

        state.addStop(geocoded("Hvar"))
        state.publish()
        assertTrue(events.any { it is PostWizardEvent.Published })
    }

    @Test
    fun routeBuildIncludesStopsAndFields() {
        val state = wizard()
        state.updateText("Island hopping")
        state.updateRouteEnabled(true)
        state.addStop(geocoded("Split", 43.5, 16.4))
        state.addStop(geocoded("Hvar", 43.1, 16.4))
        state.updateDurationDays("7")
        state.updateLengthNm("120.5")

        val request = state.buildRequest()
        val route = request.content.route!!

        assertEquals(2, route.stops.size)
        assertEquals("Split", route.stops.first().name)
        assertEquals(43.5, route.stops.first().coordinates.lat, 0.0)
        assertEquals(7, route.durationDays)
        assertEquals(120.5, route.lengthNm!!, 0.0)
        assertNull(request.content.alert)
    }

    @Test
    fun stopsCanBeReorderedAndRemoved() {
        val state = wizard()
        state.updateRouteEnabled(true)
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

    // --- Alerts (creation lives in the map flow, not here) ---

    @Test
    fun createModeNeverIncludesAlert() {
        val state = wizard()
        state.updateText("Storm warning")
        state.selectLocation(geocoded("Bay", 44.0, 15.0))

        assertNull(state.editingAlert)
        assertNull(state.buildRequest().content.alert)
    }

    // --- Route management ---

    @Test
    fun stopSearchDoesNotClearChosenLocation() {
        gateway.locations = listOf(geocoded("Hvar", 43.1, 16.4))
        val state = wizard()
        state.updateText("Island hopping")
        state.updateLocationQuery("Hva")
        state.selectLocation(state.locationResults.single())

        state.updateStopQuery("Hva")
        assertEquals(1, state.stopResults.size)
        state.addStop(state.stopResults.single())

        assertEquals("Hvar", state.locationName)
        assertEquals("", state.stopQuery)
        assertTrue(state.stopResults.isEmpty())
        assertEquals(listOf("Hvar"), state.stops.map { it.name })
    }

    @Test
    fun removeRouteClearsStopsAndSummaryFields() {
        val state = wizard()
        state.updateText("Island hopping")
        state.addStop(geocoded("Split"))
        state.updateDurationDays("7")
        state.updateLengthNm("120.5")
        state.updateRouteEnabled(true)

        state.removeRoute()

        assertFalse(state.routeEnabled)
        assertTrue(state.stops.isEmpty())
        assertEquals("", state.durationDaysText)
        assertEquals("", state.lengthNmText)
        assertNull(state.buildRequest().content.route)
    }

    // --- Media & tags ---

    @Test
    fun uploadMediaCollectsIdsIntoRequest() {
        val state = wizard()
        state.updateText("Sunset shots")

        state.uploadMedia(
            "a.jpg",
            "image/jpeg",
            byteArrayOf(1, 2),
            MediaUploadMeta(width = 100, height = 80, camera = "Pixel"),
        )

        val item = state.media.single()
        assertFalse(item.isUploading)
        assertEquals("media-a.jpg", item.mediaId)
        assertEquals("Pixel", gateway.lastUploadMeta?.camera)

        assertEquals(listOf("media-a.jpg"), state.buildRequest().mediaIds)
    }

    @Test
    fun failedUploadMarksItemAndEmitsEvent() {
        gateway.mutationError = PostsError.Validation("too large")
        val state = wizard()

        state.uploadMedia("a.jpg", "image/jpeg", byteArrayOf(1), MediaUploadMeta())

        assertTrue(state.media.single().failed)
        assertTrue(events.any { it is PostWizardEvent.MediaUploadFailed })
    }

    @Test
    fun tagSearchExcludesAlreadyTaggedAndBuildRequestIncludesIds() {
        gateway.friends = listOf(FriendUser("u1", "Ann"), FriendUser("u2", "Bo"))
        val state = wizard()
        state.updateText("Crew shout-out")

        state.updateTagQuery("an")
        assertEquals(2, state.tagResults.size)
        state.addTag(FriendUser("u1", "Ann"))
        assertEquals(listOf("Ann"), state.taggedUsers.map { it.name })

        state.updateTagQuery("bo")
        assertEquals(listOf("u2"), state.tagResults.map { it.id })

        assertEquals(listOf("u1"), state.buildRequest().taggedUserIds)
    }

    // --- Publish failure & session ---

    @Test
    fun publishFailureEmitsPublishFailed() {
        gateway.mutationError = PostsError.Server(500, null)
        val state = wizard()
        state.updateText("Hi")

        state.publish()

        assertTrue(events.any { it is PostWizardEvent.PublishFailed })
        assertFalse(state.isPublishing)
    }

    // --- Edit mode ---

    @Test
    fun editModeSeedsRoutePost() {
        val post = editPost(
            text = "Great loop",
            route = PostRoute(
                stops = listOf(
                    PostRouteStop("Split", PostCoordinates(43.5, 16.4)),
                    PostRouteStop("Hvar", PostCoordinates(43.1, 16.4)),
                ),
                durationDays = 5,
                lengthNm = 90.0,
            ),
            location = PostLocation(name = "Split", point = PostCoordinates(43.5, 16.4)),
            media = listOf(PostMedia(id = "m1", type = "image", url = "https://cdn/x.jpg")),
            taggedUsers = listOf(PostUser("u1", "Ann")),
        )
        val state = wizard(editingPost = post)

        assertTrue(state.isEditing)
        assertEquals("Great loop", state.text)
        assertTrue(state.routeEnabled)
        assertNull(state.editingAlert)
        assertEquals(listOf("Split", "Hvar"), state.stops.map { it.name })
        assertEquals("5", state.durationDaysText)
        assertEquals("90", state.lengthNmText)
        assertEquals("Split", state.locationName)
        assertEquals(43.5, state.coordinates!!.lat, 0.0)
        assertEquals(listOf("Ann"), state.taggedUsers.map { it.name })
        assertEquals("m1", state.media.single().mediaId)
    }

    @Test
    fun editModeSeedsAlertPost() {
        val post = editPost(
            text = "Diving works",
            alert = PostAlert(category = AlertCategory.Diving, severity = AlertSeverity.Critical),
            location = PostLocation(name = "Reef", point = PostCoordinates(43.0, 16.0)),
        )
        val state = wizard(editingPost = post)

        assertFalse(state.routeEnabled)
        val alert = state.editingAlert!!
        assertEquals(AlertCategory.Diving, alert.category)
        assertEquals(AlertSeverity.Critical, alert.severity)
        assertEquals(43.0, state.coordinates!!.lat, 0.0)
    }

    @Test
    fun editAlertPostBlocksRouteSection() {
        val post = editPost(
            text = "Storm",
            alert = PostAlert(category = AlertCategory.Weather, severity = AlertSeverity.Info),
            location = PostLocation(name = "Bay", point = PostCoordinates(44.0, 15.0)),
        )
        val state = wizard(editingPost = post)

        state.updateRouteEnabled(true)

        assertFalse(state.routeEnabled)
        assertNull(state.buildUpdateRequest().content.route)
    }

    @Test
    fun editAlertPostRequiresLocation() {
        val post = editPost(
            text = "Storm",
            alert = PostAlert(category = AlertCategory.Weather, severity = AlertSeverity.Info),
            location = PostLocation(name = "Bay", point = PostCoordinates(44.0, 15.0)),
        )
        val state = wizard(editingPost = post)
        assertTrue(state.validate().isEmpty())

        state.clearLocation()

        assertEquals(setOf(PostWizardError.AlertLocationRequired), state.validate())
        state.publish()
        assertTrue(events.filterIsInstance<PostWizardEvent.Updated>().isEmpty())
    }

    @Test
    fun editModePublishEmitsUpdatedWithRequest() {
        val post = editPost(id = "p1", text = "old body")
        val state = wizard(editingPost = post)
        state.updateText("new body")

        state.publish()

        val updated = events.filterIsInstance<PostWizardEvent.Updated>().single()
        assertEquals("p1", updated.postId)
        assertEquals("new body", updated.request.content.text)
        assertNull(updated.request.content.route)
        assertNull(updated.request.content.alert)
        assertFalse(gateway.calls.any { it.startsWith("create") })
    }

    @Test
    fun editModeAlertUpdatePreservesAlertVerbatim() {
        val post = editPost(
            text = "Storm",
            alert = PostAlert(category = AlertCategory.Weather, severity = AlertSeverity.Info),
            location = PostLocation(name = "Bay", point = PostCoordinates(44.0, 15.0)),
        )
        val state = wizard(editingPost = post)
        state.updateText("Storm has passed")

        state.publish()

        val updated = events.filterIsInstance<PostWizardEvent.Updated>().single()
        val alert = updated.request.content.alert!!
        assertEquals("Storm has passed", updated.request.content.text)
        assertEquals(AlertCategory.Weather, alert.category)
        assertEquals(AlertSeverity.Info, alert.severity)
        assertEquals(44.0, updated.request.location!!.point!!.lat, 0.0)
    }
}
