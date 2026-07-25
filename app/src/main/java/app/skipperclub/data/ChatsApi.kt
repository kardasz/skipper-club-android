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
 * Client for the `/v1/chats` and `/v1/users` endpoints backing the messages
 * feature. Modeled on [PostsApi] (raw OkHttp + manual RFC 7807 mapping) to stay
 * consistent until the codebase grows enough to justify Retrofit + DI
 * (see CLAUDE.md §Networking).
 */
object ChatsApi {
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

    private fun chatsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/chats".toHttpUrl()

    private fun usersUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/users".toHttpUrl()

    internal fun listChatsRequest(accessToken: String, query: ChatListQuery): Request {
        val url = chatsUrl().newBuilder().apply {
            query.type?.let { addQueryParameter("type", it.wireValue) }
            query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it) }
            addQueryParameter("sort", query.sort.wireValue)
            addQueryParameter("order", query.order.wireValue)
            addQueryParameter("limit", query.limit.toString())
            // Keyset paging: the opaque `cursor` addresses a fixed `(updatedAt, id)` position, so
            // a chat bumped to the top mid-walk cannot shift the window the way `offset` did. No
            // chat-list request sends `offset` any more — it is deprecated server-side and
            // mutually exclusive with `cursor` (even `offset=0` alongside it is a 400).
            query.cursor?.let { addQueryParameter("cursor", it) }
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun listChats(accessToken: String, query: ChatListQuery): ChatsPage =
        executeAndDecode<ChatsListDto, ChatsPage>(listChatsRequest(accessToken, query)) { it.toDomain() }

    internal fun createChatRequest(accessToken: String, payload: CreateChatRequest): Request =
        baseRequest(accessToken)
            .url(chatsUrl())
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun createChat(accessToken: String, payload: CreateChatRequest): Chat =
        executeAndDecode<ChatDto, Chat>(createChatRequest(accessToken, payload)) {
            it.toDomain() ?: throw ChatsError.Server(201, "Malformed response")
        }

    internal fun getChatRequest(accessToken: String, chatId: String): Request =
        baseRequest(accessToken)
            .url(chatsUrl().newBuilder().addPathSegment(chatId).build())
            .get()
            .build()

    suspend fun getChat(accessToken: String, chatId: String): Chat =
        executeAndDecode<ChatDto, Chat>(getChatRequest(accessToken, chatId)) {
            it.toDomain() ?: throw ChatsError.Server(200, "Malformed response")
        }

    internal fun deleteChatRequest(accessToken: String, chatId: String): Request =
        baseRequest(accessToken)
            .url(chatsUrl().newBuilder().addPathSegment(chatId).build())
            .delete()
            .build()

    /** Hides the chat from the current user's list (Messenger-style delete). */
    suspend fun deleteChat(accessToken: String, chatId: String) {
        executeExpectingNoContent(deleteChatRequest(accessToken, chatId))
    }

    internal fun listMessagesRequest(
        accessToken: String,
        chatId: String,
        limit: Int,
        before: String?,
        order: SortOrder,
    ): Request {
        val url = chatsUrl().newBuilder()
            .addPathSegment(chatId)
            .addPathSegment("messages")
            .addQueryParameter("order", order.wireValue)
            .addQueryParameter("limit", limit.toString())
            // Keyset paging: the `before` cursor addresses a fixed `(createdAt, id)`
            // position, so it never skips a row the way `offset` does over a growing
            // chat (task_shared_catchup_contract.md). No message request sends `offset`.
            .apply { before?.let { addQueryParameter("before", it) } }
            .build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun listMessages(
        accessToken: String,
        chatId: String,
        limit: Int = 20,
        before: String? = null,
        order: SortOrder = SortOrder.Desc,
    ): MessagesPage =
        executeAndDecode<MessagesListDto, MessagesPage>(
            listMessagesRequest(accessToken, chatId, limit, before, order),
        ) { it.toDomain() }

    internal fun sendMessageRequest(
        accessToken: String,
        chatId: String,
        text: String,
        clientMessageId: String? = null,
    ): Request =
        baseRequest(accessToken)
            .url(chatsUrl().newBuilder().addPathSegment(chatId).addPathSegment("messages").build())
            .post(
                json.encodeToString(SendMessageRequest(text, clientMessageId))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    /**
     * [clientMessageId] is an optional idempotency key: a repeat POST with the same value returns
     * the existing message (same `id`) instead of creating a duplicate. Callers generate one UUID
     * per logical message and reuse it on retries.
     */
    suspend fun sendMessage(
        accessToken: String,
        chatId: String,
        text: String,
        clientMessageId: String? = null,
    ): ChatMessage =
        executeAndDecode<ChatMessageDto, ChatMessage>(
            sendMessageRequest(accessToken, chatId, text, clientMessageId),
        ) {
            it.toDomain()
        }

    internal fun bulkActionRequest(
        accessToken: String,
        action: ChatBulkAction,
        chatIds: List<String>,
    ): Request =
        baseRequest(accessToken)
            .url(chatsUrl().newBuilder().addPathSegment("actions").build())
            .post(
                json.encodeToString(ChatBulkActionRequest(action.wireValue, chatIds))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    /** `mark-read` marks every message in the chats as read; `delete` hides them. */
    suspend fun bulkAction(accessToken: String, action: ChatBulkAction, chatIds: List<String>) {
        executeExpectingNoContent(bulkActionRequest(accessToken, action, chatIds))
    }

    internal fun unreadCountRequest(accessToken: String): Request =
        baseRequest(accessToken)
            .url(chatsUrl().newBuilder().addPathSegment("unread-count").build())
            .get()
            .build()

    suspend fun unreadCount(accessToken: String): Int =
        executeAndDecode<UnreadCountDto, Int>(unreadCountRequest(accessToken)) { it.totalUnread }

    internal fun presenceRequest(accessToken: String): Request =
        baseRequest(accessToken)
            .url(chatsUrl().newBuilder().addPathSegment("presence").build())
            .get()
            .build()

    /**
     * Presence snapshot of the caller's chat co-participants, keyed by userId, for seeding
     * [PresenceStore] after every WS (re)connect.
     */
    suspend fun presence(accessToken: String): Map<String, UserPresence> =
        executeAndDecode<ChatPresenceDto, Map<String, UserPresence>>(presenceRequest(accessToken)) { it.toDomain() }

    internal fun searchUsersRequest(accessToken: String, query: UserSearchQuery): Request {
        val url = usersUrl().newBuilder().apply {
            query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it) }
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    /** Community member search used by the new-chat participant picker. */
    suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage =
        executeAndDecode<UsersListDto, UsersPage>(searchUsersRequest(accessToken, query)) { it.toDomain() }

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
            if (!response.isSuccessful) throw response.toChatsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw ChatsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toChatsError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(ChatsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toChatsError(): ChatsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> ChatsError.AuthenticationRequired(detail)
            403 -> ChatsError.Forbidden(detail)
            404 -> ChatsError.NotFound(detail)
            429 -> ChatsError.RateLimited(detail)
            400, 422 -> ChatsError.Validation(detail)
            else -> ChatsError.Server(code, detail)
        }
    }
}

sealed class ChatsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : ChatsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : ChatsError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : ChatsError(detail ?: "Forbidden")
    class NotFound(detail: String?) : ChatsError(detail ?: "Chat not found")
    class RateLimited(detail: String?) : ChatsError(detail ?: "Too many requests")
    class Validation(detail: String?) : ChatsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : ChatsError(detail ?: "Server error ($statusCode)")
}
