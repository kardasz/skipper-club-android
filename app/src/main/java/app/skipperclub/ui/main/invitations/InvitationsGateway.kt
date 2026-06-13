package app.skipperclub.ui.main.invitations

import app.skipperclub.data.InvitationListQuery
import app.skipperclub.data.InvitationsApi
import app.skipperclub.data.InvitationsPage

/**
 * Seam between the invitations UI controller and [InvitationsApi] so the
 * state-machine logic stays unit-testable with fakes (no MockWebServer needed
 * at this layer).
 */
interface InvitationsGateway {
    suspend fun list(accessToken: String, query: InvitationListQuery): InvitationsPage
    suspend fun send(accessToken: String, email: String)
    suspend fun delete(accessToken: String, invitationId: String)
}

object RealInvitationsGateway : InvitationsGateway {
    override suspend fun list(accessToken: String, query: InvitationListQuery): InvitationsPage =
        InvitationsApi.list(accessToken, query)

    override suspend fun send(accessToken: String, email: String) =
        InvitationsApi.send(accessToken, email)

    override suspend fun delete(accessToken: String, invitationId: String) =
        InvitationsApi.delete(accessToken, invitationId)
}
