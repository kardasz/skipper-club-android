package app.skipperclub.data

import app.skipperclub.BuildConfig
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
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

/** Account-level notification channel preferences (`docs/api/notifications/notification-settings.md`). */
data class NotificationSettings(
    val emailNotificationsEnabled: Boolean,
    val pushNotificationsEnabled: Boolean,
)

/**
 * Client for the `/v1/profile/notification-settings` endpoints backing the
 * "Settings" screen. Modeled on [ProfileApi] (raw OkHttp + manual RFC 7807
 * mapping) to stay consistent until the codebase grows enough to justify
 * Retrofit + DI (see CLAUDE.md §Networking).
 */
object SettingsApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Full-replacement (PUT) semantics require both fields on the wire even
        // when a value equals its default — see updateNotificationSettings.
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    private fun notificationSettingsUrl(): HttpUrl =
        "${BuildConfig.API_BASE_URL}/v1/profile/notification-settings".toHttpUrl()

    internal fun getNotificationSettingsRequest(accessToken: String): Request =
        baseRequest(accessToken).url(notificationSettingsUrl()).get().build()

    suspend fun getNotificationSettings(accessToken: String): NotificationSettings =
        executeAndDecode<NotificationSettingsDto, NotificationSettings>(
            getNotificationSettingsRequest(accessToken),
        ) { it.toDomain() }

    internal fun updateNotificationSettingsRequest(
        accessToken: String,
        settings: NotificationSettings,
    ): Request {
        // Full-replacement (PUT) semantics: both fields are always required.
        val body = json.encodeToString(NotificationSettingsDto.from(settings))
        return baseRequest(accessToken)
            .url(notificationSettingsUrl())
            .put(body.toRequestBody(jsonMediaType))
            .build()
    }

    suspend fun updateNotificationSettings(
        accessToken: String,
        settings: NotificationSettings,
    ): NotificationSettings =
        executeAndDecode<NotificationSettingsDto, NotificationSettings>(
            updateNotificationSettingsRequest(accessToken, settings),
        ) { it.toDomain() }

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
            if (!response.isSuccessful) throw response.toSettingsError()
            val payload = response.body.string()
            val dto = try {
                json.decodeFromString<DtoT>(payload)
            } catch (_: SerializationException) {
                throw SettingsError.Server(response.code, "Malformed response")
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
                        continuation.resumeWithException(SettingsError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toSettingsError(): SettingsError {
        val payload = body.string()
        val problem = runCatching {
            if (payload.isNotBlank()) json.decodeFromString<ProblemDetails>(payload) else null
        }.getOrNull()
        val detail = problem?.detail ?: problem?.title
        return when (code) {
            401 -> SettingsError.AuthenticationRequired(detail)
            429 -> SettingsError.RateLimited(detail)
            400, 422 -> SettingsError.Validation(detail)
            else -> SettingsError.Server(code, detail)
        }
    }
}

@Serializable
internal data class NotificationSettingsDto(
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
) {
    fun toDomain(): NotificationSettings = NotificationSettings(
        emailNotificationsEnabled = emailNotificationsEnabled,
        pushNotificationsEnabled = pushNotificationsEnabled,
    )

    companion object {
        fun from(settings: NotificationSettings): NotificationSettingsDto = NotificationSettingsDto(
            emailNotificationsEnabled = settings.emailNotificationsEnabled,
            pushNotificationsEnabled = settings.pushNotificationsEnabled,
        )
    }
}

sealed class SettingsError(message: String) : Exception(message) {
    class Network(cause: Throwable) : SettingsError(cause.message ?: "Network error")
    class AuthenticationRequired(detail: String?) : SettingsError(detail ?: "Authentication required")
    class RateLimited(detail: String?) : SettingsError(detail ?: "Too many requests")
    class Validation(detail: String?) : SettingsError(detail ?: "Validation failed")
    class Server(val statusCode: Int, detail: String?) : SettingsError(detail ?: "Server error ($statusCode)")
}
