package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodesNotificationsListWithMetadataAndPaging() {
        val payload = """
            {
              "notifications": [
                {
                  "id": "n1",
                  "recipientId": "u1",
                  "eventType": "POST_COMMENTED",
                  "sourceType": "POST",
                  "sourceId": "post-1",
                  "relationId": "actor-1",
                  "status": "UNREAD",
                  "metadata": { "actorName": "Jane Doe", "commentText": "Nice trip" },
                  "createdAt": "2025-11-23T16:00:00Z",
                  "readAt": null
                }
              ],
              "total": 15,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<NotificationsListDto>(payload).toDomain()

        assertEquals(1, page.notifications.size)
        val notification = page.notifications.first()
        assertEquals("n1", notification.id)
        assertEquals(NotificationEventType.PostCommented, notification.eventType)
        assertEquals(NotificationSourceType.Post, notification.sourceType)
        assertEquals(NotificationStatus.Unread, notification.status)
        assertEquals("Jane Doe", notification.actorName)
        assertEquals("Nice trip", notification.commentText)
        assertEquals(15, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun unknownEventTypeFallsBackToUnknownWithoutDropping() {
        val payload = """
            {
              "notifications": [
                {
                  "id": "n1",
                  "recipientId": "u1",
                  "eventType": "SOMETHING_NEW",
                  "sourceType": "CRUISE",
                  "sourceId": "c1",
                  "status": "READ",
                  "createdAt": "2025-11-23T16:00:00Z"
                }
              ],
              "total": 1, "limit": 20, "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<NotificationsListDto>(payload).toDomain()

        assertEquals(NotificationEventType.Unknown, page.notifications.single().eventType)
    }

    @Test
    fun unknownSourceTypeRowIsDropped() {
        val payload = """
            {
              "notifications": [
                {
                  "id": "n1", "recipientId": "u1", "eventType": "POST_REACTED",
                  "sourceType": "GALAXY", "sourceId": "c1", "status": "UNREAD",
                  "createdAt": "2025-11-23T16:00:00Z"
                }
              ],
              "total": 1, "limit": 20, "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<NotificationsListDto>(payload).toDomain()

        assertTrue(page.notifications.isEmpty())
    }

    @Test
    fun nullRelationIdAndMetadataDecodeToDefaults() {
        val payload = """
            {
              "notifications": [
                {
                  "id": "n1", "recipientId": "u1", "eventType": "CRUISE_REVIEW_REMINDER",
                  "sourceType": "CRUISE", "sourceId": "c1", "relationId": null,
                  "status": "UNREAD", "metadata": null, "createdAt": "2025-11-23T16:00:00Z"
                }
              ],
              "total": 1, "limit": 20, "offset": 0
            }
        """.trimIndent()

        val notification = json.decodeFromString<NotificationsListDto>(payload).toDomain().notifications.single()

        assertNull(notification.relationId)
        assertTrue(notification.metadata.isEmpty())
        assertNull(notification.actorName)
    }

    @Test
    fun hasMoreIsFalseWhenAllLoaded() {
        val page = NotificationsPage(
            notifications = listOf(),
            total = 0,
            limit = 20,
            offset = 0,
        )
        assertEquals(false, page.hasMore)
    }
}
