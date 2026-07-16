package app.skipperclub.data

import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRealtimeClientTest {

    @Test
    fun parsesMessageNewPayload() {
        val payload = """
            {
              "id": "m1",
              "chatId": "chat-1",
              "text": "Hello everyone! 👋",
              "read": false,
              "createdAt": "2025-11-23T14:30:00.000Z",
              "updatedAt": "2025-11-23T14:30:00.000Z",
              "user": {
                "id": "u1",
                "name": "Jan Kowalski",
                "avatarUrl": "https://cdn.example.com/avatars/jan.jpg"
              }
            }
        """.trimIndent()

        val message = parseRealtimeChatMessage(payload)

        requireNotNull(message)
        assertEquals("m1", message.id)
        assertEquals("chat-1", message.chatId)
        assertEquals("Hello everyone! 👋", message.text)
        assertFalse(message.read)
        assertEquals("Jan Kowalski", message.user.name)
    }

    @Test
    fun ignoresUnknownFields() {
        val payload = """
            {
              "id": "m1",
              "chatId": "chat-1",
              "text": "Hi",
              "createdAt": "2025-11-23T14:30:00Z",
              "updatedAt": "2025-11-23T14:30:00Z",
              "user": {"id": "u1", "name": "Jan"},
              "someFutureField": {"nested": true}
            }
        """.trimIndent()

        assertEquals("Hi", parseRealtimeChatMessage(payload)?.text)
    }

    @Test
    fun malformedPayloadReturnsNull() {
        assertNull(parseRealtimeChatMessage("not json"))
        assertNull(parseRealtimeChatMessage("""{"id":"m1"}"""))
    }

    @Test
    fun encodesFrameAsEventDataEnvelope() {
        val frame = encodeRealtimeFrame("chat:join", chatIdFramePayload("chat-1"))

        assertEquals("""{"event":"chat:join","data":{"chatId":"chat-1"}}""", frame)
    }

    @Test
    fun decodesEventDataEnvelope() {
        val frame = decodeRealtimeFrame("""{"event":"message:new","data":{"chatId":"chat-1"}}""")

        requireNotNull(frame)
        assertEquals("message:new", frame.event)
        assertEquals("chat-1", frame.data.jsonObject["chatId"]?.jsonPrimitive?.content)
    }

    @Test
    fun decodeReturnsNullForMalformedFrame() {
        assertNull(decodeRealtimeFrame("not json"))
        assertNull(decodeRealtimeFrame("""{"data":{}}"""))
    }

    @Test
    fun toWebSocketUrlSwapsHttpsScheme() {
        assertEquals("wss://api.skipperclub.app", "https://api.skipperclub.app".toWebSocketUrl())
        assertEquals("ws://localhost:8080", "http://localhost:8080".toWebSocketUrl())
    }

    @Test
    fun buildChatWebSocketRequestTargetsWsPathWithBearerAuth() {
        val request = buildChatWebSocketRequest("https://api.skipperclub.app", "access-token")

        // OkHttp's HttpUrl canonicalizes ws(s):// back to http(s):// internally — the upgrade
        // still happens over TLS because isHttps mirrors the wss:// scheme we built the URL with.
        assertTrue(request.url.isHttps)
        assertEquals("api.skipperclub.app", request.url.host)
        assertEquals("/v1/ws/chat", request.url.encodedPath)
        assertEquals("Bearer access-token", request.header("Authorization"))
    }

    @Test
    fun reconnectBackoffGrowsAndCapsAtThirtySeconds() {
        val fixed = Random(0)

        val first = reconnectBackoffMillis(attempt = 0, random = fixed)
        val later = reconnectBackoffMillis(attempt = 10, random = fixed)

        assertTrue(first in 500..1000)
        assertTrue(later in 15_000..30_000)
    }

    @Test
    fun authCloseCodesForceTokenRefresh() {
        assertEquals(ReconnectPolicy.RefreshToken, reconnectPolicyForClose(CLOSE_CODE_UNAUTHORIZED))
        assertEquals(ReconnectPolicy.RefreshToken, reconnectPolicyForClose(CLOSE_CODE_TOKEN_EXPIRED))
        assertEquals(ReconnectPolicy.RefreshToken, reconnectPolicyForClose(1008))
        assertEquals(ReconnectPolicy.RefreshToken, reconnectPolicyForClose(4401))
    }

    @Test
    fun otherCloseCodesBackOffWithoutRefresh() {
        // Normal close and going away reconnect via plain backoff.
        assertEquals(ReconnectPolicy.Backoff, reconnectPolicyForClose(1000))
        assertEquals(ReconnectPolicy.Backoff, reconnectPolicyForClose(1001))
        assertEquals(ReconnectPolicy.Backoff, reconnectPolicyForClose(1011))
    }

    @Test
    fun messageTooBigDoesNotRetry() {
        // 1009 means a frame we sent was rejected as too large — a client bug, not a transient
        // failure, so it must not be retried (docs/api/messages/websocket.md close-codes table).
        assertEquals(ReconnectPolicy.NoRetry, reconnectPolicyForClose(CLOSE_CODE_MESSAGE_TOO_BIG))
        assertEquals(ReconnectPolicy.NoRetry, reconnectPolicyForClose(1009))
    }

    @Test
    fun httpUnauthorizedOnUpgradeForcesTokenRefresh() {
        // A rejected upgrade (401) means the token itself is bad; retrying it verbatim loops.
        assertTrue(shouldRefreshTokenForHttpFailure(HTTP_UNAUTHORIZED))
        assertTrue(shouldRefreshTokenForHttpFailure(401))
    }

    @Test
    fun httpForbiddenOnUpgradeBacksOffWithoutRefresh() {
        // 403 means the token is valid but access is denied — a refresh cannot fix that, so
        // refreshing would loop `refresh → reconnect → 403` and hammer the refresh endpoint.
        assertFalse(shouldRefreshTokenForHttpFailure(HTTP_FORBIDDEN))
        assertFalse(shouldRefreshTokenForHttpFailure(403))
    }

    @Test
    fun transientUpgradeFailuresDoNotRefreshToken() {
        // No HTTP response (pure transport failure) or any non-auth status just backs off.
        assertFalse(shouldRefreshTokenForHttpFailure(null))
        assertFalse(shouldRefreshTokenForHttpFailure(500))
        assertFalse(shouldRefreshTokenForHttpFailure(503))
    }

    @Test
    fun decodesNotificationNewEnvelope() {
        val frame = decodeRealtimeFrame(
            """{"event":"notification:new","data":{"id":"n1","type":"MESSAGE_NEW"}}""",
        )

        requireNotNull(frame)
        assertEquals("notification:new", frame.event)
        assertEquals("n1", frame.data.jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesTypingUpdatePayload() {
        val payload = """{"chatId":"chat-1","userId":"u1","isTyping":true}"""

        val update = parseTypingUpdate(payload)

        requireNotNull(update)
        assertEquals("chat-1", update.chatId)
        assertEquals("u1", update.userId)
        assertTrue(update.isTyping)
    }

    @Test
    fun typingUpdateMalformedPayloadReturnsNull() {
        assertNull(parseTypingUpdate("not json"))
        assertNull(parseTypingUpdate("""{"chatId":"chat-1"}"""))
    }

    @Test
    fun parsesMessageReadPayload() {
        val payload = """{"messageId":"m1","userId":"u1","readAt":"2026-07-10T12:00:00Z"}"""

        val receipt = parseMessageRead(payload)

        requireNotNull(receipt)
        assertEquals("m1", receipt.messageId)
        assertEquals("u1", receipt.userId)
        assertEquals("2026-07-10T12:00:00Z", receipt.readAt)
    }

    @Test
    fun messageReadMalformedPayloadReturnsNull() {
        assertNull(parseMessageRead("not json"))
        assertNull(parseMessageRead("""{"messageId":"m1"}"""))
    }

    @Test
    fun parsesPresenceUpdatePayload() {
        val payload = """{"userId":"u1","isOnline":true,"lastSeen":"2026-07-10T12:00:00Z"}"""

        val update = parsePresenceUpdate(payload)

        requireNotNull(update)
        assertEquals("u1", update.userId)
        assertTrue(update.isOnline)
        assertEquals("2026-07-10T12:00:00Z", update.lastSeen)
    }

    @Test
    fun presenceUpdateToleratesMissingLastSeen() {
        val update = parsePresenceUpdate("""{"userId":"u1","isOnline":false}""")

        requireNotNull(update)
        assertNull(update.lastSeen)
    }

    @Test
    fun presenceUpdateMalformedPayloadReturnsNull() {
        assertNull(parsePresenceUpdate("not json"))
        assertNull(parsePresenceUpdate("""{"userId":"u1"}"""))
    }

    @Test
    fun parsesServerErrorPayload() {
        val payload = """
            {
              "type": "websocket_error",
              "message": "Chat not found or access denied",
              "timestamp": "2026-07-10T12:00:00Z"
            }
        """.trimIndent()

        val error = parseServerError(payload)

        requireNotNull(error)
        assertEquals("websocket_error", error.type)
        assertEquals("Chat not found or access denied", error.message)
    }

    @Test
    fun serverErrorMalformedPayloadReturnsNull() {
        assertNull(parseServerError("not json"))
        assertNull(parseServerError("""{"type":"websocket_error"}"""))
    }

    @Test
    fun typingFramePayloadEncodesChatIdAndFlag() {
        val frame = encodeRealtimeFrame("chat:typing", typingFramePayload("chat-1", isTyping = true))

        assertEquals("""{"event":"chat:typing","data":{"chatId":"chat-1","isTyping":true}}""", frame)
    }

    @Test
    fun messageReadFramePayloadEncodesChatAndMessageIds() {
        val frame = encodeRealtimeFrame("message:read", messageReadFramePayload("chat-1", "m1"))

        assertEquals("""{"event":"message:read","data":{"chatId":"chat-1","messageId":"m1"}}""", frame)
    }

    @Test
    fun manualDisconnectEmitsDisconnected() = runBlocking {
        // Logout/backgrounding go through disconnect(); without the Disconnected emission,
        // PresenceStore would keep stale "online" flags across those paths.
        val events = mutableListOf<ChatRealtimeEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            WebSocketChatRealtimeClient.events.collect { events += it }
        }
        // Park connect() inside the token provider so no real socket (or Android Log call) is ever
        // touched; await the entry so disconnect() races neither the launch nor the provider.
        val providerEntered = CompletableDeferred<Unit>()
        WebSocketChatRealtimeClient.connect(
            accessTokenProvider = {
                providerEntered.complete(Unit)
                awaitCancellation()
            },
        )
        providerEntered.await()

        WebSocketChatRealtimeClient.disconnect()

        yield()
        collector.cancel()
        assertEquals(listOf<ChatRealtimeEvent>(ChatRealtimeEvent.Disconnected), events)
    }

    @Test
    fun disconnectWithoutActiveConnectionEmitsNothing() = runBlocking {
        // RealtimeConnectionManager.reconcile() calls disconnect() on every background/logout
        // signal; repeated calls with no live connection must not emit spurious events.
        val events = mutableListOf<ChatRealtimeEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            WebSocketChatRealtimeClient.events.collect { events += it }
        }

        WebSocketChatRealtimeClient.disconnect()

        yield()
        collector.cancel()
        assertTrue(events.isEmpty())
    }
}
