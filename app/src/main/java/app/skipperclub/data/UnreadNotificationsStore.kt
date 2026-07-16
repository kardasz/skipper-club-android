package app.skipperclub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Optimistic badge count after a realtime [event]: an unread `notification:new` bumps the count for
 * instant feedback; every other event returns `null`, meaning "no optimistic change" (the caller
 * reconciles from the server on [ChatRealtimeEvent.Connected]). Extracted as a pure function so the
 * increment rule is unit-testable without the singleton's coroutine machinery (mirrors
 * [unreadCountAfter] in `UnreadMessagesStore.kt`).
 */
internal fun unreadNotificationsCountAfter(current: Int, event: ChatRealtimeEvent): Int? =
    when {
        event is ChatRealtimeEvent.NotificationNew && event.notification.isUnread -> current + 1
        else -> null
    }

/**
 * App-wide unread-notifications counter that drives the notifications badge. Like
 * [UnreadMessagesStore] it is deliberately decoupled from the notification center screen: the
 * realtime socket is held app-wide by [RealtimeConnectionManager], but the
 * [app.skipperclub.ui.main.notifications.NotificationsController] only exists while the dialog is
 * composed, so it cannot be the badge source. This singleton observes the same [ChatRealtimeClient]
 * and keeps a count that updates everywhere in the app.
 *
 * The count is optimistic-plus-reconciled: `notification:new` bumps it instantly for live feedback,
 * and [refresh] re-reads the authoritative `GET /notifications/unread-count` on connect, on login,
 * and when the user changes read state (opening the notification center, marking read) — so any
 * drift from the optimistic increment self-heals. Started once from
 * [app.skipperclub.SkipperClubApplication].
 */
object UnreadNotificationsStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var accessTokenProvider: suspend () -> String? = { null }

    @Volatile
    private var started = false

    /**
     * @param sessionFlow authentication signal — logout resets the badge, login reconciles it.
     * @param accessTokenProvider fresh token for the reconciliation fetch (e.g. [SessionStore.validSession]).
     */
    fun start(
        realtime: ChatRealtimeClient = WebSocketChatRealtimeClient,
        sessionFlow: StateFlow<SessionResponse?>,
        accessTokenProvider: suspend () -> String?,
    ) {
        if (started) return
        started = true
        this.accessTokenProvider = accessTokenProvider

        scope.launch {
            sessionFlow.collect { session ->
                if (session == null) _count.value = 0 else refresh()
            }
        }
        scope.launch {
            realtime.events.collect { event ->
                unreadNotificationsCountAfter(_count.value, event)?.let { next -> _count.value = next }
                if (event is ChatRealtimeEvent.Connected) refresh()
            }
        }
    }

    /** Reconciles the badge with the authoritative server count; a failed fetch leaves it unchanged. */
    fun refresh() {
        scope.launch {
            val token = accessTokenProvider() ?: return@launch
            runCatching { NotificationsApi.unreadCount(token) }.onSuccess { _count.value = it }
        }
    }
}
