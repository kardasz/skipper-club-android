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

class PostsApiTest {

    @Test
    fun listRequestIncludesFiltersPaginationAndHeaders() {
        val request = PostsApi.listRequest(
            accessToken = "access-token",
            query = PostFeedQuery(
                types = setOf(PostType.Photo, PostType.Berth),
                regionCode = "ADR-HR",
                hashtag = "sailing",
                sort = PostSortField.UpdatedAt,
                order = SortOrder.Asc,
                limit = 10,
                offset = 30,
            ),
        )

        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("/v1/posts", url.encodedPath)
        assertEquals(listOf("photo", "berth"), url.queryParameterValues("type"))
        assertEquals("ADR-HR", url.queryParameter("regionCode"))
        assertEquals("sailing", url.queryParameter("hashtag"))
        assertEquals("updatedAt", url.queryParameter("sort"))
        assertEquals("asc", url.queryParameter("order"))
        assertEquals("10", url.queryParameter("limit"))
        assertEquals("30", url.queryParameter("offset"))
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
    }

    @Test
    fun listRequestOmitsUnsetFilters() {
        val request = PostsApi.listRequest("token", PostFeedQuery())

        val url = request.url
        assertTrue(url.queryParameterValues("type").isEmpty())
        assertNull(url.queryParameter("regionCode"))
        assertNull(url.queryParameter("hashtag"))
        assertNull(url.queryParameter("userId"))
        assertEquals("createdAt", url.queryParameter("sort"))
        assertEquals("desc", url.queryParameter("order"))
    }

    @Test
    fun createRequestSerializesPhotoPostWithoutRouteFields() {
        val request = PostsApi.createRequest(
            accessToken = "token",
            payload = CreatePostRequest(
                type = "photo",
                regionCode = "ADR-HR",
                description = "Sunset #sailing",
                mediaIds = listOf("media-1"),
            ),
        )

        assertEquals("POST", request.method)
        assertEquals(
            """{"type":"photo","regionCode":"ADR-HR","description":"Sunset #sailing","mediaIds":["media-1"]}""",
            request.bodyString(),
        )
    }

    @Test
    fun createRequestSerializesRoutePostWithStops() {
        val request = PostsApi.createRequest(
            accessToken = "token",
            payload = CreatePostRequest(
                type = "route",
                regionCode = "ADR-HR",
                description = "Island hopping",
                locationName = "Split",
                coordinates = CoordinatesDto(43.5, 16.4),
                stops = listOf(RouteStopDto("Hvar", CoordinatesDto(43.1, 16.4))),
                durationDays = 7,
                lengthNm = 120.0,
            ),
        )

        assertEquals(
            """{"type":"route","regionCode":"ADR-HR","description":"Island hopping",""" +
                """"locationName":"Split","coordinates":{"lat":43.5,"lng":16.4},""" +
                """"stops":[{"name":"Hvar","coordinates":{"lat":43.1,"lng":16.4}}],""" +
                """"durationDays":7,"lengthNm":120.0}""",
            request.bodyString(),
        )
    }

    @Test
    fun reactionRequestsUseWireValueInPath() {
        val add = PostsApi.reactionRequest("token", "post-1", ReactionType.ThumbsUp, add = true)
        val remove = PostsApi.reactionRequest("token", "post-1", ReactionType.ThumbsUp, add = false)

        assertEquals("PUT", add.method)
        assertEquals("/v1/posts/post-1/reactions/thumbs_up", add.url.encodedPath)
        assertEquals("DELETE", remove.method)
        assertEquals("/v1/posts/post-1/reactions/thumbs_up", remove.url.encodedPath)
    }

    @Test
    fun statusPatchRequestSerializesWireValue() {
        val request = PostsApi.updateStatusRequest("token", "post-1", PostStatus.Archived)

        assertEquals("PATCH", request.method)
        assertEquals("/v1/posts/post-1", request.url.encodedPath)
        assertEquals("""{"status":"archived"}""", request.bodyString())
    }

    @Test
    fun validityVoteRequestSerializesVoteType() {
        val request = PostsApi.validityVoteRequest("token", "post-1", ValidityVoteType.ReportInvalid)

        assertEquals("PUT", request.method)
        assertEquals("/v1/posts/post-1/validity-vote", request.url.encodedPath)
        assertEquals("""{"voteType":"report_invalid"}""", request.bodyString())
    }

    @Test
    fun commentRequestsTargetNestedPath() {
        val list = PostsApi.commentsRequest("token", "post-1", limit = 20, offset = 40)
        val add = PostsApi.addCommentRequest("token", "post-1", "Great photo!")
        val delete = PostsApi.deleteCommentRequest("token", "post-1", "comment-9")

        assertEquals("/v1/posts/post-1/comments", list.url.encodedPath)
        assertEquals("20", list.url.queryParameter("limit"))
        assertEquals("40", list.url.queryParameter("offset"))
        assertEquals("""{"text":"Great photo!"}""", add.bodyString())
        assertEquals("DELETE", delete.method)
        assertEquals("/v1/posts/post-1/comments/comment-9", delete.url.encodedPath)
    }

    @Test
    fun bookmarkRequestsUseBookmarkPath() {
        val add = PostsApi.bookmarkRequest("token", "post-1", add = true)
        val remove = PostsApi.bookmarkRequest("token", "post-1", add = false)

        assertEquals("PUT", add.method)
        assertEquals("DELETE", remove.method)
        assertEquals("/v1/posts/post-1/bookmark", add.url.encodedPath)
    }

    @Test
    fun notFoundProblemMapsToNotFound() {
        val error = response(
            code = 404,
            body = """{"type":"/errors/post-not-found","title":"Post Not Found","detail":"Gone"}""",
        ).toPostsErrorForTest()

        assertTrue(error is PostsError.NotFound)
        assertEquals("Gone", error.message)
    }

    @Test
    fun conflictProblemMapsToVoteConflict() {
        val error = response(
            code = 409,
            body = """{"type":"/errors/vote-already-cast","title":"Conflict","detail":"Vote already cast"}""",
        ).toPostsErrorForTest()

        assertTrue(error is PostsError.VoteConflict)
    }

    @Test
    fun validationProblemMapsToValidation() {
        val error = response(
            code = 422,
            body = """{"type":"/errors/validation","title":"Validation Failed","detail":"Invalid"}""",
        ).toPostsErrorForTest()

        assertTrue(error is PostsError.Validation)
    }

    @Test
    fun unauthorizedMapsToAuthenticationRequired() {
        val error = response(code = 401, body = "").toPostsErrorForTest()

        assertTrue(error is PostsError.AuthenticationRequired)
    }

    @Test
    fun forbiddenMapsToForbidden() {
        val error = response(code = 403, body = "").toPostsErrorForTest()

        assertTrue(error is PostsError.Forbidden)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toPostsErrorForTest(): PostsError =
        PostsApi.run { toPostsError() }
}

private fun Request.bodyString(): String {
    val buffer = Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
