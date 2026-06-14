package app.skipperclub.ui.main.posts

import app.skipperclub.data.PostSortField
import app.skipperclub.data.PostType
import app.skipperclub.data.PostsError
import app.skipperclub.data.ReactionSummary
import app.skipperclub.data.ReactionType
import app.skipperclub.data.SortOrder
import app.skipperclub.data.ValidityVoteType
import app.skipperclub.data.VoteSummary
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
 * coroutine to completion synchronously — no coroutines-test dependency needed.
 */
class PostsFeedControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakePostsGateway()
    private val events = mutableListOf<PostsFeedEvent>()

    private fun controller(token: String? = "token"): PostsFeedController {
        val controller = PostsFeedController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            pageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesPostsAndPagingState() {
        gateway.pages = listOf(page(listOf(testPost("p1"), testPost("p2")), hasMore = true, total = 5))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("p1", "p2"), state.posts.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertEquals(0, gateway.listQueries.single().offset)
    }

    @Test
    fun loadInitialIsIdempotent() {
        gateway.pages = listOf(page(listOf(testPost("p1")), hasMore = false))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun loadMoreAppendsNextPageWithOffset() {
        gateway.pages = listOf(
            page(listOf(testPost("p1"), testPost("p2")), hasMore = true, total = 3),
            page(listOf(testPost("p3")), hasMore = false, total = 3),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf("p1", "p2", "p3"), state.posts.map { it.id })
        assertFalse(state.hasMore)
        assertEquals(2, gateway.listQueries[1].offset)
    }

    @Test
    fun loadMoreIsIgnoredWhenNoMorePages() {
        gateway.pages = listOf(page(listOf(testPost("p1")), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun applyFiltersReloadsFromOffsetZeroWithQueryParams() {
        gateway.pages = listOf(page(listOf(testPost("p1")), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.applyFilters(
            PostFilters(
                types = setOf(PostType.Berth),
                regionCode = "ADR-HR",
                sort = PostSortField.UpdatedAt,
                order = SortOrder.Asc,
            ),
        )

        val query = gateway.listQueries.last()
        assertEquals(setOf(PostType.Berth), query.types)
        assertEquals("ADR-HR", query.regionCode)
        assertEquals(PostSortField.UpdatedAt, query.sort)
        assertEquals(SortOrder.Asc, query.order)
        assertEquals(0, query.offset)
    }

    @Test
    fun applyingSameFiltersDoesNotReload() {
        gateway.pages = listOf(page(emptyList(), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.applyFilters(PostFilters())

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun failedLoadSetsLoadFailedAndEmitsEvent() {
        gateway.listError = PostsError.Network(RuntimeException("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is PostsFeedEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(PostsFeedEvent.SessionExpired))
    }

    @Test
    fun addingReactionAppliesServerSummary() {
        val post = testPost("p1")
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        gateway.reactionSummary = ReactionSummary(
            total = 1,
            byType = mapOf(ReactionType.Anchor to 1),
            userReactions = setOf(ReactionType.Anchor),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.toggleReaction(post, ReactionType.Anchor)

        assertEquals("addReaction:p1:anchor", gateway.calls.last())
        val updated = controller.state.value.posts.single()
        assertEquals(1, updated.reactions.total)
        assertEquals(setOf(ReactionType.Anchor), updated.reactions.userReactions)
    }

    @Test
    fun removingReactionDecrementsLocally() {
        val post = testPost(
            "p1",
            reactions = ReactionSummary(
                total = 2,
                byType = mapOf(ReactionType.Heart to 1, ReactionType.Anchor to 1),
                userReactions = setOf(ReactionType.Heart),
            ),
        )
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.toggleReaction(post, ReactionType.Heart)

        assertEquals("removeReaction:p1:heart", gateway.calls.last())
        val updated = controller.state.value.posts.single()
        assertEquals(1, updated.reactions.total)
        assertEquals(mapOf(ReactionType.Anchor to 1), updated.reactions.byType)
        assertTrue(updated.reactions.userReactions.isEmpty())
    }

    @Test
    fun toggleBookmarkFlipsFlag() {
        val post = testPost("p1", bookmarked = false)
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.toggleBookmark(post)
        assertTrue(controller.state.value.posts.single().bookmarked)

        controller.toggleBookmark(controller.state.value.posts.single())
        assertFalse(controller.state.value.posts.single().bookmarked)
        assertEquals(listOf("addBookmark:p1", "removeBookmark:p1"), gateway.calls.takeLast(2))
    }

    @Test
    fun castingVoteUpdatesVoteSummary() {
        val post = testPost("p1", type = PostType.Berth).copy(validityVotes = VoteSummary())
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        gateway.voteResult = app.skipperclub.data.ValidityVoteResult(
            postId = "p1",
            voteType = ValidityVoteType.Confirm,
            confirmCount = 3,
            invalidCount = 1,
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.castValidityVote(post, ValidityVoteType.Confirm)

        val votes = controller.state.value.posts.single().validityVotes
        assertEquals(3, votes?.confirmCount)
        assertEquals(1, votes?.invalidCount)
        assertEquals(ValidityVoteType.Confirm, votes?.userVote)
    }

    @Test
    fun deleteRemovesPostAndEmitsEvent() {
        val post = testPost("p1")
        gateway.pages = listOf(page(listOf(post, testPost("p2")), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.deletePost(post)

        assertEquals(listOf("p2"), controller.state.value.posts.map { it.id })
        assertTrue(events.contains(PostsFeedEvent.PostDeleted))
    }

    @Test
    fun archiveRemovesPostFromPublishedFeed() {
        val post = testPost("p1")
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.archivePost(post)

        assertEquals("updateStatus:p1:archived", gateway.calls.last())
        assertTrue(controller.state.value.posts.isEmpty())
        assertTrue(events.contains(PostsFeedEvent.PostArchived))
    }

    @Test
    fun reportPostCallsGatewayAndEmitsReported() {
        val post = testPost("p1")
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.reportPost(post, app.skipperclub.data.ReportReason.Spam, "looks fake")

        assertEquals("report:p1:spam:looks fake", gateway.calls.last())
        assertTrue(events.contains(PostsFeedEvent.PostReported))
        // Reporting never removes the card.
        assertEquals(listOf("p1"), controller.state.value.posts.map { it.id })
    }

    @Test
    fun editPostReplacesCardAndEmitsUpdated() {
        val post = testPost("p1")
        gateway.pages = listOf(page(listOf(post, testPost("p2")), hasMore = false))
        gateway.updatedPost = testPost("p1").copy(description = "updated body")
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.editPost(
            "p1",
            app.skipperclub.data.UpdatePostRequest(regionCode = "ADR-HR", description = "updated body"),
        )

        assertEquals("update:p1", gateway.calls.last())
        assertEquals("updated body", controller.state.value.posts.first { it.id == "p1" }.description)
        assertTrue(events.any { it is PostsFeedEvent.PostUpdated })
    }

    @Test
    fun failedMutationEmitsOperationFailedAndKeepsState() {
        val post = testPost("p1")
        gateway.pages = listOf(page(listOf(post), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = PostsError.NotFound(null)

        controller.deletePost(post)

        assertEquals(listOf("p1"), controller.state.value.posts.map { it.id })
        assertTrue(events.any { it is PostsFeedEvent.OperationFailed })
    }

    @Test
    fun postFiltersToQueryMapsLifecycleAndExtras() {
        val filters = PostFilters(
            hashtag = "sailing",
            locationName = "Split",
            userId = "me",
            statuses = setOf(app.skipperclub.data.PostStatus.Archived),
            crossRegionTypes = setOf(PostType.Photo),
            center = app.skipperclub.data.PostCoordinates(43.5, 16.4),
            radiusKm = 25,
            sort = PostSortField.Distance,
            fromDate = "2025-01-01T00:00:00Z",
            toDate = "2025-12-31T00:00:00Z",
        )

        val query = filters.toQuery(limit = 20, offset = 0)

        assertEquals("sailing", query.hashtag)
        assertEquals("Split", query.locationName)
        assertEquals("me", query.userId)
        assertEquals(setOf(app.skipperclub.data.PostStatus.Archived), query.statuses)
        assertEquals(setOf(PostType.Photo), query.crossRegionTypes)
        assertEquals(43.5, query.lat!!, 0.0)
        assertEquals(25, query.distanceKm)
        assertEquals("2025-01-01T00:00:00Z", query.fromDate)
        assertEquals(PostSortField.Distance, query.sort)
    }

    @Test
    fun postFiltersDropDistanceSortAndCoordsWithoutRadius() {
        val query = PostFilters(sort = PostSortField.Distance).toQuery(limit = 20, offset = 0)

        assertEquals(PostSortField.CreatedAt, query.sort)
        assertEquals(null, query.lat)
        assertEquals(null, query.distanceKm)
    }

    @Test
    fun postFiltersStatusesIgnoredWithoutUserId() {
        val query = PostFilters(statuses = setOf(app.skipperclub.data.PostStatus.Archived)).toQuery(20, 0)

        assertTrue(query.statuses.isEmpty())
    }

    @Test
    fun pageLoaderSourcesBookmarksInsteadOfFeed() {
        gateway.bookmarkPages = listOf(page(listOf(testPost("b1"), testPost("b2")), hasMore = false))
        val controller = PostsFeedController(
            scope = scope,
            accessToken = { "token" },
            gateway = gateway,
            pageSize = 2,
            pageLoader = { token, offset, limit ->
                gateway.listBookmarks(token, app.skipperclub.data.BookmarksQuery(limit = limit, offset = offset))
            },
        )

        controller.loadInitialIfNeeded()

        assertEquals(listOf("b1", "b2"), controller.state.value.posts.map { it.id })
        assertTrue(gateway.calls.contains("listBookmarks"))
        assertFalse(gateway.calls.contains("list"))
    }

    @Test
    fun onPostCreatedPrependsPost() {
        gateway.pages = listOf(page(listOf(testPost("p1")), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.onPostCreated(testPost("new"))

        assertEquals(listOf("new", "p1"), controller.state.value.posts.map { it.id })
    }

    @Test
    fun adjustCommentsCountClampsAtZero() {
        gateway.pages = listOf(page(listOf(testPost("p1", commentsCount = 1)), hasMore = false))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.adjustCommentsCount("p1", +1)
        assertEquals(2, controller.state.value.posts.single().commentsCount)

        controller.adjustCommentsCount("p1", -5)
        assertEquals(0, controller.state.value.posts.single().commentsCount)
    }
}
