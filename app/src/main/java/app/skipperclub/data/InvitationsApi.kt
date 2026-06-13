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
 * Client for the admin-only `/v1/invitations` endpoints backing the invitations
 * screen. Modeled on [NotificationsApi] (raw OkHttp + manual RFC 7807 mapping) to
 * stay consistent with the other feature modules until the codebase grows enough
 * to justify Retrofit + DI (see CLAUDE.md §Networking).
 */
object InvitationsApi {
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

    private fun invitationsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/invitations".toHttpUrl()

    internal fun listRequest(accessToken: String, query: InvitationListQuery): Request {
        val url = invitationsUrl().newBuilder().apply {
            query.status?.let { addQueryParameter("status", it.wireValue) }
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun list(accessToken: String, query: InvitationListQuery): InvitationsPage =
        executeAndDecode<InvitationListDto, InvitationsPage>(listRequest(accessToken, query)) { it.toDomain() }

    internal fun sendRequest(accessToken: String, email: String): Request =
        baseRequest(accessToken)
            .url(invitationsUrl())
            .post(json.encodeToString(SendInvitationRequest(email)).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    /** Sends a fresh invitation to [email]; an existing active invitation is soft-deleted server-side. */
    suspend fun send(accessToken: String, email: String) {
        executeExpectingNoContent(sendRequest(accessToken, email))
    }

    internal fun deleteRequest(accessToken: String, invitationId: String): Request =
        baseRequest(accessToken)
            .url(invitationsUrl().newBuilder().addPathSegment(invitationId).build())
            .delete()
            .build()

    suspend fun delete(accessToken: String, invitationId: String) {
        executeExpectingNoContent(deleteRequest(accessToken, invitationId))
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
            if (!response.isSuccessful) throw response.toInvitationsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw InvitationsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toInvitationsError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(InvitationsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toInvitationsError(): InvitationsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> InvitationsError.AuthenticationRequired(detail)
            403 -> InvitationsError.Forbidden(detail)
            404 -> InvitationsError.NotFound(detail)
            409 -> InvitationsError.EmailAlreadyRegistered(detail)
            429 -> InvitationsError.RateLimited(detail)
            400, 422 -> InvitationsError.Validation(detail)
            else -> InvitationsError.Server(code, detail)
        }
    }
}

sealed class InvitationsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : InvitationsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : InvitationsError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : InvitationsError(detail ?: "Administrator access required")
    class NotFound(detail: String?) : InvitationsError(detail ?: "Invitation not found")
    class EmailAlreadyRegistered(detail: String?) : InvitationsError(detail ?: "Email already registered")
    class RateLimited(detail: String?) : InvitationsError(detail ?: "Too many requests")
    class Validation(detail: String?) : InvitationsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : InvitationsError(detail ?: "Server error ($statusCode)")
}
