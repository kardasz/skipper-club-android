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

class InvitationsApiTest {

    @Test
    fun listRequestIncludesStatusFilterPaginationAndHeaders() {
        val request = InvitationsApi.listRequest(
            accessToken = "access-token",
            query = InvitationListQuery(status = InvitationStatus.Pending, limit = 10, offset = 30),
        )

        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("/v1/invitations", url.encodedPath)
        assertEquals("pending", url.queryParameter("status"))
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("30", url.queryParameter("offset"))
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
    }

    @Test
    fun listRequestOmitsStatusWhenUnset() {
        val request = InvitationsApi.listRequest("token", InvitationListQuery())

        assertNull(request.url.queryParameter("status"))
        assertEquals("20", request.url.queryParameter("limit"))
        assertEquals("0", request.url.queryParameter("offset"))
    }

    @Test
    fun sendRequestPostsEmailBody() {
        val request = InvitationsApi.sendRequest("token", "friend@example.com")

        assertEquals("POST", request.method)
        assertEquals("/v1/invitations", request.url.encodedPath)
        assertEquals("""{"email":"friend@example.com"}""", request.bodyString())
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
    }

    @Test
    fun deleteRequestTargetsInvitationPath() {
        val request = InvitationsApi.deleteRequest("token", "inv-1")

        assertEquals("DELETE", request.method)
        assertEquals("/v1/invitations/inv-1", request.url.encodedPath)
    }

    @Test
    fun unauthorizedMapsToAuthenticationRequired() {
        val error = response(code = 401, body = "").toInvitationsErrorForTest()

        assertTrue(error is InvitationsError.AuthenticationRequired)
    }

    @Test
    fun forbiddenMapsToForbidden() {
        val error = response(code = 403, body = "").toInvitationsErrorForTest()

        assertTrue(error is InvitationsError.Forbidden)
    }

    @Test
    fun notFoundMapsToNotFoundWithDetail() {
        val error = response(
            code = 404,
            body = """{"type":"/errors/invitation-not-found","title":"Invitation Not Found","detail":"Gone"}""",
        ).toInvitationsErrorForTest()

        assertTrue(error is InvitationsError.NotFound)
        assertEquals("Gone", error.message)
    }

    @Test
    fun conflictMapsToEmailAlreadyRegistered() {
        val error = response(
            code = 409,
            body = """{"type":"/errors/invitation-email-already-registered","title":"Email Already Registered"}""",
        ).toInvitationsErrorForTest()

        assertTrue(error is InvitationsError.EmailAlreadyRegistered)
    }

    @Test
    fun validationMapsToValidation() {
        val error = response(code = 422, body = """{"type":"/errors/validation"}""").toInvitationsErrorForTest()

        assertTrue(error is InvitationsError.Validation)
    }

    @Test
    fun serverErrorMapsToServerWithStatusCode() {
        val error = response(code = 502, body = "").toInvitationsErrorForTest()

        assertTrue(error is InvitationsError.Server)
        assertEquals(502, (error as InvitationsError.Server).statusCode)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toInvitationsErrorForTest(): InvitationsError =
        InvitationsApi.run { toInvitationsError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
