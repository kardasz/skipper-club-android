package app.skipperclub.ui.main.notifications

import app.skipperclub.data.NotificationEventType
import app.skipperclub.data.NotificationSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTargetTest {

    @Test
    fun cruiseSourceTargetsCruiseDetailBySourceId() {
        val target = testNotification(
            "n1",
            sourceType = NotificationSourceType.Cruise,
            sourceId = "cruise-42",
        ).target()

        assertEquals(NotificationTarget.Cruise("cruise-42"), target)
    }

    @Test
    fun postCommentedTargetsPostWithCommentsFocus() {
        val target = testNotification(
            "n1",
            eventType = NotificationEventType.PostCommented,
            sourceType = NotificationSourceType.Post,
            sourceId = "post-7",
        ).target()

        assertEquals(NotificationTarget.Post("post-7", focusComments = true), target)
    }

    @Test
    fun postReactedTargetsPostWithoutCommentsFocus() {
        val target = testNotification(
            "n1",
            eventType = NotificationEventType.PostReacted,
            sourceType = NotificationSourceType.Post,
            sourceId = "post-7",
        ).target()

        assertEquals(NotificationTarget.Post("post-7", focusComments = false), target)
    }

    @Test
    fun friendReviewAndMessageSourcesHaveNoTargetYet() {
        listOf(
            NotificationSourceType.Friend,
            NotificationSourceType.Review,
            NotificationSourceType.Message,
            NotificationSourceType.Media,
        ).forEach { source ->
            assertNull(testNotification("n1", sourceType = source).target())
        }
    }

    @Test
    fun unreadHelperReflectsStatus() {
        assertTrue(testNotification("n1").isUnread)
    }
}
