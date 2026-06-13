package app.skipperclub.ui.main.friends

import app.skipperclub.data.FriendRequestState
import app.skipperclub.data.FriendsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends, so an Unconfined scope runs every launched
 * coroutine to completion synchronously.
 */
class FriendsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeFriendsGateway()
    private val events = mutableListOf<FriendsEvent>()

    private fun controller(token: String? = "token"): FriendsController {
        val controller = FriendsController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            friendsPageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesRequestsAndFriends() {
        gateway.receivedRequests = requestsPage(listOf(testRequest("r1", FriendRequestState.Pending)))
        gateway.sentRequests = requestsPage(listOf(testRequest("s1", FriendRequestState.Sent)))
        gateway.friendsPages = listOf(friendsPage(listOf(testFriendUser("f1"), testFriendUser("f2")), total = 5))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("r1"), state.receivedRequests.map { it.id })
        assertEquals(listOf("s1"), state.sentRequests.map { it.id })
        assertEquals(listOf("f1", "f2"), state.friends.map { it.id })
        assertEquals(5, state.friendsTotal)
        assertTrue(state.hasMoreFriends)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadInitialIsIdempotent() {
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "listFriends:0" })
    }

    @Test
    fun loadMoreFriendsAppendsNextPageDeduplicated() {
        gateway.friendsPages = listOf(
            friendsPage(listOf(testFriendUser("f1"), testFriendUser("f2")), total = 3),
            friendsPage(listOf(testFriendUser("f2"), testFriendUser("f3")), total = 3, offset = 2),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMoreFriends()

        val state = controller.state.value
        assertEquals(listOf("f1", "f2", "f3"), state.friends.map { it.id })
        assertEquals(2, gateway.friendsQueries.last().offset)
    }

    @Test
    fun acceptRequestMovesUserIntoFriends() {
        gateway.receivedRequests = requestsPage(
            listOf(testRequest("r1", FriendRequestState.Pending, testFriendUser("u1", "Jan"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.acceptRequest(controller.state.value.receivedRequests.first())

        val state = controller.state.value
        assertTrue(state.receivedRequests.isEmpty())
        assertEquals(listOf("u1"), state.friends.map { it.id })
        assertEquals(1, state.friendsTotal)
        assertTrue(gateway.calls.contains("update:r1:accepted"))
    }

    @Test
    fun acceptFailureKeepsRequestAndEmitsError() {
        gateway.receivedRequests = requestsPage(listOf(testRequest("r1", FriendRequestState.Pending)))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = FriendsError.Server(500, null)

        controller.acceptRequest(controller.state.value.receivedRequests.first())

        assertEquals(listOf("r1"), controller.state.value.receivedRequests.map { it.id })
        assertTrue(controller.state.value.friends.isEmpty())
        assertTrue(events.any { it is FriendsEvent.OperationFailed })
    }

    @Test
    fun rejectRequestRemovesItFromReceived() {
        gateway.receivedRequests = requestsPage(listOf(testRequest("r1", FriendRequestState.Pending)))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.rejectRequest(controller.state.value.receivedRequests.first())

        assertTrue(controller.state.value.receivedRequests.isEmpty())
        assertTrue(gateway.calls.contains("update:r1:rejected"))
    }

    @Test
    fun cancelRequestRemovesItFromSent() {
        gateway.sentRequests = requestsPage(listOf(testRequest("s1", FriendRequestState.Sent)))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.cancelRequest(controller.state.value.sentRequests.first())

        assertTrue(controller.state.value.sentRequests.isEmpty())
        assertTrue(gateway.calls.contains("cancel:s1"))
    }

    @Test
    fun removeFriendDropsOptimisticallyAndCallsGateway() {
        gateway.friendsPages = listOf(friendsPage(listOf(testFriendUser("f1"), testFriendUser("f2")), total = 2))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.removeFriend(controller.state.value.friends.first())

        assertEquals(listOf("f2"), controller.state.value.friends.map { it.id })
        assertEquals(1, controller.state.value.friendsTotal)
        assertTrue(gateway.calls.contains("removeFriend:f1"))
    }

    @Test
    fun removeFriendFailureRestoresFriend() {
        gateway.friendsPages = listOf(friendsPage(listOf(testFriendUser("f1")), total = 1))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = FriendsError.Network(Exception("offline"))

        controller.removeFriend(controller.state.value.friends.first())

        assertEquals(listOf("f1"), controller.state.value.friends.map { it.id })
        assertEquals(1, controller.state.value.friendsTotal)
        assertTrue(events.any { it is FriendsEvent.OperationFailed })
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.listError = FriendsError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(controller.state.value.hasLoadedOnce)
        assertTrue(events.any { it is FriendsEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(FriendsEvent.SessionExpired))
    }

    @Test
    fun refreshReloadsAllSections() {
        gateway.receivedRequests = requestsPage(listOf(testRequest("r1", FriendRequestState.Pending)))
        gateway.friendsPages = listOf(
            friendsPage(listOf(testFriendUser("f1"))),
            friendsPage(listOf(testFriendUser("f2"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.refresh()

        assertEquals(listOf("f2"), controller.state.value.friends.map { it.id })
    }
}
