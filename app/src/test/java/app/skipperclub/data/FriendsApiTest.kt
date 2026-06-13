package app.skipperclub.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsApiTest {

    @Test
    fun listRequestsIncludesStateFilterAndPagination() {
        val request = FriendsApi.listRequestsRequest(
            "access-token",
            FriendRequestListQuery(state = FriendRequestState.Pending, limit = 10, offset = 20),
        )

        assertEquals("GET", request.method)
        assertEquals("/v1/friend-requests", request.url.encodedPath)
        assertEquals("pending", request.url.queryParameter("state"))
        assertEquals("10", request.url.queryParameter("limit"))
        assertEquals("20", request.url.queryParameter("offset"))
        assertEquals("Bearer access-token", request.header("Authorization"))
    }

    @Test
    fun listRequestsOmitsStateWhenUnset() {
        val request = FriendsApi.listRequestsRequest("token", FriendRequestListQuery())

        assertNull(request.url.queryParameter("state"))
    }

    @Test
    fun sendRequestPostsUserIdBody() {
        val request = FriendsApi.sendRequest("token", "user-42")

        assertEquals("POST", request.method)
        assertEquals("/v1/friend-requests", request.url.encodedPath)
        assertEquals("""{"userId":"user-42"}""", request.bodyString())
    }

    @Test
    fun updateRequestPatchesStateBody() {
        val request = FriendsApi.updateRequestRequest("token", "req-1", FriendRequestState.Accepted)

        assertEquals("PATCH", request.method)
        assertEquals("/v1/friend-requests/req-1", request.url.encodedPath)
        assertEquals("""{"state":"accepted"}""", request.bodyString())
    }

    @Test
    fun cancelRequestDeletesByPath() {
        val request = FriendsApi.cancelRequestRequest("token", "req-1")

        assertEquals("DELETE", request.method)
        assertEquals("/v1/friend-requests/req-1", request.url.encodedPath)
    }

    @Test
    fun listFriendsIncludesSearchAndPagination() {
        val request = FriendsApi.listFriendsRequest("token", FriendListQuery(search = "jan", limit = 30, offset = 5))

        assertEquals("/v1/friends", request.url.encodedPath)
        assertEquals("jan", request.url.queryParameter("search"))
        assertEquals("30", request.url.queryParameter("limit"))
        assertEquals("5", request.url.queryParameter("offset"))
    }

    @Test
    fun removeFriendDeletesByPath() {
        val request = FriendsApi.removeFriendRequest("token", "friend-9")

        assertEquals("DELETE", request.method)
        assertEquals("/v1/friends/friend-9", request.url.encodedPath)
    }

    @Test
    fun searchUsersHitsUsersEndpoint() {
        val request = FriendsApi.searchUsersRequest("token", FriendListQuery(search = "an", limit = 20))

        assertEquals("/v1/users", request.url.encodedPath)
        assertEquals("an", request.url.queryParameter("search"))
    }

    @Test
    fun unauthorizedMapsToAuthenticationRequired() {
        val error = response(401, "").toFriendsErrorForTest()
        assertTrue(error is FriendsError.AuthenticationRequired)
    }

    @Test
    fun forbiddenMapsToForbidden() {
        val error = response(403, "").toFriendsErrorForTest()
        assertTrue(error is FriendsError.Forbidden)
    }

    @Test
    fun conflictPreservesTypeAndDetail() {
        val error = response(
            422,
            """{"type":"/errors/users-already-friends","title":"Already friends","detail":"You are already friends"}""",
        ).toFriendsErrorForTest()

        assertTrue(error is FriendsError.Conflict)
        assertEquals("/errors/users-already-friends", (error as FriendsError.Conflict).type)
        assertEquals("You are already friends", error.message)
    }

    @Test
    fun serverErrorCarriesStatusCode() {
        val error = response(503, "").toFriendsErrorForTest()
        assertTrue(error is FriendsError.Server)
        assertEquals(503, (error as FriendsError.Server).statusCode)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toFriendsErrorForTest(): FriendsError =
        FriendsApi.run { toFriendsError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
