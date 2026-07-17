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
import kotlinx.coroutines.channels.BufferOverflow
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

    /**
     * In-app notification pushed on the personal room while foregrounded. The payload is the same
     * notification object the REST `/v1/notifications` endpoints return (docs/api/notifications/index.md).
     */
    data class NotificationNew(val notification: AppNotification) : ChatRealtimeEvent

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

/**
 * Parses a `notification:new` payload — a REST-shaped notification object — reusing
 * [NotificationDto] so realtime and REST rows stay in lockstep; null when malformed or when the
 * row carries an unknown source/status (same forward-compat drop rule as the REST list).
 */
internal fun parseRealtimeNotification(payload: String): AppNotification? =
    try {
        realtimeJson.decodeFromString<NotificationDto>(payload).toDomain()
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

/**
 * How often OkHttp pings the server to prove the connection is still alive, and how long it then
 * waits for the pong before failing the connection. Matches the server's own 30s heartbeat.
 */
internal const val PING_INTERVAL_SECONDS = 30L

/** Inbound realtime events buffered for main-thread collectors before the oldest is dropped. */
internal const val EVENT_BUFFER_CAPACITY = 256

/** Normal closure — what we send when tearing a connection down deliberately. */
internal const val CLOSE_CODE_NORMAL = 1000

/** WebSocket close code the server uses when the connection was never authorized. */
internal const val CLOSE_CODE_UNAUTHORIZED = 1008

/** Application close code the server uses when a live access token expires. */
internal const val CLOSE_CODE_TOKEN_EXPIRED = 4401

/** Close code the server uses when an inbound frame we sent exceeded the 32 KiB limit. */
internal const val CLOSE_CODE_MESSAGE_TOO_BIG = 1009

/** HTTP status the upgrade handshake returns when the bearer token is rejected. */
internal const val HTTP_UNAUTHORIZED = 401

/** HTTP status the upgrade handshake returns when the token is valid but access is denied. */
internal const val HTTP_FORBIDDEN = 403

/**
 * A `401` on the WebSocket **upgrade** (as opposed to a post-connect close) means the server
 * rejected the token itself — typically revoked server-side while still locally valid — so retrying
 * with the same token would loop forever. Force a refresh first. A `403` means the token is valid
 * but access is denied; a refresh cannot fix that, so refreshing on it would hammer the refresh
 * endpoint in a `refresh → reconnect → 403` loop — it backs off like any other failure instead.
 * Any other or absent HTTP status is a transient transport failure that also just backs off.
 */
internal fun shouldRefreshTokenForHttpFailure(httpCode: Int?): Boolean =
    httpCode == HTTP_UNAUTHORIZED

internal enum class ReconnectPolicy {
    /** Force a token refresh before reconnecting (auth close). */
    RefreshToken,

    /** Reconnect with bounded exponential backoff (any other close). */
    Backoff,

}

/**
 * Auth closes need a fresh token first; everything else — including `1009` ("message too big") —
 * backs off and retries.
 *
 * `1009` does indicate a client bug rather than a transient failure, and it used to be terminal for
 * that reason. But nothing re-sends the offending frame after a reconnect, so refusing to retry
 * only meant one anomalous frame silently killed realtime for the rest of the process: no live
 * messages, no typing, no presence, until the app was backgrounded and restored. The frame is still
 * logged loudly (see [handleClose]) — the bug is worth fixing, but not by leaving chat dead.
 */
internal fun reconnectPolicyForClose(code: Int): ReconnectPolicy = when (code) {
    CLOSE_CODE_UNAUTHORIZED, CLOSE_CODE_TOKEN_EXPIRED -> ReconnectPolicy.RefreshToken
    else -> ReconnectPolicy.Backoff
}

object WebSocketChatRealtimeClient : ChatRealtimeClient {
    private const val TAG = "ChatRealtime"

    /**
     * Socket callbacks run off-main and must never block, so emission is always `tryEmit` — which
     * means a full buffer silently drops the event. Collectors run on the main thread, so a burst
     * (the backlog replayed after a rejoin, a busy group chat) can outrun them.
     *
     * The buffer is sized well past any plausible burst, and overflow drops the *oldest* event: a
     * dropped frame is unavoidable at that point, and losing the stalest one keeps the live tail
     * of the conversation intact rather than discarding exactly the newest message. Anything lost
     * is still recoverable — [ChatRealtimeEvent.Connected] triggers a REST catch-up, and the chat
     * list refetches on open.
     */
    private val _events = MutableSharedFlow<ChatRealtimeEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<ChatRealtimeEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Detect a half-open connection instead of waiting to discover it on the next send. The
        // server pings every 30s, but a peer that vanishes without a FIN (a dropped NAT binding,
        // a sleeping radio) leaves us reading a socket that will never deliver anything again:
        // isConnected stays true, incoming messages silently stop, and nothing reconnects. OkHttp
        // sends its own pings on this interval and fails the connection when a pong doesn't come
        // back in time, which surfaces as onFailure and drives the normal backoff path.
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
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
        // Idempotent: RealtimeConnectionManager.reconcile() calls this on every background/logout
        // signal, including when nothing was ever connected — bail out so repeated calls do not
        // emit spurious Disconnected events.
        val activeScope = scope ?: return
        activeScope.cancel()
        scope = null
        tokenProvider = null
        authCloseHandler = null
        webSocket?.close(1000, null)
        webSocket = null
        joinedChatIds.clear()
        // Emit Disconnected so consumers (e.g. PresenceStore) clear stale state on logout and
        // app-backgrounding too, not only on server-side drops. No double emission when the
        // socket's close callback fires later: handleClose/handleFailure bail out because `scope`
        // is already null.
        markDisconnected()
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
            // A null token is usually a transient refresh failure (e.g. SessionStore swallowing an
            // AuthError.Network), not a real logout — the connection guard keeps reconcile() from
            // re-entering, so we must schedule our own retry or realtime dies until the app is
            // backgrounded and restored.
            debugLog("no access token available; scheduling reconnect")
            scheduleReconnect(ownerScope, attempt)
            return
        }
        val request = buildChatWebSocketRequest(BuildConfig.API_BASE_URL, token)
        openSocketIfCurrent(ownerScope, request, attempt)
    }

    /**
     * Open the socket and publish it, or do nothing if this connect attempt has been superseded.
     *
     * Synchronized on the same monitor as [disconnect] because the check and the assignment must be
     * one step: `tokenProvider` above is a suspension point, so a logout or a background can land
     * anywhere before this. A plain `if (scope !== ownerScope) return` outside the lock passes,
     * `disconnect()` then runs to completion (cancelling the scope, closing and nulling the old
     * socket), and only then does the assignment land — publishing a live socket that nothing will
     * ever close. The user reads as online to everyone they share a chat with, after logging out,
     * until the server reaps the connection.
     */
    @Synchronized
    private fun openSocketIfCurrent(ownerScope: CoroutineScope, request: Request, attempt: Int) {
        if (scope !== ownerScope) return
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

        // A frame we sent was rejected as too big. Reconnect anyway (nothing re-sends it), but
        // surface it loudly — unconditionally, not the debug-only log — since this is a client bug,
        // not a network blip, and there is no crash-reporting hook yet to catch it.
        if (code == CLOSE_CODE_MESSAGE_TOO_BIG) {
            Log.w(
                TAG,
                "connection closed with code $code (message too big) — fix the outgoing payload " +
                    "that caused this close; reconnecting",
            )
        }

        when (reconnectPolicyForClose(code)) {
            ReconnectPolicy.RefreshToken -> ownerScope.launch {
                runCatching { authCloseHandler?.invoke() }
                scheduleReconnect(ownerScope, attempt)
            }

            ReconnectPolicy.Backoff -> scheduleReconnect(ownerScope, attempt)
        }
    }

    /**
     * Handshake/transport failure (never opened, or dropped without a close frame). A `401` on the
     * upgrade means the token itself was rejected, so we force a refresh before reconnecting;
     * everything else (including `403`, which a refresh cannot fix) just backs off. Mirrors the
     * auth path in [handleClose] for server closes.
     */
    private fun handleFailure(ownerScope: CoroutineScope, attempt: Int, httpCode: Int?) {
        if (scope !== ownerScope) return
        markDisconnected()
        if (shouldRefreshTokenForHttpFailure(httpCode)) {
            ownerScope.launch {
                runCatching { authCloseHandler?.invoke() }
                scheduleReconnect(ownerScope, attempt)
            }
        } else {
            scheduleReconnect(ownerScope, attempt)
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
            // Server acks for the frames we send. We do not correlate them to their requests yet
            // (join failures still surface only as an out-of-band `error` frame); log them in debug
            // so they are at least visible rather than invisibly dropped.
            "chat:joined", "chat:left", "message:sent", "message:read:confirmed", "chat:typing:sent" ->
                debugLog("server ack: ${frame.event}")

            else -> debugLog("unhandled frame: ${frame.event}")
        }
    }

    private fun emitNotification(data: JsonElement) {
        val notification = parseRealtimeNotification(data.toString()) ?: run {
            debugLog("dropped malformed notification payload")
            return
        }
        _events.tryEmit(ChatRealtimeEvent.NotificationNew(notification))
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
        initialAttempt: Int,
    ) : WebSocketListener() {
        /** Reset in [onOpen], read in [onClosed]/[onFailure]; OkHttp may invoke them on different threads. */
        @Volatile
        private var attempt: Int = initialAttempt

        override fun onOpen(webSocket: WebSocket, response: Response) {
            // The handshake can complete after a logout or a background — this socket belongs to a
            // connection nobody wants any more. Close it rather than reporting it as the live one,
            // which would flip isConnected back on and re-join rooms for a signed-out session.
            if (scope !== ownerScope) {
                debugLog("connected after disconnect; closing orphaned socket")
                webSocket.close(CLOSE_CODE_NORMAL, null)
                return
            }
            debugLog("connected")
            // A healthy connection clears the accumulated backoff so the next disconnect retries
            // promptly instead of inheriting the pre-connect delay (which otherwise grows to the
            // 15-30s cap after a handful of reconnects over the app's lifetime).
            attempt = 0
            _isConnected.value = true
            _events.tryEmit(ChatRealtimeEvent.Connected)
            joinedChatIds.toList().forEach { chatId ->
                webSocket.send(encodeRealtimeFrame("chat:join", chatIdFramePayload(chatId)))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Frames from an orphaned socket are not this session's to deliver.
            if (scope !== ownerScope) return
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
            handleFailure(ownerScope, attempt, response?.code)
        }
    }
}
