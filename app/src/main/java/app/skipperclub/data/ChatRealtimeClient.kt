package app.skipperclub.data

import android.util.Log
import app.skipperclub.BuildConfig
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Hint that device connectivity returned. Short-circuits a pending reconnect backoff so the
     * next attempt starts immediately instead of waiting out the remaining delay (up to 30s). The
     * retry still goes through the normal attempt path — token refresh and scope guards are not
     * bypassed. No-op while connected, disconnected deliberately, or with no backoff pending.
     * [RealtimeConnectionManager] wires this to a [android.net.ConnectivityManager.NetworkCallback].
     */
    fun onNetworkAvailable() {}

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

@Serializable
internal data class ChatJoinedDto(val chatId: String)

/**
 * Parses a `chat:joined` ack payload — `{chatId}` — returning the chat id, or null when malformed.
 * The only ack we correlate: it is the sole reliable signal a `chat:join` landed, since the server's
 * `error` frame carries no correlation id (docs/api/messages/websocket.md).
 */
internal fun parseChatJoinedChatId(payload: String): String? =
    try {
        realtimeJson.decodeFromString<ChatJoinedDto>(payload).chatId
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

/**
 * Dedicated, much longer backoff cap for HTTP-403 upgrade rejections. A server that answers the WS
 * upgrade with 403 persistently (banned account, revoked WS entitlement, feature flag off) cannot be
 * fixed by a token refresh (see [shouldRefreshTokenForHttpFailure]); parking on this 5-minute tier
 * instead of the 30s one stops the pointless ~15-30s upgrade attempts while the app is foregrounded.
 * Still recoverable: a foreground/login cycle (RealtimeConnectionManager.reconcile), a network change
 * ([onNetworkAvailable] via [ReconnectBackoffGate.skip]), or the slow retry itself all re-attempt.
 */
private const val FORBIDDEN_BACKOFF_MILLIS = 300_000L

/**
 * Upper bound on the exponential shift so `INITIAL_BACKOFF_MILLIS shl attempt` cannot overflow a Long
 * on a long-lived failing session. Well past the shift needed to saturate either cap
 * (`1_000L shl 9` already exceeds [FORBIDDEN_BACKOFF_MILLIS]), so it never changes the curve — it is
 * purely an overflow guard.
 */
private const val MAX_BACKOFF_SHIFT = 30

/**
 * The backoff ceiling for the next reconnect: forbidden upgrades park on the long
 * [FORBIDDEN_BACKOFF_MILLIS] tier, every other failure on the standard [MAX_BACKOFF_MILLIS] one.
 */
internal fun backoffCapFor(forbidden: Boolean): Long =
    if (forbidden) FORBIDDEN_BACKOFF_MILLIS else MAX_BACKOFF_MILLIS

/**
 * A rejected WS upgrade with HTTP 403 is the only failure that earns the long backoff tier; anything
 * else (401, transport errors, no HTTP status) stays on the fast tier. Kept as a named seam so the
 * cap selection is unit-testable without driving the singleton's real reconnect schedule.
 */
internal fun isForbiddenUpgradeFailure(httpCode: Int?): Boolean = httpCode == HTTP_FORBIDDEN

/** Bounded exponential backoff with jitter, capped at [maxCap], per the migration guide. */
internal fun reconnectBackoffMillis(
    attempt: Int,
    maxCap: Long = MAX_BACKOFF_MILLIS,
    random: Random = Random.Default,
): Long {
    val cap = (INITIAL_BACKOFF_MILLIS shl attempt.coerceIn(0, MAX_BACKOFF_SHIFT)).coerceAtMost(maxCap)
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
 * endpoint in a `refresh → reconnect → 403` loop — it backs off instead, on the dedicated long
 * [FORBIDDEN_BACKOFF_MILLIS] tier (see [backoffCapFor]/[isForbiddenUpgradeFailure]) so a persistent
 * 403 stops re-attempting every 30s. Any other or absent HTTP status is a transient transport
 * failure that just backs off on the standard tier.
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

/**
 * Interruptible reconnect wait. [awaitBackoff] suspends for the backoff delay unless [skip]
 * short-circuits the wait currently in flight — used when connectivity returns, so the client
 * retries immediately instead of sitting out the remainder of an up-to-30s backoff. A skip with no
 * wait in flight is dropped rather than latched: connectivity flapping while connected must not
 * silently shorten some future backoff.
 */
internal class ReconnectBackoffGate {
    @Volatile
    private var pendingSkip: CompletableDeferred<Unit>? = null

    suspend fun awaitBackoff(millis: Long) {
        val skipSignal = CompletableDeferred<Unit>()
        pendingSkip = skipSignal
        try {
            withTimeoutOrNull(millis) { skipSignal.await() }
        } finally {
            pendingSkip = null
        }
    }

    fun skip() {
        pendingSkip?.complete(Unit)
    }
}

/** How long to wait for a `chat:joined` ack before re-sending the `chat:join` frame. */
internal const val JOIN_ACK_TIMEOUT_MILLIS = 10_000L

/** Total `chat:join` sends (initial + retries) before surfacing a failure and giving up. */
internal const val MAX_JOIN_ATTEMPTS = 3

/**
 * Correlates `chat:join` requests with their positive `chat:joined` acks and retries the ones the
 * server never acknowledges. A failed join otherwise leaves the connection silently out of the room
 * (the conversation screen believes it is live but receives no `message:new`/`chat:typing` until the
 * next reconnect replays the joins), and the `error` frame the server sends on failure carries no
 * correlation id (docs/api/messages/websocket.md), so a positive ack is the only reliable signal.
 *
 * Each tracked join re-sends up to [maxAttempts] total, [timeoutMillis] apart, then invokes
 * `onExhausted` once. Joining a room is idempotent server-side (membership is a set; a re-join is
 * just re-acked) and joins are spaced well under the inbound rate limit, so retries are safe.
 *
 * A separate, injectable seam (like [ReconnectBackoffGate]) so the retry timing is unit-testable
 * with a short timeout instead of the production 10s. All state is guarded by this instance's
 * monitor; the retry jobs run on the [CoroutineScope] passed to [track], so cancelling that scope
 * (a full disconnect) stops them, and [clear] stops them on an in-place reconnect.
 */
internal class JoinAckTracker(
    private val timeoutMillis: Long = JOIN_ACK_TIMEOUT_MILLIS,
    private val maxAttempts: Int = MAX_JOIN_ATTEMPTS,
) {
    private val pending = mutableMapOf<String, Job>()

    /**
     * Register a just-sent join for [chatId] and arm its retry timer on [scope]. [resend] fires for
     * each retry; [onExhausted] once, after the final attempt goes unacked. Re-tracking a chat that
     * is already pending supersedes the previous timer, so a reconnect replay re-arms cleanly.
     */
    @Synchronized
    fun track(
        chatId: String,
        scope: CoroutineScope,
        resend: (String) -> Unit,
        onExhausted: (String) -> Unit,
    ) {
        pending.remove(chatId)?.cancel()
        pending[chatId] = scope.launch {
            // The caller already sent attempt 1; re-send for each remaining attempt, then wait one
            // last timeout before giving up. An ack (or a leave/clear) cancels this job.
            repeat(maxAttempts - 1) {
                delay(timeoutMillis)
                resend(chatId)
            }
            delay(timeoutMillis)
            // Remove ourselves atomically: if an ack raced in during the final wait it already
            // removed the entry, in which case the join succeeded and we must not report a failure.
            val stillPending = synchronized(this@JoinAckTracker) { pending.remove(chatId) != null }
            if (stillPending) onExhausted(chatId)
        }
    }

    /** A `chat:joined` ack (or an explicit leave) landed: stop retrying [chatId]. No-op if absent. */
    @Synchronized
    fun resolve(chatId: String) {
        pending.remove(chatId)?.cancel()
    }

    /** Cancel and forget every pending join — disconnect or in-place connection teardown. */
    @Synchronized
    fun clear() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    @Synchronized
    fun isPending(chatId: String): Boolean = pending.containsKey(chatId)
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

    /**
     * Whether the most recent upgrade/connection failure was an HTTP 403, so [scheduleReconnect]
     * picks the long [FORBIDDEN_BACKOFF_MILLIS] tier instead of [MAX_BACKOFF_MILLIS]. Written under
     * the object monitor like the other state transitions, read from the reconnect coroutine.
     */
    @Volatile
    private var lastFailureWasForbidden: Boolean = false

    /** Lets a returning network cut a pending reconnect backoff short; see [onNetworkAvailable]. */
    private val backoffGate = ReconnectBackoffGate()

    /** Retries `chat:join` frames the server never acks with `chat:joined`; see [JoinAckTracker]. */
    private val joinAckTracker = JoinAckTracker()

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

    /**
     * Connectivity returned: skip the remainder of a pending backoff delay so the reconnect fires
     * now rather than after the full (up to 30s) wait. Only the delay is cut short — the retry
     * re-enters [attemptConnect], so the token provider runs and [openSocketIfCurrent] still
     * refuses superseded attempts. Safe to call at any time from any thread.
     */
    override fun onNetworkAvailable() {
        backoffGate.skip()
    }

    override fun joinChat(chatId: String) {
        joinedChatIds += chatId
        sendFrame("chat:join", chatIdFramePayload(chatId))
        // Arm the ack timeout only when a frame could actually go out; with no live scope/socket the
        // frame was dropped, and the reconnect replay (through this same method) re-arms on open.
        val activeScope = scope ?: return
        joinAckTracker.track(
            chatId = chatId,
            scope = activeScope,
            resend = { sendFrame("chat:join", chatIdFramePayload(it)) },
            onExhausted = ::emitJoinFailed,
        )
    }

    override fun leaveChat(chatId: String) {
        joinedChatIds -= chatId
        joinAckTracker.resolve(chatId)
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

    /**
     * Publish an opened socket as the live connection, or refuse when the connect attempt behind it
     * has been superseded. Called from [RealtimeListener.onOpen], which runs on an OkHttp thread.
     *
     * Shares the monitor with [connect]/[disconnect]/[openSocketIfCurrent] because the guard and
     * the state flip must be one step: an unlocked `scope !== ownerScope` check can pass, lose the
     * thread to a full `disconnect()` (scope cancelled, socket closed, Disconnected emitted), and
     * only then set `isConnected = true` and emit Connected — for a session that no longer exists.
     * The socket's own later close callback bails on its scope guard and never calls
     * [markDisconnected], so `isConnected` would stay true after logout: the REST-poll fallback
     * stays disabled and consumers see a spurious Connected.
     *
     * Holding the monitor here is safe: [MutableSharedFlow.tryEmit] and the [MutableStateFlow]
     * write never suspend or block, and no other lock is taken inside.
     */
    @Synchronized
    internal fun publishOpenIfCurrent(ownerScope: CoroutineScope, openedSocket: WebSocket): Boolean {
        if (scope !== ownerScope) return false
        webSocket = openedSocket
        _isConnected.value = true
        // The server accepted the upgrade — any earlier 403 is stale, so the next failure starts
        // from the fast backoff tier again.
        lastFailureWasForbidden = false
        _events.tryEmit(ChatRealtimeEvent.Connected)
        return true
    }

    private fun markDisconnected() {
        _isConnected.value = false
        // Stop retrying joins across the outage: pending timers would otherwise fire spurious
        // re-sends (dropped while disconnected) and a bogus failure event before the reconnect. The
        // onOpen replay re-arms them through joinChat. Reached under the object monitor from every
        // disconnect path (disconnect / markDisconnectedIfCurrent), and JoinAckTracker locks its own.
        joinAckTracker.clear()
        _events.tryEmit(ChatRealtimeEvent.Disconnected)
    }

    /**
     * Records whether the last failure was an HTTP 403, on the same monitor the connection-state
     * transitions use so it cannot race a concurrent connect/disconnect flipping [scope].
     */
    @Synchronized
    private fun recordForbiddenFailure(forbidden: Boolean) {
        lastFailureWasForbidden = forbidden
    }

    /**
     * Guard-and-flip for the close paths, on the same monitor as [publishOpenIfCurrent] and
     * [disconnect] so a server close racing a logout cannot re-emit Disconnected after
     * `disconnect()` already did, and cannot interleave with a publish from a late handshake.
     */
    @Synchronized
    private fun markDisconnectedIfCurrent(ownerScope: CoroutineScope): Boolean {
        if (scope !== ownerScope) return false
        markDisconnected()
        return true
    }

    private fun scheduleReconnect(ownerScope: CoroutineScope, attempt: Int) {
        if (scope !== ownerScope) return
        ownerScope.launch {
            // Interruptible: RealtimeConnectionManager signals network return through
            // onNetworkAvailable, which skips the remainder of this delay. A persistent 403 upgrade
            // rejection uses the long forbidden cap; every other failure the standard 30s one.
            backoffGate.awaitBackoff(
                reconnectBackoffMillis(attempt, backoffCapFor(lastFailureWasForbidden)),
            )
            if (scope === ownerScope) attemptConnect(ownerScope, attempt + 1)
        }
    }

    /**
     * Server-initiated close. Auth codes (`1008`/`4401`) force a token refresh before the reconnect
     * so we do not retry in a tight loop with the same rejected token; every other code just backs
     * off. The bounded backoff still applies after the refresh as a runaway-loop guard.
     */
    private fun handleClose(ownerScope: CoroutineScope, attempt: Int, code: Int) {
        if (!markDisconnectedIfCurrent(ownerScope)) return

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
        if (!markDisconnectedIfCurrent(ownerScope)) return
        // Record whether this was a 403 so the reconnect picks the right backoff tier. Setting it on
        // every failure (not only 403) also resets the flag after a non-403 — a 403 followed by a
        // network error drops back to the fast tier, since the 403 may have been a proxy fluke.
        recordForbiddenFailure(isForbiddenUpgradeFailure(httpCode))
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
            // The one ack we correlate: a positive `chat:joined` stops the join-retry timer for that
            // chat (see [JoinAckTracker]). Its payload is `{chatId}`.
            "chat:joined" -> confirmJoin(frame.data)
            // The remaining acks are not correlated to their requests: sends go over REST on Android
            // (their acks are unused) and typing/read are fire-and-forget with REST backstops. Log
            // them in debug so they are visible rather than invisibly dropped.
            "chat:left", "message:sent", "message:read:confirmed", "chat:typing:sent" ->
                debugLog("server ack: ${frame.event}")

            else -> debugLog("unhandled frame: ${frame.event}")
        }
    }

    /**
     * A `chat:joined` ack: stop the join-retry timer for its chat. A malformed payload is left
     * pending so the timeout path retries it, and never crashes the dispatch loop.
     */
    private fun confirmJoin(data: JsonElement) {
        val chatId = parseChatJoinedChatId(data.toString()) ?: run {
            debugLog("dropped malformed chat:joined payload")
            return
        }
        joinAckTracker.resolve(chatId)
        debugLog("chat:join acknowledged for $chatId")
    }

    /**
     * A `chat:join` went unacked after [MAX_JOIN_ATTEMPTS] sends. Surface it as a [ChatRealtimeEvent.ServerError]
     * so `MessagesScreen` shows the same localized realtime-error notice as any other server-side WS
     * failure (the raw type/message stays in the log, per the UI's English-only-protocol-text policy).
     * The chat stays in [joinedChatIds] so the next reconnect replay remains the backstop.
     */
    private fun emitJoinFailed(chatId: String) {
        Log.w(TAG, "chat:join for $chatId not acknowledged after $MAX_JOIN_ATTEMPTS attempts; giving up until next reconnect")
        _events.tryEmit(
            ChatRealtimeEvent.ServerError(
                type = "join_failed",
                message = "chat:join for $chatId was not acknowledged",
                timestamp = "",
            ),
        )
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
            // which would flip isConnected back on and re-join rooms for a signed-out session. The
            // guard and the publish are one atomic step under the client monitor (see
            // publishOpenIfCurrent) so a disconnect() cannot slip in between them.
            if (!publishOpenIfCurrent(ownerScope, webSocket)) {
                debugLog("connected after disconnect; closing orphaned socket")
                webSocket.close(CLOSE_CODE_NORMAL, null)
                return
            }
            debugLog("connected")
            // A healthy connection clears the accumulated backoff so the next disconnect retries
            // promptly instead of inheriting the pre-connect delay (which otherwise grows to the
            // 15-30s cap after a handful of reconnects over the app's lifetime).
            attempt = 0
            // Replay through joinChat (not a raw send) so each replayed room re-arms its ack-timeout
            // retry on the fresh connection; publishOpenIfCurrent already set isConnected and the
            // live socket, so sendFrame targets this socket.
            joinedChatIds.toList().forEach { chatId -> joinChat(chatId) }
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
