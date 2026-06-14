package app.skipperclub.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CruisesApiTest {

    @Test
    fun nestJsBadRequestSurfacesMessageAsValidationDetail() {
        val error = response(
            code = 400,
            body = """{"message":"Departure date must be in the future","error":"Bad Request","statusCode":400}""",
        ).toCruisesErrorForTest()

        assertTrue(error is CruisesError.Validation)
        assertEquals("Departure date must be in the future", error.message)
    }

    @Test
    fun nestJsValidationArrayMessageIsJoined() {
        val error = response(
            code = 422,
            body = """{"message":["title must be longer","cost must be positive"],"error":"Unprocessable Entity"}""",
        ).toCruisesErrorForTest()

        assertTrue(error is CruisesError.Validation)
        assertEquals("title must be longer cost must be positive", error.message)
    }

    @Test
    fun rfc7807ProblemDetailStillWins() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","title":"Validation Failed","detail":"Invalid body"}""",
        ).toCruisesErrorForTest()

        assertTrue(error is CruisesError.Validation)
        assertEquals("Invalid body", error.message)
    }

    @Test
    fun unparseableBodyFallsBackToGenericValidationMessage() {
        val error = response(code = 400, body = "not json").toCruisesErrorForTest()

        assertTrue(error is CruisesError.Validation)
        assertEquals("Validation failed", error.message)
    }

    @Test
    fun createRequestPostsPayloadWithHeaders() {
        val payload = CruisePayload(
            title = "Adriatic Summer",
            description = "A relaxed week along the coast.",
            departureDate = "2025-07-15",
            departurePort = CruisePortDto("Split", CoordinatesDto(43.5, 16.4)),
            arrivalDate = "2025-07-22",
            arrivalPort = CruisePortDto("Dubrovnik", CoordinatesDto(42.6, 18.0)),
            costPerPerson = 850.0,
            currency = "EUR",
            maxParticipants = 6,
            isPrivate = false,
            vessel = "Bavaria Cruiser 46",
            vesselType = "SAILING_YACHT",
        )

        val request = CruisesApi.createRequest("access-token", payload)

        assertEquals("POST", request.method)
        assertEquals("/v1/cruises", request.url.encodedPath)
        assertEquals("Bearer access-token", request.header("Authorization"))
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun Response.toCruisesErrorForTest(): CruisesError =
        CruisesApi.run { toCruisesError() }
}
