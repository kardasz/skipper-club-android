package app.skipperclub.data

import kotlin.random.Random
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
}
