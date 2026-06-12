package app.skipperclub.ui.main.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import app.skipperclub.data.Chat
import app.skipperclub.data.ChatUser
import app.skipperclub.data.ChatsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun NewChatScreen(
    currentUserId: String?,
    onClose: () -> Unit,
    onChatCreated: (Chat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        NewChatController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            currentUserId = currentUserId,
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetworkMessage = stringResource(R.string.messages_error_network)
    val errorAuthMessage = stringResource(R.string.messages_error_auth)
    val errorGenericMessage = stringResource(R.string.messages_error_generic)

    LaunchedEffect(controller) {
        controller.loadInitialIfNeeded()
    }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is NewChatEvent.ChatCreated -> onChatCreated(event.chat)

                is NewChatEvent.OperationFailed -> notificationHostState.show(
                    when (event.error) {
                        is ChatsError.Network -> errorNetworkMessage
                        is ChatsError.AuthenticationRequired -> errorAuthMessage
                        else -> errorGenericMessage
                    },
                    InAppNotificationType.Error,
                )

                NewChatEvent.SessionExpired ->
                    notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Box(modifier = modifier.fillMaxSize()) {
        NewChatScreenContent(
            state = state,
            onSearchChange = controller::setSearchQuery,
            onToggleUser = controller::toggleUser,
            onGroupNameChange = controller::setGroupName,
            onCreate = controller::create,
            onClose = onClose,
        )
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewChatScreenContent(
    state: NewChatUiState,
    onSearchChange: (String) -> Unit,
    onToggleUser: (ChatUser) -> Unit,
    onGroupNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("new_chat"),
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
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("new_chat_back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.conversation_back),
                    )
                }
                Text(
                    text = stringResource(R.string.new_chat_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onCreate,
                    enabled = state.canCreate,
                    modifier = Modifier.testTag("new_chat_create"),
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.new_chat_create))
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("new_chat_search"),
                placeholder = { Text(stringResource(R.string.new_chat_search_placeholder)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )

            if (state.selected.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.selected.forEach { user ->
                        InputChip(
                            selected = true,
                            onClick = { onToggleUser(user) },
                            label = { Text(user.name) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(
                                        R.string.new_chat_remove_participant,
                                        user.name,
                                    ),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            if (state.isGroup) {
                OutlinedTextField(
                    value = state.groupName,
                    onValueChange = onGroupNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("new_chat_group_name"),
                    label = { Text(stringResource(R.string.new_chat_group_name_label)) },
                    singleLine = true,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isSearching && state.results.isEmpty() -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.searchFailed && state.results.isEmpty() -> Text(
                        text = stringResource(R.string.new_chat_search_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    state.results.isEmpty() && state.hasSearchedOnce -> Text(
                        text = stringResource(R.string.new_chat_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("new_chat_results"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    ) {
                        items(state.results, key = { it.id }) { user ->
                            val isSelected = state.selected.any { it.id == user.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleUser(user) }
                                    .testTag("new_chat_user_${user.id}")
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ChatAvatar(
                                    user = user,
                                    modifier = Modifier.size(44.dp),
                                )
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
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

private val previewNewChatState = NewChatUiState(
    results = previewChatUsers,
    selected = listOf(previewChatUsers[1], previewChatUsers[2]),
    groupName = "",
    hasSearchedOnce = true,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun NewChatPreview() {
    SkipperClubTheme {
        NewChatScreenContent(
            state = previewNewChatState,
            onSearchChange = {},
            onToggleUser = {},
            onGroupNameChange = {},
            onCreate = {},
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
private fun NewChatPreviewDark() {
    SkipperClubTheme {
        NewChatScreenContent(
            state = previewNewChatState,
            onSearchChange = {},
            onToggleUser = {},
            onGroupNameChange = {},
            onCreate = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun NewChatPreviewPl() {
    SkipperClubTheme {
        NewChatScreenContent(
            state = previewNewChatState.copy(
                searchQuery = "an",
                groupName = "Letnia załoga",
            ),
            onSearchChange = {},
            onToggleUser = {},
            onGroupNameChange = {},
            onCreate = {},
            onClose = {},
        )
    }
}
