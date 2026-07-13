package app.skipperclub.ui.main.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.Chat
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatRealtimeEvent
import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatUser
import app.skipperclub.data.ChatsError
import app.skipperclub.data.SessionStore
import app.skipperclub.data.WebSocketChatRealtimeClient
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.delay

/** Bottom inset that keeps the list clear of the floating [SkipperBottomBar]. */
private val ListBottomInset = 120.dp

@Composable
fun MessagesScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        ChatListController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val errorNetworkMessage = stringResource(R.string.messages_error_network)
    val errorAuthMessage = stringResource(R.string.messages_error_auth)
    val errorGenericMessage = stringResource(R.string.messages_error_generic)
    val chatDeletedMessage = stringResource(R.string.messages_deleted)

    fun errorMessage(error: Exception): String = when (error) {
        is ChatsError.Network -> errorNetworkMessage
        is ChatsError.AuthenticationRequired -> errorAuthMessage
        else -> errorGenericMessage
    }

    LaunchedEffect(controller) {
        controller.loadInitialIfNeeded()
    }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is ChatListEvent.OperationFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                ChatListEvent.SessionExpired ->
                    notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)

                ChatListEvent.ChatDeleted ->
                    notificationHostState.show(chatDeletedMessage, InAppNotificationType.Success)
            }
        }
    }

    var openChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var chatPendingDelete by remember { mutableStateOf<Chat?>(null) }

    // The socket is owned app-wide by RealtimeConnectionManager (connected while foregrounded and
    // logged in), so it outlives this tab. Here we only consume events to keep the list live while
    // it is on screen; the conversation dialog joins/leaves its chat room on the same connection.
    val realtime = remember { WebSocketChatRealtimeClient }
    val currentOpenChatId by rememberUpdatedState(openChatId)
    LaunchedEffect(controller, realtime) {
        realtime.events.collect { event ->
            val message = when (event) {
                is ChatRealtimeEvent.MessageNew -> event.message
                is ChatRealtimeEvent.MessageReceived -> event.message
                else -> null
            }
            if (message != null) {
                controller.onRealtimeMessage(
                    message = message,
                    isChatOpen = message.chatId == currentOpenChatId,
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ChatListScreenContent(
            state = state,
            nowMillis = nowMillis,
            currentUserId = currentUserId,
            onSearchChange = controller::setSearchQuery,
            onOpenFilters = { showFilters = true },
            onOpenChat = { chat ->
                controller.onChatOpened(chat.id)
                openChatId = chat.id
            },
            onNewChat = { showNewChat = true },
            onMarkRead = controller::markChatRead,
            onDeleteRequest = { chatPendingDelete = it },
            onRefresh = controller::refresh,
            onLoadMore = controller::loadMore,
            onRetry = controller::refresh,
        )
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    chatPendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatPendingDelete = null },
            title = { Text(stringResource(R.string.messages_delete_confirm_title)) },
            text = { Text(stringResource(R.string.messages_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        chatPendingDelete = null
                        controller.deleteChat(chat)
                    },
                    modifier = Modifier.testTag("chat_delete_confirm"),
                ) {
                    Text(stringResource(R.string.messages_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { chatPendingDelete = null }) {
                    Text(stringResource(R.string.messages_cancel))
                }
            },
        )
    }

    if (showFilters) {
        MessageFilterSheet(
            selected = state.typeFilter,
            onApply = { type ->
                showFilters = false
                controller.setTypeFilter(type)
            },
            onDismiss = { showFilters = false },
        )
    }

    openChatId?.let { chatId ->
        Dialog(
            onDismissRequest = { openChatId = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            ChatConversationScreen(
                chatId = chatId,
                currentUserId = currentUserId,
                onClose = {
                    openChatId = null
                    // Pick up the new lastMessage/ordering produced while chatting.
                    if (state.hasLoadedOnce) controller.refresh()
                },
            )
        }
    }

    if (showNewChat) {
        Dialog(
            onDismissRequest = { showNewChat = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            NewChatScreen(
                currentUserId = currentUserId,
                onClose = { showNewChat = false },
                onChatCreated = { chat ->
                    showNewChat = false
                    controller.onChatCreated(chat)
                    openChatId = chat.id
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatListScreenContent(
    state: ChatListUiState,
    nowMillis: Long,
    currentUserId: String?,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenChat: (Chat) -> Unit,
    onNewChat: () -> Unit,
    onMarkRead: (Chat) -> Unit,
    onDeleteRequest: (Chat) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = searchActive) { searchActive = false }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.hasMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (searchActive) {
            MessageSearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchChange,
                onClose = { searchActive = false },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nav_messages),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.testTag("messages_new_chat"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.messages_new_chat),
                    )
                }
                IconButton(
                    onClick = { searchActive = true },
                    modifier = Modifier.testTag("messages_search"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.messages_search),
                    )
                }
                BadgedBox(
                    badge = {
                        if (state.typeFilter != null) {
                            Badge { Text("1") }
                        }
                    },
                ) {
                    IconButton(
                        onClick = onOpenFilters,
                        modifier = Modifier.testTag("messages_filters"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.messages_filter),
                        )
                    }
                }
            }

            if (state.searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
                ) {
                    InputChip(
                        selected = true,
                        onClick = { searchActive = true },
                        label = { Text("\"${state.searchQuery}\"") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.messages_search_clear),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onSearchChange("") },
                            )
                        },
                        modifier = Modifier.testTag("messages_search_chip"),
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                state.loadFailed && state.chats.isEmpty() -> {
                    ListMessage(
                        title = stringResource(R.string.messages_load_failed),
                        actionLabel = stringResource(R.string.messages_retry),
                        onAction = onRetry,
                    )
                }

                state.chats.isEmpty() && state.hasLoadedOnce -> {
                    if (state.searchQuery.isNotBlank() || state.typeFilter != null) {
                        ListMessage(title = stringResource(R.string.messages_empty_search))
                    } else {
                        ListMessage(
                            title = stringResource(R.string.messages_empty_title),
                            subtitle = stringResource(R.string.messages_empty_subtitle),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("messages_list"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = ListBottomInset),
                    ) {
                        items(state.chats, key = { it.id }) { chat ->
                            ChatListItem(
                                chat = chat,
                                nowMillis = nowMillis,
                                currentUserId = currentUserId,
                                onOpen = { onOpenChat(chat) },
                                onMarkRead = { onMarkRead(chat) },
                                onDeleteRequest = { onDeleteRequest(chat) },
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(vertical = 8.dp)
                                            .size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Inline search bar shown in place of the header while searching; matches the cruises surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.testTag("messages_search_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .testTag("messages_search_field"),
            placeholder = { Text(stringResource(R.string.messages_search_placeholder)) },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("messages_search_clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.messages_search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

internal fun ChatType.labelRes(): Int = when (this) {
    ChatType.OneToOne -> R.string.chat_type_one_to_one
    ChatType.Group -> R.string.chat_type_group
    ChatType.CruiseQna -> R.string.chat_type_cruise_qna
    ChatType.CruiseGroup -> R.string.chat_type_cruise_group
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: Chat,
    nowMillis: Long,
    currentUserId: String?,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasUnread = chat.unreadCount > 0

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true },
                )
                .testTag("chat_item_${chat.id}")
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatListAvatar(
                participants = otherParticipants(chat, currentUserId),
                size = 52.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = chatTitle(chat, currentUserId)
                        ?: stringResource(R.string.messages_title_fallback),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = lastMessagePreview(chat, currentUserId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasUnread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                val timestamp = chat.lastMessage?.createdAt ?: chat.updatedAt
                Text(
                    text = chatRelativeTime(timestamp, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (hasUnread) {
                    Badge(
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            text = if (chat.unreadCount > 99) {
                                stringResource(R.string.messages_unread_badge_max)
                            } else {
                                chat.unreadCount.toString()
                            },
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (hasUnread) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.messages_mark_read)) },
                    onClick = {
                        menuExpanded = false
                        onMarkRead()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.messages_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDeleteRequest()
                },
                modifier = Modifier.testTag("chat_item_delete_${chat.id}"),
            )
        }
    }
}

@Composable
private fun lastMessagePreview(chat: Chat, currentUserId: String?): String {
    val message = chat.lastMessage
        ?: return stringResource(R.string.messages_preview_empty)
    return when {
        message.user.id == currentUserId ->
            stringResource(R.string.messages_preview_you, message.text)

        chat.type != ChatType.OneToOne ->
            stringResource(R.string.messages_preview_other, message.user.name, message.text)

        else -> message.text
    }
}

@Composable
private fun ListMessage(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

internal val previewChatUsers = listOf(
    ChatUser(id = "u1", name = "Jan Kowalski"),
    ChatUser(id = "u2", name = "Anna Nowak"),
    ChatUser(id = "u3", name = "Piotr Wiśniewski"),
)

internal fun previewChat(
    id: String,
    type: ChatType = ChatType.OneToOne,
    name: String? = null,
    unreadCount: Int = 0,
    lastMessageText: String? = "See you at the marina!",
    lastMessageUser: ChatUser = previewChatUsers[1],
): Chat = Chat(
    id = id,
    type = type,
    name = name,
    participants = previewChatUsers,
    lastMessage = lastMessageText?.let {
        ChatMessage(
            id = "$id-last",
            chatId = id,
            text = it,
            read = unreadCount == 0,
            user = lastMessageUser,
            createdAt = "2026-06-12T10:30:00Z",
            updatedAt = "2026-06-12T10:30:00Z",
        )
    },
    unreadCount = unreadCount,
    updatedAt = "2026-06-12T10:30:00Z",
)

private val previewState = ChatListUiState(
    chats = listOf(
        previewChat("c1", unreadCount = 3),
        previewChat(
            "c2",
            type = ChatType.Group,
            name = "Summer Sailing Crew",
            lastMessageUser = previewChatUsers[2],
        ),
        previewChat("c3", lastMessageText = null),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun ChatListPreview() {
    SkipperClubTheme {
        ChatListScreenContent(
            state = previewState,
            nowMillis = 1_775_000_000_000,
            currentUserId = "u1",
            onSearchChange = {},
            onOpenFilters = {},
            onOpenChat = {},
            onNewChat = {},
            onMarkRead = {},
            onDeleteRequest = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
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
private fun ChatListPreviewDark() {
    SkipperClubTheme {
        ChatListScreenContent(
            state = previewState,
            nowMillis = 1_775_000_000_000,
            currentUserId = "u1",
            onSearchChange = {},
            onOpenFilters = {},
            onOpenChat = {},
            onNewChat = {},
            onMarkRead = {},
            onDeleteRequest = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun ChatListPreviewPl() {
    SkipperClubTheme {
        ChatListScreenContent(
            state = previewState.copy(
                searchQuery = "Anna",
                typeFilter = ChatType.OneToOne,
                chats = listOf(previewChat("c1", unreadCount = 120)),
            ),
            nowMillis = 1_775_000_000_000,
            currentUserId = "u1",
            onSearchChange = {},
            onOpenFilters = {},
            onOpenChat = {},
            onNewChat = {},
            onMarkRead = {},
            onDeleteRequest = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun ChatListPreviewEmptyPl() {
    SkipperClubTheme {
        ChatListScreenContent(
            state = ChatListUiState(hasLoadedOnce = true),
            nowMillis = 1_775_000_000_000,
            currentUserId = "u1",
            onSearchChange = {},
            onOpenFilters = {},
            onOpenChat = {},
            onNewChat = {},
            onMarkRead = {},
            onDeleteRequest = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
