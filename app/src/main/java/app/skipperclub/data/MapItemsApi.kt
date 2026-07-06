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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Client for `/v1/map/items`. The endpoint performs viewport filtering and
 * clustering server-side; the app sends only the currently visible bounds.
 */
object MapItemsApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    internal fun listRequest(
        accessToken: String,
        bounds: MapViewportBounds,
        postContains: Set<PostContainsFilter> = emptySet(),
    ): Request {
        val url = "${BuildConfig.API_BASE_URL}/v1/map/items".toHttpUrl()
            .newBuilder()
            .addQueryParameter("north", bounds.north.toString())
            .addQueryParameter("south", bounds.south.toString())
            .addQueryParameter("east", bounds.east.toString())
            .addQueryParameter("west", bounds.west.toString())
            .apply {
                postContains.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter(
                        "postContains",
                        it.sortedBy { c -> c.ordinal }.joinToString(",") { c -> c.wireValue },
                    )
                }
            }
            .build()

        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    suspend fun list(
        accessToken: String,
        bounds: MapViewportBounds,
        postContains: Set<PostContainsFilter> = emptySet(),
    ): MapItemsResponse {
        execute(listRequest(accessToken, bounds, postContains)).use { response ->
            if (!response.isSuccessful) throw response.toMapItemsError()
            return decodeResponse(response.body.string(), response.code)
        }
    }

    internal fun decodeResponse(payload: String, statusCode: Int = 200): MapItemsResponse =
        try {
            json.decodeFromString<MapItemsResponseDto>(payload).toDomain()
        } catch (_: SerializationException) {
            throw MapItemsError.Server(statusCode, "Malformed response")
        }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(MapItemsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toMapItemsError(): MapItemsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401, 403 -> MapItemsError.AuthenticationRequired(detail)
            400, 422 -> MapItemsError.Validation(detail)
            429 -> MapItemsError.RateLimited(detail)
            else -> MapItemsError.Server(code, detail)
        }
    }
}

sealed class MapItemsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : MapItemsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : MapItemsError(detail ?: "Authentication required")
    class RateLimited(detail: String?) : MapItemsError(detail ?: "Too many requests")
    class Validation(detail: String?) : MapItemsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : MapItemsError(detail ?: "Server error ($statusCode)")
}
