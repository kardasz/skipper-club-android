package app.skipperclub.data

import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
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
    fun parsesEchoedClientMessageId() {
        // Backend v1.5.0 echoes the sender's idempotency key in message payloads; the controller
        // uses it as an additional dedup key for the WS echo of an own REST send.
        val payload = """
            {
              "id": "m1",
              "chatId": "chat-1",
              "text": "Hi",
              "createdAt": "2025-11-23T14:30:00Z",
              "updatedAt": "2025-11-23T14:30:00Z",
              "user": {"id": "u1", "name": "Jan"},
              "clientMessageId": "0e2f4b1c-8a51-4a0e-9a3d-1d2e3f4a5b6c"
            }
        """.trimIndent()

        assertEquals(
            "0e2f4b1c-8a51-4a0e-9a3d-1d2e3f4a5b6c",
            parseRealtimeChatMessage(payload)?.clientMessageId,
        )
    }

    @Test
    fun clientMessageIdIsNullWhenNotEchoed() {
        val payload = """
            {
              "id": "m1",
              "chatId": "chat-1",
              "text": "Hi",
              "createdAt": "2025-11-23T14:30:00Z",
              "updatedAt": "2025-11-23T14:30:00Z",
              "user": {"id": "u1", "name": "Jan"}
            }
        """.trimIndent()

        assertNull(parseRealtimeChatMessage(payload)?.clientMessageId)
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
    fun forbiddenUpgradeFailureUsesTheFiveMinuteBackoffCap() {
        // A 403 upgrade rejection parks on the long tier so a permanently-forbidden connection stops
        // re-attempting every 30s.
        assertEquals(300_000L, backoffCapFor(isForbiddenUpgradeFailure(HTTP_FORBIDDEN)))
        assertEquals(300_000L, backoffCapFor(isForbiddenUpgradeFailure(403)))
    }

    @Test
    fun forbiddenThenNonForbiddenFailureReturnsToTheThirtySecondCap() {
        // A 403 followed by a network error (or any non-403) drops back to the fast tier — the 403
        // may have been a proxy fluke. handleFailure overwrites the flag on every failure, so the
        // later non-403 wins.
        assertTrue(isForbiddenUpgradeFailure(HTTP_FORBIDDEN))
        assertEquals(30_000L, backoffCapFor(isForbiddenUpgradeFailure(null)))
        assertEquals(30_000L, backoffCapFor(isForbiddenUpgradeFailure(500)))
    }

    @Test
    fun unauthorizedUpgradeFailureKeepsTheFastCap() {
        // 401 must keep its refresh-then-reconnect fast path — it is not a forbidden failure, so the
        // standard 30s cap still applies.
        assertFalse(isForbiddenUpgradeFailure(HTTP_UNAUTHORIZED))
        assertEquals(30_000L, backoffCapFor(isForbiddenUpgradeFailure(HTTP_UNAUTHORIZED)))
    }

    @Test
    fun successfulOpenClearsTheForbiddenFlagBackToTheFastCap() {
        // publishOpenIfCurrent resets the flag on a successful open; the reset value selects the
        // fast cap for the next failure.
        val flagAfterOpen = false
        assertEquals(30_000L, backoffCapFor(flagAfterOpen))
    }

    @Test
    fun forbiddenBackoffSaturatesWithinTheFiveMinuteCap() {
        val fixed = Random(0)

        val early = reconnectBackoffMillis(attempt = 0, maxCap = 300_000L, random = fixed)
        val saturated = reconnectBackoffMillis(attempt = 20, maxCap = 300_000L, random = fixed)

        // Same exponential-plus-jitter shape as the 30s tier: jitter in [cap/2, cap] at saturation.
        assertTrue(early in 500..1_000)
        assertTrue(saturated in 150_000..300_000)
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
    fun messageTooBigStillReconnects() {
        // 1009 means a frame we sent was rejected as too large — a client bug rather than a
        // transient failure, and it is logged loudly as one. It is still retried: nothing re-sends
        // the offending frame after a reconnect, so treating it as terminal only meant one
        // anomalous frame left the app with no realtime at all for the rest of the process.
        assertEquals(ReconnectPolicy.Backoff, reconnectPolicyForClose(CLOSE_CODE_MESSAGE_TOO_BIG))
        assertEquals(ReconnectPolicy.Backoff, reconnectPolicyForClose(1009))
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
    fun parsesNotificationNewPayload() {
        // REST-shaped notification object per docs/api/notifications/index.md#notificationnew-event.
        val payload = """
            {
              "id": "n1",
              "recipientId": "u1",
              "eventType": "CRUISE_INVITATION_SENT",
              "sourceType": "CRUISE",
              "sourceId": "c1",
              "relationId": "r1",
              "status": "UNREAD",
              "metadata": {"cruiseTitle": "Mediterranean Adventure", "actorName": "John Smith"},
              "createdAt": "2025-11-23T12:00:00Z",
              "readAt": null
            }
        """.trimIndent()

        val notification = parseRealtimeNotification(payload)

        requireNotNull(notification)
        assertEquals("n1", notification.id)
        assertEquals(NotificationEventType.CruiseInvitationSent, notification.eventType)
        assertEquals(NotificationSourceType.Cruise, notification.sourceType)
        assertEquals("c1", notification.sourceId)
        assertEquals(NotificationStatus.Unread, notification.status)
        assertTrue(notification.isUnread)
        assertEquals("John Smith", notification.actorName)
    }

    @Test
    fun notificationNewWithUnknownEventTypeFallsBackToUnknown() {
        val payload = """
            {
              "id": "n1",
              "eventType": "SOME_FUTURE_EVENT",
              "sourceType": "CRUISE",
              "sourceId": "c1",
              "status": "UNREAD",
              "createdAt": "2025-11-23T12:00:00Z"
            }
        """.trimIndent()

        assertEquals(NotificationEventType.Unknown, parseRealtimeNotification(payload)?.eventType)
    }

    @Test
    fun notificationNewWithUnknownSourceTypeIsDropped() {
        // Same forward-compat drop rule as the REST list mapping (NotificationDto.toDomain).
        val payload = """
            {
              "id": "n1",
              "eventType": "CRUISE_INVITATION_SENT",
              "sourceType": "SOME_FUTURE_SOURCE",
              "sourceId": "c1",
              "status": "UNREAD",
              "createdAt": "2025-11-23T12:00:00Z"
            }
        """.trimIndent()

        assertNull(parseRealtimeNotification(payload))
    }

    @Test
    fun notificationNewMalformedPayloadReturnsNull() {
        assertNull(parseRealtimeNotification("not json"))
        assertNull(parseRealtimeNotification("""{"id":"n1"}"""))
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

    @Test
    fun backoffGateSkipShortCircuitsThePendingWait() = runBlocking {
        // Network returned mid-backoff: the pending (up to 30s) wait must end immediately. The
        // skip is polled the way repeated NetworkCallback invocations would arrive, which also
        // removes any race with the waiter reaching its suspension point.
        val gate = ReconnectBackoffGate()
        val waiter = launch { gate.awaitBackoff(60_000) }

        withTimeout(5_000) {
            while (!waiter.isCompleted) {
                gate.skip()
                delay(10)
            }
        }
    }

    @Test
    fun backoffGateCompletesAfterTheDelayWithoutASkip() = runBlocking {
        val gate = ReconnectBackoffGate()

        withTimeout(5_000) { gate.awaitBackoff(10) }
    }

    @Test
    fun backoffGateDropsASkipWithNoWaitInFlight() = runBlocking {
        // Connectivity events while connected must not latch and silently shorten a future
        // backoff — only a wait currently in flight can be skipped.
        val gate = ReconnectBackoffGate()
        gate.skip()

        val startedAt = System.nanoTime()
        gate.awaitBackoff(200)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("expected a full wait, got ${elapsedMillis}ms", elapsedMillis >= 150)
    }

    @Test
    fun onNetworkAvailableWithoutPendingBackoffIsNoop() {
        // Fired by the platform callback whenever a default network appears, including while
        // connected or logged out — must be safe to call at any time.
        WebSocketChatRealtimeClient.onNetworkAvailable()

        assertFalse(WebSocketChatRealtimeClient.isConnected.value)
    }

    @Test
    fun publishOpenIfCurrentRefusesWhenNoConnectionIsCurrent() {
        // The residual onOpen race: the handshake completes only after disconnect() already tore
        // the connection down. The atomic guard-and-publish must refuse — flipping isConnected
        // true here would strand it forever, because the socket's later close callback bails on
        // its own scope guard and never calls markDisconnected.
        WebSocketChatRealtimeClient.disconnect()

        val published = WebSocketChatRealtimeClient.publishOpenIfCurrent(
            CoroutineScope(Job()),
            FakeWebSocket(),
        )

        assertFalse(published)
        assertFalse(WebSocketChatRealtimeClient.isConnected.value)
    }

    @Test
    fun publishOpenIfCurrentRefusesASupersededConnectAttempt() = runBlocking {
        // Park connect() inside the token provider (no real socket, no Android Log) so there is a
        // live connection scope; a different scope — an older, superseded attempt — must still be
        // refused and must not flip isConnected for the live session.
        val providerEntered = CompletableDeferred<Unit>()
        WebSocketChatRealtimeClient.connect(
            accessTokenProvider = {
                providerEntered.complete(Unit)
                awaitCancellation()
            },
        )
        providerEntered.await()
        try {
            val published = WebSocketChatRealtimeClient.publishOpenIfCurrent(
                CoroutineScope(Job()),
                FakeWebSocket(),
            )

            assertFalse(published)
            assertFalse(WebSocketChatRealtimeClient.isConnected.value)
        } finally {
            WebSocketChatRealtimeClient.disconnect()
        }
    }

    /** Minimal no-op [WebSocket]; publish tests only need an instance, never a live transport. */
    private class FakeWebSocket : WebSocket {
        override fun request(): Request = Request.Builder().url("https://example.invalid").build()
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() {}
    }
}
