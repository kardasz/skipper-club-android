package app.skipperclub.ui.main.invitations

import app.skipperclub.data.Invitation
import app.skipperclub.data.InvitationListQuery
import app.skipperclub.data.InvitationStatus
import app.skipperclub.data.InvitationsError
import app.skipperclub.data.InvitationsPage
import app.skipperclub.data.Inviter

internal fun testInvitation(
    id: String,
    email: String = "$id@example.com",
    status: InvitationStatus = InvitationStatus.Pending,
) = Invitation(
    id = id,
    email = email,
    status = status,
    expiresAt = "2026-06-20T09:00:00Z",
    createdAt = "2026-06-13T09:00:00Z",
    inviter = Inviter(id = "admin-1", name = "Anna Nowak"),
)

internal fun invitationsPage(
    invitations: List<Invitation>,
    total: Int = invitations.size,
    offset: Int = 0,
) = InvitationsPage(invitations = invitations, total = total, limit = 20, offset = offset)

/** Configurable in-memory [InvitationsGateway]; records calls for assertions. */
internal class FakeInvitationsGateway : InvitationsGateway {
    var pages: List<InvitationsPage> = listOf(invitationsPage(emptyList()))
    var listError: InvitationsError? = null
    var mutationError: InvitationsError? = null
    val listQueries = mutableListOf<InvitationListQuery>()
    val calls = mutableListOf<String>()

    private var listCallCount = 0

    override suspend fun list(accessToken: String, query: InvitationListQuery): InvitationsPage {
        calls += "list"
        listQueries += query
        listError?.let { throw it }
        val page = pages[minOf(listCallCount, pages.lastIndex)]
        listCallCount++
        return page
    }

    override suspend fun send(accessToken: String, email: String) {
        calls += "send:$email"
        mutationError?.let { throw it }
    }

    override suspend fun delete(accessToken: String, invitationId: String) {
        calls += "delete:$invitationId"
        mutationError?.let { throw it }
    }
}
