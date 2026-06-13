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

class AlertsApiTest {
    @Test
    fun createRequestSendsCategoryContentGeometryAndLanguageHeaders() {
        val request = AlertsApi.createRequest(
            accessToken = "access-token",
            category = AlertCategory.Weather,
            content = "  Strong bora expected near Velebit channel.  ",
            geometry = AlertGeometry.point(lat = 44.71, lng = 15.12),
        )

        assertEquals("POST", request.method)
        assertEquals("https://api.skipperclub.app/v1/alerts", request.url.toString())
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
        assertEquals(Locale.getDefault().language.ifBlank { "en" }, request.header("Content-Language"))
        assertEquals(
            """{"category":"weather","content":"Strong bora expected near Velebit channel.","geometry":{"type":"Point","coordinates":[15.12,44.71]}}""",
            request.bodyString(),
        )
    }

    @Test
    fun createRequestOmitsGeometryWhenNull() {
        val request = AlertsApi.createRequest(
            accessToken = "token",
            category = AlertCategory.Obstruction,
            content = "Submerged net reported.",
            geometry = null,
        )

        assertEquals(
            """{"category":"obstruction","content":"Submerged net reported."}""",
            request.bodyString(),
        )
    }

    @Test
    fun validationProblemSurfacesContentFieldError() {
        val error = response(
            code = 422,
            body = """
                {
                  "type": "/errors/validation",
                  "title": "Błąd walidacji",
                  "status": 422,
                  "detail": "Żądanie zawiera nieprawidłowe dane",
                  "violations": [
                    { "propertyPath": "content", "message": "content should not be empty" }
                  ]
                }
            """.trimIndent(),
        ).toAlertErrorForTest()

        assertTrue(error is AlertError.Validation)
        assertEquals(
            "content should not be empty",
            (error as AlertError.Validation).fieldErrors["content"],
        )
    }

    @Test
    fun unauthorizedProblemMapsToAuthenticationRequired() {
        val error = response(
            code = 401,
            body = """{"title":"Unauthorized","detail":"Token expired"}""",
        ).toAlertErrorForTest()

        assertTrue(error is AlertError.AuthenticationRequired)
        assertEquals("Token expired", error.message)
    }

    @Test
    fun serverProblemKeepsStatusCode() {
        val error = response(code = 500, body = "").toAlertErrorForTest()

        assertTrue(error is AlertError.Server)
        assertEquals(500, (error as AlertError.Server).statusCode)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toAlertErrorForTest(): AlertError =
        AlertsApi.run { toAlertError() }
}

private fun Request.bodyString(): String {
    val requestBody: RequestBody = checkNotNull(body)
    val buffer = Buffer()
    requestBody.writeTo(buffer)
    return buffer.readUtf8()
}
