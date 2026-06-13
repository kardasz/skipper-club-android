package app.skipperclub.ui.main.friends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.FriendRequest
import app.skipperclub.data.FriendRequestState
import app.skipperclub.data.FriendUser
import app.skipperclub.data.FriendsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.profile.PublicProfileScreen
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Full-screen "Friends" surface launched from the main menu: pending requests, the
 *  friend list, and an entry point to invite new friends. */
@Composable
fun FriendsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        FriendsController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.friends_error_network)
    val errorAuth = stringResource(R.string.friends_error_auth)
    val errorGeneric = stringResource(R.string.friends_error_generic)

    fun errorMessage(error: Exception): String = when (error) {
        is FriendsError.Network -> errorNetwork
        is FriendsError.AuthenticationRequired -> errorAuth
        is FriendsError.Conflict -> error.message ?: errorGeneric
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is FriendsEvent.OperationFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                FriendsEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    var showInvite by remember { mutableStateOf(false) }
    var openProfileUserId by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("friends_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FriendsScreenContent(
                state = state,
                onClose = onClose,
                onRefresh = controller::refresh,
                onRetry = controller::refresh,
                onAccept = controller::acceptRequest,
                onReject = controller::rejectRequest,
                onCancel = controller::cancelRequest,
                onRemoveFriend = controller::removeFriend,
                onOpenProfile = { openProfileUserId = it.id },
                onInviteClick = { showInvite = true },
                onLoadMore = controller::loadMoreFriends,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (showInvite) {
        Dialog(
            onDismissRequest = {
                showInvite = false
                controller.refresh()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            FriendSearchScreen(
                currentUserId = currentUserId,
                onClose = {
                    showInvite = false
                    controller.refresh()
                },
            )
        }
    }

    openProfileUserId?.let { userId ->
        Dialog(
            onDismissRequest = { openProfileUserId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            PublicProfileScreen(
                userId = userId,
                onClose = { openProfileUserId = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsScreenContent(
    state: FriendsUiState,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAccept: (FriendRequest) -> Unit,
    onReject: (FriendRequest) -> Unit,
    onCancel: (FriendRequest) -> Unit,
    onRemoveFriend: (FriendUser) -> Unit,
    onOpenProfile: (FriendUser) -> Unit,
    onInviteClick: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<FriendUser?>(null) }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.hasMoreFriends) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMoreFriends && lastVisible >= listState.layoutInfo.totalItemsCount - 3
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
            IconButton(onClick = onClose, modifier = Modifier.testTag("friends_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.friends_back),
                )
            }
            Text(
                text = stringResource(R.string.friends_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    state.loadFailed && state.isEmpty -> FriendsMessage(
                        title = stringResource(R.string.friends_load_failed),
                        actionLabel = stringResource(R.string.friends_retry),
                        onAction = onRetry,
                    )

                    state.isEmpty && state.hasLoadedOnce -> FriendsMessage(
                        title = stringResource(R.string.friends_empty_title),
                        subtitle = stringResource(R.string.friends_empty_subtitle),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("friends_list"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    ) {
                        if (state.hasRequests) {
                            item(key = "requests_header") {
                                FriendsSectionHeader(stringResource(R.string.friends_section_requests))
                            }
                            items(state.receivedRequests, key = { "recv_${it.id}" }) { request ->
                                FriendRequestRow(
                                    request = request,
                                    isReceived = true,
                                    isBusy = request.id in state.busyRequestIds,
                                    onAccept = { onAccept(request) },
                                    onReject = { onReject(request) },
                                    onCancel = { onCancel(request) },
                                    onOpenProfile = { onOpenProfile(request.user) },
                                )
                            }
                            items(state.sentRequests, key = { "sent_${it.id}" }) { request ->
                                FriendRequestRow(
                                    request = request,
                                    isReceived = false,
                                    isBusy = request.id in state.busyRequestIds,
                                    onAccept = { onAccept(request) },
                                    onReject = { onReject(request) },
                                    onCancel = { onCancel(request) },
                                    onOpenProfile = { onOpenProfile(request.user) },
                                )
                            }
                        }

                        item(key = "friends_header") {
                            FriendsSectionHeader(
                                stringResource(R.string.friends_section_friends, state.friendsTotal),
                            )
                        }

                        if (state.friends.isEmpty()) {
                            item(key = "friends_empty") {
                                Text(
                                    text = stringResource(R.string.friends_list_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        } else {
                            items(state.friends, key = { "friend_${it.id}" }) { friend ->
                                FriendRow(
                                    friend = friend,
                                    isRemoving = friend.id in state.removingFriendIds,
                                    onOpenProfile = { onOpenProfile(friend) },
                                    onRemove = { pendingRemoval = friend },
                                )
                            }
                        }

                        if (state.isLoadingMore) {
                            item(key = "friends_loading_more") {
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

        Button(
            onClick = onInviteClick,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("friends_invite_button"),
        ) {
            Icon(imageVector = Icons.Filled.PersonAddAlt1, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.friends_invite_button),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    pendingRemoval?.let { friend ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.friends_remove_title)) },
            text = { Text(stringResource(R.string.friends_remove_message, friend.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFriend(friend)
                        pendingRemoval = null
                    },
                    modifier = Modifier.testTag("friends_remove_confirm"),
                ) {
                    Text(
                        text = stringResource(R.string.friends_remove_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.friends_remove_cancel))
                }
            },
        )
    }
}

@Composable
private fun FriendsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FriendRequestRow(
    request: FriendRequest,
    isReceived: Boolean,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("friend_request_${request.id}")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(
            user = request.user,
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onOpenProfile),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clickable(onClick = onOpenProfile),
        ) {
            Text(
                text = request.user.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (isReceived) R.string.friends_request_received else R.string.friends_request_sent,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (isReceived) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onReject,
                    modifier = Modifier.testTag("friend_request_reject_${request.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.friends_request_reject),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onAccept,
                    modifier = Modifier.testTag("friend_request_accept_${request.id}"),
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.friends_request_accept),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("friend_request_cancel_${request.id}"),
            ) {
                Text(stringResource(R.string.friends_request_cancel))
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: FriendUser,
    isRemoving: Boolean,
    onOpenProfile: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
            .testTag("friend_${friend.id}")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(user = friend, modifier = Modifier.size(48.dp))
        Text(
            text = friend.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        if (isRemoving) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("friend_remove_${friend.id}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.friends_remove_title),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FriendsMessage(
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
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

// --- Previews ---

private fun previewRequest(id: String, name: String, state: FriendRequestState) = FriendRequest(
    id = id,
    user = FriendUser(id = "user-$id", name = name, avatarUrl = null),
    state = state,
    createdAt = "2026-06-13T09:00:00Z",
    updatedAt = "2026-06-13T09:00:00Z",
)

private val previewFriendsState = FriendsUiState(
    receivedRequests = listOf(previewRequest("r1", "Jan Kowalski", FriendRequestState.Pending)),
    sentRequests = listOf(previewRequest("s1", "Anna Nowak", FriendRequestState.Sent)),
    friends = listOf(
        FriendUser(id = "f1", name = "Piotr Wiśniewski", avatarUrl = null),
        FriendUser(id = "f2", name = "Maria Lewandowska", avatarUrl = null),
    ),
    friendsTotal = 2,
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 380, heightDp = 840, locale = "en")
@Composable
private fun FriendsPreview() {
    SkipperClubTheme {
        FriendsScreenContent(
            state = previewFriendsState,
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onAccept = {},
            onReject = {},
            onCancel = {},
            onRemoveFriend = {},
            onOpenProfile = {},
            onInviteClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 840,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FriendsPreviewDark() {
    SkipperClubTheme {
        FriendsScreenContent(
            state = previewFriendsState,
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onAccept = {},
            onReject = {},
            onCancel = {},
            onRemoveFriend = {},
            onOpenProfile = {},
            onInviteClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 840, locale = "pl")
@Composable
private fun FriendsPreviewPl() {
    SkipperClubTheme {
        FriendsScreenContent(
            state = previewFriendsState,
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onAccept = {},
            onReject = {},
            onCancel = {},
            onRemoveFriend = {},
            onOpenProfile = {},
            onInviteClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 840, locale = "pl")
@Composable
private fun FriendsEmptyPreviewPl() {
    SkipperClubTheme {
        FriendsScreenContent(
            state = FriendsUiState(hasLoadedOnce = true),
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onAccept = {},
            onReject = {},
            onCancel = {},
            onRemoveFriend = {},
            onOpenProfile = {},
            onInviteClick = {},
            onLoadMore = {},
        )
    }
}
