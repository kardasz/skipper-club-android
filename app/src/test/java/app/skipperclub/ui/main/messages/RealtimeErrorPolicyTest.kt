package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatRealtimeEvent
import app.skipperclub.data.authGaveUpEvent
import app.skipperclub.data.joinFailedEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which server `error` frames get the user-facing realtime-error notice (D-AN-4). */
class RealtimeErrorPolicyTest {

    private fun serverError(message: String, type: String = "websocket_error") =
        ChatRealtimeEvent.ServerError(
            type = type,
            message = message,
            timestamp = "2026-07-24T12:00:00Z",
        )

    @Test
    fun backpressureErrorsAreSuppressed() {
        // Parity with web: "Rate limit exceeded" is the one WS error never toasted — it signals
        // the client outran the server's inbound limit, nothing the user caused or can act on.
        assertFalse(shouldSurfaceRealtimeError(serverError("Rate limit exceeded")))
    }

    @Test
    fun genuineServerErrorsAreSurfaced() {
        assertTrue(shouldSurfaceRealtimeError(serverError("Chat not found or access denied")))
        assertTrue(shouldSurfaceRealtimeError(serverError("Invalid payload")))
        assertTrue(shouldSurfaceRealtimeError(serverError("Internal server error")))
    }

    @Test
    fun clientMintedErrorsAreSurfaced() {
        // The synthetic join-failure and the auth breaker's give-up must stay visible — the
        // give-up in particular is the whole point of C-AN-2 (web banner / iOS alert parity).
        assertTrue(shouldSurfaceRealtimeError(joinFailedEvent("chat-1")))
        assertTrue(shouldSurfaceRealtimeError(authGaveUpEvent()))
    }
}
