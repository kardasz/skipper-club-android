package app.skipperclub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Last known online/offline state for a user, as reported by `presence:update`. */
data class UserPresence(
    val isOnline: Boolean,
    val lastSeen: String? = null,
)

/**
 * Applies a realtime event to the known presence map; only `presence:update` mutates state, so
 * this is a pure function extracted for unit testing without the singleton's coroutine machinery
 * (mirrors [unreadCountAfter] in `UnreadMessagesStore.kt`).
 */
internal fun presenceAfter(
    current: Map<String, UserPresence>,
    event: ChatRealtimeEvent,
): Map<String, UserPresence> = when (event) {
    is ChatRealtimeEvent.PresenceUpdate ->
        current + (event.userId to UserPresence(isOnline = event.isOnline, lastSeen = event.lastSeen))

    else -> current
}

/**
 * App-wide presence cache fed by `presence:update` frames on the shared socket ([RealtimeConnectionManager]),
 * which the server delivers to a user's personal room for every chat co-participant (see
 * docs/api/messages/websocket.md#presence-semantics). Started once from
 * [app.skipperclub.SkipperClubApplication], like [UnreadMessagesStore], so presence keeps updating
 * regardless of which screen is on top; screens read [presence] for the participants they render
 * (the conversation header, chat list rows).
 */
object PresenceStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _presence = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val presence: StateFlow<Map<String, UserPresence>> = _presence.asStateFlow()

    @Volatile
    private var started = false

    fun start(realtime: ChatRealtimeClient = WebSocketChatRealtimeClient) {
        if (started) return
        started = true
        scope.launch {
            realtime.events.collect { event ->
                _presence.update { presenceAfter(it, event) }
            }
        }
    }
}
