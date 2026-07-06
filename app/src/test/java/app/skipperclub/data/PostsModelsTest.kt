package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostsModelsTest {

    @Test
    fun contentKeyAndStatusWireValuesRoundTrip() {
        PostContentKey.entries.forEach { key ->
            assertEquals(key, PostContentKey.fromWire(key.wireValue))
        }
        assertNull(PostContentKey.fromWire("unknown"))
        PostStatus.entries.forEach { status ->
            assertEquals(status, PostStatus.fromWire(status.wireValue))
        }
        assertEquals(PostStatus.Resolved, PostStatus.fromWire("resolved"))
        assertNull(PostStatus.fromWire("hologram"))
    }

    @Test
    fun feedResponseDecodesContentKeysContentAndDropsUnknownStatuses() {
        val payload = """
            {
              "data": [
                {
                  "id": "post-1",
                  "contentKeys": ["route"],
                  "status": "published",
                  "user": {"id": "u1", "name": "Jan", "avatarUrl": null},
                  "content": {
                    "text": "Trip #adriatic",
                    "route": {
                      "stops": [{"name": "Hvar", "coordinates": {"lat": 43.1, "lng": 16.4}}],
                      "durationDays": 7,
                      "lengthNm": 120
                    }
                  },
                  "location": {"name": "Split", "point": {"lat": 43.5, "lng": 16.4}},
                  "hashtags": ["adriatic"],
                  "media": [
                    {"id": "m1", "type": "image", "url": "https://cdn/img.jpg", "width": 100, "height": 80}
                  ],
                  "commentsCount": 3,
                  "reactions": {
                    "total": 4,
                    "byType": {"heart": 3, "mystery_reaction": 1},
                    "userReactions": ["heart"]
                  },
                  "bookmarked": true,
                  "permissions": {"edit": true, "react": true},
                  "publishedAt": "2025-12-01T10:00:00Z",
                  "createdAt": "2025-12-01T09:00:00Z",
                  "updatedAt": "2025-12-01T10:00:00Z"
                },
                {
                  "id": "post-2",
                  "contentKeys": [],
                  "status": "hologram",
                  "user": {"id": "u2", "name": "Anna"},
                  "content": {"text": "?"},
                  "publishedAt": "2025-12-01T10:00:00Z",
                  "createdAt": "2025-12-01T10:00:00Z",
                  "updatedAt": "2025-12-01T10:00:00Z"
                },
                {
                  "id": "post-3",
                  "contentKeys": ["alert"],
                  "status": "published",
                  "user": {"id": "u3", "name": "Marek"},
                  "content": {
                    "text": "Submerged obstruction",
                    "alert": {"category": "obstruction", "severity": "warning", "source": "navtex"}
                  },
                  "location": {"name": "Hvar"},
                  "source": {"type": "import", "id": "navtex-1"},
                  "validityVotes": {"confirmCount": 2, "invalidCount": 1, "userVote": "confirm"},
                  "expiresAt": "2025-12-01T16:00:00Z",
                  "publishedAt": "2025-12-01T10:00:00Z",
                  "createdAt": "2025-12-01T10:00:00Z",
                  "updatedAt": "2025-12-01T10:00:00Z"
                }
              ],
              "meta": {"total": 3, "limit": 20, "offset": 0, "hasMore": true}
            }
        """.trimIndent()

        val page = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }.decodeFromString<PostsListDto>(payload).toDomain()

        // unknown status is dropped, not fatal
        assertEquals(listOf("post-1", "post-3"), page.posts.map { it.id })
        assertTrue(page.meta.hasMore)

        val route = page.posts[0]
        assertEquals(setOf(PostContentKey.Route), route.contentKeys)
        assertTrue(route.hasRoute)
        assertEquals("Trip #adriatic", route.content.text)
        assertEquals(1, route.route?.stops?.size)
        assertEquals(7, route.route?.durationDays)
        assertEquals(120.0, route.route?.lengthNm!!, 0.0)
        assertEquals("Split", route.location.name)
        assertEquals(43.5, route.location.point?.lat!!, 0.0)
        // publishedAt is read as the feed timestamp, distinct from createdAt
        assertEquals("2025-12-01T10:00:00Z", route.publishedAt)
        assertTrue(route.bookmarked)
        assertTrue(route.permissions.edit)
        assertFalse(route.permissions.delete)
        assertFalse(route.isSystemGenerated)
        // unknown reaction types are dropped from byType but total is preserved
        assertEquals(4, route.reactions.total)
        assertEquals(mapOf(ReactionType.Heart to 3), route.reactions.byType)
        assertEquals(setOf(ReactionType.Heart), route.reactions.userReactions)

        val alert = page.posts[1]
        assertEquals(setOf(PostContentKey.Alert), alert.contentKeys)
        assertTrue(alert.hasAlert)
        assertEquals(AlertCategory.Obstruction, alert.alert?.category)
        assertEquals(AlertSeverity.Warning, alert.alert?.severity)
        assertTrue(alert.isSystemGenerated)
        assertNotNull(alert.validityVotes)
        assertEquals(ValidityVoteType.Confirm, alert.validityVotes?.userVote)
        assertEquals("2025-12-01T16:00:00Z", alert.expiresAt)
    }

    @Test
    fun postMediaIsVideoDistinguishesImagesFromVideos() {
        val image = PostMedia(id = "m1", type = "image", url = "https://cdn/img.jpg")
        val video = PostMedia(id = "m2", type = "video", url = "https://cdn/clip.mp4")
        assertFalse(image.isVideo)
        assertTrue(video.isVideo)
        // wire value is matched case-insensitively
        assertTrue(PostMedia(id = "m3", type = "VIDEO", url = "https://cdn/c.mp4").isVideo)
    }

    @Test
    fun commentsListDecodes() {
        val payload = """
            {
              "comments": [
                {
                  "id": "c1",
                  "user": {"id": "u1", "name": "Jan"},
                  "text": "Great!",
                  "createdAt": "2025-12-01T10:00:00Z",
                  "updatedAt": "2025-12-01T10:00:00Z"
                }
              ],
              "total": 5,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }.decodeFromString<CommentsListDto>(payload).toDomain()

        assertEquals(1, page.comments.size)
        assertEquals(5, page.total)
        assertEquals("Great!", page.comments[0].text)
    }
}
