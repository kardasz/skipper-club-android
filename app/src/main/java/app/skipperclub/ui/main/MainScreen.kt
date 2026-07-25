package app.skipperclub.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.SessionUser
import app.skipperclub.data.UnreadMessagesStore
import app.skipperclub.data.UnreadNotificationsStore
import app.skipperclub.data.WebSocketChatRealtimeClient
import app.skipperclub.ui.main.cruises.CruisesScreen
import app.skipperclub.ui.main.cruises.reviews.CruiseReviewsScreen
import app.skipperclub.ui.main.friends.FriendsScreen
import app.skipperclub.ui.main.invitations.InvitationsScreen
import app.skipperclub.ui.main.messages.MessagesScreen
import app.skipperclub.ui.main.notifications.NotificationsScreen
import app.skipperclub.ui.main.posts.PostsScreen
import app.skipperclub.ui.main.profile.ProfileScreen
import app.skipperclub.ui.main.settings.SettingsScreen
import app.skipperclub.ui.main.spots.SpotsScreen
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun MainScreen(
    user: SessionUser,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    pendingReviewsCruiseId: String? = null,
    onPendingReviewsConsumed: () -> Unit = {},
) {
    val currentSelection = rememberSaveable { mutableStateOf(value = MainDestination.MAP) }
    var current by currentSelection
    val messagesBadgeCount by UnreadMessagesStore.count.collectAsState()
    val notificationsBadgeCount by UnreadNotificationsStore.count.collectAsState()
    // App-wide: the WS auth breaker's give-up must stay visible on every tab, not only under
    // Messages where the transient event-driven notice lives (parity with web's connection
    // banner). Persistent state, cleared by the client itself on the next successful connect
    // or session start.
    val realtimeGaveUp by WebSocketChatRealtimeClient.connectionGaveUp.collectAsState()
    MainScreenContent(
        current = current,
        user = user,
        onSelect = { currentSelection.value = it },
        onLogout = onLogout,
        pendingReviewsCruiseId = pendingReviewsCruiseId,
        onPendingReviewsConsumed = onPendingReviewsConsumed,
        messagesBadgeCount = messagesBadgeCount,
        notificationsBadgeCount = notificationsBadgeCount,
        onNotificationsClosed = { UnreadNotificationsStore.refresh() },
        showRealtimeGaveUpBanner = realtimeGaveUp,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    current: MainDestination,
    user: SessionUser,
    onSelect: (MainDestination) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    pendingReviewsCruiseId: String? = null,
    onPendingReviewsConsumed: () -> Unit = {},
    messagesBadgeCount: Int = 0,
    notificationsBadgeCount: Int = 0,
    onNotificationsClosed: () -> Unit = {},
    showRealtimeGaveUpBanner: Boolean = false,
) {
    val menuOpenState = rememberSaveable { mutableStateOf(value = false) }
    var isMenuOpen by menuOpenState
    var showNotifications by rememberSaveable { mutableStateOf(value = false) }
    var showFriends by rememberSaveable { mutableStateOf(value = false) }
    var showInvitations by rememberSaveable { mutableStateOf(value = false) }
    var showSpots by rememberSaveable { mutableStateOf(value = false) }
    var showProfile by rememberSaveable { mutableStateOf(value = false) }
    var showSettings by rememberSaveable { mutableStateOf(value = false) }
    var reviewsCruiseId by rememberSaveable { mutableStateOf<String?>(value = null) }
    // Set when the feed's "Create → Navigation alert" option is picked; the map
    // consumes it by entering the aim-on-the-map alert flow.
    var pendingAlertPicking by rememberSaveable { mutableStateOf(value = false) }

    // Reconcile the notifications badge after the notification center closes (its mark-read
    // mutations have committed by then), mirroring how MessagesScreen reconciles the messages
    // badge when a conversation closes.
    androidx.compose.runtime.LaunchedEffect(showNotifications) {
        if (!showNotifications) onNotificationsClosed()
    }

    // Open the reviews center when a `…/cruises/{id}/reviews` deep link arrives.
    androidx.compose.runtime.LaunchedEffect(pendingReviewsCruiseId) {
        if (pendingReviewsCruiseId != null) {
            reviewsCruiseId = pendingReviewsCruiseId
            onPendingReviewsConsumed()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (current) {
                MainDestination.POSTS -> PostsScreen(
                    onCreateAlert = {
                        pendingAlertPicking = true
                        onSelect(MainDestination.MAP)
                    },
                )
                MainDestination.CRUISES -> CruisesScreen()
                MainDestination.MAP -> MapScreen(
                    startAlertPicking = pendingAlertPicking,
                    onAlertPickingStarted = { pendingAlertPicking = false },
                )
                MainDestination.MESSAGES -> MessagesScreen()
                MainDestination.MENU -> MenuScreen()
            }
        }

        SkipperBottomBar(
            selected = current,
            user = user,
            onSelect = { destination ->
                if (destination == MainDestination.MENU) {
                    menuOpenState.value = true
                } else {
                    onSelect(destination)
                }
            },
            messagesBadgeCount = messagesBadgeCount,
            menuBadgeCount = notificationsBadgeCount,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showRealtimeGaveUpBanner) {
            RealtimeGaveUpBanner(modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    if (isMenuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpenState.value = false },
        ) {
            MainMenuSheet(
                user = user,
                notificationsBadgeCount = notificationsBadgeCount,
                onClose = { menuOpenState.value = false },
                onOpenProfile = {
                    menuOpenState.value = false
                    showProfile = true
                },
                onOpenNotifications = {
                    menuOpenState.value = false
                    showNotifications = true
                },
                onOpenFriends = {
                    menuOpenState.value = false
                    showFriends = true
                },
                onOpenInvitations = {
                    menuOpenState.value = false
                    showInvitations = true
                },
                onOpenSpots = {
                    menuOpenState.value = false
                    showSpots = true
                },
                onOpenSettings = {
                    menuOpenState.value = false
                    showSettings = true
                },
                onLogout = {
                    menuOpenState.value = false
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .navigationBarsPadding(),
            )
        }
    }

    if (showNotifications) {
        Dialog(
            onDismissRequest = { showNotifications = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            NotificationsScreen(onClose = { showNotifications = false })
        }
    }

    if (showFriends) {
        Dialog(
            onDismissRequest = { showFriends = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            FriendsScreen(onClose = { showFriends = false })
        }
    }

    if (showInvitations) {
        Dialog(
            onDismissRequest = { showInvitations = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            InvitationsScreen(onClose = { showInvitations = false })
        }
    }

    if (showSpots) {
        Dialog(
            onDismissRequest = { showSpots = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SpotsScreen(onClose = { showSpots = false })
        }
    }

    if (showProfile) {
        Dialog(
            onDismissRequest = { showProfile = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            ProfileScreen(onClose = { showProfile = false })
        }
    }

    if (showSettings) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            SettingsScreen(onClose = { showSettings = false })
        }
    }

    reviewsCruiseId?.let { cruiseId ->
        Dialog(
            onDismissRequest = { reviewsCruiseId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CruiseReviewsScreen(
                cruiseId = cruiseId,
                currentUserId = user.id,
                onClose = { reviewsCruiseId = null },
            )
        }
    }
}

/**
 * Persistent notice that realtime gave up reconnecting (WS auth breaker exhausted). Rendered
 * app-wide over every tab, unlike the transient Messages-tab notice fed by the same event —
 * recovery is a background/foreground cycle (the next `connect()` clears the state), so the
 * banner simply stays until the client reports either a fresh session or an open socket.
 */
@Composable
private fun RealtimeGaveUpBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("realtime_gave_up_banner"),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(R.string.messages_error_connection_lost),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun MainMenuSheet(
    user: SessionUser,
    notificationsBadgeCount: Int,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenSpots: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                UserAvatar(
                    user = user,
                    selected = false,
                    modifier = Modifier.size(48.dp),
                )
            },
            headlineContent = {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        MainMenuItem(
            label = stringResource(R.string.menu_my_profile),
            iconRes = R.drawable.ic_person,
            onClick = onOpenProfile,
        )
        MainMenuItem(
            label = stringResource(R.string.menu_notifications),
            iconRes = R.drawable.ic_notifications,
            onClick = onOpenNotifications,
            badgeCount = notificationsBadgeCount,
        )
        MainMenuItem(
            label = stringResource(R.string.menu_friends),
            iconRes = R.drawable.ic_group,
            onClick = onOpenFriends,
        )
        // Invitations and Spots are admin-only surfaces — hide them for standard users.
        if (user.isAdmin) {
            MainMenuItem(
                label = stringResource(R.string.menu_invitations),
                iconRes = R.drawable.ic_mail,
                onClick = onOpenInvitations,
            )
            MainMenuItem(
                label = stringResource(R.string.menu_spots),
                iconRes = R.drawable.ic_place,
                onClick = onOpenSpots,
            )
        }
        MainMenuItem(
            label = stringResource(R.string.menu_saved),
            iconRes = R.drawable.ic_favorite,
            onClick = onClose,
        )
        MainMenuItem(
            label = stringResource(R.string.menu_settings),
            iconRes = R.drawable.ic_settings,
            onClick = onOpenSettings,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        MainMenuItem(
            label = stringResource(R.string.menu_logout),
            iconRes = R.drawable.ic_logout,
            onClick = onLogout,
        )
    }
}

@Composable
private fun MainMenuItem(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label) },
        trailingContent = if (badgeCount > 0) {
            {
                Badge {
                    Text(text = if (badgeCount > 99) "99+" else badgeCount.toString())
                }
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    )
}

private val previewUser = SessionUser(
    id = "preview-user",
    email = "anna.nowak@example.com",
    name = "Anna Nowak",
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun MainScreenPreviewMap() {
    SkipperClubTheme {
        MainScreenContent(
            current = MainDestination.MAP,
            user = previewUser,
            onSelect = {},
            onLogout = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun MainScreenPreviewPosts() {
    SkipperClubTheme {
        MainScreenContent(
            current = MainDestination.POSTS,
            user = previewUser,
            onSelect = {},
            onLogout = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun MainScreenPreviewPl() {
    SkipperClubTheme {
        MainScreenContent(
            current = MainDestination.MESSAGES,
            user = previewUser,
            onSelect = {},
            onLogout = {},
            // The busy state: the realtime give-up banner pinned over the tab content.
            showRealtimeGaveUpBanner = true,
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
private fun MainScreenPreviewDark() {
    SkipperClubTheme {
        MainScreenContent(
            current = MainDestination.MAP,
            user = previewUser,
            onSelect = {},
            onLogout = {},
        )
    }
}
