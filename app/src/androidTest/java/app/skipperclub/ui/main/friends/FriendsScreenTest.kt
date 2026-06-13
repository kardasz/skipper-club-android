package app.skipperclub.ui.main.friends

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.skipperclub.R
import app.skipperclub.data.FriendRequest
import app.skipperclub.data.FriendRequestState
import app.skipperclub.data.FriendUser
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FriendsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun request(id: String, name: String, state: FriendRequestState) = FriendRequest(
        id = id,
        user = FriendUser(id = "user-$id", name = name, avatarUrl = null),
        state = state,
        createdAt = "2026-06-13T09:00:00Z",
        updatedAt = "2026-06-13T09:00:00Z",
    )

    private val populated = FriendsUiState(
        receivedRequests = listOf(request("r1", "Jan Kowalski", FriendRequestState.Pending)),
        sentRequests = listOf(request("s1", "Anna Nowak", FriendRequestState.Sent)),
        friends = listOf(FriendUser(id = "f1", name = "Piotr Wiśniewski", avatarUrl = null)),
        friendsTotal = 1,
        hasLoadedOnce = true,
    )

    @Test
    fun rendersRequestsAndFriends() {
        content(populated)

        compose.onNodeWithText(text(R.string.friends_title)).assertExists()
        compose.onNodeWithTag("friend_request_r1").assertExists()
        compose.onNodeWithTag("friend_request_s1").assertExists()
        compose.onNodeWithTag("friend_f1").assertExists()
    }

    @Test
    fun acceptAndRejectButtonsEmitCallbacks() {
        var accepted: FriendRequest? = null
        var rejected: FriendRequest? = null
        content(populated, onAccept = { accepted = it }, onReject = { rejected = it })

        compose.onNodeWithTag("friend_request_accept_r1").performClick()
        compose.onNodeWithTag("friend_request_reject_r1").performClick()

        assertEquals("r1", accepted?.id)
        assertEquals("r1", rejected?.id)
    }

    @Test
    fun cancelButtonEmitsCallbackForSentRequest() {
        var canceled: FriendRequest? = null
        content(populated, onCancel = { canceled = it })

        compose.onNodeWithTag("friend_request_cancel_s1").performClick()

        assertEquals("s1", canceled?.id)
    }

    @Test
    fun removingFriendRequiresConfirmation() {
        var removed: FriendUser? = null
        content(populated, onRemoveFriend = { removed = it })

        compose.onNodeWithTag("friend_remove_f1").performClick()
        // Nothing removed until the confirmation dialog is accepted.
        assertEquals(null, removed)

        compose.onNodeWithTag("friends_remove_confirm").performClick()
        assertEquals("f1", removed?.id)
    }

    @Test
    fun tappingFriendOpensProfile() {
        var opened: FriendUser? = null
        content(populated, onOpenProfile = { opened = it })

        compose.onNodeWithTag("friend_f1").performClick()

        assertEquals("f1", opened?.id)
    }

    @Test
    fun inviteButtonEmitsCallback() {
        var clicks = 0
        content(populated, onInviteClick = { clicks++ })

        compose.onNodeWithTag("friends_invite_button").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun emptyStateShownWhenNothingToShow() {
        content(FriendsUiState(hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.friends_empty_title)).assertExists()
    }

    @Test
    fun backButtonEmitsCloseCallback() {
        var closed = 0
        content(populated, onClose = { closed++ })

        compose.onNodeWithTag("friends_back").performClick()
        assertEquals(1, closed)
    }

    private fun content(
        state: FriendsUiState,
        onClose: () -> Unit = {},
        onAccept: (FriendRequest) -> Unit = {},
        onReject: (FriendRequest) -> Unit = {},
        onCancel: (FriendRequest) -> Unit = {},
        onRemoveFriend: (FriendUser) -> Unit = {},
        onOpenProfile: (FriendUser) -> Unit = {},
        onInviteClick: () -> Unit = {},
    ) {
        compose.setContent {
            SkipperClubTheme {
                FriendsScreenContent(
                    state = state,
                    onClose = onClose,
                    onRefresh = {},
                    onRetry = {},
                    onAccept = onAccept,
                    onReject = onReject,
                    onCancel = onCancel,
                    onRemoveFriend = onRemoveFriend,
                    onOpenProfile = onOpenProfile,
                    onInviteClick = onInviteClick,
                    onLoadMore = {},
                )
            }
        }
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
