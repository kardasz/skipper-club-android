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

class SpotsApiTest {

    @Test
    fun listRequestIncludesNameFilterPaginationAndHeaders() {
        val request = SpotsApi.listRequest(
            accessToken = "access-token",
            query = SpotListQuery(name = "neptun", limit = 10, offset = 30),
        )

        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("/v1/spots", url.encodedPath)
        assertEquals("neptun", url.queryParameter("name"))
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("30", url.queryParameter("offset"))
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
    }

    @Test
    fun listRequestOmitsBlankName() {
        val request = SpotsApi.listRequest("token", SpotListQuery(name = "  "))

        assertNull(request.url.queryParameter("name"))
        assertEquals("20", request.url.queryParameter("limit"))
        assertEquals("0", request.url.queryParameter("offset"))
    }

    @Test
    fun getRequestTargetsSpotPathWithHeaders() {
        val request = SpotsApi.getRequest("access-token", "spot-1")

        assertEquals("GET", request.method)
        assertEquals("/v1/spots/spot-1", request.url.encodedPath)
        assertNull(request.body)
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
    }

    @Test
    fun createRequestPostsSpotBody() {
        val request = SpotsApi.createRequest(
            "token",
            CreateSpotRequest(
                name = "Neptun",
                coordinates = SpotCoordinatesDto(54.35, 18.65),
                phoneContacts = listOf(CreatePhoneContactPayload(phone = "+48581234567", label = "Office")),
                radioChannels = listOf(CreateRadioChannelPayload(name = "Port", vhfChannel = 12, isPrimary = true)),
            ),
        )

        assertEquals("POST", request.method)
        assertEquals("/v1/spots", request.url.encodedPath)
        val body = request.bodyString()
        assertTrue(body.contains("\"name\":\"Neptun\""))
        assertTrue(body.contains("\"lat\":54.35"))
        assertTrue(body.contains("\"phone\":\"+48581234567\""))
        assertTrue(body.contains("\"vhfChannel\":12"))
    }

    @Test
    fun updateRequestPatchesSpotPathAndOmitsNullFields() {
        val request = SpotsApi.updateRequest(
            "token",
            "spot-1",
            UpdateSpotAggregateRequest(name = "Renamed"),
        )

        assertEquals("PATCH", request.method)
        assertEquals("/v1/spots/spot-1", request.url.encodedPath)
        val body = request.bodyString()
        assertEquals("""{"name":"Renamed"}""", body)
    }

    @Test
    fun deleteRequestTargetsSpotPath() {
        val request = SpotsApi.deleteRequest("token", "spot-1")

        assertEquals("DELETE", request.method)
        assertEquals("/v1/spots/spot-1", request.url.encodedPath)
    }

    @Test
    fun unauthorizedMapsToAuthenticationRequired() {
        val error = response(401, "").toSpotsErrorForTest()
        assertTrue(error is SpotsError.AuthenticationRequired)
    }

    @Test
    fun forbiddenMapsToForbidden() {
        val error = response(403, "").toSpotsErrorForTest()
        assertTrue(error is SpotsError.Forbidden)
    }

    @Test
    fun notFoundMapsToNotFoundWithDetail() {
        val error = response(
            404,
            """{"type":"/errors/spot-not-found","title":"Spot Not Found","detail":"Gone"}""",
        ).toSpotsErrorForTest()

        assertTrue(error is SpotsError.NotFound)
        assertEquals("Gone", error.message)
    }

    @Test
    fun conflictMapsToDuplicateWithNearbySpots() {
        val error = response(
            409,
            """
            {
              "type":"/errors/spot-duplicate",
              "title":"Spot Duplicate",
              "detail":"A nearby spot already exists",
              "nearbySpots":[
                {"id":"019dfd19","name":"Sopot Marina","coordinates":{"lat":54.441,"lng":18.567},"distanceMeters":87}
              ]
            }
            """.trimIndent(),
        ).toSpotsErrorForTest()

        assertTrue(error is SpotsError.Duplicate)
        val duplicate = error as SpotsError.Duplicate
        assertEquals("A nearby spot already exists", duplicate.message)
        assertEquals(1, duplicate.nearbySpots.size)
        assertEquals("Sopot Marina", duplicate.nearbySpots.first().name)
        assertEquals(87, duplicate.nearbySpots.first().distanceMeters)
    }

    @Test
    fun validationMapsToValidation() {
        val error = response(422, """{"type":"/errors/validation"}""").toSpotsErrorForTest()
        assertTrue(error is SpotsError.Validation)
    }

    @Test
    fun serverErrorMapsToServerWithStatusCode() {
        val error = response(502, "").toSpotsErrorForTest()
        assertTrue(error is SpotsError.Server)
        assertEquals(502, (error as SpotsError.Server).statusCode)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toSpotsErrorForTest(): SpotsError =
        SpotsApi.run { toSpotsError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
