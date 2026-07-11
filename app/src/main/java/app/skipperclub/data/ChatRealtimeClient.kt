package app.skipperclub.data

import android.util.Log
import app.skipperclub.BuildConfig
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface ChatRealtimeEvent {
    /** New message in a chat room the client joined via `chat:join`. */
    data class MessageNew(val message: ChatMessage) : ChatRealtimeEvent

    /** New message notification from any chat the user participates in. */
    data class MessageReceived(val message: ChatMessage) : ChatRealtimeEvent

    data object Connected : ChatRealtimeEvent
    data object Disconnected : ChatRealtimeEvent
}

/**
 * Seam over the plain WebSocket channel (docs/api/asyncapi.yaml,
 * docs/api/messages/websocket.md) so screens depend on a small interface and
 * tests can drive realtime events with a fake. Sending and read receipts stay
 * on REST; the socket is inbound-only.
 */
interface ChatRealtimeClient {
    val events: SharedFlow<ChatRealtimeEvent>
    val isConnected: StateFlow<Boolean>

    /** [accessTokenProvider] is invoked before every (re)connect attempt so a near-expiry token is refreshed first. */
    fun connect(accessTokenProvider: suspend () -> String?)
    fun disconnect()
    fun joinChat(chatId: String)
    fun leaveChat(chatId: String)
}

private val realtimeJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** One `{"event":"...","data":{...}}` wire frame, either direction. */
@Serializable
internal data class RealtimeFrame(val event: String, val data: JsonElement)

/** Parses a `message:new` / `message:received` payload; null when malformed. */
internal fun parseRealtimeChatMessage(payload: String): ChatMessage? =
    try {
        realtimeJson.decodeFromString<ChatMessageDto>(payload).toDomain()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun encodeRealtimeFrame(event: String, data: JsonObject): String =
    realtimeJson.encodeToString(RealtimeFrame.serializer(), RealtimeFrame(event, data))

internal fun decodeRealtimeFrame(text: String): RealtimeFrame? =
    try {
        realtimeJson.decodeFromString(RealtimeFrame.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun chatIdFramePayload(chatId: String): JsonObject = buildJsonObject { put("chatId", chatId) }

/** `https://` -> `wss://`, `http://` -> `ws://`; the API base URL is always one of the two. */
internal fun String.toWebSocketUrl(): String = when {
    startsWith("https://", ignoreCase = true) -> "wss://" + substring("https://".length)
    startsWith("http://", ignoreCase = true) -> "ws://" + substring("http://".length)
    else -> this
}

private const val WS_PATH = "/v1/ws/chat"

internal fun buildChatWebSocketRequest(baseUrl: String, accessToken: String): Request =
    Request.Builder()
        .url(baseUrl.toWebSocketUrl() + WS_PATH)
        .addHeader("Authorization", "Bearer $accessToken")
        .build()

private const val INITIAL_BACKOFF_MILLIS = 1_000L
private const val MAX_BACKOFF_MILLIS = 30_000L

/** Bounded exponential backoff with jitter, capped at 30s, per the migration guide. */
internal fun reconnectBackoffMillis(attempt: Int, random: Random = Random.Default): Long {
    val cap = (INITIAL_BACKOFF_MILLIS shl attempt.coerceIn(0, 5)).coerceAtMost(MAX_BACKOFF_MILLIS)
    return random.nextLong(cap / 2, cap + 1)
}

object WebSocketChatRealtimeClient : ChatRealtimeClient {
    private const val TAG = "ChatRealtime"

    private val _events = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ChatRealtimeEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    /** Rooms to (re-)join, kept across reconnects; socket callbacks run off-main. */
    private val joinedChatIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var tokenProvider: (suspend () -> String?)? = null

    @Synchronized
    override fun connect(accessTokenProvider: suspend () -> String?) {
        if (scope != null) return
        tokenProvider = accessTokenProvider
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        newScope.launch { attemptConnect(newScope, attempt = 0) }
    }

    @Synchronized
    override fun disconnect() {
        scope?.cancel()
        scope = null
        tokenProvider = null
        webSocket?.close(1000, null)
        webSocket = null
        joinedChatIds.clear()
        _isConnected.value = false
    }

    override fun joinChat(chatId: String) {
        joinedChatIds += chatId
        sendFrame("chat:join", chatIdFramePayload(chatId))
    }

    override fun leaveChat(chatId: String) {
        joinedChatIds -= chatId
        sendFrame("chat:leave", chatIdFramePayload(chatId))
    }

    private suspend fun attemptConnect(ownerScope: CoroutineScope, attempt: Int) {
        val token = tokenProvider?.invoke() ?: run {
            debugLog("no access token available, dropping connect attempt")
            return
        }
        if (scope !== ownerScope) return
        val request = buildChatWebSocketRequest(BuildConfig.API_BASE_URL, token)
        webSocket = client.newWebSocket(request, RealtimeListener(ownerScope, attempt))
    }

    private fun scheduleReconnect(ownerScope: CoroutineScope, attempt: Int) {
        if (scope !== ownerScope) return
        _isConnected.value = false
        _events.tryEmit(ChatRealtimeEvent.Disconnected)
        ownerScope.launch {
            delay(reconnectBackoffMillis(attempt))
            if (scope === ownerScope) attemptConnect(ownerScope, attempt + 1)
        }
    }

    private fun sendFrame(event: String, data: JsonObject) {
        webSocket?.takeIf { _isConnected.value }?.send(encodeRealtimeFrame(event, data))
    }

    private fun handleFrame(text: String) {
        val frame = decodeRealtimeFrame(text) ?: run {
            debugLog("dropped malformed frame")
            return
        }
        when (frame.event) {
            "message:new" -> emitMessage(frame.data) { ChatRealtimeEvent.MessageNew(it) }
            "message:received" -> emitMessage(frame.data) { ChatRealtimeEvent.MessageReceived(it) }
            else -> Unit
        }
    }

    private fun emitMessage(data: JsonElement, wrap: (ChatMessage) -> ChatRealtimeEvent) {
        val message = parseRealtimeChatMessage(data.toString()) ?: run {
            debugLog("dropped malformed message payload")
            return
        }
        _events.tryEmit(wrap(message))
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private class RealtimeListener(
        private val ownerScope: CoroutineScope,
        private val attempt: Int,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            debugLog("connected")
            _isConnected.value = true
            _events.tryEmit(ChatRealtimeEvent.Connected)
            joinedChatIds.toList().forEach { chatId ->
                webSocket.send(encodeRealtimeFrame("chat:join", chatIdFramePayload(chatId)))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            debugLog("closed: $code $reason")
            scheduleReconnect(ownerScope, attempt)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            debugLog("failure: ${t.message}, close=${response?.code}")
            scheduleReconnect(ownerScope, attempt)
        }
    }
}
