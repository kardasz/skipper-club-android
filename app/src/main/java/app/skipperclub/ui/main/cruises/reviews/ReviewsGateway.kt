package app.skipperclub.ui.main.cruises.reviews

import app.skipperclub.data.CreateReviewPayload
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruisesApi
import app.skipperclub.data.Review
import app.skipperclub.data.ReviewsApi
import app.skipperclub.data.ReviewsPage

/**
 * Seam between the reviews UI controller and the API singletons so the eligibility
 * + submission state machine stays unit-testable with fakes (no MockWebServer).
 * Bundles the cruise lookup (for permissions/completion) with the review calls.
 */
interface ReviewsGateway {
    suspend fun getCruise(accessToken: String, cruiseId: String): Cruise
    suspend fun listReviews(accessToken: String, cruiseId: String): ReviewsPage
    suspend fun createReview(
        accessToken: String,
        cruiseId: String,
        payload: CreateReviewPayload,
    ): Review
}

object RealReviewsGateway : ReviewsGateway {
    override suspend fun getCruise(accessToken: String, cruiseId: String): Cruise =
        CruisesApi.get(accessToken, cruiseId)

    override suspend fun listReviews(accessToken: String, cruiseId: String): ReviewsPage =
        ReviewsApi.listCruiseReviews(accessToken, cruiseId)

    override suspend fun createReview(
        accessToken: String,
        cruiseId: String,
        payload: CreateReviewPayload,
    ): Review = ReviewsApi.createReview(accessToken, cruiseId, payload)
}
