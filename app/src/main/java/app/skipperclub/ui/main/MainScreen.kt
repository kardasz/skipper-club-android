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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.SessionUser
import app.skipperclub.ui.main.cruises.CruisesScreen
import app.skipperclub.ui.main.invitations.InvitationsScreen
import app.skipperclub.ui.main.messages.MessagesScreen
import app.skipperclub.ui.main.notifications.NotificationsScreen
import app.skipperclub.ui.main.posts.PostsScreen
import app.skipperclub.ui.main.profile.ProfileScreen
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun MainScreen(
    user: SessionUser,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSelection = rememberSaveable { mutableStateOf(value = MainDestination.MAP) }
    var current by currentSelection
    MainScreenContent(
        current = current,
        user = user,
        onSelect = { currentSelection.value = it },
        onLogout = onLogout,
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
) {
    val menuOpenState = rememberSaveable { mutableStateOf(value = false) }
    var isMenuOpen by menuOpenState
    var showNotifications by rememberSaveable { mutableStateOf(value = false) }
    var showInvitations by rememberSaveable { mutableStateOf(value = false) }
    var showProfile by rememberSaveable { mutableStateOf(value = false) }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (current) {
                MainDestination.POSTS -> PostsScreen()
                MainDestination.CRUISES -> CruisesScreen()
                MainDestination.MAP -> MapScreen()
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
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (isMenuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpenState.value = false },
        ) {
            MainMenuSheet(
                user = user,
                onClose = { menuOpenState.value = false },
                onOpenProfile = {
                    menuOpenState.value = false
                    showProfile = true
                },
                onOpenNotifications = {
                    menuOpenState.value = false
                    showNotifications = true
                },
                onOpenInvitations = {
                    menuOpenState.value = false
                    showInvitations = true
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

    if (showInvitations) {
        Dialog(
            onDismissRequest = { showInvitations = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            InvitationsScreen(onClose = { showInvitations = false })
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
}

@Composable
private fun MainMenuSheet(
    user: SessionUser,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenInvitations: () -> Unit,
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
        )
        // Invitations are an admin-only surface (see docs/api/invitations) — hide it for standard users.
        if (user.isAdmin) {
            MainMenuItem(
                label = stringResource(R.string.menu_invitations),
                iconRes = R.drawable.ic_mail,
                onClick = onOpenInvitations,
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
            onClick = onClose,
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
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label) },
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
