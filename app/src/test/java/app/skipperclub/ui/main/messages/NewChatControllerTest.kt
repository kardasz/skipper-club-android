package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatsError
import app.skipperclub.data.UsersPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChatControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeChatsGateway()
    private val events = mutableListOf<NewChatEvent>()

    private fun controller(token: String? = "token"): NewChatController {
        val controller = NewChatController(
            scope = scope,
            accessToken = { token },
            currentUserId = "me",
            gateway = gateway,
            searchDebounceMillis = 0,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    private fun users(vararg ids: String) =
        UsersPage(ids.map { testUser(it) }, total = ids.size, limit = 20, offset = 0)

    @Test
    fun initialLoadListsUsersAndFiltersOutSelf() {
        gateway.usersPage = users("me", "u1", "u2")
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("u1", "u2"), state.results.map { it.id })
        assertTrue(state.hasSearchedOnce)
        assertFalse(state.isSearching)
    }

    @Test
    fun shortQueriesAreNotSentAsSearchParameter() {
        gateway.usersPage = users("u1")
        val controller = controller()

        controller.setSearchQuery("a")

        assertNull(gateway.userSearchQueries.last().search)
    }

    @Test
    fun queryWithTwoCharactersIsSent() {
        gateway.usersPage = users("u1")
        val controller = controller()

        controller.setSearchQuery("an")

        assertEquals("an", gateway.userSearchQueries.last().search)
    }

    @Test
    fun toggleUserSelectsAndDeselects() {
        val controller = controller()
        val user = testUser("u1")

        controller.toggleUser(user)
        assertEquals(listOf("u1"), controller.state.value.selected.map { it.id })

        controller.toggleUser(user)
        assertTrue(controller.state.value.selected.isEmpty())
    }

    @Test
    fun canCreateRequiresSelectionAndGroupName() {
        val controller = controller()
        assertFalse(controller.state.value.canCreate)

        controller.toggleUser(testUser("u1"))
        assertTrue(controller.state.value.canCreate)
        assertFalse(controller.state.value.isGroup)

        controller.toggleUser(testUser("u2"))
        assertTrue(controller.state.value.isGroup)
        assertFalse(controller.state.value.canCreate)

        controller.setGroupName("Crew")
        assertTrue(controller.state.value.canCreate)
    }

    @Test
    fun createOneToOneOmitsName() {
        gateway.createdChat = testChat("created")
        val controller = controller()
        controller.toggleUser(testUser("u1"))
        controller.setGroupName("ignored")

        controller.create()

        val request = gateway.createChatRequests.single()
        assertEquals(listOf("u1"), request.participantIds)
        assertNull(request.name)
        assertTrue(events.any { it is NewChatEvent.ChatCreated && it.chat.id == "created" })
        assertFalse(controller.state.value.isCreating)
    }

    @Test
    fun createGroupSendsTrimmedName() {
        val controller = controller()
        controller.toggleUser(testUser("u1"))
        controller.toggleUser(testUser("u2"))
        controller.setGroupName("  Summer Crew  ")

        controller.create()

        val request = gateway.createChatRequests.single()
        assertEquals(listOf("u1", "u2"), request.participantIds)
        assertEquals("Summer Crew", request.name)
    }

    @Test
    fun createWithoutSelectionIsNoop() {
        val controller = controller()

        controller.create()

        assertTrue(gateway.createChatRequests.isEmpty())
    }

    @Test
    fun createFailureEmitsErrorAndResetsFlag() {
        gateway.mutationError = ChatsError.Validation(null)
        val controller = controller()
        controller.toggleUser(testUser("u1"))

        controller.create()

        assertFalse(controller.state.value.isCreating)
        assertTrue(events.any { it is NewChatEvent.OperationFailed })
    }

    @Test
    fun searchFailureSetsFlagAndEmitsEvent() {
        gateway.searchUsersError = ChatsError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.searchFailed)
        assertTrue(events.any { it is NewChatEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(events.contains(NewChatEvent.SessionExpired))
    }
}
