package app.skipperclub.ui.main.cruises.reviews

import app.skipperclub.data.CreateReviewPayload
import app.skipperclub.data.Cruise
import app.skipperclub.data.Review
import app.skipperclub.data.ReviewRatings
import app.skipperclub.data.ReviewStatus
import app.skipperclub.data.ReviewUser
import app.skipperclub.data.ReviewsError
import app.skipperclub.data.ReviewsPage
import app.skipperclub.ui.main.cruises.testCruise

internal fun testReview(
    id: String,
    reviewerId: String,
    reviewedUserId: String,
    status: ReviewStatus = ReviewStatus.Published,
): Review = Review(
    id = id,
    cruiseId = "c1",
    reviewer = ReviewUser(id = reviewerId, name = "User $reviewerId"),
    reviewedUser = ReviewUser(id = reviewedUserId, name = "User $reviewedUserId"),
    cruise = null,
    ratings = ReviewRatings(5, 4, 5, 4, 4.5),
    comment = "c".repeat(120),
    status = status,
    createdAt = "2026-01-01T10:00:00Z",
    updatedAt = "2026-01-01T10:00:00Z",
)

/** Configurable in-memory [ReviewsGateway] that records calls for assertions. */
internal class FakeReviewsGateway : ReviewsGateway {
    var cruise: Cruise = testCruise("c1")
    var getError: ReviewsError? = null
    var reviews: List<Review> = emptyList()
    var listError: ReviewsError? = null
    var createError: ReviewsError? = null
    var createdStatus: ReviewStatus = ReviewStatus.Pending

    val calls = mutableListOf<String>()
    val createPayloads = mutableListOf<CreateReviewPayload>()

    override suspend fun getCruise(accessToken: String, cruiseId: String): Cruise {
        calls += "getCruise:$cruiseId"
        getError?.let { throw it }
        return cruise
    }

    override suspend fun listReviews(accessToken: String, cruiseId: String): ReviewsPage {
        calls += "listReviews:$cruiseId"
        listError?.let { throw it }
        return ReviewsPage(reviews = reviews, total = reviews.size, limit = 100, offset = 0)
    }

    override suspend fun createReview(
        accessToken: String,
        cruiseId: String,
        payload: CreateReviewPayload,
    ): Review {
        calls += "createReview:$cruiseId:${payload.reviewedUserId}"
        createPayloads += payload
        createError?.let { throw it }
        return testReview(
            id = "new",
            reviewerId = "me",
            reviewedUserId = payload.reviewedUserId,
            status = createdStatus,
        )
    }
}
