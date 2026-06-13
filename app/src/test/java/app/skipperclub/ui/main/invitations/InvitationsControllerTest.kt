package app.skipperclub.ui.main.invitations

import app.skipperclub.data.InvitationStatus
import app.skipperclub.data.InvitationsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends, so an Unconfined scope runs every launched
 * coroutine to completion synchronously.
 */
class InvitationsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeInvitationsGateway()
    private val events = mutableListOf<InvitationsEvent>()

    private fun controller(token: String? = "token"): InvitationsController {
        val controller = InvitationsController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            pageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesInvitationsAndPagingState() {
        gateway.pages = listOf(
            invitationsPage(listOf(testInvitation("i1"), testInvitation("i2")), total = 5),
        )
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("i1", "i2"), state.invitations.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadInitialIsIdempotent() {
        gateway.pages = listOf(invitationsPage(listOf(testInvitation("i1"))))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun loadMoreAppendsNextPageDeduplicated() {
        gateway.pages = listOf(
            invitationsPage(listOf(testInvitation("i1"), testInvitation("i2")), total = 3),
            invitationsPage(listOf(testInvitation("i2"), testInvitation("i3")), total = 3, offset = 2),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf("i1", "i2", "i3"), state.invitations.map { it.id })
        assertEquals(2, gateway.listQueries.last().offset)
    }

    @Test
    fun loadMoreIsNoopWithoutMorePages() {
        gateway.pages = listOf(invitationsPage(listOf(testInvitation("i1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun deleteRemovesInvitationAndCallsGateway() {
        gateway.pages = listOf(invitationsPage(listOf(testInvitation("i1"), testInvitation("i2"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.delete(controller.state.value.invitations.first())

        assertEquals(listOf("i2"), controller.state.value.invitations.map { it.id })
        assertTrue(gateway.calls.contains("delete:i1"))
    }

    @Test
    fun deleteFailureRestoresInvitationAndEmitsError() {
        gateway.pages = listOf(invitationsPage(listOf(testInvitation("i1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = InvitationsError.Server(500, null)

        controller.delete(controller.state.value.invitations.first())

        assertEquals(listOf("i1"), controller.state.value.invitations.map { it.id })
        assertTrue(events.any { it is InvitationsEvent.OperationFailed })
    }

    @Test
    fun createInvitationSendsReloadsAndEmitsCreatedEvent() {
        gateway.pages = listOf(
            invitationsPage(emptyList()),
            invitationsPage(listOf(testInvitation("i1", email = "new@example.com"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.createInvitation("new@example.com")

        assertTrue(gateway.calls.contains("send:new@example.com"))
        assertEquals(listOf("i1"), controller.state.value.invitations.map { it.id })
        assertFalse(controller.state.value.isSending)
        assertTrue(events.any { it is InvitationsEvent.InvitationCreated })
    }

    @Test
    fun createInvitationFailureEmitsErrorAndClearsSending() {
        gateway.pages = listOf(invitationsPage(emptyList()))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = InvitationsError.EmailAlreadyRegistered(null)

        controller.createInvitation("taken@example.com")

        assertFalse(controller.state.value.isSending)
        assertFalse(events.any { it is InvitationsEvent.InvitationCreated })
        assertTrue(events.any { it is InvitationsEvent.OperationFailed })
    }

    @Test
    fun resendCallsSendReloadsListAndEmitsResentEvent() {
        gateway.pages = listOf(
            invitationsPage(listOf(testInvitation("i1", email = "friend@example.com"))),
            invitationsPage(listOf(testInvitation("i1b", email = "friend@example.com"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.resend(controller.state.value.invitations.first())

        assertTrue(gateway.calls.contains("send:friend@example.com"))
        assertEquals(listOf("i1b"), controller.state.value.invitations.map { it.id })
        assertNull(controller.state.value.resendingId)
        assertTrue(events.any { it is InvitationsEvent.InvitationResent })
    }

    @Test
    fun resendFailureClearsResendingAndEmitsError() {
        gateway.pages = listOf(invitationsPage(listOf(testInvitation("i1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = InvitationsError.Network(Exception("offline"))

        controller.resend(controller.state.value.invitations.first())

        assertNull(controller.state.value.resendingId)
        assertTrue(events.any { it is InvitationsEvent.OperationFailed })
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.listError = InvitationsError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(controller.state.value.hasLoadedOnce)
        assertTrue(events.any { it is InvitationsEvent.OperationFailed })
    }

    @Test
    fun forbiddenLoadEmitsOperationFailed() {
        gateway.listError = InvitationsError.Forbidden(null)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is InvitationsEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(InvitationsEvent.SessionExpired))
    }

    @Test
    fun refreshReplacesInvitations() {
        gateway.pages = listOf(
            invitationsPage(listOf(testInvitation("i1"))),
            invitationsPage(listOf(testInvitation("i2", status = InvitationStatus.Accepted))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.refresh()

        assertEquals(listOf("i2"), controller.state.value.invitations.map { it.id })
    }
}
