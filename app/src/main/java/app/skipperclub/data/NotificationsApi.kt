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
 * Client for the `/v1/notifications` endpoints backing the notification center.
 * Modeled on [ChatsApi] (raw OkHttp + manual RFC 7807 mapping) to stay consistent
 * until the codebase grows enough to justify Retrofit + DI (see CLAUDE.md §Networking).
 */
object NotificationsApi {
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

    private fun notificationsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/notifications".toHttpUrl()

    internal fun listRequest(accessToken: String, query: NotificationListQuery): Request {
        val url = notificationsUrl().newBuilder().apply {
            query.status?.let { addQueryParameter("status", it.wireValue) }
            query.sourceType?.let { addQueryParameter("sourceType", it.wireValue) }
            addQueryParameter("sort", "createdAt")
            addQueryParameter("order", query.order.wireValue)
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun list(accessToken: String, query: NotificationListQuery): NotificationsPage =
        executeAndDecode<NotificationsListDto, NotificationsPage>(listRequest(accessToken, query)) { it.toDomain() }

    internal fun unreadCountRequest(accessToken: String): Request =
        baseRequest(accessToken)
            .url(notificationsUrl().newBuilder().addPathSegment("unread-count").build())
            .get()
            .build()

    suspend fun unreadCount(accessToken: String): Int =
        executeAndDecode<UnreadNotificationCountDto, Int>(unreadCountRequest(accessToken)) { it.count }

    internal fun updateStatusRequest(accessToken: String, notificationId: String, status: NotificationStatus): Request =
        baseRequest(accessToken)
            .url(notificationsUrl().newBuilder().addPathSegment(notificationId).build())
            .patch(
                json.encodeToString(UpdateNotificationRequest(status.wireValue))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    suspend fun updateStatus(accessToken: String, notificationId: String, status: NotificationStatus) {
        executeExpectingNoContent(updateStatusRequest(accessToken, notificationId, status))
    }

    internal fun deleteRequest(accessToken: String, notificationId: String): Request =
        baseRequest(accessToken)
            .url(notificationsUrl().newBuilder().addPathSegment(notificationId).build())
            .delete()
            .build()

    suspend fun delete(accessToken: String, notificationId: String) {
        executeExpectingNoContent(deleteRequest(accessToken, notificationId))
    }

    internal fun bulkActionRequest(accessToken: String, body: NotificationActionsRequest): Request =
        baseRequest(accessToken)
            .url(notificationsUrl().newBuilder().addPathSegment("actions").build())
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    /** Marks the given notifications read (or every notification when [all] is true). */
    suspend fun markRead(accessToken: String, notificationIds: List<String>? = null, all: Boolean = false) {
        val body = NotificationActionsRequest(
            action = NotificationBulkAction.MarkRead.wireValue,
            notificationIds = notificationIds,
            all = if (all) true else null,
        )
        executeExpectingNoContent(bulkActionRequest(accessToken, body))
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
            if (!response.isSuccessful) throw response.toNotificationsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw NotificationsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toNotificationsError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(NotificationsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toNotificationsError(): NotificationsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> NotificationsError.AuthenticationRequired(detail)
            404 -> NotificationsError.NotFound(detail)
            429 -> NotificationsError.RateLimited(detail)
            400, 422 -> NotificationsError.Validation(detail)
            else -> NotificationsError.Server(code, detail)
        }
    }
}

sealed class NotificationsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : NotificationsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : NotificationsError(detail ?: "Authentication required")
    class NotFound(detail: String?) : NotificationsError(detail ?: "Notification not found")
    class RateLimited(detail: String?) : NotificationsError(detail ?: "Too many requests")
    class Validation(detail: String?) : NotificationsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : NotificationsError(detail ?: "Server error ($statusCode)")
}
