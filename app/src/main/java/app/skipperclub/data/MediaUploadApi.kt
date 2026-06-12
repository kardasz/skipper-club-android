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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Serializable
data class PresignedUrlRequest(
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val width: Int? = null,
    val height: Int? = null,
)

data class PresignedUpload(
    val uploadUrl: String,
    val mediaId: String,
    val publicUrl: String,
)

data class UploadedMedia(
    val mediaId: String,
    val publicUrl: String,
)

/**
 * Client for the presigned-URL media upload flow (`POST /v1/media/presigned-url`,
 * `PUT <uploadUrl>`, `POST /v1/media/{id}/confirm-upload`). The recommended
 * pattern for mobile apps per docs/api/media/index.md.
 */
object MediaUploadApi {
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    internal fun presignedUrlRequest(accessToken: String, payload: PresignedUrlRequest): Request =
        Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/v1/media/presigned-url".toHttpUrl())
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")
            .build()

    suspend fun createPresignedUrl(accessToken: String, payload: PresignedUrlRequest): PresignedUpload {
        execute(presignedUrlRequest(accessToken, payload)).use { response ->
            if (!response.isSuccessful) throw response.toMediaUploadError()
            return try {
                json.decodeFromString<PresignedUrlResponseDto>(response.body.string()).toDomain()
            } catch (_: SerializationException) {
                throw MediaUploadError.Server(response.code, "Malformed response")
            }
        }
    }

    internal fun storageUploadRequest(uploadUrl: String, contentType: String, bytes: ByteArray): Request =
        Request.Builder()
            .url(uploadUrl)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()

    /** Direct PUT to storage; no API auth header (the URL itself is signed). */
    suspend fun uploadToStorage(uploadUrl: String, contentType: String, bytes: ByteArray) {
        execute(storageUploadRequest(uploadUrl, contentType, bytes)).use { response ->
            if (!response.isSuccessful) {
                throw MediaUploadError.Server(response.code, "Storage upload failed")
            }
        }
    }

    internal fun confirmUploadRequest(accessToken: String, mediaId: String): Request =
        Request.Builder()
            .url(
                "${BuildConfig.API_BASE_URL}/v1/media".toHttpUrl()
                    .newBuilder()
                    .addPathSegment(mediaId)
                    .addPathSegment("confirm-upload")
                    .build(),
            )
            .post(ByteArray(0).toRequestBody(null))
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .header("Authorization", "Bearer $accessToken")
            .build()

    suspend fun confirmUpload(accessToken: String, mediaId: String) {
        execute(confirmUploadRequest(accessToken, mediaId)).use { response ->
            if (!response.isSuccessful) throw response.toMediaUploadError()
        }
    }

    /** Full presigned flow: request URL → PUT bytes to storage → confirm. */
    suspend fun upload(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int? = null,
        height: Int? = null,
    ): UploadedMedia {
        val presigned = createPresignedUrl(
            accessToken,
            PresignedUrlRequest(
                fileName = fileName,
                fileType = mimeType,
                fileSize = bytes.size.toLong(),
                width = width,
                height = height,
            ),
        )
        uploadToStorage(presigned.uploadUrl, mimeType, bytes)
        confirmUpload(accessToken, presigned.mediaId)
        return UploadedMedia(mediaId = presigned.mediaId, publicUrl = presigned.publicUrl)
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(MediaUploadError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toMediaUploadError(): MediaUploadError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401, 403 -> MediaUploadError.AuthenticationRequired(detail)
            400, 404, 422 -> MediaUploadError.Validation(detail)
            429 -> MediaUploadError.RateLimited(detail)
            else -> MediaUploadError.Server(code, detail)
        }
    }
}

sealed class MediaUploadError(message: String) : Exception(message) {
    class Network(cause: Throwable) : MediaUploadError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : MediaUploadError(detail ?: "Authentication required")
    class RateLimited(detail: String?) : MediaUploadError(detail ?: "Too many requests")
    class Validation(detail: String?) : MediaUploadError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : MediaUploadError(detail ?: "Server error ($statusCode)")
}

@Serializable
internal data class PresignedUrlResponseDto(
    val uploadUrl: String,
    val mediaId: String,
    val publicUrl: String,
) {
    fun toDomain(): PresignedUpload =
        PresignedUpload(uploadUrl = uploadUrl, mediaId = mediaId, publicUrl = publicUrl)
}
