package app.skipperclub.ui.main.posts

import app.skipperclub.data.PostsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakePostsGateway()
    private val events = mutableListOf<CommentsEvent>()

    private fun controller(token: String? = "token"): CommentsController {
        val controller = CommentsController(
            scope = scope,
            accessToken = { token },
            postId = "post-1",
            gateway = gateway,
            pageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun loadPopulatesCommentsAndHasMore() {
        gateway.commentPages = listOf(
            commentsPage(listOf(testComment("c1"), testComment("c2")), total = 3),
        )
        val controller = controller()

        controller.load()

        val state = controller.state.value
        assertEquals(listOf("c1", "c2"), state.comments.map { it.id })
        assertEquals(3, state.total)
        assertTrue(state.hasMore)
    }

    @Test
    fun loadMoreAppendsWithOffset() {
        gateway.commentPages = listOf(
            commentsPage(listOf(testComment("c1"), testComment("c2")), total = 3),
            commentsPage(listOf(testComment("c3")), total = 3),
        )
        val controller = controller()
        controller.load()

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf("c1", "c2", "c3"), state.comments.map { it.id })
        assertFalse(state.hasMore)
        assertTrue(gateway.calls.contains("comments:post-1:2"))
    }

    @Test
    fun sendAppendsCommentAndEmitsAdded() {
        gateway.commentPages = listOf(commentsPage(emptyList(), total = 0))
        gateway.addedComment = testComment("new", text = "Hello")
        val controller = controller()
        controller.load()

        controller.send("  Hello  ")

        assertEquals("addComment:post-1:Hello", gateway.calls.last())
        assertEquals(listOf("new"), controller.state.value.comments.map { it.id })
        assertEquals(1, controller.state.value.total)
        assertTrue(events.contains(CommentsEvent.CommentAdded))
    }

    @Test
    fun blankCommentIsNotSent() {
        gateway.commentPages = listOf(commentsPage(emptyList(), total = 0))
        val controller = controller()
        controller.load()

        controller.send("   ")

        assertFalse(gateway.calls.any { it.startsWith("addComment") })
    }

    @Test
    fun deleteRemovesCommentAndEmitsDeleted() {
        gateway.commentPages = listOf(
            commentsPage(listOf(testComment("c1"), testComment("c2")), total = 2),
        )
        val controller = controller()
        controller.load()

        controller.delete("c1")

        assertEquals(listOf("c2"), controller.state.value.comments.map { it.id })
        assertEquals(1, controller.state.value.total)
        assertTrue(events.contains(CommentsEvent.CommentDeleted))
    }

    @Test
    fun editReplacesCommentTextAndEmitsUpdated() {
        gateway.commentPages = listOf(
            commentsPage(listOf(testComment("c1", text = "old"), testComment("c2")), total = 2),
        )
        val controller = controller()
        controller.load()

        controller.edit("c1", "  new text  ")

        assertEquals("updateComment:post-1:c1:new text", gateway.calls.last())
        assertEquals("new text", controller.state.value.comments.first { it.id == "c1" }.text)
        assertEquals(2, controller.state.value.total)
        assertTrue(events.contains(CommentsEvent.CommentUpdated))
    }

    @Test
    fun blankEditIsNotSent() {
        gateway.commentPages = listOf(commentsPage(listOf(testComment("c1")), total = 1))
        val controller = controller()
        controller.load()

        controller.edit("c1", "   ")

        assertFalse(gateway.calls.any { it.startsWith("updateComment") })
    }

    @Test
    fun failedLoadSetsFlagAndEmitsEvent() {
        gateway.commentsError = PostsError.Server(500, null)
        val controller = controller()

        controller.load()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is CommentsEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.load()

        assertTrue(events.contains(CommentsEvent.SessionExpired))
    }
}
