package app.skipperclub.ui.main.cruises.reviews

import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseUser
import app.skipperclub.data.CruiseUserRole
import app.skipperclub.data.ReviewStatus
import app.skipperclub.data.ReviewsError
import app.skipperclub.ui.main.cruises.testCruise
import app.skipperclub.ui.main.cruises.testParticipant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CruiseReviewsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeReviewsGateway()
    private val events = mutableListOf<CruiseReviewsEvent>()
    private val today = LocalDate.parse("2026-01-01")

    private fun controller(currentUserId: String? = "me"): CruiseReviewsController {
        val controller = CruiseReviewsController(
            scope = scope,
            accessToken = { "token" },
            currentUserId = { currentUserId },
            cruiseId = "c1",
            gateway = gateway,
            today = { today },
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun visitorSeesAccessDeniedAndReviewsAreNotFetched() {
        gateway.cruise = testCruise("c1") // role None, no participation
        val controller = controller()

        controller.load()

        assertEquals(ReviewsAccessState.AccessDenied, controller.state.value.accessState)
        assertFalse(gateway.calls.any { it.startsWith("listReviews") })
    }

    @Test
    fun acceptedParticipantBeforeCompletionSeesNotCompleted() {
        gateway.cruise = testCruise(
            "c1",
            currentUserParticipation = testParticipant("p1", state = CruiseParticipantState.Accepted),
        ).copy(arrivalDate = "2030-08-01")
        val controller = controller()

        controller.load()

        assertEquals(ReviewsAccessState.NotCompleted, controller.state.value.accessState)
        assertFalse(gateway.calls.any { it.startsWith("listReviews") })
    }

    @Test
    fun organizerAfterCompletionIsReadyAndReviewsAreFetched() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
            .copy(arrivalDate = "2025-07-22")
        gateway.reviews = listOf(testReview("r1", reviewerId = "x", reviewedUserId = "me"))
        val controller = controller()

        controller.load()

        assertEquals(ReviewsAccessState.Ready, controller.state.value.accessState)
        assertTrue(gateway.calls.contains("listReviews:c1"))
    }

    @Test
    fun reviewableUsersExcludeSelfAndAlreadyReviewed() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
            .copy(
                arrivalDate = "2025-07-22",
                organizer = CruiseUser(id = "org", name = "Organizer"),
                participants = listOf(
                    CruiseUser(id = "me", name = "Me"),
                    CruiseUser(id = "p2", name = "Piotr"),
                ),
            )
        // I already reviewed the organizer → only p2 remains reviewable.
        gateway.reviews = listOf(testReview("r1", reviewerId = "me", reviewedUserId = "org"))
        val controller = controller()

        controller.load()

        val reviewable = controller.state.value.reviewableUsers("me").map { it.id }
        assertEquals(listOf("p2"), reviewable)
    }

    @Test
    fun givenAndReceivedReviewsAreSplitByCurrentUser() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
            .copy(arrivalDate = "2025-07-22")
        gateway.reviews = listOf(
            testReview("r1", reviewerId = "me", reviewedUserId = "p2"),
            testReview("r2", reviewerId = "p2", reviewedUserId = "me"),
        )
        val controller = controller()

        controller.load()

        assertEquals(listOf("r1"), controller.state.value.givenReviews("me").map { it.id })
        assertEquals(listOf("r2"), controller.state.value.receivedReviews("me").map { it.id })
    }

    @Test
    fun submitPublishedReviewEmitsPublishedAndRemovesUserFromReviewable() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
            .copy(
                arrivalDate = "2025-07-22",
                organizer = CruiseUser(id = "org", name = "Organizer"),
                participants = listOf(CruiseUser(id = "me", name = "Me"), CruiseUser(id = "p2", name = "Piotr")),
            )
        gateway.createdStatus = ReviewStatus.Published
        val controller = controller()
        controller.load()

        controller.submit(
            reviewedUser = app.skipperclub.data.ReviewUser("p2", "Piotr"),
            communication = 5, behavior = 4, skills = 5, duties = 4,
            comment = "c".repeat(120),
        )

        assertTrue(gateway.calls.contains("createReview:c1:p2"))
        val submitted = events.filterIsInstance<CruiseReviewsEvent.Submitted>().last()
        assertTrue(submitted.published)
        // p2 must drop off the reviewable list even before the reciprocal review lands.
        assertFalse(controller.state.value.reviewableUsers("me").any { it.id == "p2" })
    }

    @Test
    fun submitPendingReviewEmitsPendingSubmitted() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
            .copy(arrivalDate = "2025-07-22", participants = listOf(CruiseUser(id = "p2", name = "Piotr")))
        gateway.createdStatus = ReviewStatus.Pending
        val controller = controller()
        controller.load()

        controller.submit(
            reviewedUser = app.skipperclub.data.ReviewUser("p2", "Piotr"),
            communication = 5, behavior = 4, skills = 5, duties = 4,
            comment = "c".repeat(120),
        )

        val submitted = events.filterIsInstance<CruiseReviewsEvent.Submitted>().last()
        assertFalse(submitted.published)
        assertEquals("Piotr", submitted.reviewedUserName)
    }

    @Test
    fun submitFailureEmitsSubmitFailed() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
            .copy(arrivalDate = "2025-07-22", participants = listOf(CruiseUser(id = "p2", name = "Piotr")))
        gateway.createError = ReviewsError.AlreadyReviewed("already")
        val controller = controller()
        controller.load()

        controller.submit(
            reviewedUser = app.skipperclub.data.ReviewUser("p2", "Piotr"),
            communication = 5, behavior = 4, skills = 5, duties = 4,
            comment = "c".repeat(120),
        )

        assertTrue(events.any { it is CruiseReviewsEvent.SubmitFailed })
        assertFalse(controller.state.value.isSubmitting)
    }

    @Test
    fun loadFailureSetsLoadFailed() {
        gateway.getError = ReviewsError.Network(RuntimeException("boom"))
        val controller = controller()

        controller.load()

        assertEquals(ReviewsAccessState.LoadFailed, controller.state.value.accessState)
    }
}
