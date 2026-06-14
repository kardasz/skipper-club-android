package app.skipperclub.data

import app.skipperclub.BuildConfig
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Sort options accepted by `GET /v1/posts`. */
enum class PostSortField(val wireValue: String) {
    CreatedAt("createdAt"),
    UpdatedAt("updatedAt"),

    /** Nearest-first; only valid together with `lat`/`lng`/`distance`. */
    Distance("distance"),
}

enum class SortOrder(val wireValue: String) {
    Asc("asc"),
    Desc("desc"),
}

/** Sort options accepted by `GET /v1/profile/bookmarks/posts` (no `distance`). */
enum class BookmarkSortField(val wireValue: String) {
    CreatedAt("createdAt"),
    UpdatedAt("updatedAt"),
}

/** Query parameters for `GET /v1/posts`. */
data class PostFeedQuery(
    val types: Set<PostType> = emptySet(),
    val regionCode: String? = null,
    val statuses: Set<PostStatus> = emptySet(),
    val crossRegionTypes: Set<PostType> = emptySet(),
    val locationName: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val distanceKm: Int? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val hashtag: String? = null,
    val userId: String? = null,
    val sort: PostSortField = PostSortField.CreatedAt,
    val order: SortOrder = SortOrder.Desc,
    val limit: Int = 20,
    val offset: Int = 0,
)

/** Query parameters for `GET /v1/profile/bookmarks/posts`. */
data class BookmarksQuery(
    val sort: BookmarkSortField = BookmarkSortField.CreatedAt,
    val order: SortOrder = SortOrder.Desc,
    val limit: Int = 20,
    val offset: Int = 0,
)

/**
 * Client for the `/v1/posts` endpoints. Modeled on [CheckInsApi] (raw OkHttp +
 * manual RFC 7807 mapping) to stay consistent until the codebase grows enough to
 * justify Retrofit + DI (see CLAUDE.md §Networking).
 */
object PostsApi {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    private fun postsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/posts".toHttpUrl()

    internal fun listRequest(accessToken: String, query: PostFeedQuery): Request {
        val url = postsUrl().newBuilder().apply {
            query.types.sortedBy { it.ordinal }.forEach { addQueryParameter("type", it.wireValue) }
            query.regionCode?.let { addQueryParameter("regionCode", it) }
            query.statuses.sortedBy { it.ordinal }.forEach { addQueryParameter("status", it.wireValue) }
            query.crossRegionTypes.sortedBy { it.ordinal }
                .forEach { addQueryParameter("crossRegionTypes", it.wireValue) }
            query.locationName?.let { addQueryParameter("locationName", it) }
            query.lat?.let { addQueryParameter("lat", it.toString()) }
            query.lng?.let { addQueryParameter("lng", it.toString()) }
            query.distanceKm?.let { addQueryParameter("distance", it.toString()) }
            query.fromDate?.let { addQueryParameter("fromDate", it) }
            query.toDate?.let { addQueryParameter("toDate", it) }
            query.hashtag?.let { addQueryParameter("hashtag", it) }
            query.userId?.let { addQueryParameter("userId", it) }
            addQueryParameter("sort", query.sort.wireValue)
            addQueryParameter("order", query.order.wireValue)
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun list(accessToken: String, query: PostFeedQuery): PostsPage =
        executeAndDecode<PostsListDto, PostsPage>(listRequest(accessToken, query)) { it.toDomain() }

    private fun bookmarksUrl(): HttpUrl =
        "${BuildConfig.API_BASE_URL}/v1/profile/bookmarks/posts".toHttpUrl()

    internal fun bookmarksRequest(accessToken: String, query: BookmarksQuery): Request {
        val url = bookmarksUrl().newBuilder()
            .addQueryParameter("sort", query.sort.wireValue)
            .addQueryParameter("order", query.order.wireValue)
            .addQueryParameter("limit", query.limit.toString())
            .addQueryParameter("offset", query.offset.toString())
            .build()
        return baseRequest(accessToken).url(url).get().build()
    }

    /** Current user's bookmarked posts (`published`, not effectively expired). */
    suspend fun listBookmarks(accessToken: String, query: BookmarksQuery): PostsPage =
        executeAndDecode<PostsListDto, PostsPage>(bookmarksRequest(accessToken, query)) { it.toDomain() }

    internal fun getRequest(accessToken: String, postId: String): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).build())
            .get()
            .build()

    suspend fun get(accessToken: String, postId: String): Post =
        executeAndDecode<PostDto, Post>(getRequest(accessToken, postId)) {
            it.toDomain() ?: throw PostsError.Server(200, "Malformed response")
        }

    internal fun createRequest(accessToken: String, payload: CreatePostRequest): Request =
        baseRequest(accessToken)
            .url(postsUrl())
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun create(accessToken: String, payload: CreatePostRequest): Post =
        executeAndDecode<PostDto, Post>(createRequest(accessToken, payload)) {
            it.toDomain() ?: throw PostsError.Server(201, "Malformed response")
        }

    internal fun updateRequest(accessToken: String, postId: String, payload: UpdatePostRequest): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).build())
            .put(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    /** Full content update of a published post (author only) via `PUT /posts/{id}`. */
    suspend fun update(accessToken: String, postId: String, payload: UpdatePostRequest): Post =
        executeAndDecode<PostDto, Post>(updateRequest(accessToken, postId, payload)) {
            it.toDomain() ?: throw PostsError.Server(200, "Malformed response")
        }

    internal fun updateStatusRequest(accessToken: String, postId: String, status: PostStatus): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).build())
            .patch(
                json.encodeToString(PostStatusPatchRequest(status.wireValue))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    /** Archive (any type) or resolve (time-sensitive types) a published post. */
    suspend fun updateStatus(accessToken: String, postId: String, status: PostStatus): Post =
        executeAndDecode<PostDto, Post>(updateStatusRequest(accessToken, postId, status)) {
            it.toDomain() ?: throw PostsError.Server(200, "Malformed response")
        }

    internal fun deleteRequest(accessToken: String, postId: String): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).build())
            .delete()
            .build()

    suspend fun delete(accessToken: String, postId: String) {
        executeExpectingNoContent(deleteRequest(accessToken, postId))
    }

    internal fun commentsRequest(accessToken: String, postId: String, limit: Int, offset: Int): Request {
        val url = postsUrl().newBuilder()
            .addPathSegment(postId)
            .addPathSegment("comments")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun comments(accessToken: String, postId: String, limit: Int = 20, offset: Int = 0): CommentsPage =
        executeAndDecode<CommentsListDto, CommentsPage>(
            commentsRequest(accessToken, postId, limit, offset),
        ) { it.toDomain() }

    internal fun addCommentRequest(accessToken: String, postId: String, text: String): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).addPathSegment("comments").build())
            .post(json.encodeToString(CommentRequest(text)).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun addComment(accessToken: String, postId: String, text: String): PostComment =
        executeAndDecode<CommentDto, PostComment>(addCommentRequest(accessToken, postId, text)) {
            it.toDomain()
        }

    internal fun updateCommentRequest(
        accessToken: String,
        postId: String,
        commentId: String,
        text: String,
    ): Request =
        baseRequest(accessToken)
            .url(
                postsUrl().newBuilder()
                    .addPathSegment(postId)
                    .addPathSegment("comments")
                    .addPathSegment(commentId)
                    .build(),
            )
            .put(json.encodeToString(CommentRequest(text)).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun updateComment(
        accessToken: String,
        postId: String,
        commentId: String,
        text: String,
    ): PostComment =
        executeAndDecode<CommentDto, PostComment>(
            updateCommentRequest(accessToken, postId, commentId, text),
        ) { it.toDomain() }

    internal fun deleteCommentRequest(accessToken: String, postId: String, commentId: String): Request =
        baseRequest(accessToken)
            .url(
                postsUrl().newBuilder()
                    .addPathSegment(postId)
                    .addPathSegment("comments")
                    .addPathSegment(commentId)
                    .build(),
            )
            .delete()
            .build()

    suspend fun deleteComment(accessToken: String, postId: String, commentId: String) {
        executeExpectingNoContent(deleteCommentRequest(accessToken, postId, commentId))
    }

    internal fun reactionRequest(
        accessToken: String,
        postId: String,
        reaction: ReactionType,
        add: Boolean,
    ): Request {
        val url = postsUrl().newBuilder()
            .addPathSegment(postId)
            .addPathSegment("reactions")
            .addPathSegment(reaction.wireValue)
            .build()
        val builder = baseRequest(accessToken).url(url)
        return if (add) {
            builder.put(ByteArray(0).toRequestBody(null)).build()
        } else {
            builder.delete().build()
        }
    }

    suspend fun addReaction(accessToken: String, postId: String, reaction: ReactionType): ReactionSummary =
        executeAndDecode<ReactionSummaryDto, ReactionSummary>(
            reactionRequest(accessToken, postId, reaction, add = true),
        ) { it.toDomain() }

    suspend fun removeReaction(accessToken: String, postId: String, reaction: ReactionType) {
        executeExpectingNoContent(reactionRequest(accessToken, postId, reaction, add = false))
    }

    internal fun bookmarkRequest(accessToken: String, postId: String, add: Boolean): Request {
        val url = postsUrl().newBuilder()
            .addPathSegment(postId)
            .addPathSegment("bookmark")
            .build()
        val builder = baseRequest(accessToken).url(url)
        return if (add) {
            builder.put(ByteArray(0).toRequestBody(null)).build()
        } else {
            builder.delete().build()
        }
    }

    suspend fun addBookmark(accessToken: String, postId: String) {
        execute(bookmarkRequest(accessToken, postId, add = true)).use { response ->
            if (!response.isSuccessful) throw response.toPostsError()
        }
    }

    suspend fun removeBookmark(accessToken: String, postId: String) {
        executeExpectingNoContent(bookmarkRequest(accessToken, postId, add = false))
    }

    internal fun validityVoteRequest(accessToken: String, postId: String, vote: ValidityVoteType): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).addPathSegment("validity-vote").build())
            .put(
                json.encodeToString(ValidityVoteRequestDto(vote.wireValue))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    suspend fun castValidityVote(
        accessToken: String,
        postId: String,
        vote: ValidityVoteType,
    ): ValidityVoteResult =
        executeAndDecode<ValidityVoteResponseDto, ValidityVoteResult>(
            validityVoteRequest(accessToken, postId, vote),
        ) { it.toDomain() }

    internal fun reportRequest(
        accessToken: String,
        postId: String,
        reason: ReportReason,
        details: String?,
    ): Request =
        baseRequest(accessToken)
            .url(postsUrl().newBuilder().addPathSegment(postId).addPathSegment("reports").build())
            .post(
                json.encodeToString(
                    PostReportRequest(reason = reason.wireValue, details = details?.takeIf { it.isNotBlank() }),
                ).toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    /** Report a post for moderation (`POST /posts/{id}/reports`). */
    suspend fun report(accessToken: String, postId: String, reason: ReportReason, details: String?) {
        execute(reportRequest(accessToken, postId, reason, details)).use { response ->
            if (!response.isSuccessful) throw response.toPostsError()
        }
    }

    private fun baseRequest(accessToken: String): Request.Builder =
        Request.Builder()
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")

    private suspend inline fun <reified DtoT, DomainT> executeAndDecode(
        request: Request,
        crossinline toDomain: (DtoT) -> DomainT,
    ): DomainT {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toPostsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw PostsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toPostsError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(PostsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toPostsError(): PostsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> PostsError.AuthenticationRequired(detail)
            403 -> PostsError.Forbidden(detail)
            404 -> PostsError.NotFound(detail)
            409 -> PostsError.VoteConflict(detail)
            429 -> PostsError.RateLimited(detail)
            400, 422 -> PostsError.Validation(detail)
            else -> PostsError.Server(code, detail)
        }
    }
}

sealed class PostsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : PostsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : PostsError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : PostsError(detail ?: "Forbidden")
    class NotFound(detail: String?) : PostsError(detail ?: "Post not found")
    class VoteConflict(detail: String?) : PostsError(detail ?: "Vote already cast")
    class RateLimited(detail: String?) : PostsError(detail ?: "Too many requests")
    class Validation(detail: String?) : PostsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : PostsError(detail ?: "Server error ($statusCode)")
}
