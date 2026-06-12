package app.skipperclub.ui.main.posts

import app.skipperclub.data.PostComment
import app.skipperclub.data.PostsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val comments: List<PostComment> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
)

sealed interface CommentsEvent {
    data class OperationFailed(val error: Exception) : CommentsEvent
    data object CommentAdded : CommentsEvent
    data object CommentDeleted : CommentsEvent
    data object SessionExpired : CommentsEvent
}

/** State holder for one post's comments sheet (offset-paginated, send, delete). */
class CommentsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val postId: String,
    private val gateway: PostsGateway = RealPostsGateway,
    private val pageSize: Int = 20,
) {
    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CommentsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CommentsEvent> = _events.asSharedFlow()

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            try {
                val page = gateway.comments(token, postId, limit = pageSize, offset = 0)
                _state.update {
                    it.copy(
                        comments = page.comments,
                        total = page.total,
                        hasMore = page.comments.size < page.total,
                        isLoading = false,
                    )
                }
            } catch (error: PostsError) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                _events.tryEmit(CommentsEvent.OperationFailed(error))
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            try {
                val page = gateway.comments(
                    token,
                    postId,
                    limit = pageSize,
                    offset = _state.value.comments.size,
                )
                _state.update { state ->
                    val knownIds = state.comments.mapTo(mutableSetOf()) { it.id }
                    val merged = state.comments + page.comments.filterNot { it.id in knownIds }
                    state.copy(
                        comments = merged,
                        total = page.total,
                        hasMore = merged.size < page.total,
                        isLoadingMore = false,
                    )
                }
            } catch (error: PostsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(CommentsEvent.OperationFailed(error))
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(isSending = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSending = false) }
                return@launch
            }
            try {
                val comment = gateway.addComment(token, postId, trimmed)
                _state.update {
                    it.copy(
                        comments = it.comments + comment,
                        total = it.total + 1,
                        isSending = false,
                    )
                }
                _events.tryEmit(CommentsEvent.CommentAdded)
            } catch (error: PostsError) {
                _state.update { it.copy(isSending = false) }
                _events.tryEmit(CommentsEvent.OperationFailed(error))
            }
        }
    }

    fun delete(commentId: String) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.deleteComment(token, postId, commentId)
                _state.update { state ->
                    state.copy(
                        comments = state.comments.filterNot { it.id == commentId },
                        total = (state.total - 1).coerceAtLeast(0),
                    )
                }
                _events.tryEmit(CommentsEvent.CommentDeleted)
            } catch (error: PostsError) {
                _events.tryEmit(CommentsEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(CommentsEvent.SessionExpired)
        return token
    }
}
