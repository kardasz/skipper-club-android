package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class InvitationsModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun listDtoMapsToDomainPageWithPagination() {
        val payload = """
            {
              "invitations": [
                {
                  "id": "inv-1",
                  "email": "friend@example.com",
                  "status": "pending",
                  "expiresAt": "2025-01-25T10:30:00Z",
                  "createdAt": "2025-01-18T10:30:00Z",
                  "inviter": { "id": "admin-1", "name": "Admin User" }
                }
              ],
              "total": 42,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<InvitationListDto>(payload).toDomain()

        assertEquals(1, page.invitations.size)
        val invitation = page.invitations.first()
        assertEquals("inv-1", invitation.id)
        assertEquals("friend@example.com", invitation.email)
        assertEquals(InvitationStatus.Pending, invitation.status)
        assertEquals("Admin User", invitation.inviter.name)
        assertEquals(42, page.total)
        assertEquals(true, page.hasMore)
    }

    @Test
    fun unknownStatusFallsBackToUnknown() {
        assertEquals(InvitationStatus.Unknown, InvitationStatus.fromWire("revoked"))
        assertEquals(InvitationStatus.Accepted, InvitationStatus.fromWire("accepted"))
        assertEquals(InvitationStatus.Expired, InvitationStatus.fromWire("expired"))
    }

    @Test
    fun hasMoreIsFalseWhenAllResultsLoaded() {
        val page = InvitationsPage(
            invitations = List(5) {
                Invitation(
                    id = "i$it",
                    email = "u$it@example.com",
                    status = InvitationStatus.Pending,
                    expiresAt = "2025-01-25T10:30:00Z",
                    createdAt = "2025-01-18T10:30:00Z",
                    inviter = Inviter("admin", "Admin"),
                )
            },
            total = 5,
            limit = 20,
            offset = 0,
        )

        assertEquals(false, page.hasMore)
    }
}
