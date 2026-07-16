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

    /** In-app notification pushed on the personal room while foregrounded. */
    data class NotificationNew(val payload: JsonObject) : ChatRealtimeEvent

    /** Another participant's typing state changed in a joined chat room. */
    data class TypingUpdate(val chatId: String, val userId: String, val isTyping: Boolean) :
        ChatRealtimeEvent

    /** A participant read a message; delivered to every connection joined to the chat room. */
    data class MessageRead(val messageId: String, val userId: String, val readAt: String) :
        ChatRealtimeEvent

    /** Online/offline transition for a user sharing at least one chat with us. */
    data class PresenceUpdate(val userId: String, val isOnline: Boolean, val lastSeen: String?) :
        ChatRealtimeEvent

    /** Server-side failure on the requesting connection (rate limit, access denied, ...). */
    data class ServerError(val type: String, val message: String, val timestamp: String) :
        ChatRealtimeEvent

    data object Connected : ChatRealtimeEvent
    data object Disconnected : ChatRealtimeEvent
}

/**
 * Seam over the plain WebSocket channel (docs/api/asyncapi.yaml,
 * docs/api/messages/websocket.md) so screens depend on a small interface and
 * tests can drive realtime events with a fake. Sending a chat message stays on
 * REST (see `ChatsApi.sendMessage`); typing indicators and per-message read
 * receipts go over the socket, per the transport-parity table in
 * docs/api/messages/websocket.md.
 */
interface ChatRealtimeClient {
    val events: SharedFlow<ChatRealtimeEvent>
    val isConnected: StateFlow<Boolean>

    /**
     * [accessTokenProvider] is invoked before every (re)connect attempt so a near-expiry token is
     * refreshed first. [onAuthClose] is invoked when the server closes with an auth code
     * (`1008`/`4401`) to force a token refresh **before** the next reconnect, rather than relying on
     * the passive [accessTokenProvider] re-read (which only refreshes near expiry).
     */
    fun connect(
        accessTokenProvider: suspend () -> String?,
        onAuthClose: suspend () -> Unit = {},
    )

    fun disconnect()
    fun joinChat(chatId: String)
    fun leaveChat(chatId: String)

    /** `chat:typing` — send once per typing burst; the caller debounces `isTyping = false`. */
    fun sendTyping(chatId: String, isTyping: Boolean)

    /** `message:read` — per-message read receipt, mirrors the REST `PATCH .../messages/{id}`. */
    fun sendMessageRead(chatId: String, messageId: String)
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

@Serializable
internal data class TypingUpdateDto(val chatId: String, val userId: String, val isTyping: Boolean)

/** Parses a `chat:typing` payload; null when malformed. */
internal fun parseTypingUpdate(payload: String): ChatRealtimeEvent.TypingUpdate? =
    try {
        realtimeJson.decodeFromString<TypingUpdateDto>(payload).let {
            ChatRealtimeEvent.TypingUpdate(chatId = it.chatId, userId = it.userId, isTyping = it.isTyping)
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

@Serializable
internal data class MessageReadDto(val messageId: String, val userId: String, val readAt: String)

/** Parses a `message:read` payload; null when malformed. */
internal fun parseMessageRead(payload: String): ChatRealtimeEvent.MessageRead? =
    try {
        realtimeJson.decodeFromString<MessageReadDto>(payload).let {
            ChatRealtimeEvent.MessageRead(messageId = it.messageId, userId = it.userId, readAt = it.readAt)
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

@Serializable
internal data class PresenceUpdateDto(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: String? = null,
)

/** Parses a `presence:update` payload; null when malformed. */
internal fun parsePresenceUpdate(payload: String): ChatRealtimeEvent.PresenceUpdate? =
    try {
        realtimeJson.decodeFromString<PresenceUpdateDto>(payload).let {
            ChatRealtimeEvent.PresenceUpdate(userId = it.userId, isOnline = it.isOnline, lastSeen = it.lastSeen)
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

@Serializable
internal data class ServerErrorDto(val type: String, val message: String, val timestamp: String)

/** Parses an `error` payload; null when malformed. */
internal fun parseServerError(payload: String): ChatRealtimeEvent.ServerError? =
    try {
        realtimeJson.decodeFromString<ServerErrorDto>(payload).let {
            ChatRealtimeEvent.ServerError(type = it.type, message = it.message, timestamp = it.timestamp)
        }
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

/** `chat:typing` outbound payload. */
internal fun typingFramePayload(chatId: String, isTyping: Boolean): JsonObject = buildJsonObject {
    put("chatId", chatId)
    put("isTyping", isTyping)
}

/** `message:read` outbound payload. */
internal fun messageReadFramePayload(chatId: String, messageId: String): JsonObject = buildJsonObject {
    put("chatId", chatId)
    put("messageId", messageId)
}

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

/** WebSocket close code the server uses when the connection was never authorized. */
internal const val CLOSE_CODE_UNAUTHORIZED = 1008

/** Application close code the server uses when a live access token expires. */
internal const val CLOSE_CODE_TOKEN_EXPIRED = 4401

/** Close code the server uses when an inbound frame we sent exceeded the 32 KiB limit. */
internal const val CLOSE_CODE_MESSAGE_TOO_BIG = 1009

internal enum class ReconnectPolicy {
    /** Force a token refresh before reconnecting (auth close). */
    RefreshToken,

    /** Reconnect with bounded exponential backoff (any other close). */
    Backoff,

    /**
     * Do not reconnect: the close indicates a client bug (a frame we sent was malformed or too
     * large), not a transient failure, so retrying would just repeat the same rejected frame.
     */
    NoRetry,
}

/**
 * Auth closes need a fresh token first; `1009` ("message too big") means we sent a bad frame and
 * must not retry it verbatim, so it is terminal rather than backed off; everything else just backs
 * off.
 */
internal fun reconnectPolicyForClose(code: Int): ReconnectPolicy = when (code) {
    CLOSE_CODE_UNAUTHORIZED, CLOSE_CODE_TOKEN_EXPIRED -> ReconnectPolicy.RefreshToken
    CLOSE_CODE_MESSAGE_TOO_BIG -> ReconnectPolicy.NoRetry
    else -> ReconnectPolicy.Backoff
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

    @Volatile
    private var authCloseHandler: (suspend () -> Unit)? = null

    @Synchronized
    override fun connect(
        accessTokenProvider: suspend () -> String?,
        onAuthClose: suspend () -> Unit,
    ) {
        if (scope != null) return
        tokenProvider = accessTokenProvider
        authCloseHandler = onAuthClose
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        newScope.launch { attemptConnect(newScope, attempt = 0) }
    }

    @Synchronized
    override fun disconnect() {
        scope?.cancel()
        scope = null
        tokenProvider = null
        authCloseHandler = null
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

    override fun sendTyping(chatId: String, isTyping: Boolean) {
        sendFrame("chat:typing", typingFramePayload(chatId, isTyping))
    }

    override fun sendMessageRead(chatId: String, messageId: String) {
        sendFrame("message:read", messageReadFramePayload(chatId, messageId))
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

    private fun markDisconnected() {
        _isConnected.value = false
        _events.tryEmit(ChatRealtimeEvent.Disconnected)
    }

    private fun scheduleReconnect(ownerScope: CoroutineScope, attempt: Int) {
        if (scope !== ownerScope) return
        ownerScope.launch {
            delay(reconnectBackoffMillis(attempt))
            if (scope === ownerScope) attemptConnect(ownerScope, attempt + 1)
        }
    }

    /**
     * Server-initiated close. Auth codes (`1008`/`4401`) force a token refresh before the reconnect
     * so we do not retry in a tight loop with the same rejected token; every other code just backs
     * off. The bounded backoff still applies after the refresh as a runaway-loop guard.
     */
    private fun handleClose(ownerScope: CoroutineScope, attempt: Int, code: Int) {
        if (scope !== ownerScope) return
        markDisconnected()
        when (reconnectPolicyForClose(code)) {
            ReconnectPolicy.RefreshToken -> ownerScope.launch {
                runCatching { authCloseHandler?.invoke() }
                scheduleReconnect(ownerScope, attempt)
            }

            ReconnectPolicy.Backoff -> scheduleReconnect(ownerScope, attempt)

            // A frame we sent was rejected as too big; retrying it would just close again in a
            // loop. Surface it loudly (unconditionally, not the debug-only log) since this is a
            // client bug, not a network blip, and there is no crash-reporting hook yet to catch it.
            ReconnectPolicy.NoRetry -> Log.w(
                TAG,
                "connection closed with code $code (message too big); not retrying — fix the " +
                    "outgoing payload that caused this close",
            )
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
            "notification:new" -> emitNotification(frame.data)
            "chat:typing" -> emitTypingUpdate(frame.data)
            "message:read" -> emitMessageRead(frame.data)
            "presence:update" -> emitPresenceUpdate(frame.data)
            "error" -> emitServerError(frame.data)
            else -> Unit
        }
    }

    private fun emitNotification(data: JsonElement) {
        val payload = data as? JsonObject ?: run {
            debugLog("dropped non-object notification payload")
            return
        }
        _events.tryEmit(ChatRealtimeEvent.NotificationNew(payload))
    }

    private fun emitMessage(data: JsonElement, wrap: (ChatMessage) -> ChatRealtimeEvent) {
        val message = parseRealtimeChatMessage(data.toString()) ?: run {
            debugLog("dropped malformed message payload")
            return
        }
        _events.tryEmit(wrap(message))
    }

    private fun emitTypingUpdate(data: JsonElement) {
        val update = parseTypingUpdate(data.toString()) ?: run {
            debugLog("dropped malformed chat:typing payload")
            return
        }
        _events.tryEmit(update)
    }

    private fun emitMessageRead(data: JsonElement) {
        val receipt = parseMessageRead(data.toString()) ?: run {
            debugLog("dropped malformed message:read payload")
            return
        }
        _events.tryEmit(receipt)
    }

    private fun emitPresenceUpdate(data: JsonElement) {
        val update = parsePresenceUpdate(data.toString()) ?: run {
            debugLog("dropped malformed presence:update payload")
            return
        }
        _events.tryEmit(update)
    }

    /**
     * Unlike the other frames this is logged unconditionally (not gated behind [debugLog]): it
     * signals a real server-side failure (rate limiting, access denied on `chat:join`, ...) that
     * would otherwise be completely invisible. There is no crash-reporting SDK wired up yet, so a
     * warning-level log is the minimum viable signal until one is.
     */
    private fun emitServerError(data: JsonElement) {
        val error = parseServerError(data.toString()) ?: run {
            Log.w(TAG, "dropped malformed error frame: $data")
            return
        }
        Log.w(TAG, "server error: type=${error.type} message=${error.message}")
        _events.tryEmit(error)
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
            handleClose(ownerScope, attempt, code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            debugLog("failure: ${t.message}, close=${response?.code}")
            if (scope !== ownerScope) return
            markDisconnected()
            scheduleReconnect(ownerScope, attempt)
        }
    }
}
