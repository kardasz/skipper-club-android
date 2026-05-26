package app.skipperclub.data

import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInsApiTest {
    @Test
    fun upsertRequestIncludesAuthorizationLanguageAndTrimmedLocationName() {
        val request = CheckInsApi.upsertRequest(
            accessToken = "access-token",
            lat = 54.352,
            lng = 18.646,
            locationName = "  Marina Gdansk  ",
        )

        assertEquals("PUT", request.method)
        assertEquals("https://api.skipperclub.app/v1/check-ins", request.url.toString())
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
        assertEquals(
            """{"lat":54.352,"lng":18.646,"locationName":"Marina Gdansk"}""",
            request.bodyString(),
        )
    }

    @Test
    fun upsertRequestOmitsBlankLocationName() {
        val request = CheckInsApi.upsertRequest(
            accessToken = "access-token",
            lat = 54.352,
            lng = 18.646,
            locationName = "   ",
        )

        assertEquals("""{"lat":54.352,"lng":18.646}""", request.bodyString())
    }

    @Test
    fun unauthorizedProblemMapsToAuthenticationRequired() {
        val error = response(
            code = 401,
            body = """{"title":"Unauthorized","detail":"Token expired"}""",
        ).toCheckInErrorForTest()

        assertTrue(error is CheckInError.AuthenticationRequired)
        assertEquals("Token expired", error.message)
    }

    @Test
    fun rateLimitProblemMapsToRateLimited() {
        val error = response(
            code = 429,
            body = """{"title":"Too many requests","detail":"Slow down"}""",
        ).toCheckInErrorForTest()

        assertTrue(error is CheckInError.RateLimited)
        assertEquals("Slow down", error.message)
    }

    @Test
    fun serverProblemKeepsStatusCode() {
        val error = response(code = 500, body = "").toCheckInErrorForTest()

        assertTrue(error is CheckInError.Server)
        assertEquals(500, (error as CheckInError.Server).statusCode)
        assertEquals("Server error (500)", error.message)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toCheckInErrorForTest(): CheckInError =
        CheckInsApi.run { toCheckInError() }
}

private fun Request.bodyString(): String {
    val requestBody: RequestBody = checkNotNull(body)
    val buffer = Buffer()
    requestBody.writeTo(buffer)
    return buffer.readUtf8()
}
