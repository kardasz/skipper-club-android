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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private const val MAX_MESSAGE_LENGTH = 1_000

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
        onDispose {
            typingIdleJob?.cancel()
            typingKeepaliveJob?.cancel()
            // Send the typing-stop before leaving: the server drops typing frames for a room we
            // have already left, which would strand the peer's indicator on the 5s receive expiry.
            if (typingSent) realtime.sendTyping(chatId, isTyping = false)
            realtime.leaveChat(chatId)
        }
    }
    LaunchedEffect(controller, realtime) {
        realtime.events.collect { event ->
            when (event) {
                is ChatRealtimeEvent.MessageNew -> controller.onRealtimeMessage(event.message)
                is ChatRealtimeEvent.MessageReceived -> controller.onRealtimeMessage(event.message)
                // Catch up on anything missed while the socket was down.
                ChatRealtimeEvent.Connected -> controller.refreshNewMessages()
                ChatRealtimeEvent.Disconnected -> Unit
                // Consumed app-wide by UnreadNotificationsStore (badge) and by the
                // notification center while it is open; nothing to do in a conversation.
                is ChatRealtimeEvent.NotificationNew -> Unit
                is ChatRealtimeEvent.TypingUpdate ->
                    controller.onRealtimeTyping(event.chatId, event.userId, event.isTyping)

                is ChatRealtimeEvent.MessageRead ->
                    controller.onRealtimeMessageRead(event.messageId, event.userId)

                // Presence is app-wide (PresenceStore); this screen only reads it below.
                is ChatRealtimeEvent.PresenceUpdate -> Unit
                // Logged in ChatRealtimeClient; surfaced to the user from MessagesScreen, which
                // stays composed underneath this dialog for the lifetime of the socket.
                is ChatRealtimeEvent.ServerError -> Unit
            }
        }
    }
    // REST poll as a fallback while the socket is down.
    LaunchedEffect(controller, realtimeConnected) {
        if (!realtimeConnected) {
            while (true) {
                delay(POLL_INTERVAL_MILLIS)
                controller.refreshNewMessages()
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
                inputText = it.take(MAX_MESSAGE_LENGTH)
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
                controller.send(inputText)
                inputText = ""
                stopTyping()
            },
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

@Composable
internal fun ChatConversationScreenContent(
    state: ChatConversationUiState,
    currentUserId: String?,
    inputText: String,
    nowMillis: Long,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
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
        override val key: String,
    ) : ConversationRow
}

private fun buildRows(
    messages: List<ChatMessage>,
    currentUserId: String?,
    isGroupChat: Boolean,
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
                key = message.id,
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
) {
    val listState = rememberLazyListState()
    val isGroupChat = state.chat?.type != ChatType.OneToOne
    val rows = remember(state.messages, currentUserId, isGroupChat) {
        buildRows(state.messages, currentUserId, isGroupChat).asReversed()
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

                is ConversationRow.MessageRow -> MessageBubble(row = row)
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
private fun MessageBubble(row: ConversationRow.MessageRow) {
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
            enabled = inputText.isNotBlank() && !isSending,
            modifier = Modifier
                .padding(start = 4.dp)
                .testTag("conversation_send"),
        ) {
            if (isSending) {
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
            onLoadMore = {},
            onRetry = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun ChatConversationPreviewPl() {
    SkipperClubTheme {
        ChatConversationScreenContent(
            state = previewConversationState.copy(
                isSending = true,
                typingUserIds = setOf(previewChatUsers[1].id),
            ),
            currentUserId = "u1",
            inputText = "Do zobaczenia na przystani!",
            nowMillis = 1_775_000_000_000,
            onInputChange = {},
            onSend = {},
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
            onLoadMore = {},
            onRetry = {},
            onClose = {},
        )
    }
}
