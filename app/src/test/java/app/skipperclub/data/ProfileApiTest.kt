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

class ProfileApiTest {

    @Test
    fun updateRequestPutsUserUpdateWithExplicitNullsForClearedFields() {
        val request = ProfileApi.updateProfileRequest(
            accessToken = "token",
            update = ProfileUpdate(
                name = "Anna Nowak",
                bio = null, // cleared
                city = "Gdańsk",
                country = "PL",
                sailingExperience = SailingExperience.Advanced,
                yearsOfExperience = 10,
                sailingLicenses = null,
                languagesSpoken = listOf("pl", "en"),
                preferredVoyageStyles = emptyList(),
                facebookUrl = null,
                instagramUsername = "@anna",
                tiktokUsername = null,
                whatsappNumber = null,
            ),
        )

        assertEquals("PUT", request.method)
        assertEquals("/v1/profile", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
        val body = request.bodyString()
        assertTrue(body.contains(""""name":"Anna Nowak""""))
        assertTrue(body.contains(""""bio":null"""))
        assertTrue(body.contains(""""sailingLicenses":null"""))
        assertTrue(body.contains(""""sailingExperience":"advanced""""))
        assertTrue(body.contains(""""languagesSpoken":["pl","en"]"""))
    }

    @Test
    fun avatarPresignedRequestTargetsProfileAvatar() {
        val request = ProfileApi.avatarPresignedRequest(
            accessToken = "token",
            payload = AvatarPresignedUrlRequest(
                fileName = "avatar.jpg",
                fileType = "image/jpeg",
                fileSize = 2048,
                width = 800,
                height = 800,
            ),
        )

        assertEquals("POST", request.method)
        assertEquals("/v1/profile/avatar/presigned-url", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
        assertEquals(
            """{"fileName":"avatar.jpg","fileType":"image/jpeg","fileSize":2048,"width":800,"height":800}""",
            request.bodyString(),
        )
    }

    @Test
    fun confirmAvatarRequestTargetsAvatarId() {
        val request = ProfileApi.confirmAvatarRequest("token", "avatar-7")

        assertEquals("POST", request.method)
        assertEquals("/v1/profile/avatar/avatar-7/confirm-upload", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
    }

    @Test
    fun validationProblemMapsToValidationError() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","title":"Validation Failed","detail":"Country invalid"}""",
        ).toProfileErrorForTest()

        assertTrue(error is ProfileError.Validation)
        assertEquals("Country invalid", error.message)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toProfileErrorForTest(): ProfileError =
        ProfileApi.run { toProfileError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
