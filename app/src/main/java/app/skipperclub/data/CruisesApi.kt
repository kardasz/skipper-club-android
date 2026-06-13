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
 * Client for the `/v1/cruises` endpoints (CRUD + participants). Modeled on
 * [PostsApi] (raw OkHttp + manual RFC 7807 mapping) to stay consistent until the
 * codebase grows enough to justify Retrofit + DI (see CLAUDE.md §Networking).
 */
object CruisesApi {
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

    private fun cruisesUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/cruises".toHttpUrl()

    internal fun listRequest(accessToken: String, query: CruiseListQuery): Request {
        val url = cruisesUrl().newBuilder().apply {
            addQueryParameter("scope", query.scope.wireValue)
            query.state?.let { addQueryParameter("state", it.wireValue) }
            query.search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it) }
            query.fromDate?.let { addQueryParameter("fromDate", it) }
            query.toDate?.let { addQueryParameter("toDate", it) }
            query.type?.let { addQueryParameter("type", it.wireValue) }
            query.vesselType?.let { addQueryParameter("vesselType", it.wireValue) }
            addQueryParameter("sort", query.sort.wireValue)
            addQueryParameter("order", query.order.wireValue)
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun list(accessToken: String, query: CruiseListQuery): CruisesPage =
        executeAndDecode<CruisesListDto, CruisesPage>(listRequest(accessToken, query)) { it.toDomain() }

    internal fun getRequest(accessToken: String, cruiseId: String): Request =
        baseRequest(accessToken)
            .url(cruisesUrl().newBuilder().addPathSegment(cruiseId).build())
            .get()
            .build()

    suspend fun get(accessToken: String, cruiseId: String): Cruise =
        executeAndDecode<CruiseDto, Cruise>(getRequest(accessToken, cruiseId)) {
            it.toDomain() ?: throw CruisesError.Server(200, "Malformed response")
        }

    internal fun createRequest(accessToken: String, payload: CruisePayload): Request =
        baseRequest(accessToken)
            .url(cruisesUrl())
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun create(accessToken: String, payload: CruisePayload): Cruise =
        executeAndDecode<CruiseDto, Cruise>(createRequest(accessToken, payload)) {
            it.toDomain() ?: throw CruisesError.Server(201, "Malformed response")
        }

    internal fun updateRequest(accessToken: String, cruiseId: String, payload: CruisePayload): Request =
        baseRequest(accessToken)
            .url(cruisesUrl().newBuilder().addPathSegment(cruiseId).build())
            .put(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun update(accessToken: String, cruiseId: String, payload: CruisePayload): Cruise =
        executeAndDecode<CruiseDto, Cruise>(updateRequest(accessToken, cruiseId, payload)) {
            it.toDomain() ?: throw CruisesError.Server(200, "Malformed response")
        }

    internal fun deleteRequest(accessToken: String, cruiseId: String): Request =
        baseRequest(accessToken)
            .url(cruisesUrl().newBuilder().addPathSegment(cruiseId).build())
            .delete()
            .build()

    suspend fun delete(accessToken: String, cruiseId: String) {
        executeExpectingNoContent(deleteRequest(accessToken, cruiseId))
    }

    internal fun participantsRequest(accessToken: String, cruiseId: String, limit: Int, offset: Int): Request {
        val url = cruisesUrl().newBuilder()
            .addPathSegment(cruiseId)
            .addPathSegment("participants")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun participants(
        accessToken: String,
        cruiseId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): CruiseParticipantsPage =
        executeAndDecode<CruiseParticipantsListDto, CruiseParticipantsPage>(
            participantsRequest(accessToken, cruiseId, limit, offset),
        ) { it.toDomain() }

    internal fun addParticipantRequest(accessToken: String, cruiseId: String, userId: String): Request =
        baseRequest(accessToken)
            .url(cruisesUrl().newBuilder().addPathSegment(cruiseId).addPathSegment("participants").build())
            .post(
                json.encodeToString(CruiseParticipantCreateRequest(userId))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    /**
     * Join request (own userId) or invitation (other userId, organizer only) —
     * the backend derives `pending` vs `invited` from who is being added.
     */
    suspend fun addParticipant(accessToken: String, cruiseId: String, userId: String): CruiseParticipant =
        executeAndDecode<CruiseParticipantDto, CruiseParticipant>(
            addParticipantRequest(accessToken, cruiseId, userId),
        ) { it.toDomain() ?: throw CruisesError.Server(201, "Malformed response") }

    internal fun updateParticipantStateRequest(
        accessToken: String,
        cruiseId: String,
        participantId: String,
        state: CruiseParticipantState,
    ): Request =
        baseRequest(accessToken)
            .url(
                cruisesUrl().newBuilder()
                    .addPathSegment(cruiseId)
                    .addPathSegment("participants")
                    .addPathSegment(participantId)
                    .build(),
            )
            .patch(
                json.encodeToString(CruiseParticipantStateUpdateRequest(state.wireValue))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Content-Type", "application/json")
            .build()

    suspend fun updateParticipantState(
        accessToken: String,
        cruiseId: String,
        participantId: String,
        state: CruiseParticipantState,
    ): CruiseParticipant =
        executeAndDecode<CruiseParticipantDto, CruiseParticipant>(
            updateParticipantStateRequest(accessToken, cruiseId, participantId, state),
        ) { it.toDomain() ?: throw CruisesError.Server(200, "Malformed response") }

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
            if (!response.isSuccessful) throw response.toCruisesError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw CruisesError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toCruisesError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(CruisesError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toCruisesError(): CruisesError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> CruisesError.AuthenticationRequired(detail)
            403 -> CruisesError.Forbidden(detail)
            404 -> CruisesError.NotFound(detail)
            409 ->
                if (problem?.type?.endsWith("/cruise-full") == true) {
                    CruisesError.CruiseFull(detail)
                } else {
                    CruisesError.Conflict(detail)
                }

            429 -> CruisesError.RateLimited(detail)
            400, 422 -> CruisesError.Validation(detail)
            else -> CruisesError.Server(code, detail)
        }
    }
}

sealed class CruisesError(message: String) : Exception(message) {
    class Network(cause: Throwable) : CruisesError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : CruisesError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : CruisesError(detail ?: "Forbidden")
    class NotFound(detail: String?) : CruisesError(detail ?: "Cruise not found")
    class CruiseFull(detail: String?) : CruisesError(detail ?: "Cruise is full")
    class Conflict(detail: String?) : CruisesError(detail ?: "Conflict")
    class RateLimited(detail: String?) : CruisesError(detail ?: "Too many requests")
    class Validation(detail: String?) : CruisesError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : CruisesError(detail ?: "Server error ($statusCode)")
}
