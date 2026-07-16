package app.skipperclub.data

import java.util.Locale
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

class ChatsApiTest {

    @Test
    fun listChatsRequestIncludesFiltersPaginationAndHeaders() {
        val request = ChatsApi.listChatsRequest(
            accessToken = "access-token",
            query = ChatListQuery(
                type = ChatType.Group,
                search = "jan",
                sort = ChatSortField.Name,
                order = SortOrder.Asc,
                limit = 10,
                offset = 30,
            ),
        )

        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("/v1/chats", url.encodedPath)
        assertEquals("GROUP", url.queryParameter("type"))
        assertEquals("jan", url.queryParameter("search"))
        assertEquals("name", url.queryParameter("sort"))
        assertEquals("asc", url.queryParameter("order"))
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("30", url.queryParameter("offset"))
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
    }

    @Test
    fun listChatsRequestOmitsUnsetFilters() {
        val request = ChatsApi.listChatsRequest("token", ChatListQuery())

        val url = request.url
        assertNull(url.queryParameter("type"))
        assertNull(url.queryParameter("search"))
        assertEquals("updatedAt", url.queryParameter("sort"))
        assertEquals("desc", url.queryParameter("order"))
    }

    @Test
    fun listChatsRequestOmitsBlankSearch() {
        val request = ChatsApi.listChatsRequest("token", ChatListQuery(search = "   "))

        assertNull(request.url.queryParameter("search"))
    }

    @Test
    fun createChatRequestSerializesParticipantsAndName() {
        val request = ChatsApi.createChatRequest(
            accessToken = "token",
            payload = CreateChatRequest(
                participantIds = listOf("u1", "u2"),
                name = "Summer Crew",
            ),
        )

        assertEquals("POST", request.method)
        assertEquals("/v1/chats", request.url.encodedPath)
        assertEquals(
            """{"participantIds":["u1","u2"],"name":"Summer Crew"}""",
            request.bodyString(),
        )
    }

    @Test
    fun createChatRequestOmitsNullName() {
        val request = ChatsApi.createChatRequest(
            accessToken = "token",
            payload = CreateChatRequest(participantIds = listOf("u1")),
        )

        assertEquals("""{"participantIds":["u1"]}""", request.bodyString())
    }

    @Test
    fun deleteChatRequestTargetsChatPath() {
        val request = ChatsApi.deleteChatRequest("token", "chat-1")

        assertEquals("DELETE", request.method)
        assertEquals("/v1/chats/chat-1", request.url.encodedPath)
    }

    @Test
    fun listMessagesRequestIncludesPaginationAndOrder() {
        val request = ChatsApi.listMessagesRequest(
            accessToken = "token",
            chatId = "chat-1",
            limit = 30,
            offset = 60,
            order = SortOrder.Desc,
        )

        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("/v1/chats/chat-1/messages", url.encodedPath)
        assertEquals("desc", url.queryParameter("order"))
        assertEquals("30", url.queryParameter("limit"))
        assertEquals("60", url.queryParameter("offset"))
    }

    @Test
    fun sendMessageRequestSerializesText() {
        val request = ChatsApi.sendMessageRequest("token", "chat-1", "Ahoy!")

        assertEquals("POST", request.method)
        assertEquals("/v1/chats/chat-1/messages", request.url.encodedPath)
        assertEquals("""{"text":"Ahoy!"}""", request.bodyString())
    }

    @Test
    fun sendMessageRequestSerializesClientMessageIdWhenProvided() {
        val request = ChatsApi.sendMessageRequest(
            accessToken = "token",
            chatId = "chat-1",
            text = "Ahoy!",
            clientMessageId = "0d3ee1a5-51f0-4be9-9e1c-7e0e2f7f8b10",
        )

        assertEquals(
            """{"text":"Ahoy!","clientMessageId":"0d3ee1a5-51f0-4be9-9e1c-7e0e2f7f8b10"}""",
            request.bodyString(),
        )
    }

    @Test
    fun bulkActionRequestSerializesWireAction() {
        val markRead = ChatsApi.bulkActionRequest("token", ChatBulkAction.MarkRead, listOf("c1", "c2"))
        val delete = ChatsApi.bulkActionRequest("token", ChatBulkAction.Delete, listOf("c1"))

        assertEquals("POST", markRead.method)
        assertEquals("/v1/chats/actions", markRead.url.encodedPath)
        assertEquals("""{"action":"mark-read","chatIds":["c1","c2"]}""", markRead.bodyString())
        assertEquals("""{"action":"delete","chatIds":["c1"]}""", delete.bodyString())
    }

    @Test
    fun unreadCountRequestTargetsUnreadCountPath() {
        val request = ChatsApi.unreadCountRequest("token")

        assertEquals("GET", request.method)
        assertEquals("/v1/chats/unread-count", request.url.encodedPath)
    }

    @Test
    fun searchUsersRequestIncludesSearchAndPagination() {
        val request = ChatsApi.searchUsersRequest(
            accessToken = "token",
            query = UserSearchQuery(search = "anna", limit = 10, offset = 20),
        )

        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("/v1/users", url.encodedPath)
        assertEquals("anna", url.queryParameter("search"))
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("20", url.queryParameter("offset"))
    }

    @Test
    fun searchUsersRequestOmitsBlankSearch() {
        val request = ChatsApi.searchUsersRequest("token", UserSearchQuery(search = " "))

        assertNull(request.url.queryParameter("search"))
    }

    @Test
    fun notFoundProblemMapsToNotFound() {
        val error = response(
            code = 404,
            body = """{"type":"/errors/chat-not-found","title":"Chat Not Found","detail":"Gone"}""",
        ).toChatsErrorForTest()

        assertTrue(error is ChatsError.NotFound)
        assertEquals("Gone", error.message)
    }

    @Test
    fun validationProblemMapsToValidation() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","title":"Validation Failed","detail":"Invalid"}""",
        ).toChatsErrorForTest()

        assertTrue(error is ChatsError.Validation)
    }

    @Test
    fun unauthorizedMapsToAuthenticationRequired() {
        val error = response(code = 401, body = "").toChatsErrorForTest()

        assertTrue(error is ChatsError.AuthenticationRequired)
    }

    @Test
    fun forbiddenMapsToForbidden() {
        val error = response(code = 403, body = "").toChatsErrorForTest()

        assertTrue(error is ChatsError.Forbidden)
    }

    @Test
    fun serverErrorMapsToServerWithStatusCode() {
        val error = response(code = 502, body = "").toChatsErrorForTest()

        assertTrue(error is ChatsError.Server)
        assertEquals(502, (error as ChatsError.Server).statusCode)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toChatsErrorForTest(): ChatsError =
        ChatsApi.run { toChatsError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
