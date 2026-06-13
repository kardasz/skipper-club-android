package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsModelsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun friendRequestStateMapsKnownWireValues() {
        assertEquals(FriendRequestState.Pending, FriendRequestState.fromWire("pending"))
        assertEquals(FriendRequestState.Sent, FriendRequestState.fromWire("sent"))
        assertEquals(FriendRequestState.Accepted, FriendRequestState.fromWire("accepted"))
        assertEquals(FriendRequestState.Rejected, FriendRequestState.fromWire("rejected"))
        assertEquals(FriendRequestState.Canceled, FriendRequestState.fromWire("canceled"))
    }

    @Test
    fun friendRequestStateFallsBackToUnknown() {
        assertEquals(FriendRequestState.Unknown, FriendRequestState.fromWire("something-new"))
        assertEquals(FriendRequestState.Unknown, FriendRequestState.fromWire(null))
    }

    @Test
    fun friendRequestsListDecodesToDomain() {
        val payload = """
            {
              "requests": [
                {
                  "id": "fr1",
                  "user": { "id": "u1", "name": "Jan Kowalski", "avatarUrl": "https://cdn/jan.jpg" },
                  "state": "pending",
                  "createdAt": "2026-06-13T10:00:00Z",
                  "updatedAt": "2026-06-13T10:00:00Z"
                }
              ],
              "total": 1,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<FriendRequestsListDto>(payload).toDomain()

        assertEquals(1, page.requests.size)
        val request = page.requests.first()
        assertEquals("fr1", request.id)
        assertEquals("Jan Kowalski", request.user.name)
        assertEquals(FriendRequestState.Pending, request.state)
    }

    @Test
    fun friendsListComputesHasMore() {
        val payload = """
            {
              "friends": [
                { "id": "f1", "name": "Anna", "avatarUrl": null },
                { "id": "f2", "name": "Piotr", "avatarUrl": null }
              ],
              "total": 5,
              "limit": 2,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<FriendsListDto>(payload).toDomain()

        assertEquals(listOf("f1", "f2"), page.friends.map { it.id })
        assertTrue(page.hasMore)
    }

    @Test
    fun userSearchListDecodesUsersIntoFriends() {
        val payload = """
            {
              "users": [ { "id": "u9", "name": "Maria", "avatarUrl": null } ],
              "total": 1, "limit": 20, "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<FriendUserSearchListDto>(payload).toDomain()

        assertEquals(listOf("u9"), page.friends.map { it.id })
    }
}
