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

data class Region(
    val code: String,
    val localizedName: String,
    val localizedParents: List<String>,
    val level: Int,
)

/**
 * Client for `GET /v1/regions` (public, no auth). Returns a flat, UI-ready list
 * localized via `Accept-Language`, sorted by popularity by default.
 */
object RegionsApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    internal fun listRequest(): Request {
        val url = "${BuildConfig.API_BASE_URL}/v1/regions".toHttpUrl()
            .newBuilder()
            .addQueryParameter("sort", "popularity")
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()
    }

    suspend fun list(): List<Region> {
        execute(listRequest()).use { response ->
            if (!response.isSuccessful) throw response.toRegionsError()
            return decodeResponse(response.body.string(), response.code)
        }
    }

    internal fun decodeResponse(payload: String, statusCode: Int = 200): List<Region> =
        try {
            json.decodeFromString<RegionsListDto>(payload).regions.map { it.toDomain() }
        } catch (_: SerializationException) {
            throw RegionsError.Server(statusCode, "Malformed response")
        }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(RegionsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toRegionsError(): RegionsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        return RegionsError.Server(code, problem?.detail ?: problem?.title)
    }
}

sealed class RegionsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : RegionsError(cause.message ?: "Network error")
    class Server(val statusCode: Int, detail: String?) : RegionsError(detail ?: "Server error ($statusCode)")
}

@Serializable
internal data class RegionsListDto(
    val regions: List<RegionFlatItemDto> = emptyList(),
)

@Serializable
internal data class RegionFlatItemDto(
    val code: String,
    val localizedName: String,
    val localizedParents: List<String> = emptyList(),
    val level: Int = 0,
) {
    fun toDomain(): Region =
        Region(
            code = code,
            localizedName = localizedName,
            localizedParents = localizedParents,
            level = level,
        )
}
