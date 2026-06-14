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

class MediaUploadApiTest {

    @Test
    fun presignedUrlRequestSerializesMetadata() {
        val request = MediaUploadApi.presignedUrlRequest(
            accessToken = "token",
            payload = PresignedUrlRequest(
                fileName = "photo.jpg",
                fileType = "image/jpeg",
                fileSize = 1024,
                width = 1920,
                height = 1080,
            ),
        )

        assertEquals("POST", request.method)
        assertEquals("/v1/media/presigned-url", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
        assertEquals(
            """{"fileName":"photo.jpg","fileType":"image/jpeg","fileSize":1024,"width":1920,"height":1080}""",
            request.bodyString(),
        )
    }

    @Test
    fun presignedUrlRequestSerializesRichCaptureMetadata() {
        val request = MediaUploadApi.presignedUrlRequest(
            accessToken = "token",
            payload = PresignedUrlRequest(
                fileName = "clip.mp4",
                fileType = "video/mp4",
                fileSize = 2048,
                width = 1920,
                height = 1080,
                duration = 30.5,
                frameRate = 30.0,
                camera = "Pixel 10",
                lat = 54.35,
                lon = 18.64,
                orientation = 6,
                dateTaken = "2024-07-28T15:30:00Z",
                metadata = mapOf("iso" to "100"),
            ),
        )

        assertEquals(
            """{"fileName":"clip.mp4","fileType":"video/mp4","fileSize":2048,"width":1920,"height":1080,""" +
                """"duration":30.5,"frameRate":30.0,"camera":"Pixel 10","lat":54.35,"lon":18.64,""" +
                """"orientation":6,"dateTaken":"2024-07-28T15:30:00Z","metadata":{"iso":"100"}}""",
            request.bodyString(),
        )
    }

    @Test
    fun storageUploadRequestPutsRawBytesWithoutAuth() {
        val request = MediaUploadApi.storageUploadRequest(
            uploadUrl = "https://storage.example.com/upload?sig=abc",
            contentType = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3),
        )

        assertEquals("PUT", request.method)
        assertNull(request.header("Authorization"))
        assertEquals("image/jpeg", request.body?.contentType().toString())
        assertEquals(3L, request.body?.contentLength())
    }

    @Test
    fun confirmUploadRequestTargetsMediaId() {
        val request = MediaUploadApi.confirmUploadRequest("token", "media-1")

        assertEquals("POST", request.method)
        assertEquals("/v1/media/media-1/confirm-upload", request.url.encodedPath)
        assertEquals("Bearer token", request.header("Authorization"))
    }

    @Test
    fun validationProblemMapsToValidation() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","title":"Validation Failed","detail":"Too large"}""",
        ).toMediaUploadErrorForTest()

        assertTrue(error is MediaUploadError.Validation)
        assertEquals("Too large", error.message)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toMediaUploadErrorForTest(): MediaUploadError =
        MediaUploadApi.run { toMediaUploadError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
