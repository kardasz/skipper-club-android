package app.skipperclub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Optimistic badge count after a realtime [event]: `message:received` bumps the count for instant
 * feedback; every other event returns `null`, meaning "no optimistic change" (the caller reconciles
 * from the server on [ChatRealtimeEvent.Connected]). Extracted as a pure function so the increment
 * rule is unit-testable without the singleton's coroutine machinery.
 *
 * [activeChatId] is the conversation the user currently has open, if any. Messages arriving there
 * are being read as they land — the screen marks them read immediately — so counting them shows the
 * user a badge for a message they are looking at, which then disappears on its own a moment later.
 */
internal fun unreadCountAfter(
    current: Int,
    event: ChatRealtimeEvent,
    activeChatId: String? = null,
): Int? = when {
    event !is ChatRealtimeEvent.MessageReceived -> null
    event.message.chatId == activeChatId -> null
    else -> current + 1
}

/**
 * App-wide unread-messages counter that drives the bottom-nav Messages badge. It is deliberately
 * decoupled from the Messages tab: the realtime socket is held app-wide by
 * [RealtimeConnectionManager], but the Messages tab (and its [app.skipperclub.ui.main.messages.ChatListController])
 * only exists while that tab is composed, so it cannot be the badge source. This singleton observes
 * the same [ChatRealtimeClient] and keeps a count that updates everywhere in the app.
 *
 * The count is optimistic-plus-reconciled: `message:received` bumps it instantly for live feedback,
 * and [refresh] re-reads the authoritative `GET /chats/unread-count` on connect, on login, and when
 * the user changes read state (opening a chat, marking read) — so any drift from the optimistic
 * increment self-heals. Started once from [app.skipperclub.SkipperClubApplication].
 */
object UnreadMessagesStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var accessTokenProvider: suspend () -> String? = { null }

    @Volatile
    private var started = false

    /**
     * The chat the user currently has open, or null. Set by the conversation screen for as long as
     * it is on screen, so live messages the user is already reading don't bump the badge.
     */
    @Volatile
    private var activeChatId: String? = null

    /** Marks [chatId] as the open conversation; pass null when it closes. */
    fun setActiveChat(chatId: String?) {
        activeChatId = chatId
    }

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
                unreadCountAfter(_count.value, event, activeChatId)?.let { next -> _count.value = next }
                if (event is ChatRealtimeEvent.Connected) refresh()
            }
        }
    }

    /** Reconciles the badge with the authoritative server count; a failed fetch leaves it unchanged. */
    fun refresh() {
        scope.launch {
            val token = accessTokenProvider() ?: return@launch
            runCatching { ChatsApi.unreadCount(token) }.onSuccess { _count.value = it }
        }
    }
}
