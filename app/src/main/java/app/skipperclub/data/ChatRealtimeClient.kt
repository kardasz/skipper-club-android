package app.skipperclub.data

import android.util.Log
import app.skipperclub.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URI
import java.util.Collections
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONObject

sealed interface ChatRealtimeEvent {
    /** New message in a chat room the client joined via `chat:join`. */
    data class MessageNew(val message: ChatMessage) : ChatRealtimeEvent

    /** New message notification from any chat the user participates in. */
    data class MessageReceived(val message: ChatMessage) : ChatRealtimeEvent

    data object Connected : ChatRealtimeEvent
    data object Disconnected : ChatRealtimeEvent
}

/**
 * Seam over the Socket.IO `/chat` namespace (docs/api/messages/websocket.md) so
 * screens depend on a small interface and tests can drive realtime events with
 * a fake. Sending and read receipts stay on REST; the socket is inbound-only.
 */
interface ChatRealtimeClient {
    val events: SharedFlow<ChatRealtimeEvent>
    val isConnected: StateFlow<Boolean>

    fun connect(accessToken: String)
    fun disconnect()
    fun joinChat(chatId: String)
    fun leaveChat(chatId: String)
}

private val realtimeJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Parses a `message:new` / `message:received` payload; null when malformed. */
internal fun parseRealtimeChatMessage(payload: String): ChatMessage? =
    try {
        realtimeJson.decodeFromString<ChatMessageDto>(payload).toDomain()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

object SocketIoChatRealtimeClient : ChatRealtimeClient {
    private const val TAG = "ChatRealtime"

    private val _events = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ChatRealtimeEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: Socket? = null

    /** Rooms to (re-)join, kept across reconnects; socket callbacks run off-main. */
    private val joinedChatIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Synchronized
    override fun connect(accessToken: String) {
        if (socket != null) return
        val options = IO.Options.builder()
            .setAuth(mapOf("token" to accessToken))
            .setReconnection(true)
            .build()
        val created = IO.socket(URI.create("${BuildConfig.API_BASE_URL}/chat"), options)
        created.on(Socket.EVENT_CONNECT) {
            debugLog("connected")
            _isConnected.value = true
            _events.tryEmit(ChatRealtimeEvent.Connected)
            joinedChatIds.toList().forEach { chatId ->
                created.emit("chat:join", chatIdPayload(chatId))
            }
        }
        created.on(Socket.EVENT_DISCONNECT) {
            debugLog("disconnected")
            _isConnected.value = false
            _events.tryEmit(ChatRealtimeEvent.Disconnected)
        }
        created.on(Socket.EVENT_CONNECT_ERROR) { args ->
            debugLog("connect error: ${args.firstOrNull()}")
            _isConnected.value = false
        }
        created.on("message:new") { args ->
            handleMessage(args) { ChatRealtimeEvent.MessageNew(it) }
        }
        created.on("message:received") { args ->
            handleMessage(args) { ChatRealtimeEvent.MessageReceived(it) }
        }
        socket = created
        created.connect()
    }

    @Synchronized
    override fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        joinedChatIds.clear()
        _isConnected.value = false
    }

    override fun joinChat(chatId: String) {
        joinedChatIds += chatId
        val current = socket ?: return
        if (current.connected()) current.emit("chat:join", chatIdPayload(chatId))
    }

    override fun leaveChat(chatId: String) {
        joinedChatIds -= chatId
        val current = socket ?: return
        if (current.connected()) current.emit("chat:leave", chatIdPayload(chatId))
    }

    private fun handleMessage(args: Array<Any?>, wrap: (ChatMessage) -> ChatRealtimeEvent) {
        val payload = args.firstOrNull() as? JSONObject ?: return
        val message = parseRealtimeChatMessage(payload.toString()) ?: run {
            debugLog("dropped malformed message payload")
            return
        }
        _events.tryEmit(wrap(message))
    }

    private fun chatIdPayload(chatId: String): JSONObject = JSONObject().put("chatId", chatId)

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
