package app.skipperclub.ui.main.posts

import app.skipperclub.data.BookmarksQuery
import app.skipperclub.data.CommentsPage
import app.skipperclub.data.CreatePostRequest
import app.skipperclub.data.FriendListQuery
import app.skipperclub.data.FriendUser
import app.skipperclub.data.FriendsApi
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.GeocoderApi
import app.skipperclub.data.MediaUploadApi
import app.skipperclub.data.MediaUploadMeta
import app.skipperclub.data.Post
import app.skipperclub.data.PostComment
import app.skipperclub.data.PostFeedQuery
import app.skipperclub.data.PostStatus
import app.skipperclub.data.PostsApi
import app.skipperclub.data.PostsPage
import app.skipperclub.data.ReactionSummary
import app.skipperclub.data.ReactionType
import app.skipperclub.data.Region
import app.skipperclub.data.RegionsApi
import app.skipperclub.data.ReportReason
import app.skipperclub.data.UpdatePostRequest
import app.skipperclub.data.UploadedMedia
import app.skipperclub.data.ValidityVoteResult
import app.skipperclub.data.ValidityVoteType

/**
 * Seam between the posts UI controllers and the API singletons so state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface PostsGateway {
    suspend fun list(accessToken: String, query: PostFeedQuery): PostsPage
    suspend fun listBookmarks(accessToken: String, query: BookmarksQuery): PostsPage
    suspend fun get(accessToken: String, postId: String): Post
    suspend fun create(accessToken: String, payload: CreatePostRequest): Post
    suspend fun update(accessToken: String, postId: String, payload: UpdatePostRequest): Post
    suspend fun updateStatus(accessToken: String, postId: String, status: PostStatus): Post
    suspend fun delete(accessToken: String, postId: String)
    suspend fun report(accessToken: String, postId: String, reason: ReportReason, details: String?)
    suspend fun comments(accessToken: String, postId: String, limit: Int, offset: Int): CommentsPage
    suspend fun addComment(accessToken: String, postId: String, text: String): PostComment
    suspend fun updateComment(accessToken: String, postId: String, commentId: String, text: String): PostComment
    suspend fun deleteComment(accessToken: String, postId: String, commentId: String)
    suspend fun addReaction(accessToken: String, postId: String, reaction: ReactionType): ReactionSummary
    suspend fun removeReaction(accessToken: String, postId: String, reaction: ReactionType)
    suspend fun addBookmark(accessToken: String, postId: String)
    suspend fun removeBookmark(accessToken: String, postId: String)
    suspend fun castValidityVote(accessToken: String, postId: String, vote: ValidityVoteType): ValidityVoteResult
    suspend fun listRegions(): List<Region>
    suspend fun searchLocations(accessToken: String, query: String): List<GeocodedLocation>
    suspend fun searchFriends(accessToken: String, query: String): List<FriendUser>
    suspend fun uploadMedia(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        meta: MediaUploadMeta,
    ): UploadedMedia
}

object RealPostsGateway : PostsGateway {
    override suspend fun list(accessToken: String, query: PostFeedQuery): PostsPage =
        PostsApi.list(accessToken, query)

    override suspend fun listBookmarks(accessToken: String, query: BookmarksQuery): PostsPage =
        PostsApi.listBookmarks(accessToken, query)

    override suspend fun get(accessToken: String, postId: String): Post =
        PostsApi.get(accessToken, postId)

    override suspend fun create(accessToken: String, payload: CreatePostRequest): Post =
        PostsApi.create(accessToken, payload)

    override suspend fun update(accessToken: String, postId: String, payload: UpdatePostRequest): Post =
        PostsApi.update(accessToken, postId, payload)

    override suspend fun updateStatus(accessToken: String, postId: String, status: PostStatus): Post =
        PostsApi.updateStatus(accessToken, postId, status)

    override suspend fun delete(accessToken: String, postId: String) =
        PostsApi.delete(accessToken, postId)

    override suspend fun report(accessToken: String, postId: String, reason: ReportReason, details: String?) =
        PostsApi.report(accessToken, postId, reason, details)

    override suspend fun comments(accessToken: String, postId: String, limit: Int, offset: Int): CommentsPage =
        PostsApi.comments(accessToken, postId, limit, offset)

    override suspend fun addComment(accessToken: String, postId: String, text: String): PostComment =
        PostsApi.addComment(accessToken, postId, text)

    override suspend fun updateComment(
        accessToken: String,
        postId: String,
        commentId: String,
        text: String,
    ): PostComment = PostsApi.updateComment(accessToken, postId, commentId, text)

    override suspend fun deleteComment(accessToken: String, postId: String, commentId: String) =
        PostsApi.deleteComment(accessToken, postId, commentId)

    override suspend fun addReaction(accessToken: String, postId: String, reaction: ReactionType): ReactionSummary =
        PostsApi.addReaction(accessToken, postId, reaction)

    override suspend fun removeReaction(accessToken: String, postId: String, reaction: ReactionType) =
        PostsApi.removeReaction(accessToken, postId, reaction)

    override suspend fun addBookmark(accessToken: String, postId: String) =
        PostsApi.addBookmark(accessToken, postId)

    override suspend fun removeBookmark(accessToken: String, postId: String) =
        PostsApi.removeBookmark(accessToken, postId)

    override suspend fun castValidityVote(
        accessToken: String,
        postId: String,
        vote: ValidityVoteType,
    ): ValidityVoteResult = PostsApi.castValidityVote(accessToken, postId, vote)

    override suspend fun listRegions(): List<Region> = RegionsApi.list()

    override suspend fun searchLocations(accessToken: String, query: String): List<GeocodedLocation> =
        GeocoderApi.search(accessToken, query)

    override suspend fun searchFriends(accessToken: String, query: String): List<FriendUser> =
        FriendsApi.listFriends(accessToken, FriendListQuery(search = query, limit = 20)).friends

    override suspend fun uploadMedia(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        meta: MediaUploadMeta,
    ): UploadedMedia =
        MediaUploadApi.upload(
            accessToken = accessToken,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            meta = meta,
        )
}
