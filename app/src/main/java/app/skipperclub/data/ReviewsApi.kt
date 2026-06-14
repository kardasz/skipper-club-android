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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
 * Client for the blind-review endpoints (`docs/api/reviews/index.md`). Modeled on
 * [CruisesApi] (raw OkHttp + manual RFC 7807 / NestJS error mapping) to stay
 * consistent until the codebase grows enough to justify Retrofit + DI.
 */
object ReviewsApi {
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

    private fun cruiseReviewsUrl(cruiseId: String): HttpUrl =
        "${BuildConfig.API_BASE_URL}/v1/cruises".toHttpUrl().newBuilder()
            .addPathSegment(cruiseId)
            .addPathSegment("reviews")
            .build()

    internal fun listRequest(accessToken: String, cruiseId: String, limit: Int, offset: Int): Request {
        val url = cruiseReviewsUrl(cruiseId).newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        return baseRequest(accessToken).url(url).get().build()
    }

    suspend fun listCruiseReviews(
        accessToken: String,
        cruiseId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): ReviewsPage =
        executeAndDecode<ReviewsListDto, ReviewsPage>(
            listRequest(accessToken, cruiseId, limit, offset),
        ) { it.toDomain() }

    internal fun createRequest(accessToken: String, cruiseId: String, payload: CreateReviewPayload): Request =
        baseRequest(accessToken)
            .url(cruiseReviewsUrl(cruiseId))
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

    suspend fun createReview(
        accessToken: String,
        cruiseId: String,
        payload: CreateReviewPayload,
    ): Review =
        executeAndDecode<ReviewDto, Review>(createRequest(accessToken, cruiseId, payload)) {
            it.toDomain() ?: throw ReviewsError.Server(201, "Malformed response")
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
            if (!response.isSuccessful) throw response.toReviewsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw ReviewsError.Server(response.code, "Malformed response")
            }
            return toDomain(dto)
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(ReviewsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toReviewsError(): ReviewsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title ?: extractNestMessage(payload)
        val type = problem?.type
        return when (code) {
            401 -> ReviewsError.AuthenticationRequired(detail)
            403 -> ReviewsError.Forbidden(detail)
            404 -> ReviewsError.NotFound(detail)
            429 -> ReviewsError.RateLimited(detail)
            422, 409, 400 -> when {
                type?.endsWith("/cruise-not-completed") == true -> ReviewsError.CruiseNotCompleted(detail)
                type?.endsWith("/review-already-exists") == true -> ReviewsError.AlreadyReviewed(detail)
                type?.endsWith("/cannot-review-self") == true -> ReviewsError.CannotReviewSelf(detail)
                else -> ReviewsError.Validation(detail)
            }
            else -> ReviewsError.Server(code, detail)
        }
    }

    /**
     * Pulls a human-readable message out of a NestJS error body
     * (`{ "message": "..." }`), where `message` is a string or array of strings.
     */
    private fun extractNestMessage(payload: String): String? =
        runCatching {
            val message = (json.parseToJsonElement(payload) as? JsonObject)?.get("message")
            when (message) {
                is JsonArray ->
                    message.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" ")

                is JsonPrimitive -> message.contentOrNull
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
}

sealed class ReviewsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : ReviewsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : ReviewsError(detail ?: "Authentication required")
    class Forbidden(detail: String?) : ReviewsError(detail ?: "Forbidden")
    class NotFound(detail: String?) : ReviewsError(detail ?: "Not found")
    class CruiseNotCompleted(detail: String?) : ReviewsError(detail ?: "Cruise not completed")
    class AlreadyReviewed(detail: String?) : ReviewsError(detail ?: "Already reviewed")
    class CannotReviewSelf(detail: String?) : ReviewsError(detail ?: "Cannot review yourself")
    class RateLimited(detail: String?) : ReviewsError(detail ?: "Too many requests")
    class Validation(detail: String?) : ReviewsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : ReviewsError(detail ?: "Server error ($statusCode)")
}
