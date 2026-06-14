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
 * Client for the admin-only direct-CRUD `/v1/spots` endpoints backing the spots
 * management screen. Modeled on [InvitationsApi] (raw OkHttp + manual RFC 7807
 * mapping) to stay consistent with the other feature modules until the codebase
 * grows enough to justify Retrofit + DI (see CLAUDE.md §Networking).
 */
object SpotsApi {
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

    private fun spotsUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/spots".toHttpUrl()

    internal fun listRequest(accessToken: String, query: SpotListQuery): Request {
        val url = spotsUrl().newBuilder().apply {
            query.name?.takeIf { it.isNotBlank() }?.let { addQueryParameter("name", it) }
            addQueryParameter("limit", query.limit.toString())
            addQueryParameter("offset", query.offset.toString())
        }.build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun list(accessToken: String, query: SpotListQuery): SpotsPage =
        executeAndDecode<SpotsListDto, SpotsPage>(listRequest(accessToken, query)) { it.toDomain() }

    internal fun createRequest(accessToken: String, body: CreateSpotRequest): Request =
        baseRequest(accessToken)
            .url(spotsUrl())
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun create(accessToken: String, body: CreateSpotRequest): Spot =
        executeAndDecode<SpotDto, Spot>(createRequest(accessToken, body)) { it.toDomain() }

    internal fun updateRequest(accessToken: String, spotId: String, body: UpdateSpotAggregateRequest): Request =
        baseRequest(accessToken)
            .url(spotsUrl().newBuilder().addPathSegment(spotId).build())
            .patch(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun update(accessToken: String, spotId: String, body: UpdateSpotAggregateRequest): Spot =
        executeAndDecode<SpotDto, Spot>(updateRequest(accessToken, spotId, body)) { it.toDomain() }

    internal fun deleteRequest(accessToken: String, spotId: String): Request =
        baseRequest(accessToken)
            .url(spotsUrl().newBuilder().addPathSegment(spotId).build())
            .delete()
            .build()

    suspend fun delete(accessToken: String, spotId: String) {
        executeExpectingNoContent(deleteRequest(accessToken, spotId))
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
            if (!response.isSuccessful) throw response.toSpotsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw SpotsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun executeExpectingNoContent(request: Request) {
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toSpotsError()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(SpotsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toSpotsError(): SpotsError {
        val payload = body.string()
        if (code == 409) {
            val duplicate = runCatching {
                if (payload.isNotBlank()) json.decodeFromString<SpotDuplicateProblemDto>(payload) else null
            }.getOrNull()
            return SpotsError.Duplicate(
                detail = duplicate?.detail ?: duplicate?.title,
                nearbySpots = duplicate?.nearbySpots.orEmpty().map { it.toDomain() },
            )
        }
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> SpotsError.AuthenticationRequired(detail)
            403 -> SpotsError.Forbidden(detail)
            404 -> SpotsError.NotFound(detail)
            400, 422 -> SpotsError.Validation(detail)
            else -> SpotsError.Server(code, detail)
        }
    }
}

sealed class SpotsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : SpotsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : SpotsError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : SpotsError(detail ?: "Administrator access required")
    class NotFound(detail: String?) : SpotsError(detail ?: "Spot not found")
    class Duplicate(detail: String?, val nearbySpots: List<NearbySpot>) :
        SpotsError(detail ?: "A nearby spot already exists")
    class Validation(detail: String?) : SpotsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : SpotsError(detail ?: "Server error ($statusCode)")
}
