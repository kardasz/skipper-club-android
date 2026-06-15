package app.skipperclub.ui.main.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.FriendshipStatus
import app.skipperclub.data.ProfileError
import app.skipperclub.data.SailingExperience
import app.skipperclub.data.SessionStore
import app.skipperclub.data.UserProfile
import app.skipperclub.ui.main.messages.ChatConversationScreen
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/**
 * Full-screen, read-only public profile of another member (`GET /v1/users/{userId}`),
 * opened from the friends list (and elsewhere users are shown). Reuses
 * [ProfileScreenContent] (no email, no edit). When the profile is not the current
 * user's, an overflow menu offers "Add to friends" and "Send message".
 */
@Composable
fun PublicProfileScreen(
    userId: String,
    currentUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope, userId) {
        PublicProfileController(
            scope = scope,
            userId = userId,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val actionState by controller.actionState.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    var openChatId by rememberSaveable(userId) { mutableStateOf<String?>(null) }

    val errorNetwork = stringResource(R.string.profile_error_network)
    val errorAuth = stringResource(R.string.profile_error_auth)
    val errorGeneric = stringResource(R.string.profile_error_generic)
    val friendRequestSent = stringResource(R.string.public_profile_friend_request_sent)
    val actionFailed = stringResource(R.string.public_profile_action_failed)
    val chatFailed = stringResource(R.string.public_profile_chat_failed)

    fun errorMessage(error: Exception): String = when (error) {
        is ProfileError.Network -> errorNetwork
        is ProfileError.AuthenticationRequired -> errorAuth
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is PublicProfileEvent.LoadFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                PublicProfileEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)

                PublicProfileEvent.FriendRequestSent ->
                    notificationHostState.show(friendRequestSent, InAppNotificationType.Success)

                is PublicProfileEvent.FriendRequestFailed ->
                    notificationHostState.show(actionFailed, InAppNotificationType.Error)

                is PublicProfileEvent.OpenChat -> openChatId = event.chatId

                is PublicProfileEvent.ChatFailed ->
                    notificationHostState.show(chatFailed, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("public_profile_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val loadedProfile = state.profile
            PublicProfileScreenContent(
                state = state,
                actionState = actionState,
                showActions = loadedProfile != null && loadedProfile.id != currentUserId,
                onClose = onClose,
                onRefresh = controller::refresh,
                onRetry = controller::refresh,
                onSendFriendRequest = controller::sendFriendRequest,
                onSendMessage = controller::openChat,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    openChatId?.let { chatId ->
        Dialog(
            onDismissRequest = { openChatId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            ChatConversationScreen(
                chatId = chatId,
                currentUserId = currentUserId,
                onClose = { openChatId = null },
            )
        }
    }
}

@Composable
internal fun PublicProfileScreenContent(
    state: ProfileUiState,
    actionState: PublicProfileActionState,
    showActions: Boolean,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSendFriendRequest: () -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileScreenContent(
        state = state,
        title = state.profile?.name ?: stringResource(R.string.public_profile_title),
        onClose = onClose,
        onRefresh = onRefresh,
        onRetry = onRetry,
        modifier = modifier,
        trailingContent = {
            if (showActions) {
                PublicProfileMenu(
                    actionState = actionState,
                    onSendFriendRequest = onSendFriendRequest,
                    onSendMessage = onSendMessage,
                )
            }
        },
    )
}

@Composable
private fun PublicProfileMenu(
    actionState: PublicProfileActionState,
    onSendFriendRequest: () -> Unit,
    onSendMessage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("public_profile_menu")) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.public_profile_menu),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        // Hidden once you are already friends; disabled (showing "request sent") while pending.
        if (!actionState.alreadyFriends) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (actionState.friendRequestPending) {
                            stringResource(R.string.public_profile_action_request_sent)
                        } else {
                            stringResource(R.string.public_profile_action_add_friend)
                        },
                    )
                },
                onClick = {
                    expanded = false
                    onSendFriendRequest()
                },
                enabled = actionState.canSendFriendRequest,
                leadingIcon = { Icon(Icons.Filled.PersonAddAlt1, contentDescription = null) },
                modifier = Modifier.testTag("public_profile_add_friend"),
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.public_profile_action_send_message)) },
            onClick = {
                expanded = false
                onSendMessage()
            },
            enabled = !actionState.isOpeningChat,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
            modifier = Modifier.testTag("public_profile_send_message"),
        )
    }
}

// --- Previews ---

private val previewPublicProfile = UserProfile(
    id = "other-user",
    name = "Jan Kowalski",
    email = "",
    role = "user",
    avatarUrl = null,
    bio = "Sailing enthusiast and Baltic Sea explorer.",
    city = "Gdańsk",
    country = "PL",
    sailingExperience = SailingExperience.Advanced,
    yearsOfExperience = 10,
    sailingLicenses = "RYA Yachtmaster Offshore",
    languagesSpoken = listOf("pl", "en"),
    preferredVoyageStyles = listOf("coastal", "racing"),
    facebookUrl = "https://facebook.com/jan.skipper",
    instagramUsername = "@jan_skipper",
    cruisesCount = 15,
    friendsCount = 42,
    postsCount = 28,
    currentUserFriendshipStatus = FriendshipStatus.None,
    createdAt = "2025-01-15T10:00:00Z",
)

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "en")
@Composable
private fun PublicProfilePreview() {
    SkipperClubTheme {
        PublicProfileScreenContent(
            state = ProfileUiState(profile = previewPublicProfile, hasLoadedOnce = true),
            actionState = PublicProfileActionState(),
            showActions = true,
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onSendFriendRequest = {},
            onSendMessage = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PublicProfilePreviewDark() {
    SkipperClubTheme {
        PublicProfileScreenContent(
            state = ProfileUiState(profile = previewPublicProfile, hasLoadedOnce = true),
            actionState = PublicProfileActionState(friendshipStatus = FriendshipStatus.Pending),
            showActions = true,
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onSendFriendRequest = {},
            onSendMessage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "pl")
@Composable
private fun PublicProfilePreviewPl() {
    SkipperClubTheme {
        PublicProfileScreenContent(
            state = ProfileUiState(
                profile = previewPublicProfile.copy(currentUserFriendshipStatus = FriendshipStatus.Accepted),
                hasLoadedOnce = true,
            ),
            actionState = PublicProfileActionState(friendshipStatus = FriendshipStatus.Accepted),
            showActions = true,
            onClose = {},
            onRefresh = {},
            onRetry = {},
            onSendFriendRequest = {},
            onSendMessage = {},
        )
    }
}
