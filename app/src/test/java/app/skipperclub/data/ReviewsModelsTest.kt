package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewsModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesReviewListWithRatingsAndStatus() {
        val page = json.decodeFromString<ReviewsListDto>(SAMPLE).toDomain()

        assertEquals(1, page.reviews.size)
        val review = page.reviews.first()
        assertEquals("Anna Nowak", review.reviewedUser.name)
        assertEquals(ReviewStatus.Published, review.status)
        assertEquals(4.5, review.ratings.average, 0.0001)
        assertEquals(5, review.ratings.communication)
        assertEquals("018fa2e4-cruise", review.cruiseId)
    }

    @Test
    fun dropsReviewsWithUnknownStatus() {
        val dto = json.decodeFromString<ReviewDto>(
            """{"id":"r1","cruiseId":"c1","reviewer":{"id":"u1"},"reviewedUser":{"id":"u2"},
               "ratings":{"communication":5,"behavior":5,"skills":5,"duties":5,"average":5.0},
               "comment":"x","status":"archived"}""",
        )
        assertNull(dto.toDomain())
    }

    @Test
    fun fallsBackToNestedCruiseIdWhenTopLevelMissing() {
        val dto = json.decodeFromString<ReviewDto>(
            """{"id":"r1","reviewer":{"id":"u1"},"reviewedUser":{"id":"u2"},
               "cruise":{"id":"nested-cruise","title":"T","departureDate":"2025-07-12"},
               "ratings":{"communication":4,"behavior":4,"skills":4,"duties":4,"average":4.0},
               "comment":"x","status":"pending"}""",
        )
        assertEquals("nested-cruise", dto.toDomain()?.cruiseId)
    }

    private companion object {
        const val SAMPLE = """
        {
          "reviews": [
            {
              "id": "018fa2e4-review",
              "cruiseId": "018fa2e4-cruise",
              "reviewer": { "id": "u1", "name": "Jan Kowalski", "avatarUrl": null },
              "reviewedUser": { "id": "u2", "name": "Anna Nowak", "avatarUrl": null },
              "cruise": { "id": "018fa2e4-cruise", "title": "Mediterranean", "departureDate": "2025-07-12" },
              "ratings": { "communication": 5, "behavior": 4, "skills": 5, "duties": 4, "average": 4.5 },
              "comment": "Great crew member",
              "status": "published",
              "createdAt": "2025-11-23T12:00:00Z",
              "updatedAt": "2025-11-23T14:00:00Z"
            }
          ],
          "total": 1,
          "limit": 20,
          "offset": 0
        }
        """
    }
}
