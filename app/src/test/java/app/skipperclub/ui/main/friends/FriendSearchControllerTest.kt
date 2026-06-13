package app.skipperclub.ui.main.friends

import app.skipperclub.data.FriendsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendSearchControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeFriendsGateway()
    private val events = mutableListOf<FriendSearchEvent>()

    private fun controller(currentUserId: String? = "me", token: String? = "token"): FriendSearchController {
        val controller = FriendSearchController(
            scope = scope,
            accessToken = { token },
            currentUserId = currentUserId,
            gateway = gateway,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialSearchPopulatesResultsExcludingSelf() {
        gateway.searchPage = friendsPage(listOf(testFriendUser("me"), testFriendUser("u1"), testFriendUser("u2")))
        val controller = controller(currentUserId = "me")

        controller.loadInitialIfNeeded()

        assertEquals(listOf("u1", "u2"), controller.state.value.results.map { it.id })
        assertTrue(controller.state.value.hasSearchedOnce)
    }

    @Test
    fun sendRequestMarksUserSentAndEmitsEvent() {
        gateway.searchPage = friendsPage(listOf(testFriendUser("u1")))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.sendRequest(controller.state.value.results.first())

        assertTrue(controller.state.value.sentUserIds.contains("u1"))
        assertFalse(controller.state.value.sendingUserIds.contains("u1"))
        assertTrue(gateway.calls.contains("send:u1"))
        assertTrue(events.any { it is FriendSearchEvent.RequestSent })
    }

    @Test
    fun sendRequestIsNoopWhenAlreadySent() {
        gateway.searchPage = friendsPage(listOf(testFriendUser("u1")))
        val controller = controller()
        controller.loadInitialIfNeeded()
        controller.sendRequest(controller.state.value.results.first())

        controller.sendRequest(controller.state.value.results.first())

        assertEquals(1, gateway.calls.count { it == "send:u1" })
    }

    @Test
    fun conflictMarksUserSentAndEmitsError() {
        gateway.searchPage = friendsPage(listOf(testFriendUser("u1")))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.sendError = FriendsError.Conflict("/errors/friend-request-already-exists", "Already exists")

        controller.sendRequest(controller.state.value.results.first())

        assertTrue(controller.state.value.sentUserIds.contains("u1"))
        assertTrue(events.any { it is FriendSearchEvent.OperationFailed })
    }

    @Test
    fun networkFailureClearsSendingAndEmitsError() {
        gateway.searchPage = friendsPage(listOf(testFriendUser("u1")))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.sendError = FriendsError.Network(Exception("offline"))

        controller.sendRequest(controller.state.value.results.first())

        assertFalse(controller.state.value.sendingUserIds.contains("u1"))
        assertFalse(controller.state.value.sentUserIds.contains("u1"))
        assertTrue(events.any { it is FriendSearchEvent.OperationFailed })
    }

    @Test
    fun searchFailureSetsFlag() {
        gateway.searchError = FriendsError.Server(500, null)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.searchFailed)
        assertTrue(events.any { it is FriendSearchEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.searchFailed)
        assertTrue(events.contains(FriendSearchEvent.SessionExpired))
    }
}
