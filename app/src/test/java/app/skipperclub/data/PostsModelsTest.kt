package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostsModelsTest {

    @Test
    fun postTypeBusinessRulesMatchApiContract() {
        assertEquals(
            setOf(PostType.Berth, PostType.Weather, PostType.NavigationWarning, PostType.Help),
            PostType.entries.filter { it.isTimeSensitive }.toSet(),
        )
        // help is author-resolved only; no community voting
        assertEquals(
            setOf(PostType.Berth, PostType.Weather, PostType.NavigationWarning),
            PostType.entries.filter { it.isVotable }.toSet(),
        )
        assertFalse(PostType.Photo.requiresDescription)
        assertTrue(PostType.Tips.requiresDescription)
        assertFalse(PostType.Photo.requiresLocation)
        assertFalse(PostType.Tips.requiresLocation)
        assertTrue(PostType.Marina.requiresLocation)
        assertTrue(PostType.Photo.requiresMedia)
        assertFalse(PostType.Route.requiresMedia)
        assertTrue(PostType.Route.requiresStops)
    }

    @Test
    fun postTypeWireValuesRoundTrip() {
        PostType.entries.forEach { type ->
            assertEquals(type, PostType.fromWire(type.wireValue))
        }
        assertEquals(PostType.Marina, PostType.fromWire("marina"))
        assertEquals(PostType.NavigationWarning, PostType.fromWire("navigation_warning"))
        assertNull(PostType.fromWire("unknown"))
    }

    @Test
    fun feedResponseDecodesAllKnownFieldsAndDropsUnknownTypes() {
        val payload = """
            {
              "data": [
                {
                  "id": "post-1",
                  "type": "route",
                  "status": "published",
                  "regionCode": "ADR-HR",
                  "user": {"id": "u1", "name": "Jan", "avatarUrl": null},
                  "description": "Trip #adriatic",
                  "locationName": "Split",
                  "coordinates": {"lat": 43.5, "lng": 16.4},
                  "hashtags": ["adriatic"],
                  "media": [
                    {"id": "m1", "type": "image", "url": "https://cdn/img.jpg", "width": 100, "height": 80}
                  ],
                  "stops": [{"name": "Hvar", "coordinates": {"lat": 43.1, "lng": 16.4}}],
                  "durationDays": 7,
                  "lengthNm": 120,
                  "commentsCount": 3,
                  "reactions": {
                    "total": 4,
                    "byType": {"heart": 3, "mystery_reaction": 1},
                    "userReactions": ["heart"]
                  },
                  "bookmarked": true,
                  "permissions": {"edit": true, "react": true},
                  "expiresAt": null,
                  "createdAt": "2025-12-01T10:00:00Z",
                  "updatedAt": "2025-12-01T10:00:00Z"
                },
                {
                  "id": "post-2",
                  "type": "hologram",
                  "status": "published",
                  "regionCode": "ADR-HR",
                  "user": {"id": "u2", "name": "Anna"},
                  "createdAt": "2025-12-01T10:00:00Z",
                  "updatedAt": "2025-12-01T10:00:00Z"
                },
                {
                  "id": "post-3",
                  "type": "berth",
                  "status": "published",
                  "regionCode": "ADR-HR",
                  "user": {"id": "u3", "name": "Marek"},
                  "description": "Free berth",
                  "validityVotes": {"confirmCount": 2, "invalidCount": 1, "userVote": "confirm"},
                  "expiresAt": "2025-12-01T16:00:00Z",
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

        // unknown post type is dropped, not fatal
        assertEquals(listOf("post-1", "post-3"), page.posts.map { it.id })
        assertTrue(page.meta.hasMore)

        val route = page.posts[0]
        assertEquals(PostType.Route, route.type)
        assertEquals(1, route.stops.size)
        assertEquals(7, route.durationDays)
        assertEquals(120.0, route.lengthNm!!, 0.0)
        assertTrue(route.bookmarked)
        assertTrue(route.permissions.edit)
        assertFalse(route.permissions.delete)
        // unknown reaction types are dropped from byType but total is preserved
        assertEquals(4, route.reactions.total)
        assertEquals(mapOf(ReactionType.Heart to 3), route.reactions.byType)
        assertEquals(setOf(ReactionType.Heart), route.reactions.userReactions)

        val berth = page.posts[1]
        assertNotNull(berth.validityVotes)
        assertEquals(ValidityVoteType.Confirm, berth.validityVotes?.userVote)
        assertEquals("2025-12-01T16:00:00Z", berth.expiresAt)
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
