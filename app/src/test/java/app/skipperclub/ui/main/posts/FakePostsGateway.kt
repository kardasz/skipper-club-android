package app.skipperclub.ui.main.posts

import app.skipperclub.data.CommentsPage
import app.skipperclub.data.CreatePostRequest
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.PageMeta
import app.skipperclub.data.Post
import app.skipperclub.data.PostComment
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostFeedQuery
import app.skipperclub.data.PostPermissions
import app.skipperclub.data.PostStatus
import app.skipperclub.data.PostType
import app.skipperclub.data.PostUser
import app.skipperclub.data.PostsError
import app.skipperclub.data.PostsPage
import app.skipperclub.data.ReactionSummary
import app.skipperclub.data.ReactionType
import app.skipperclub.data.Region
import app.skipperclub.data.UploadedMedia
import app.skipperclub.data.ValidityVoteResult
import app.skipperclub.data.ValidityVoteType

internal fun testPost(
    id: String,
    type: PostType = PostType.Photo,
    reactions: ReactionSummary = ReactionSummary(),
    bookmarked: Boolean = false,
    commentsCount: Int = 0,
) = Post(
    id = id,
    type = type,
    status = PostStatus.Published,
    regionCode = "ADR-HR",
    user = PostUser(id = "author", name = "Author"),
    description = "post $id",
    reactions = reactions,
    bookmarked = bookmarked,
    commentsCount = commentsCount,
    permissions = PostPermissions(react = true, comment = true, bookmark = true),
    createdAt = "2025-12-01T10:00:00Z",
    updatedAt = "2025-12-01T10:00:00Z",
)

internal fun testComment(id: String, userId: String = "author", text: String = "comment $id") =
    PostComment(
        id = id,
        user = PostUser(id = userId, name = "User $userId"),
        text = text,
        createdAt = "2025-12-01T11:00:00Z",
        updatedAt = "2025-12-01T11:00:00Z",
    )

/** Configurable in-memory [PostsGateway]; records calls for assertions. */
internal class FakePostsGateway : PostsGateway {
    var pages: List<PostsPage> = listOf(PostsPage(emptyList(), PageMeta(0, 20, 0, false)))
    var listError: PostsError? = null
    val listQueries = mutableListOf<PostFeedQuery>()

    var commentPages: List<CommentsPage> = emptyList()
    var commentsError: PostsError? = null
    var addedComment: PostComment = testComment("new-comment")
    var mutationError: PostsError? = null
    var reactionSummary: ReactionSummary = ReactionSummary()
    var voteResult: ValidityVoteResult = ValidityVoteResult("post", ValidityVoteType.Confirm, 1, 0)
    var createdPost: Post = testPost("created")
    var fetchedPost: Post = testPost("fetched")
    var getError: PostsError? = null
    var regions: List<Region> = emptyList()
    var locations: List<GeocodedLocation> = emptyList()

    val calls = mutableListOf<String>()

    private var listCallCount = 0
    private var commentsCallCount = 0

    override suspend fun list(accessToken: String, query: PostFeedQuery): PostsPage {
        calls += "list"
        listQueries += query
        listError?.let { throw it }
        val page = pages[minOf(listCallCount, pages.lastIndex)]
        listCallCount++
        return page
    }

    override suspend fun get(accessToken: String, postId: String): Post {
        calls += "get:$postId"
        getError?.let { throw it }
        return fetchedPost
    }

    override suspend fun create(accessToken: String, payload: CreatePostRequest): Post {
        calls += "create:${payload.type}"
        mutationError?.let { throw it }
        return createdPost
    }

    override suspend fun updateStatus(accessToken: String, postId: String, status: PostStatus): Post {
        calls += "updateStatus:$postId:${status.wireValue}"
        mutationError?.let { throw it }
        return testPost(postId)
    }

    override suspend fun delete(accessToken: String, postId: String) {
        calls += "delete:$postId"
        mutationError?.let { throw it }
    }

    override suspend fun comments(accessToken: String, postId: String, limit: Int, offset: Int): CommentsPage {
        calls += "comments:$postId:$offset"
        commentsError?.let { throw it }
        val page = commentPages[minOf(commentsCallCount, commentPages.lastIndex)]
        commentsCallCount++
        return page
    }

    override suspend fun addComment(accessToken: String, postId: String, text: String): PostComment {
        calls += "addComment:$postId:$text"
        mutationError?.let { throw it }
        return addedComment
    }

    override suspend fun deleteComment(accessToken: String, postId: String, commentId: String) {
        calls += "deleteComment:$postId:$commentId"
        mutationError?.let { throw it }
    }

    override suspend fun addReaction(
        accessToken: String,
        postId: String,
        reaction: ReactionType,
    ): ReactionSummary {
        calls += "addReaction:$postId:${reaction.wireValue}"
        mutationError?.let { throw it }
        return reactionSummary
    }

    override suspend fun removeReaction(accessToken: String, postId: String, reaction: ReactionType) {
        calls += "removeReaction:$postId:${reaction.wireValue}"
        mutationError?.let { throw it }
    }

    override suspend fun addBookmark(accessToken: String, postId: String) {
        calls += "addBookmark:$postId"
        mutationError?.let { throw it }
    }

    override suspend fun removeBookmark(accessToken: String, postId: String) {
        calls += "removeBookmark:$postId"
        mutationError?.let { throw it }
    }

    override suspend fun castValidityVote(
        accessToken: String,
        postId: String,
        vote: ValidityVoteType,
    ): ValidityVoteResult {
        calls += "vote:$postId:${vote.wireValue}"
        mutationError?.let { throw it }
        return voteResult
    }

    override suspend fun listRegions(): List<Region> {
        calls += "listRegions"
        return regions
    }

    override suspend fun searchLocations(accessToken: String, query: String): List<GeocodedLocation> {
        calls += "searchLocations:$query"
        return locations
    }

    override suspend fun uploadMedia(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int?,
        height: Int?,
    ): UploadedMedia {
        calls += "uploadMedia:$fileName"
        mutationError?.let { throw it }
        return UploadedMedia(mediaId = "media-$fileName", publicUrl = "https://cdn/$fileName")
    }
}

internal fun page(posts: List<Post>, hasMore: Boolean, total: Int = posts.size) =
    PostsPage(posts, PageMeta(total = total, limit = 20, offset = 0, hasMore = hasMore))

internal fun commentsPage(comments: List<PostComment>, total: Int) =
    CommentsPage(comments = comments, total = total, limit = 20, offset = 0)

internal fun geocoded(name: String, lat: Double = 43.5, lng: Double = 16.4) =
    GeocodedLocation(name = name, formattedAddress = "$name, Croatia", coordinates = PostCoordinates(lat, lng))
