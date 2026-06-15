package app.skipperclub.ui.main.profile

import app.skipperclub.data.ChatsError
import app.skipperclub.data.FriendsError
import app.skipperclub.data.FriendshipStatus
import app.skipperclub.data.ProfileError
import app.skipperclub.ui.main.friends.FakeFriendsGateway
import app.skipperclub.ui.main.messages.FakeChatsGateway
import app.skipperclub.ui.main.messages.testChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicProfileControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeProfileGateway()
    private val friendsGateway = FakeFriendsGateway()
    private val chatsGateway = FakeChatsGateway()
    private val events = mutableListOf<PublicProfileEvent>()

    private fun controller(userId: String = "other", token: String? = "token"): PublicProfileController {
        val controller = PublicProfileController(
            scope = scope,
            userId = userId,
            accessToken = { token },
            gateway = gateway,
            friendsGateway = friendsGateway,
            chatsGateway = chatsGateway,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun loadFetchesTargetUserById() {
        gateway.userProfile = testProfile(id = "other", name = "Jan Kowalski", email = "")
        val controller = controller(userId = "other")

        controller.loadInitialIfNeeded()

        assertEquals("Jan Kowalski", controller.state.value.profile?.name)
        assertEquals("other", gateway.lastUserId)
        assertTrue(controller.state.value.hasLoadedOnce)
        assertFalse(controller.state.value.loadFailed)
    }

    @Test
    fun loadReflectsFriendshipStatusInActionState() {
        gateway.userProfile = testProfile(id = "other", email = "")
            .copy(currentUserFriendshipStatus = FriendshipStatus.Accepted)
        val controller = controller(userId = "other")

        controller.loadInitialIfNeeded()

        assertEquals(FriendshipStatus.Accepted, controller.actionState.value.friendshipStatus)
        assertTrue(controller.actionState.value.alreadyFriends)
        assertFalse(controller.actionState.value.canSendFriendRequest)
    }

    @Test
    fun loadInitialIsIdempotent() {
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.userCalls)
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.userError = ProfileError.NotFound(null)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is PublicProfileEvent.LoadFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(PublicProfileEvent.SessionExpired))
    }

    @Test
    fun sendFriendRequestMarksPendingAndEmitsSent() {
        val controller = controller(userId = "other")

        controller.sendFriendRequest()

        assertEquals(FriendshipStatus.Pending, controller.actionState.value.friendshipStatus)
        assertFalse(controller.actionState.value.isSendingFriendRequest)
        assertTrue(events.contains(PublicProfileEvent.FriendRequestSent))
        assertTrue(friendsGateway.calls.contains("send:other"))
    }

    @Test
    fun sendFriendRequestIgnoredWhenAlreadyFriends() {
        gateway.userProfile = testProfile(id = "other", email = "")
            .copy(currentUserFriendshipStatus = FriendshipStatus.Accepted)
        val controller = controller(userId = "other")
        controller.loadInitialIfNeeded()

        controller.sendFriendRequest()

        assertFalse(friendsGateway.calls.any { it.startsWith("send:") })
    }

    @Test
    fun sendFriendRequestConflictSettlesAsPending() {
        friendsGateway.sendError = FriendsError.Conflict(type = "/errors/friend-request-exists", detail = null)
        val controller = controller(userId = "other")

        controller.sendFriendRequest()

        assertEquals(FriendshipStatus.Pending, controller.actionState.value.friendshipStatus)
        assertTrue(events.contains(PublicProfileEvent.FriendRequestSent))
        assertFalse(events.any { it is PublicProfileEvent.FriendRequestFailed })
    }

    @Test
    fun sendFriendRequestOtherErrorEmitsFailed() {
        friendsGateway.sendError = FriendsError.Network(RuntimeException("boom"))
        val controller = controller(userId = "other")

        controller.sendFriendRequest()

        assertEquals(FriendshipStatus.None, controller.actionState.value.friendshipStatus)
        assertTrue(events.any { it is PublicProfileEvent.FriendRequestFailed })
    }

    @Test
    fun openChatCreatesOneToOneChatAndEmitsOpen() {
        chatsGateway.createdChat = testChat("created-chat")
        val controller = controller(userId = "other")

        controller.openChat()

        assertEquals(listOf("other"), chatsGateway.createChatRequests.single().participantIds)
        assertTrue(events.contains(PublicProfileEvent.OpenChat("created-chat")))
        assertFalse(controller.actionState.value.isOpeningChat)
    }

    @Test
    fun openChatFailureEmitsChatFailed() {
        chatsGateway.mutationError = ChatsError.Network(RuntimeException("boom"))
        val controller = controller(userId = "other")

        controller.openChat()

        assertTrue(events.any { it is PublicProfileEvent.ChatFailed })
        assertFalse(controller.actionState.value.isOpeningChat)
    }
}
