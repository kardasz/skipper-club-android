package app.skipperclub.data

import app.skipperclub.BuildConfig
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class GeocodedLocation(
    val name: String?,
    val formattedAddress: String,
    val coordinates: PostCoordinates,
) {
    /** Short label preferred for `locationName` (place name when available). */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: formattedAddress
}

/**
 * Client for `GET /v1/geocoder/search` — name/address search used by the post
 * wizard to resolve `locationName` + `coordinates` in a single call.
 */
object GeocoderApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    internal fun searchRequest(accessToken: String, query: String, limit: Int = 5): Request {
        val url = "${BuildConfig.API_BASE_URL}/v1/geocoder/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", limit.toString())
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    suspend fun search(accessToken: String, query: String, limit: Int = 5): List<GeocodedLocation> {
        execute(searchRequest(accessToken, query, limit)).use { response ->
            if (!response.isSuccessful) throw response.toGeocoderError()
            return decodeResponse(response.body.string(), response.code)
        }
    }

    internal fun decodeResponse(payload: String, statusCode: Int = 200): List<GeocodedLocation> =
        try {
            json.decodeFromString<GeocoderSearchResponseDto>(payload).data.map { it.toDomain() }
        } catch (_: SerializationException) {
            throw GeocoderError.Server(statusCode, "Malformed response")
        }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(GeocoderError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toGeocoderError(): GeocoderError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        return GeocoderError.Server(code, problem?.detail ?: problem?.title)
    }
}

sealed class GeocoderError(message: String) : Exception(message) {
    class Network(cause: Throwable) : GeocoderError(cause.message ?: "Network error")
    class Server(val statusCode: Int, detail: String?) : GeocoderError(detail ?: "Server error ($statusCode)")
}

@Serializable
internal data class GeocoderSearchResponseDto(
    val data: List<GeocoderLocationDto> = emptyList(),
)

@Serializable
internal data class GeocoderLocationDto(
    val name: String? = null,
    val formattedAddress: String,
    val coordinates: CoordinatesDto,
) {
    fun toDomain(): GeocodedLocation =
        GeocodedLocation(
            name = name,
            formattedAddress = formattedAddress,
            coordinates = coordinates.toDomain(),
        )
}
