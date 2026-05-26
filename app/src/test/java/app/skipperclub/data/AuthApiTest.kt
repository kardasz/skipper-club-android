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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiTest {
    @Test
    fun loginRequestIncludesExpectedMethodHeadersAndBody() {
        val request = AuthApi.loginRequest(
            email = "sailor@example.com",
            password = "secret123",
            turnstileToken = "turnstile-token",
        )

        assertEquals("POST", request.method)
        assertEquals("https://api.skipperclub.app/v1/auth/login", request.url.toString())
        assertEquals("application/json", request.header("Content-Type"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
        assertEquals("turnstile-token", request.header("X-Turnstile-Token"))
        assertEquals(
            """{"email":"sailor@example.com","password":"secret123"}""",
            request.bodyString(),
        )
    }

    @Test
    fun refreshSessionRequestDoesNotSendCaptchaToken() {
        val request = AuthApi.refreshSessionRequest(
            sessionId = "session-1",
            refreshToken = "refresh-token",
        )

        assertEquals("POST", request.method)
        assertEquals("https://api.skipperclub.app/v1/sessions/session-1/refresh", request.url.toString())
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
        assertNull(request.header("X-Turnstile-Token"))
        assertEquals("""{"refreshToken":"refresh-token"}""", request.bodyString())
    }

    @Test
    fun validationProblemMapsFieldNamesFromViolations() {
        val error = response(
            code = 422,
            body = """
                {
                  "type": "/errors/validation",
                  "title": "Invalid request",
                  "detail": "Validation failed",
                  "violations": [
                    {"propertyPath": "email.first", "message": "Invalid email"},
                    {"propertyPath": "password", "message": "Too short"}
                  ]
                }
            """.trimIndent(),
        ).toAuthErrorForTest()

        assertTrue(error is AuthError.Validation)
        assertEquals("Validation failed", error.message)
        assertEquals(setOf("email", "password"), (error as AuthError.Validation).fields)
    }

    @Test
    fun invalidCredentialsProblemMapsToTypedError() {
        val error = response(
            code = 401,
            body = problem(type = "/errors/invalid-credentials", detail = "No match"),
        ).toAuthErrorForTest()

        assertTrue(error is AuthError.InvalidCredentials)
        assertEquals("No match", error.message)
    }

    @Test
    fun invalidInvitationProblemMapsToTypedError() {
        val error = response(
            code = 400,
            body = problem(type = "/errors/invalid-invitation", detail = "Invitation expired"),
        ).toAuthErrorForTest()

        assertTrue(error is AuthError.InvalidInvitation)
        assertEquals("Invitation expired", error.message)
    }

    @Test
    fun malformedProblemFallsBackToServerError() {
        val error = response(code = 503, body = "not-json").toAuthErrorForTest()

        assertTrue(error is AuthError.Server)
        assertEquals(503, (error as AuthError.Server).statusCode)
        assertEquals("Server error (503)", error.message)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun problem(type: String, detail: String): String =
        """{"type":"$type","title":"Error","detail":"$detail"}"""

    private fun Response.toAuthErrorForTest(): AuthError =
        AuthApi.run { toAuthError() }
}

private fun Request.bodyString(): String {
    val requestBody: RequestBody = checkNotNull(body)
    val buffer = Buffer()
    requestBody.writeTo(buffer)
    return buffer.readUtf8()
}
