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

    // The cache goes stale the moment the socket drops (down connection or logout, which
    // disconnects it), so clear it rather than leave users falsely lit as "online".
    ChatRealtimeEvent.Disconnected -> emptyMap()

    else -> current
}

/**
 * Whether [event] invalidates a presence snapshot fetch still in flight. Both connection
 * transitions do: [ChatRealtimeEvent.Connected] starts a new session (a seed launched for a
 * previous one must not apply to it), and [ChatRealtimeEvent.Disconnected] ends one — the map was
 * just cleared, and a snapshot landing afterwards would repopulate presence that is now unknown,
 * leaving users falsely "online" until the next reconnect (D-AN-2). Pure for unit testing, like
 * [presenceAfter].
 */
internal fun invalidatesInFlightPresenceSeed(event: ChatRealtimeEvent): Boolean = when (event) {
    ChatRealtimeEvent.Connected, ChatRealtimeEvent.Disconnected -> true
    else -> false
}

/**
 * Merges a `GET /chats/presence` [snapshot] into [current] under the snapshot-vs-live race rule:
 * a snapshot entry is applied only for a user NOT in [liveUpdatedSinceOpen] — the set of users that
 * received a live `presence:update` since the current connection opened. Live events always win, so
 * a snapshot that lands after a live event never regresses it. Pure for unit testing, like
 * [presenceAfter].
 */
internal fun seededPresence(
    current: Map<String, UserPresence>,
    snapshot: Map<String, UserPresence>,
    liveUpdatedSinceOpen: Set<String>,
): Map<String, UserPresence> {
    val next = current.toMutableMap()
    for ((userId, presence) in snapshot) {
        if (userId in liveUpdatedSinceOpen) continue
        next[userId] = presence
    }
    return next
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

    private var accessTokenProvider: suspend () -> String? = { null }
    private var snapshotProvider: suspend (String) -> Map<String, UserPresence> = { ChatsApi.presence(it) }

    // userIds that received a live presence:update since the current connection opened. The seed
    // fetched on connect is applied only to users NOT in this set (race rule). Confined to the store's
    // single Main-immediate thread, so a plain set is safe.
    private val liveUpdatedSinceOpen = mutableSetOf<String>()

    // Bumped on every Connected AND every Disconnected (see invalidatesInFlightPresenceSeed). A
    // seed fetch in flight when a newer connect or disconnect happens is dropped: a late snapshot
    // from a superseded connection must not overwrite fresh state, and one landing after a
    // disconnect must not repopulate the just-cleared map with stale "online" flags (D-AN-2).
    private var connectionEpoch = 0

    /**
     * @param accessTokenProvider fresh token for the snapshot fetch (e.g. [SessionStore.validSession]).
     * @param snapshotProvider fetches `GET /chats/presence`; overridable for tests.
     */
    fun start(
        realtime: ChatRealtimeClient = WebSocketChatRealtimeClient,
        accessTokenProvider: suspend () -> String? = { null },
        snapshotProvider: suspend (String) -> Map<String, UserPresence> = { ChatsApi.presence(it) },
    ) {
        if (started) return
        started = true
        this.accessTokenProvider = accessTokenProvider
        this.snapshotProvider = snapshotProvider
        scope.launch {
            realtime.events.collect { event -> onEvent(event) }
        }
    }

    private fun onEvent(event: ChatRealtimeEvent) {
        if (invalidatesInFlightPresenceSeed(event)) connectionEpoch += 1
        when (event) {
            // Live events always win: record the user so the seed skips them, then apply.
            is ChatRealtimeEvent.PresenceUpdate -> liveUpdatedSinceOpen.add(event.userId)
            // A fresh connection: reset the race set and seed from the REST snapshot. presenceAfter
            // leaves the map untouched for Connected — the seed (launched below) fills it.
            ChatRealtimeEvent.Connected -> {
                liveUpdatedSinceOpen.clear()
                val epoch = connectionEpoch
                scope.launch { seedFromSnapshot(epoch) }
            }
            // Clearing on disconnect resets the race set too (presenceAfter empties the map); the
            // epoch bump above additionally drops any seed fetch still in flight, so its snapshot
            // cannot repopulate the map we are about to clear.
            ChatRealtimeEvent.Disconnected -> liveUpdatedSinceOpen.clear()
            else -> Unit
        }
        _presence.update { presenceAfter(it, event) }
    }

    /**
     * Fetches the presence snapshot and merges it under the race rule. Runs concurrently with the
     * event collector (same single thread), so live events arriving during the fetch are recorded in
     * [liveUpdatedSinceOpen] and win over the snapshot. A failed fetch leaves state as "unknown",
     * never wrong; a stale [epoch] (a newer connect/disconnect happened meanwhile) is discarded.
     */
    private suspend fun seedFromSnapshot(epoch: Int) {
        val token = accessTokenProvider() ?: return
        val snapshot = runCatching { snapshotProvider(token) }.getOrNull() ?: return
        if (epoch != connectionEpoch) return
        _presence.update { seededPresence(it, snapshot, liveUpdatedSinceOpen) }
    }
}
