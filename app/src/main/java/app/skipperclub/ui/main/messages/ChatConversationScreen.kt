package app.skipperclub.ui.main.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatRealtimeEvent
import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatsError
import app.skipperclub.data.PresenceStore
import app.skipperclub.data.SessionStore
import app.skipperclub.data.UnreadMessagesStore
import app.skipperclub.data.UserPresence
import app.skipperclub.data.WebSocketChatRealtimeClient
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.theme.extended
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MILLIS = 5_000L

/**
 * Server-side message limit, counted in Unicode code points (the API validates runes, not UTF-16
 * units) — see docs/api/messages/websocket.md.
 */
internal const val MAX_MESSAGE_LENGTH = 1_000

/**
 * Truncate [text] to [maxCodePoints] Unicode code points.
 *
 * `String.take` would cut by UTF-16 units instead, which is wrong twice over: it counts an emoji as
 * two toward a limit the server counts as one, and — worse — a cut landing between a surrogate pair
 * splits the emoji, leaving a lone surrogate the user sees as `�` and the server rejects.
 */
internal fun truncateToCodePoints(text: String, maxCodePoints: Int): String {
    if (text.codePointCount(0, text.length) <= maxCodePoints) return text
    return text.substring(0, text.offsetByCodePoints(0, maxCodePoints))
}

/**
 * Typing indicator timings, unified with web/iOS (see docs/api/messages/websocket.md):
 * while the user keeps typing we re-send `chat:typing {isTyping:true}` every [TYPING_KEEPALIVE_MS];
 * [TYPING_IDLE_STOP_MS] after the last keystroke we send `{isTyping:false}` once and stop the
 * keepalive. The receiver's expiry ([ChatConversationController.TYPING_RECEIVE_EXPIRY_MS], 5s) is
 * longer than the keepalive so a still-typing peer never flickers off between beats.
 */
private const val TYPING_KEEPALIVE_MS = 2_000L
private const val TYPING_IDLE_STOP_MS = 3_000L

@Composable
fun ChatConversationScreen(
    chatId: String,
    currentUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope, chatId) {
        ChatConversationController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            chatId = chatId,
            currentUserId = currentUserId,
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    var inputText by rememberSaveable(chatId) { mutableStateOf("") }

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val errorNetworkMessage = stringResource(R.string.messages_error_network)
    val errorAuthMessage = stringResource(R.string.messages_error_auth)
    val errorGenericMessage = stringResource(R.string.messages_error_generic)

    LaunchedEffect(controller) {
        controller.loadInitialIfNeeded()
    }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is ChatConversationEvent.OperationFailed -> notificationHostState.show(
                    when (event.error) {
                        is ChatsError.Network -> errorNetworkMessage
                        is ChatsError.AuthenticationRequired -> errorAuthMessage
                        else -> errorGenericMessage
                    },
                    InAppNotificationType.Error,
                )

                ChatConversationEvent.SessionExpired ->
                    notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)

                ChatConversationEvent.MessageSent -> Unit

                // Put the draft back so the text is never lost. Only into an input the user has
                // not started refilling — the failed bubble keeps its own copy and its retry,
                // so clobbering a newer draft would trade one lost message for another.
                is ChatConversationEvent.SendFailed ->
                    if (inputText.isBlank()) inputText = event.text
            }
        }
    }
    // Live messages arrive over the shared socket (connected by MessagesScreen);
    // this screen only joins/leaves its chat room and re-joins after reconnects.
    val realtime = remember { WebSocketChatRealtimeClient }
    val realtimeConnected by realtime.isConnected.collectAsState()

    // Typing indicator (send side): the first keystroke of a burst sends `isTyping:true` once
    // (guarded by [typingSent]) and starts a keepalive that re-sends `isTyping:true` every
    // TYPING_KEEPALIVE_MS so the peer's indicator never expires while typing continues. Each further
    // keystroke only resets the idle timer; after TYPING_IDLE_STOP_MS of inactivity, on send, or on
    // dispose we send `isTyping:false` once and cancel the keepalive, mirroring iOS/Web. Not
    // `rememberSaveable` — it is fine, even desirable, to re-arm this after a process death/config
    // change since the server has no memory of it either.
    var typingSent by remember(chatId) { mutableStateOf(false) }
    var typingIdleJob by remember(chatId) { mutableStateOf<Job?>(null) }
    var typingKeepaliveJob by remember(chatId) { mutableStateOf<Job?>(null) }
    fun stopTyping() {
        typingIdleJob?.cancel()
        typingIdleJob = null
        typingKeepaliveJob?.cancel()
        typingKeepaliveJob = null
        if (typingSent) {
            typingSent = false
            realtime.sendTyping(chatId, isTyping = false)
        }
    }

    DisposableEffect(realtime, chatId) {
        realtime.joinChat(chatId)
        // Messages arriving in the chat on screen are read as they land, so they must not bump the
        // app-wide badge — otherwise it counts up while the user watches the very conversation it
        // is counting, and only settles when the screen closes and reconciles.
        UnreadMessagesStore.setActiveChat(chatId)
        onDispose {
            UnreadMessagesStore.setActiveChat(null)
            typingIdleJob?.cancel()
            typingKeepaliveJob?.cancel()
            // Send the typing-stop before leaving: the server drops typing frames for a room we
            // have already left, which would strand the peer's indicator on the 5s receive expiry.
            if (typingSent) realtime.sendTyping(chatId, isTyping = false)
            // Same ordering constraint for a mark-read still sitting in its debounce window:
            // flush it (WS receipt synchronously, REST on a scope that survives this screen)
            // before chat:leave, or closing the conversation within the window silently loses
            // the receipt.
            controller.flushPendingMarkRead()
            realtime.leaveChat(chatId)
            // Last: the flush above is already launched on the controller's own surviving scope,
            // and close() only arms a grace timer on it — so the REST mark-read still commits,
            // while the scope no longer outlives the screen indefinitely.
            controller.close()
        }
    }
    LaunchedEffect(controller, realtime) {
        realtime.events.collect { event ->
            applyConversationRealtimeEvent(
                event = event,
                chatId = chatId,
                controller = controller,
                // Skipped when the room is already in the client's joined set. On a server-side
                // drop the set survives, and membership guarantees a `chat:join` was or will be
                // sent on this very connection — by the onOpen replay (which runs on the OkHttp
                // thread and may finish before OR after this collector observes Connected) or by
                // the joinChat call that added the room once the socket was up — so the skip can
                // never lose the trigger; it only closes the window in which Connected was
                // processed mid-replay and a pending-ack check still read false, firing a
                // duplicate join and a duplicate catch-up. After a deliberate disconnect
                // (backgrounding/logout) the set was cleared, so the rejoin proceeds.
                rejoinChat = { id -> if (!realtime.isRoomJoined(id)) realtime.joinChat(id) },
            )
        }
    }
    // REST poll as a fallback while the socket is down. Same entry point as the rejoin catch-up —
    // a poll tick is just a catch-up whose first page usually overlaps immediately.
    //
    // Gated on the app being foregrounded (AN-2): a LaunchedEffect keeps running while the Activity
    // is stopped, so without this the socket-down poll would keep firing catch-up — and its
    // mark-read — in the background, draining battery/network and marking messages the user never
    // saw as "seen" for the peer. repeatOnLifecycle pauses the loop below STARTED and resumes it on
    // foreground; ProcessLifecycleOwner tracks the whole app, matching the socket's own lifecycle.
    LaunchedEffect(controller, realtimeConnected) {
        if (!realtimeConnected) {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(POLL_INTERVAL_MILLIS)
                    controller.catchUp()
                }
            }
        }
    }

    val presenceByUserId by PresenceStore.presence.collectAsState()
    val otherParticipantPresence = state.chat?.let { chat ->
        otherParticipants(chat, currentUserId).singleOrNull()?.let { presenceByUserId[it.id] }
    }

    BackHandler(onBack = onClose)

    Box(modifier = modifier.fillMaxSize()) {
        ChatConversationScreenContent(
            state = state,
            currentUserId = currentUserId,
            inputText = inputText,
            nowMillis = nowMillis,
            otherParticipantPresence = otherParticipantPresence,
            onInputChange = {
                inputText = truncateToCodePoints(it, MAX_MESSAGE_LENGTH)
                if (!typingSent) {
                    typingSent = true
                    realtime.sendTyping(chatId, isTyping = true)
                    typingKeepaliveJob = scope.launch {
                        while (true) {
                            delay(TYPING_KEEPALIVE_MS)
                            realtime.sendTyping(chatId, isTyping = true)
                        }
                    }
                }
                // Each keystroke only restarts the idle timer; the keepalive keeps beating.
                typingIdleJob?.cancel()
                typingIdleJob = scope.launch {
                    delay(TYPING_IDLE_STOP_MS)
                    typingKeepaliveJob?.cancel()
                    typingKeepaliveJob = null
                    typingSent = false
                    realtime.sendTyping(chatId, isTyping = false)
                }
            },
            onSend = {
                // The draft is cleared only once the controller has accepted the send — and put
                // back by ChatConversationEvent.SendFailed if the send then fails, so a failure
                // can no longer silently destroy what the user typed.
                if (controller.send(inputText) != null) inputText = ""
                stopTyping()
            },
            onRetrySend = controller::retrySend,
            onLoadMore = controller::loadMore,
            onRetry = controller::retry,
            onClose = onClose,
        )
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Applies one realtime event to the open conversation. Extracted from [ChatConversationScreen]'s
 * collector so the reconnect → rejoin → `chat:joined` → catch-up cycle is unit-testable without
 * composition (same reason [ChatListRealtimeEffect] exists for the list).
 *
 * [rejoinChat] is invoked on every [ChatRealtimeEvent.Connected]. A deliberate `disconnect()`
 * (app backgrounded, logout) clears the client's joined-room set, so the client's own onOpen
 * replay cannot restore this room after a background→foreground cycle — without this call the
 * conversation would never re-join, never receive `ChatJoined`, and therefore never catch up on
 * messages sent while the app was in the background (C-AN-1/D-AN-1). `joinChat` is idempotent and
 * ack-tracked, and it is the resulting [ChatRealtimeEvent.ChatJoined] — not `Connected` itself —
 * that drives the catch-up: the join replay runs after `Connected`, so a message created between
 * a Connected-triggered fetch and the server processing our `chat:join` would be in neither.
 */
internal fun applyConversationRealtimeEvent(
    event: ChatRealtimeEvent,
    chatId: String,
    controller: ChatConversationController,
    rejoinChat: (String) -> Unit,
) {
    when (event) {
        is ChatRealtimeEvent.MessageNew -> controller.onRealtimeMessage(event.message)
        is ChatRealtimeEvent.MessageReceived -> controller.onRealtimeMessage(event.message)
        // Catch up on anything missed while the socket was down — keyed off the room ack,
        // not off Connected; see the kdoc above.
        is ChatRealtimeEvent.ChatJoined -> if (event.chatId == chatId) controller.catchUp()
        // The socket being up says nothing about this room being joined — and after a deliberate
        // disconnect nothing else re-joins it — so ensure membership here; see the kdoc above.
        ChatRealtimeEvent.Connected -> rejoinChat(chatId)
        // A peer typing when the socket dropped never gets to send `isTyping:false`, and
        // the receive-expiry timers are not a dependable backstop across a recomposition.
        ChatRealtimeEvent.Disconnected -> controller.onRealtimeDisconnected()
        // Consumed app-wide by UnreadNotificationsStore (badge) and by the
        // notification center while it is open; nothing to do in a conversation.
        is ChatRealtimeEvent.NotificationNew -> Unit
        is ChatRealtimeEvent.TypingUpdate ->
            controller.onRealtimeTyping(event.chatId, event.userId, event.isTyping)

        // `readAt` matters: receipts cascade, and the anchor message is often one we never
        // loaded — the timestamp is then the only thing that says which own bubbles were
        // seen (ChatConversationController.onRealtimeMessageRead).
        is ChatRealtimeEvent.MessageRead ->
            controller.onRealtimeMessageRead(event.messageId, event.userId, event.readAt)

        // Presence is app-wide (PresenceStore); this screen only reads it.
        is ChatRealtimeEvent.PresenceUpdate -> Unit
        // Logged in ChatRealtimeClient; surfaced to the user from MessagesScreen, which
        // stays composed underneath this dialog for the lifetime of the socket.
        is ChatRealtimeEvent.ServerError -> Unit
    }
}

@Composable
internal fun ChatConversationScreenContent(
    state: ChatConversationUiState,
    currentUserId: String?,
    inputText: String,
    nowMillis: Long,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetrySend: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    otherParticipantPresence: UserPresence? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_conversation"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            ConversationTopBar(
                state = state,
                currentUserId = currentUserId,
                otherParticipantPresence = otherParticipantPresence,
                nowMillis = nowMillis,
                onClose = onClose,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.loadFailed && state.messages.isEmpty() -> Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.conversation_load_failed),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(stringResource(R.string.messages_retry))
                        }
                    }

                    state.messages.isEmpty() && state.hasLoadedOnce -> Text(
                        text = stringResource(R.string.conversation_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    else -> MessagesList(
                        state = state,
                        currentUserId = currentUserId,
                        nowMillis = nowMillis,
                        onLoadMore = onLoadMore,
                        onRetrySend = onRetrySend,
                    )
                }
            }

            TypingIndicator(state = state)

            MessageInputBar(
                inputText = inputText,
                isSending = state.isSending,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }
    }
}

/** "X is typing…" for the other participant(s), resolved from [ChatConversationUiState.typingUserIds]. */
@Composable
private fun TypingIndicator(state: ChatConversationUiState) {
    val typingNames = remember(state.typingUserIds, state.chat) {
        state.chat?.participants
            ?.filter { it.id in state.typingUserIds }
            ?.map { it.name }
            .orEmpty()
    }
    if (typingNames.isEmpty()) return
    Text(
        text = if (typingNames.size == 1) {
            stringResource(R.string.conversation_typing_one, typingNames.first())
        } else {
            stringResource(R.string.conversation_typing_multiple)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
            .testTag("conversation_typing_indicator"),
    )
}

@Composable
private fun ConversationTopBar(
    state: ChatConversationUiState,
    currentUserId: String?,
    otherParticipantPresence: UserPresence?,
    nowMillis: Long,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.testTag("conversation_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.conversation_back),
            )
        }
        val chat = state.chat
        if (chat != null) {
            ChatListAvatar(
                participants = otherParticipants(chat, currentUserId),
                size = 40.dp,
                isOnline = otherParticipantPresence?.isOnline == true,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = chatTitle(chat, currentUserId)
                        ?: stringResource(R.string.messages_title_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chat.type != ChatType.OneToOne) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.conversation_participants,
                            chat.participants.size,
                            chat.participants.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    when (val status = presenceStatus(otherParticipantPresence, nowMillis)) {
                        PresenceStatus.Online -> Text(
                            text = stringResource(R.string.presence_online),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extended.success,
                        )

                        is PresenceStatus.LastSeen -> Text(
                            text = stringResource(R.string.presence_last_seen, status.relativeTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        null -> Unit
                    }
                }
            }
        }
    }
}

/** One renderable row of the conversation, oldest first. */
private sealed interface ConversationRow {
    val key: String

    data class DaySeparator(val day: LocalDate, override val key: String) : ConversationRow
    data class MessageRow(
        val message: ChatMessage,
        val isOwn: Boolean,
        val showHeader: Boolean,
        /** Delivery state of an own optimistic send; null for anything already on the server. */
        val sendStatus: MessageSendStatus?,
        override val key: String,
    ) : ConversationRow
}

private fun buildRows(
    messages: List<ChatMessage>,
    currentUserId: String?,
    isGroupChat: Boolean,
    sendStatusByClientMessageId: Map<String, MessageSendStatus>,
): List<ConversationRow> = buildList {
    var previousDay: LocalDate? = null
    var previousSenderId: String? = null
    messages.forEach { message ->
        val day = messageDay(message.createdAt)
        if (day != null && day != previousDay) {
            add(ConversationRow.DaySeparator(day, key = "day-$day"))
            previousSenderId = null
        }
        previousDay = day ?: previousDay
        val isOwn = message.user.id == currentUserId
        add(
            ConversationRow.MessageRow(
                message = message,
                isOwn = isOwn,
                showHeader = isGroupChat && !isOwn && message.user.id != previousSenderId,
                sendStatus = if (isOwn) {
                    message.clientMessageId?.let { sendStatusByClientMessageId[it] }
                } else {
                    null
                },
                // Keyed on the client id where there is one, so the row survives the optimistic id
                // being swapped for the server id — a changing key re-animates the bubble as if it
                // were a brand new message.
                key = message.clientMessageId ?: message.id,
            ),
        )
        previousSenderId = message.user.id
    }
}

@Composable
private fun MessagesList(
    state: ChatConversationUiState,
    currentUserId: String?,
    nowMillis: Long,
    onLoadMore: () -> Unit,
    onRetrySend: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val isGroupChat = state.chat?.type != ChatType.OneToOne
    val rows = remember(state.messages, currentUserId, isGroupChat, state.sendStatusByClientMessageId) {
        buildRows(
            messages = state.messages,
            currentUserId = currentUserId,
            isGroupChat = isGroupChat,
            sendStatusByClientMessageId = state.sendStatusByClientMessageId,
        ).asReversed()
    }

    val shouldLoadMore by remember(state.hasMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    // Keep the newest message in view when one arrives while we're at the bottom.
    // A new message can insert two rows at once (day separator + bubble), and the
    // list anchors to the previously visible key, so allow that much drift.
    val newestKey = rows.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier
            .fillMaxSize()
            .testTag("conversation_messages"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ConversationRow.DaySeparator -> Text(
                    text = messageDayLabel(row.day, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                is ConversationRow.MessageRow -> MessageBubble(row = row, onRetrySend = onRetrySend)
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 8.dp)
                            .size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(row: ConversationRow.MessageRow, onRetrySend: (String) -> Unit) {
    val message = row.message
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (row.isOwn) Alignment.End else Alignment.Start,
    ) {
        if (row.showHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
            ) {
                ChatAvatar(
                    user = message.user,
                    modifier = Modifier.size(20.dp),
                    textStyle = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = message.user.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                // Subdued until the server confirms it, so a bubble that is only on this device
                // never reads as delivered.
                .alpha(if (row.sendStatus == MessageSendStatus.Sending) 0.5f else 1f)
                .background(
                    color = if (row.isOwn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (row.isOwn) 18.dp else 4.dp,
                        bottomEnd = if (row.isOwn) 4.dp else 18.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.isOwn) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val footerColor = if (row.isOwn) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                ) {
                    Text(
                        text = messageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = footerColor,
                    )
                    // The `read` flag is a single boolean (see docs/api/openapi.yaml ChatMessage),
                    // kept live by `onRealtimeMessageRead` on `message:read`; own messages only, so
                    // it does not misleadingly claim the *other* side's message was read by us.
                    if (row.isOwn && message.read) {
                        Text(
                            text = stringResource(R.string.conversation_seen),
                            style = MaterialTheme.typography.labelSmall,
                            color = footerColor,
                            modifier = Modifier.testTag("message_seen_${message.id}"),
                        )
                    }
                }
            }
        }
        if (row.sendStatus == MessageSendStatus.Failed) {
            SendFailedAffordance(
                clientMessageId = message.clientMessageId,
                onRetrySend = onRetrySend,
            )
        }
    }
}

/**
 * "Not sent · Retry" under a failed own bubble. The bubble itself keeps the message, so retrying
 * re-sends it under the same `clientMessageId` — the server dedupes on that key, which is what
 * makes a retry safe even when the original request did reach it.
 */
@Composable
private fun SendFailedAffordance(clientMessageId: String?, onRetrySend: (String) -> Unit) {
    if (clientMessageId == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(top = 2.dp)
            .testTag("message_failed_$clientMessageId"),
    ) {
        Text(
            text = stringResource(R.string.conversation_send_failed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = { onRetrySend(clientMessageId) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.testTag("message_retry_$clientMessageId"),
        ) {
            Text(
                text = stringResource(R.string.conversation_send_retry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    inputText: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .testTag("conversation_input"),
            placeholder = { Text(stringResource(R.string.conversation_input_placeholder)) },
            shape = MaterialTheme.shapes.extraLarge,
            maxLines = 5,
        )
        IconButton(
            onClick = onSend,
            // Only a blank input disables sending. Gating on [isSending] too would block exactly
            // the burst that optimistic bubbles are meant to support: the previous message is
            // already rendered as its own pending bubble, so there is nothing to wait for.
            enabled = inputText.isNotBlank(),
            modifier = Modifier
                .padding(start = 4.dp)
                .testTag("conversation_send"),
        ) {
            // ...which is also why the in-flight spinner only shows while there is nothing to
            // send: a user composing the next message needs an actionable send button, not a
            // spinner reporting on the previous one.
            if (isSending && inputText.isBlank()) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.conversation_send),
                    tint = if (inputText.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun previewMessage(
    id: String,
    text: String,
    userIndex: Int,
    createdAt: String = "2026-06-12T10:30:00Z",
): ChatMessage = ChatMessage(
    id = id,
    chatId = "c1",
    text = text,
    read = true,
    user = previewChatUsers[userIndex],
    createdAt = createdAt,
    updatedAt = createdAt,
)

private val previewConversationState = ChatConversationUiState(
    chat = previewChat("c1", type = ChatType.Group, name = "Summer Sailing Crew"),
    messages = listOf(
        previewMessage("m1", "What time should I arrive?", 1, "2026-06-11T14:25:00Z"),
        previewMessage("m2", "Around 9:00, the briefing starts at 9:30.", 0, "2026-06-11T14:30:00Z"),
        previewMessage("m3", "Perfect, see you at the marina!", 1, "2026-06-12T08:05:00Z"),
        previewMessage("m4", "Don't forget your sailing licenses 😀", 2, "2026-06-12T08:06:00Z"),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun ChatConversationPreview() {
    SkipperClubTheme {
        ChatConversationScreenContent(
            state = previewConversationState,
            currentUserId = "u1",
            inputText = "",
            nowMillis = 1_775_000_000_000,
            onInputChange = {},
            onSend = {},
            onRetrySend = {},
            onLoadMore = {},
            onRetry = {},
            onClose = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatConversationPreviewDark() {
    SkipperClubTheme {
        ChatConversationScreenContent(
            state = previewConversationState,
            currentUserId = "u1",
            inputText = "",
            nowMillis = 1_775_000_000_000,
            onInputChange = {},
            onSend = {},
            onRetrySend = {},
            onLoadMore = {},
            onRetry = {},
            onClose = {},
        )
    }
}

/** Own bubble that only exists on this device yet — what [ChatConversationController.send] inserts. */
private fun previewOptimisticMessage(
    clientMessageId: String,
    text: String,
    createdAt: String,
): ChatMessage = previewMessage(
    id = ChatConversationController.OPTIMISTIC_ID_PREFIX + clientMessageId,
    text = text,
    userIndex = 0,
    createdAt = createdAt,
).copy(read = false, clientMessageId = clientMessageId)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun ChatConversationPreviewPl() {
    SkipperClubTheme {
        ChatConversationScreenContent(
            state = previewConversationState.copy(
                messages = previewConversationState.messages +
                    previewOptimisticMessage("cid-failed", "Spóźnię się 15 minut", "2026-06-12T08:07:00Z") +
                    previewOptimisticMessage("cid-sending", "Już jestem w drodze", "2026-06-12T08:08:00Z"),
                isSending = true,
                typingUserIds = setOf(previewChatUsers[1].id),
                sendStatusByClientMessageId = mapOf(
                    "cid-failed" to MessageSendStatus.Failed,
                    "cid-sending" to MessageSendStatus.Sending,
                ),
            ),
            currentUserId = "u1",
            inputText = "Do zobaczenia na przystani!",
            nowMillis = 1_775_000_000_000,
            onInputChange = {},
            onSend = {},
            onRetrySend = {},
            onLoadMore = {},
            onRetry = {},
            onClose = {},
        )
    }
}

private val previewOneToOneConversationState = ChatConversationUiState(
    chat = previewChat("c4", type = ChatType.OneToOne),
    messages = listOf(
        previewMessage("m1", "Are we still on for Saturday?", 1, "2026-06-12T08:00:00Z"),
        previewMessage("m2", "Yes, see you at the marina!", 0, "2026-06-12T08:02:00Z"),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun ChatConversationPreviewOnline() {
    SkipperClubTheme {
        ChatConversationScreenContent(
            state = previewOneToOneConversationState,
            currentUserId = "u1",
            inputText = "",
            nowMillis = 1_775_000_000_000,
            otherParticipantPresence = UserPresence(isOnline = true),
            onInputChange = {},
            onSend = {},
            onRetrySend = {},
            onLoadMore = {},
            onRetry = {},
            onClose = {},
        )
    }
}
