package app.skipperclub.ui.main.posts

import app.skipperclub.data.Post
import app.skipperclub.data.PostFeedQuery
import app.skipperclub.data.PostSortField
import app.skipperclub.data.PostStatus
import app.skipperclub.data.PostType
import app.skipperclub.data.PostsError
import app.skipperclub.data.ReactionType
import app.skipperclub.data.SortOrder
import app.skipperclub.data.ValidityVoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Feed filters chosen in the filter sheet. */
data class PostFilters(
    val types: Set<PostType> = emptySet(),
    val regionCode: String? = null,
    val sort: PostSortField = PostSortField.CreatedAt,
    val order: SortOrder = SortOrder.Desc,
) {
    val activeCount: Int
        get() = (if (types.isEmpty()) 0 else 1) +
            (if (regionCode == null) 0 else 1) +
            (if (sort != PostSortField.CreatedAt || order != SortOrder.Desc) 1 else 0)

    fun toQuery(limit: Int, offset: Int): PostFeedQuery =
        PostFeedQuery(
            types = types,
            regionCode = regionCode,
            sort = sort,
            order = order,
            limit = limit,
            offset = offset,
        )
}

data class PostsFeedUiState(
    val posts: List<Post> = emptyList(),
    val filters: PostFilters = PostFilters(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val hasLoadedOnce: Boolean = false,
)

sealed interface PostsFeedEvent {
    data class OperationFailed(val error: Exception) : PostsFeedEvent
    data object SessionExpired : PostsFeedEvent
    data object PostDeleted : PostsFeedEvent
    data object PostArchived : PostsFeedEvent
    data object PostResolved : PostsFeedEvent
}

/**
 * State holder for the posts feed: pagination, filters and post mutations.
 * Plain class (no ViewModel/DI yet — see CLAUDE.md §State); owned by the
 * composable via `remember` and unit-tested with a fake [PostsGateway].
 */
class PostsFeedController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: PostsGateway = RealPostsGateway,
    private val pageSize: Int = 20,
) {
    private val _state = MutableStateFlow(PostsFeedUiState())
    val state: StateFlow<PostsFeedUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PostsFeedEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PostsFeedEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun loadInitialIfNeeded() {
        val current = _state.value
        if (current.hasLoadedOnce || current.isLoading) return
        reload(showAsRefreshing = false)
    }

    fun refresh() {
        reload(showAsRefreshing = true)
    }

    fun applyFilters(filters: PostFilters) {
        if (filters == _state.value.filters) return
        _state.update { it.copy(filters = filters) }
        reload(showAsRefreshing = false)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isRefreshing || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        loadJob = scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            try {
                val snapshot = _state.value
                val page = gateway.list(
                    token,
                    snapshot.filters.toQuery(limit = pageSize, offset = snapshot.posts.size),
                )
                _state.update { state ->
                    val knownIds = state.posts.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        posts = state.posts + page.posts.filterNot { it.id in knownIds },
                        hasMore = page.meta.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (error: PostsError) {
                _state.update { it.copy(isLoadingMore = false) }
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    /** Prepends a freshly created post so it is visible without a round-trip. */
    fun onPostCreated(post: Post) {
        _state.update { state ->
            state.copy(posts = listOf(post) + state.posts.filterNot { it.id == post.id })
        }
    }

    fun toggleReaction(post: Post, reaction: ReactionType) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                if (reaction in post.reactions.userReactions) {
                    gateway.removeReaction(token, post.id, reaction)
                    updatePost(post.id) { it.copy(reactions = it.reactions.withoutUserReaction(reaction)) }
                } else {
                    val summary = gateway.addReaction(token, post.id, reaction)
                    updatePost(post.id) { it.copy(reactions = summary) }
                }
            } catch (error: PostsError) {
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    fun toggleBookmark(post: Post) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                if (post.bookmarked) {
                    gateway.removeBookmark(token, post.id)
                    updatePost(post.id) { it.copy(bookmarked = false) }
                } else {
                    gateway.addBookmark(token, post.id)
                    updatePost(post.id) { it.copy(bookmarked = true) }
                }
            } catch (error: PostsError) {
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    fun castValidityVote(post: Post, vote: ValidityVoteType) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                val result = gateway.castValidityVote(token, post.id, vote)
                updatePost(post.id) {
                    it.copy(
                        validityVotes = (it.validityVotes ?: app.skipperclub.data.VoteSummary()).copy(
                            confirmCount = result.confirmCount,
                            invalidCount = result.invalidCount,
                            userVote = vote,
                        ),
                    )
                }
            } catch (error: PostsError) {
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    fun archivePost(post: Post) {
        changeStatus(post, PostStatus.Archived, PostsFeedEvent.PostArchived)
    }

    fun resolvePost(post: Post) {
        changeStatus(post, PostStatus.Resolved, PostsFeedEvent.PostResolved)
    }

    fun deletePost(post: Post) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.delete(token, post.id)
                removePost(post.id)
                _events.tryEmit(PostsFeedEvent.PostDeleted)
            } catch (error: PostsError) {
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    /** Keeps the card's comment counter in sync with the comments sheet. */
    fun adjustCommentsCount(postId: String, delta: Int) {
        updatePost(postId) { it.copy(commentsCount = (it.commentsCount + delta).coerceAtLeast(0)) }
    }

    private fun changeStatus(post: Post, status: PostStatus, successEvent: PostsFeedEvent) {
        scope.launch {
            val token = requireToken() ?: return@launch
            try {
                gateway.updateStatus(token, post.id, status)
                // The default feed lists published posts only, so drop the card.
                removePost(post.id)
                _events.tryEmit(successEvent)
            } catch (error: PostsError) {
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    private fun reload(showAsRefreshing: Boolean) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = !showAsRefreshing,
                isRefreshing = showAsRefreshing,
                isLoadingMore = false,
                loadFailed = false,
            )
        }
        loadJob = scope.launch {
            val token = requireToken() ?: run {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                return@launch
            }
            try {
                val page = gateway.list(
                    token,
                    _state.value.filters.toQuery(limit = pageSize, offset = 0),
                )
                _state.update {
                    it.copy(
                        posts = page.posts,
                        hasMore = page.meta.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        loadFailed = false,
                        hasLoadedOnce = true,
                    )
                }
            } catch (error: PostsError) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = true, hasLoadedOnce = true)
                }
                _events.tryEmit(PostsFeedEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(PostsFeedEvent.SessionExpired)
        return token
    }

    private fun updatePost(postId: String, transform: (Post) -> Post) {
        _state.update { state ->
            state.copy(posts = state.posts.map { if (it.id == postId) transform(it) else it })
        }
    }

    private fun removePost(postId: String) {
        _state.update { state ->
            state.copy(posts = state.posts.filterNot { it.id == postId })
        }
    }
}

private fun app.skipperclub.data.ReactionSummary.withoutUserReaction(
    reaction: ReactionType,
): app.skipperclub.data.ReactionSummary {
    if (reaction !in userReactions) return this
    val newCount = ((byType[reaction] ?: 1) - 1).coerceAtLeast(0)
    return copy(
        total = (total - 1).coerceAtLeast(0),
        byType = if (newCount == 0) byType - reaction else byType + (reaction to newCount),
        userReactions = userReactions - reaction,
    )
}
