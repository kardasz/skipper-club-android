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

/**
 * Client for the `/v1/friend-requests`, `/v1/friends` and `/v1/users` endpoints
 * backing the "Friends" screen. Modeled on [NotificationsApi] (raw OkHttp + manual
 * RFC 7807 mapping) to stay consistent until the codebase grows enough to justify
 * Retrofit + DI (see CLAUDE.md §Networking).
 */
object FriendsApi {
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

    private fun friendRequestsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/friend-requests".toHttpUrl()

    private fun friendsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/friends".toHttpUrl()

    private fun usersUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/users".toHttpUrl()

    // --- Friend requests ---

    internal fun listRequestsRequest(accessToken: String, query: FriendRequestListQuery): Request {
        val url = friendRequestsUrl().newBuilder().apply {
            query.state?.let { addQueryParameter("state", it.wireValue) }
            addQueryParameter("sort", "createdAt")
            addQueryParameter("order", "desc")
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun listFriendRequests(accessToken: String, query: FriendRequestListQuery): FriendRequestsPage =
        executeAndDecode<FriendRequestsListDto, FriendRequestsPage>(listRequestsRequest(accessToken, query)) {
            it.toDomain()
        }

    internal fun sendRequest(accessToken: String, userId: String): Request =
        baseRequest(accessToken)
            .url(friendRequestsUrl())
            .post(json.encodeToString(SendFriendRequestBody(userId)).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun sendFriendRequest(accessToken: String, userId: String): FriendRequest =
        executeAndDecode<FriendRequestDto, FriendRequest>(sendRequest(accessToken, userId)) { it.toDomain() }

    internal fun updateRequestRequest(
        accessToken: String,
        requestId: String,
        state: FriendRequestState,
    ): Request =
        baseRequest(accessToken)
            .url(friendRequestsUrl().newBuilder().addPathSegment(requestId).build())
            .patch(json.encodeToString(UpdateFriendRequestBody(state.wireValue)).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    /** Accept or reject a received request (`PATCH /friend-requests/{id}`). */
    suspend fun updateFriendRequest(
        accessToken: String,
        requestId: String,
        state: FriendRequestState,
    ): FriendRequest =
        executeAndDecode<FriendRequestDto, FriendRequest>(
            updateRequestRequest(accessToken, requestId, state),
        ) { it.toDomain() }

    internal fun cancelRequestRequest(accessToken: String, requestId: String): Request =
        baseRequest(accessToken)
            .url(friendRequestsUrl().newBuilder().addPathSegment(requestId).build())
            .delete()
            .build()

    /** Withdraw a request the current user sent (`DELETE /friend-requests/{id}`). */
    suspend fun cancelFriendRequest(accessToken: String, requestId: String) {
        executeExpectingNoContent(cancelRequestRequest(accessToken, requestId))
    }

    // --- Friends ---

    internal fun listFriendsRequest(accessToken: String, query: FriendListQuery): Request {
        val url = friendsUrl().newBuilder().apply {
            query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it) }
            addQueryParameter("sort", "name")
            addQueryParameter("order", "asc")
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun listFriends(accessToken: String, query: FriendListQuery): FriendsPage =
        executeAndDecode<FriendsListDto, FriendsPage>(listFriendsRequest(accessToken, query)) { it.toDomain() }

    internal fun removeFriendRequest(accessToken: String, friendId: String): Request =
        baseRequest(accessToken)
            .url(friendsUrl().newBuilder().addPathSegment(friendId).build())
            .delete()
            .build()

    /** Remove a friend; the friendship is dropped for both users (`DELETE /friends/{id}`). */
    suspend fun removeFriend(accessToken: String, friendId: String) {
        executeExpectingNoContent(removeFriendRequest(accessToken, friendId))
    }

    // --- User search (invite flow) ---

    internal fun searchUsersRequest(accessToken: String, query: FriendListQuery): Request {
        val url = usersUrl().newBuilder().apply {
            query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it) }
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    /** Community member search used by the "invite a friend" picker. */
    suspend fun searchUsers(accessToken: String, query: FriendListQuery): FriendsPage =
        executeAndDecode<FriendUserSearchListDto, FriendsPage>(searchUsersRequest(accessToken, query)) {
            it.toDomain()
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
            if (!response.isSuccessful) throw response.toFriendsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw FriendsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toFriendsError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(FriendsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toFriendsError(): FriendsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> FriendsError.AuthenticationRequired(detail)
            403 -> FriendsError.Forbidden(detail)
            404 -> FriendsError.NotFound(detail)
            409 -> FriendsError.Conflict(problem?.type, detail)
            429 -> FriendsError.RateLimited(detail)
            400, 422 -> FriendsError.Conflict(problem?.type, detail)
            else -> FriendsError.Server(code, detail)
        }
    }
}

sealed class FriendsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : FriendsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : FriendsError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : FriendsError(detail ?: "Forbidden")
    class NotFound(detail: String?) : FriendsError(detail ?: "Not found")

    /**
     * 4xx that the friend flow can usually surface verbatim: already friends, request
     * already exists, can't friend yourself, validation. [type] is the RFC 7807
     * `/errors/...` identifier when present so the UI can localize known cases.
     */
    class Conflict(val type: String?, detail: String?) : FriendsError(detail ?: "Request could not be completed")
    class RateLimited(detail: String?) : FriendsError(detail ?: "Too many requests")
    class Server(val statusCode: Int, detail: String?) : FriendsError(detail ?: "Server error ($statusCode)")
}
