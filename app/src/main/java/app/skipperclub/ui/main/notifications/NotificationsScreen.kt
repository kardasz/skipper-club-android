package app.skipperclub.ui.main.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.AppNotification
import app.skipperclub.data.NotificationEventType
import app.skipperclub.data.NotificationSourceType
import app.skipperclub.data.NotificationStatus
import app.skipperclub.data.NotificationsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.cruises.CruiseDetailScreen
import app.skipperclub.ui.main.posts.PostDetailScreen
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.delay

/** Full-screen notification center launched from the main menu. */
@Composable
fun NotificationsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        NotificationsController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id
    val notificationHostState = rememberInAppNotificationHostState()

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val errorNetwork = stringResource(R.string.notifications_error_network)
    val errorAuth = stringResource(R.string.notifications_error_auth)
    val errorGeneric = stringResource(R.string.notifications_error_generic)
    val targetUnavailable = stringResource(R.string.notifications_target_unavailable)

    fun errorMessage(error: Exception): String = when (error) {
        is NotificationsError.Network -> errorNetwork
        is NotificationsError.AuthenticationRequired -> errorAuth
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is NotificationsEvent.OperationFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                NotificationsEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    var openCruiseId by remember { mutableStateOf<String?>(null) }
    var openPostId by remember { mutableStateOf<String?>(null) }
    var openPostFocusComments by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("notifications_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NotificationsScreenContent(
                state = state,
                nowMillis = nowMillis,
                onClose = onClose,
                onMarkAllRead = controller::markAllRead,
                onOpen = { notification ->
                    controller.markRead(notification)
                    when (val target = notification.target()) {
                        is NotificationTarget.Cruise -> openCruiseId = target.cruiseId
                        is NotificationTarget.Post -> {
                            openPostFocusComments = target.focusComments
                            openPostId = target.postId
                        }
                        null -> notificationHostState.show(targetUnavailable, InAppNotificationType.Info)
                    }
                },
                onMarkRead = controller::markRead,
                onDelete = controller::delete,
                onRefresh = controller::refresh,
                onLoadMore = controller::loadMore,
                onRetry = controller::refresh,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    openCruiseId?.let { cruiseId ->
        Dialog(
            onDismissRequest = { openCruiseId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CruiseDetailScreen(
                cruiseId = cruiseId,
                currentUserId = currentUserId,
                onClose = { openCruiseId = null },
                onCruiseChanged = {},
                onCruiseDeleted = { openCruiseId = null },
            )
        }
    }

    openPostId?.let { postId ->
        Dialog(
            onDismissRequest = { openPostId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            PostDetailScreen(
                postId = postId,
                focusComments = openPostFocusComments,
                onClose = { openPostId = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsScreenContent(
    state: NotificationsUiState,
    nowMillis: Long,
    onClose: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onMarkRead: (AppNotification) -> Unit,
    onDelete: (AppNotification) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("notifications_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.notifications_back),
                )
            }
            Text(
                text = stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state.hasUnread) {
                IconButton(
                    onClick = onMarkAllRead,
                    modifier = Modifier.testTag("notifications_mark_all_read"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = stringResource(R.string.notifications_mark_all_read),
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
                state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.loadFailed && state.notifications.isEmpty() -> NotificationsMessage(
                    title = stringResource(R.string.notifications_load_failed),
                    actionLabel = stringResource(R.string.notifications_retry),
                    onAction = onRetry,
                )

                state.notifications.isEmpty() && state.hasLoadedOnce -> NotificationsMessage(
                    title = stringResource(R.string.notifications_empty_title),
                    subtitle = stringResource(R.string.notifications_empty_subtitle),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notifications_list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            nowMillis = nowMillis,
                            onOpen = { onOpen(notification) },
                            onMarkRead = { onMarkRead(notification) },
                            onDelete = { onDelete(notification) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    notification: AppNotification,
    nowMillis: Long,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val unread = notification.isUnread

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (unread) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    } else {
                        Color.Transparent
                    },
                )
                .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
                .testTag("notification_item_${notification.id}")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notification.eventType.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = notification.displayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = notificationRelativeTime(notification.createdAt, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (unread) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (unread) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notifications_mark_all_read)) },
                    onClick = {
                        menuExpanded = false
                        onMarkRead()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.notifications_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
                modifier = Modifier.testTag("notification_item_delete_${notification.id}"),
            )
        }
    }
}

@Composable
private fun NotificationsMessage(
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
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

// --- Previews ---

internal fun previewNotification(
    id: String,
    eventType: NotificationEventType,
    sourceType: NotificationSourceType,
    status: NotificationStatus = NotificationStatus.Unread,
    metadata: Map<String, String> = mapOf("actorName" to "Anna Nowak", "cruiseTitle" to "Mazury 2026"),
    createdAt: String = "2026-06-13T09:00:00Z",
): AppNotification = AppNotification(
    id = id,
    eventType = eventType,
    sourceType = sourceType,
    sourceId = "src-$id",
    relationId = "rel-$id",
    status = status,
    metadata = metadata,
    createdAt = createdAt,
    readAt = if (status == NotificationStatus.Read) createdAt else null,
)

private val previewState = NotificationsUiState(
    notifications = listOf(
        previewNotification("n1", NotificationEventType.CruiseInvitationSent, NotificationSourceType.Cruise),
        previewNotification(
            "n2",
            NotificationEventType.PostCommented,
            NotificationSourceType.Post,
            metadata = mapOf("actorName" to "Piotr Wiśniewski", "commentText" to "Świetny rejs, chętnie dołączę następnym razem!"),
        ),
        previewNotification(
            "n3",
            NotificationEventType.FriendRequestAccepted,
            NotificationSourceType.Friend,
            status = NotificationStatus.Read,
            metadata = mapOf("actorName" to "Jan Kowalski"),
        ),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "en")
@Composable
private fun NotificationsPreview() {
    SkipperClubTheme {
        NotificationsScreenContent(
            state = previewState,
            nowMillis = 1_781_337_600_000,
            onClose = {},
            onMarkAllRead = {},
            onOpen = {},
            onMarkRead = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NotificationsPreviewDark() {
    SkipperClubTheme {
        NotificationsScreenContent(
            state = previewState,
            nowMillis = 1_781_337_600_000,
            onClose = {},
            onMarkAllRead = {},
            onOpen = {},
            onMarkRead = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun NotificationsPreviewPl() {
    SkipperClubTheme {
        NotificationsScreenContent(
            state = previewState,
            nowMillis = 1_781_337_600_000,
            onClose = {},
            onMarkAllRead = {},
            onOpen = {},
            onMarkRead = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun NotificationsEmptyPreviewPl() {
    SkipperClubTheme {
        NotificationsScreenContent(
            state = NotificationsUiState(hasLoadedOnce = true),
            nowMillis = 1_781_337_600_000,
            onClose = {},
            onMarkAllRead = {},
            onOpen = {},
            onMarkRead = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
