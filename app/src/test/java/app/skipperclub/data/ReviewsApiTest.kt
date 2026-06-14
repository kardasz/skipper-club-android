package app.skipperclub.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewsApiTest {

    @Test
    fun cruiseNotCompletedMapsToTypedError() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/cruise-not-completed","title":"Cruise Not Completed","detail":"Not finished"}""",
        ).toReviewsErrorForTest()

        assertTrue(error is ReviewsError.CruiseNotCompleted)
        assertEquals("Not finished", error.message)
    }

    @Test
    fun reviewAlreadyExistsMapsToTypedError() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/review-already-exists","detail":"Already reviewed"}""",
        ).toReviewsErrorForTest()

        assertTrue(error is ReviewsError.AlreadyReviewed)
    }

    @Test
    fun cannotReviewSelfMapsToTypedError() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/cannot-review-self","detail":"No self review"}""",
        ).toReviewsErrorForTest()

        assertTrue(error is ReviewsError.CannotReviewSelf)
    }

    @Test
    fun notCruiseParticipantMapsToForbidden() {
        val error = response(
            code = 403,
            body = """{"type":"/errors/not-cruise-participant","detail":"Not a participant"}""",
        ).toReviewsErrorForTest()

        assertTrue(error is ReviewsError.Forbidden)
        assertEquals("Not a participant", error.message)
    }

    @Test
    fun genericValidationFallsBackForUnknownType() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","detail":"comment too short"}""",
        ).toReviewsErrorForTest()

        assertTrue(error is ReviewsError.Validation)
        assertEquals("comment too short", error.message)
    }

    @Test
    fun nestJsValidationArrayMessageIsJoined() {
        val error = response(
            code = 400,
            body = """{"message":["comment must be longer","skills must be 1-5"],"error":"Bad Request"}""",
        ).toReviewsErrorForTest()

        assertTrue(error is ReviewsError.Validation)
        assertEquals("comment must be longer skills must be 1-5", error.message)
    }

    @Test
    fun createRequestPostsPayloadToCruiseReviews() {
        val request = ReviewsApi.createRequest(
            accessToken = "access-token",
            cruiseId = "cruise-1",
            payload = CreateReviewPayload(
                reviewedUserId = "user-2",
                communication = 5,
                behavior = 4,
                skills = 5,
                duties = 4,
                comment = "x".repeat(120),
            ),
        )

        assertEquals("POST", request.method)
        assertEquals("/v1/cruises/cruise-1/reviews", request.url.encodedPath)
        assertEquals("Bearer access-token", request.header("Authorization"))
    }

    @Test
    fun listRequestTargetsCruiseReviewsWithPaging() {
        val request = ReviewsApi.listRequest("token", cruiseId = "cruise-1", limit = 100, offset = 0)

        assertEquals("GET", request.method)
        assertEquals("/v1/cruises/cruise-1/reviews", request.url.encodedPath)
        assertEquals("100", request.url.queryParameter("limit"))
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun Response.toReviewsErrorForTest(): ReviewsError =
        ReviewsApi.run { toReviewsError() }
}
