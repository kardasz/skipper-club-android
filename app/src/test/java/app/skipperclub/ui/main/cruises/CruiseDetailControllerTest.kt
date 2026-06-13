package app.skipperclub.ui.main.cruises

import app.skipperclub.data.ChatUser
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseParticipantsPage
import app.skipperclub.data.CruiseUserRole
import app.skipperclub.data.CruisesError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CruiseDetailControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeCruisesGateway()
    private val events = mutableListOf<CruiseDetailEvent>()

    private fun controller(
        token: String? = "token",
        currentUserId: String? = "me",
    ): CruiseDetailController {
        val controller = CruiseDetailController(
            scope = scope,
            accessToken = { token },
            currentUserId = { currentUserId },
            cruiseId = "c1",
            gateway = gateway,
            userSearchDebounceMillis = 0,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun loadFetchesCruise() {
        gateway.cruise = testCruise("c1")
        val controller = controller()

        controller.load()

        assertEquals("c1", controller.state.value.cruise?.id)
        assertTrue(gateway.calls.none { it.startsWith("participants") })
    }

    @Test
    fun loadAsOrganizerFetchesParticipants() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
        gateway.participantsResult = CruiseParticipantsPage(
            participants = listOf(testParticipant("p1", state = CruiseParticipantState.Pending)),
            total = 1,
            limit = 100,
            offset = 0,
        )
        val controller = controller()

        controller.load()

        assertEquals(listOf("p1"), controller.state.value.participants.map { it.id })
        assertTrue(gateway.calls.contains("participants:c1"))
    }

    @Test
    fun joinAddsParticipantAndEmitsChange() {
        gateway.cruise = testCruise("c1")
        val controller = controller(currentUserId = "me")
        controller.load()

        controller.join()

        assertTrue(gateway.calls.contains("addParticipant:c1:me"))
        assertTrue(events.any { it is CruiseDetailEvent.CruiseChanged })
    }

    @Test
    fun acceptInvitationTransitionsOwnParticipation() {
        gateway.cruise = testCruise(
            "c1",
            currentUserParticipation = testParticipant("p1", state = CruiseParticipantState.Invited),
        )
        val controller = controller()
        controller.load()

        controller.acceptInvitation()

        assertTrue(gateway.calls.contains("updateParticipantState:p1:accepted"))
    }

    @Test
    fun leaveCancelsOwnParticipation() {
        gateway.cruise = testCruise(
            "c1",
            currentUserParticipation = testParticipant("p1", state = CruiseParticipantState.Accepted),
        )
        val controller = controller()
        controller.load()

        controller.leave()

        assertTrue(gateway.calls.contains("updateParticipantState:p1:canceled_by_participant"))
    }

    @Test
    fun organizerAcceptsAndRejectsRequests() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
        val controller = controller()
        controller.load()

        controller.acceptRequest(testParticipant("p2", state = CruiseParticipantState.Pending))
        controller.rejectRequest(testParticipant("p3", state = CruiseParticipantState.Pending))
        controller.removeParticipant(testParticipant("p4", state = CruiseParticipantState.Accepted))
        controller.cancelInvitation(testParticipant("p5", state = CruiseParticipantState.Invited))

        assertTrue(gateway.calls.contains("updateParticipantState:p2:accepted"))
        assertTrue(gateway.calls.contains("updateParticipantState:p3:rejected_by_organizer"))
        assertTrue(gateway.calls.contains("updateParticipantState:p4:canceled_by_organizer"))
        assertTrue(gateway.calls.contains("updateParticipantState:p5:withdrawn_by_organizer"))
    }

    @Test
    fun inviteAddsParticipantForSelectedUser() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
        val controller = controller()
        controller.load()

        controller.invite(ChatUser(id = "u9", name = "Nowy"))

        assertTrue(gateway.calls.contains("addParticipant:c1:u9"))
    }

    @Test
    fun deleteCruiseEmitsDeleted() {
        gateway.cruise = testCruise("c1", currentUserRole = CruiseUserRole.Organizer)
        val controller = controller()
        controller.load()

        controller.deleteCruise()

        assertTrue(gateway.calls.contains("delete:c1"))
        assertTrue(events.any { it is CruiseDetailEvent.Deleted })
    }

    @Test
    fun mutationFailureEmitsOperationFailed() {
        gateway.cruise = testCruise("c1")
        val controller = controller()
        controller.load()
        gateway.mutationError = CruisesError.Conflict("already joined")

        controller.join()

        assertTrue(events.any { it is CruiseDetailEvent.OperationFailed })
    }

    @Test
    fun loadFailureSetsFlag() {
        gateway.getError = CruisesError.NotFound(null)
        val controller = controller()

        controller.load()

        assertTrue(controller.state.value.loadFailed)
    }
}
