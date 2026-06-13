package app.skipperclub.ui.main.friends

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.FriendUser
import app.skipperclub.data.FriendsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Full-screen community-member search for sending friend requests. */
@Composable
fun FriendSearchScreen(
    currentUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        FriendSearchController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            currentUserId = currentUserId,
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.friends_error_network)
    val errorAuth = stringResource(R.string.friends_error_auth)
    val errorAlready = stringResource(R.string.friend_invite_error_already)
    val errorGeneric = stringResource(R.string.friends_error_generic)
    val sentMessage = stringResource(R.string.friend_invite_sent)

    fun errorMessage(error: Exception): String = when (error) {
        is FriendsError.Network -> errorNetwork
        is FriendsError.AuthenticationRequired -> errorAuth
        is FriendsError.Conflict -> error.message ?: errorAlready
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is FriendSearchEvent.RequestSent ->
                    notificationHostState.show(
                        sentMessage.format(event.user.name),
                        InAppNotificationType.Success,
                    )

                is FriendSearchEvent.OperationFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                FriendSearchEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Box(modifier = modifier.fillMaxSize()) {
        FriendSearchScreenContent(
            state = state,
            onSearchChange = controller::setSearchQuery,
            onSendRequest = controller::sendRequest,
            onClose = onClose,
        )
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun FriendSearchScreenContent(
    state: FriendSearchUiState,
    onSearchChange: (String) -> Unit,
    onSendRequest: (FriendUser) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("friend_search"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("friend_search_back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.friends_back),
                    )
                }
                Text(
                    text = stringResource(R.string.friend_invite_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("friend_search_input"),
                placeholder = { Text(stringResource(R.string.friend_invite_search_placeholder)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isSearching && state.results.isEmpty() -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.searchFailed && state.results.isEmpty() -> Text(
                        text = stringResource(R.string.friend_invite_search_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    state.results.isEmpty() && state.hasSearchedOnce -> Text(
                        text = stringResource(R.string.friend_invite_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("friend_search_results"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    ) {
                        items(state.results, key = { it.id }) { user ->
                            FriendSearchRow(
                                user = user,
                                isSending = user.id in state.sendingUserIds,
                                isSent = user.id in state.sentUserIds,
                                onSendRequest = { onSendRequest(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendSearchRow(
    user: FriendUser,
    isSending: Boolean,
    isSent: Boolean,
    onSendRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("friend_search_user_${user.id}")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(user = user, modifier = Modifier.size(44.dp))
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        when {
            isSent -> OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.testTag("friend_search_sent_${user.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.friend_invite_sent_label),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            else -> Button(
                onClick = onSendRequest,
                enabled = !isSending,
                modifier = Modifier.testTag("friend_search_add_${user.id}"),
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.PersonAddAlt1,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.friend_invite_add),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

// --- Previews ---

internal val previewFriendUsers = listOf(
    FriendUser(id = "u1", name = "Jan Kowalski", avatarUrl = null),
    FriendUser(id = "u2", name = "Anna Nowak", avatarUrl = null),
    FriendUser(id = "u3", name = "Piotr Wiśniewski", avatarUrl = null),
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun FriendSearchPreview() {
    SkipperClubTheme {
        FriendSearchScreenContent(
            state = FriendSearchUiState(
                results = previewFriendUsers,
                sentUserIds = setOf("u2"),
                hasSearchedOnce = true,
            ),
            onSearchChange = {},
            onSendRequest = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun FriendSearchPreviewPl() {
    SkipperClubTheme {
        FriendSearchScreenContent(
            state = FriendSearchUiState(
                searchQuery = "an",
                results = previewFriendUsers,
                sendingUserIds = setOf("u3"),
                hasSearchedOnce = true,
            ),
            onSearchChange = {},
            onSendRequest = {},
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
private fun FriendSearchPreviewDark() {
    SkipperClubTheme {
        FriendSearchScreenContent(
            state = FriendSearchUiState(results = previewFriendUsers, hasSearchedOnce = true),
            onSearchChange = {},
            onSendRequest = {},
            onClose = {},
        )
    }
}
