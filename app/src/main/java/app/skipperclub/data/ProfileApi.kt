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
 * Client for the `/v1/profile` endpoints backing the "My profile" screen.
 * Modeled on [NotificationsApi] (raw OkHttp + manual RFC 7807 mapping) to stay
 * consistent until the codebase grows enough to justify Retrofit + DI (see
 * CLAUDE.md §Networking).
 */
object ProfileApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Coalesce an explicit JSON `null` on a non-nullable, defaulted field (e.g. counts) to its default.
        coerceInputValues = true
    }

    // Profile updates use full-replacement (PUT) semantics, so cleared optional
    // fields must be sent as explicit `null` for the server to unset them.
    private val requestJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    private fun profileUrl(): HttpUrl = "${BuildConfig.API_BASE_URL}/v1/profile".toHttpUrl()

    internal fun getProfileRequest(accessToken: String): Request =
        baseRequest(accessToken).url(profileUrl()).get().build()

    suspend fun getProfile(accessToken: String): UserProfile =
        executeAndDecode<ProfileDto, UserProfile>(getProfileRequest(accessToken)) { it.toDomain() }

    internal fun getUserRequest(accessToken: String, userId: String): Request =
        baseRequest(accessToken)
            .url("${BuildConfig.API_BASE_URL}/v1/users".toHttpUrl().newBuilder().addPathSegment(userId).build())
            .get()
            .build()

    /**
     * Another member's public profile (`GET /v1/users/{userId}`). The response omits
     * `email`, so [UserProfile.email] comes back blank for other users.
     */
    suspend fun getUser(accessToken: String, userId: String): UserProfile =
        executeAndDecode<ProfileDto, UserProfile>(getUserRequest(accessToken, userId)) { it.toDomain() }

    internal fun updateProfileRequest(accessToken: String, update: ProfileUpdate): Request {
        val body = requestJson.encodeToString(ProfileUpdateDto.from(update))
        return baseRequest(accessToken).url(profileUrl()).put(body.toRequestBody(jsonMediaType)).build()
    }

    /**
     * Full profile update (`PUT /v1/profile`). The response is a `UserDetail`,
     * which omits `email`, so callers should preserve the email they already hold.
     */
    suspend fun updateProfile(accessToken: String, update: ProfileUpdate): UserProfile =
        executeAndDecode<ProfileDto, UserProfile>(updateProfileRequest(accessToken, update)) { it.toDomain() }

    private fun avatarUrl(vararg segments: String): HttpUrl =
        "${BuildConfig.API_BASE_URL}/v1/profile/avatar".toHttpUrl().newBuilder()
            .apply { segments.forEach { addPathSegment(it) } }
            .build()

    internal fun avatarPresignedRequest(accessToken: String, payload: AvatarPresignedUrlRequest): Request =
        baseRequest(accessToken)
            .url(avatarUrl("presigned-url"))
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .build()

    internal fun confirmAvatarRequest(accessToken: String, avatarId: String): Request =
        baseRequest(accessToken)
            .url(avatarUrl(avatarId, "confirm-upload"))
            .post(ByteArray(0).toRequestBody(null))
            .build()

    /**
     * Avatar upload via the presigned-URL flow recommended for mobile
     * (`docs/api/users` → Avatar Upload): request a URL, PUT the bytes to storage,
     * then confirm. Returns the public CDN URL of the new avatar.
     */
    suspend fun uploadAvatar(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int? = null,
        height: Int? = null,
    ): String {
        val presigned = executeAndDecode<AvatarPresignedUrlResponse, AvatarPresignedUrlResponse>(
            avatarPresignedRequest(
                accessToken,
                AvatarPresignedUrlRequest(
                    fileName = fileName,
                    fileType = mimeType,
                    fileSize = bytes.size.toLong(),
                    width = width,
                    height = height,
                ),
            ),
        ) { it }

        execute(
            Request.Builder().url(presigned.uploadUrl).put(bytes.toRequestBody(mimeType.toMediaType())).build(),
        ).use { response ->
            if (!response.isSuccessful) throw ProfileError.Server(response.code, "Avatar upload failed")
        }

        execute(confirmAvatarRequest(accessToken, presigned.avatarId)).use { response ->
            if (!response.isSuccessful) throw response.toProfileError()
        }
        return presigned.publicUrl
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
            if (!response.isSuccessful) throw response.toProfileError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw ProfileError.Server(response.code, "Malformed response")
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
                        continuation.resumeWithException(ProfileError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toProfileError(): ProfileError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> ProfileError.AuthenticationRequired(detail)
            404 -> ProfileError.NotFound(detail)
            429 -> ProfileError.RateLimited(detail)
            400, 422 -> ProfileError.Validation(detail)
            else -> ProfileError.Server(code, detail)
        }
    }
}

sealed class ProfileError(message: String) : Exception(message) {
    class Network(cause: Throwable) : ProfileError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : ProfileError(detail ?: "Authentication required")
    class NotFound(detail: String?) : ProfileError(detail ?: "Profile not found")
    class RateLimited(detail: String?) : ProfileError(detail ?: "Too many requests")
    class Validation(detail: String?) : ProfileError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : ProfileError(detail ?: "Server error ($statusCode)")
}
