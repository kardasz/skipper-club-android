package app.skipperclub.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsApiTest {

    @Test
    fun getRequestTargetsNotificationSettingsWithAuth() {
        val request = SettingsApi.getNotificationSettingsRequest("token")

        assertEquals("GET", request.method)
        assertEquals("/v1/profile/notification-settings", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
    }

    @Test
    fun updateRequestPutsBothFields() {
        val request = SettingsApi.updateNotificationSettingsRequest(
            accessToken = "token",
            settings = NotificationSettings(
                emailNotificationsEnabled = true,
                pushNotificationsEnabled = false,
            ),
        )

        assertEquals("PUT", request.method)
        assertEquals("/v1/profile/notification-settings", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
        assertEquals(
            """{"emailNotificationsEnabled":true,"pushNotificationsEnabled":false}""",
            request.bodyString(),
        )
    }

    @Test
    fun unauthorizedMapsToAuthenticationRequired() {
        val error = response(
            code = 401,
            body = """{"type":"/errors/unauthorized","title":"Unauthorized","detail":"Token expired"}""",
        ).toSettingsErrorForTest()

        assertTrue(error is SettingsError.AuthenticationRequired)
        assertEquals("Token expired", error.message)
    }

    @Test
    fun validationProblemMapsToValidationError() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","title":"Validation Failed",""" +
                """"detail":"pushNotificationsEnabled must be defined"}""",
        ).toSettingsErrorForTest()

        assertTrue(error is SettingsError.Validation)
        assertEquals("pushNotificationsEnabled must be defined", error.message)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toSettingsErrorForTest(): SettingsError =
        SettingsApi.run { toSettingsError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
