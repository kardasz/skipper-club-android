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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.SessionUser
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun MainScreen(
    user: SessionUser,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by rememberSaveable { mutableStateOf(MainDestination.MAP) }
    MainScreenContent(
        current = current,
        user = user,
        onSelect = { destination ->
            if (destination != MainDestination.MENU) {
                current = destination
            }
        },
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
    var isMenuOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            SkipperBottomBar(
                selected = current,
                user = user,
                onSelect = { destination ->
                    if (destination == MainDestination.MENU) {
                        isMenuOpen = true
                    } else {
                        onSelect(destination)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (current) {
                MainDestination.POSTS -> PostsScreen()
                MainDestination.CRUISES -> CruisesScreen()
                MainDestination.MAP -> MapScreen()
                MainDestination.MESSAGES -> MessagesScreen()
                MainDestination.MENU -> MenuScreen()
            }
        }
    }

    if (isMenuOpen) {
        ModalBottomSheet(
            onDismissRequest = { isMenuOpen = false },
        ) {
            MainMenuSheet(
                user = user,
                onClose = { isMenuOpen = false },
                onLogout = {
                    isMenuOpen = false
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun MainMenuSheet(
    user: SessionUser,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
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
            onClick = onClose,
        )
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
