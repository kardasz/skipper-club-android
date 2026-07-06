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
                contains = setOf(PostContainsFilter.Media, PostContainsFilter.Alert),
                query = "hvar bay",
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
        // contains is joined into a single comma param, sorted by ordinal.
        assertEquals("alert,media", url.queryParameter("contains"))
        assertEquals("hvar bay", url.queryParameter("q"))
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
        assertNull(url.queryParameter("contains"))
        assertNull(url.queryParameter("q"))
        assertNull(url.queryParameter("hashtag"))
        assertNull(url.queryParameter("userId"))
        assertEquals("publishedAt", url.queryParameter("sort"))
        assertEquals("desc", url.queryParameter("order"))
        assertEquals("0", url.queryParameter("offset"))
    }

    @Test
    fun listRequestUsesKeysetCursorAndOmitsOffset() {
        val request = PostsApi.listRequest(
            accessToken = "token",
            query = PostFeedQuery(
                cursor = PostFeedCursor(beforePublishedAt = "2025-12-01T10:00:00Z", beforeId = "post-9"),
            ),
        )

        val url = request.url
        assertEquals("2025-12-01T10:00:00Z", url.queryParameter("beforePublishedAt"))
        assertEquals("post-9", url.queryParameter("beforeId"))
        // Keyset walks the feed; offset is not sent alongside a cursor.
        assertNull(url.queryParameter("offset"))
        assertEquals("publishedAt", url.queryParameter("sort"))
    }

    @Test
    fun listRequestEncodesLifecycleAndLocationFilters() {
        val request = PostsApi.listRequest(
            accessToken = "token",
            query = PostFeedQuery(
                contains = setOf(PostContainsFilter.Note),
                statuses = setOf(PostStatus.Archived, PostStatus.Published),
                userId = "me",
                locationName = "Split",
                lat = 43.5,
                lng = 16.4,
                distanceKm = 25,
                fromDate = "2025-01-01T00:00:00Z",
                toDate = "2025-12-31T00:00:00Z",
                sort = PostSortField.Distance,
            ),
        )

        val url = request.url
        assertEquals("note", url.queryParameter("contains"))
        assertEquals(listOf("published", "archived"), url.queryParameterValues("status"))
        assertEquals("me", url.queryParameter("userId"))
        assertEquals("Split", url.queryParameter("locationName"))
        assertEquals("43.5", url.queryParameter("lat"))
        assertEquals("16.4", url.queryParameter("lng"))
        assertEquals("25", url.queryParameter("distance"))
        assertEquals("2025-01-01T00:00:00Z", url.queryParameter("fromDate"))
        assertEquals("2025-12-31T00:00:00Z", url.queryParameter("toDate"))
        assertEquals("distance", url.queryParameter("sort"))
    }

    @Test
    fun bookmarksRequestTargetsProfilePath() {
        val request = PostsApi.bookmarksRequest(
            accessToken = "token",
            query = BookmarksQuery(sort = BookmarkSortField.UpdatedAt, order = SortOrder.Asc, limit = 15, offset = 30),
        )

        assertEquals("GET", request.method)
        assertEquals("/v1/profile/bookmarks/posts", request.url.encodedPath)
        assertEquals("updatedAt", request.url.queryParameter("sort"))
        assertEquals("asc", request.url.queryParameter("order"))
        assertEquals("15", request.url.queryParameter("limit"))
        assertEquals("30", request.url.queryParameter("offset"))
    }

    @Test
    fun updateRequestSerializesFullPostUpdate() {
        val request = PostsApi.updateRequest(
            accessToken = "token",
            postId = "post-1",
            payload = UpdatePostRequest(
                content = PostContentInputDto(text = "Updated text"),
                location = PostLocationInputDto(name = "Hvar", point = CoordinatesDto(43.1, 16.4)),
                mediaIds = listOf("media-1"),
                taggedUserIds = listOf("user-1"),
            ),
        )

        assertEquals("PUT", request.method)
        assertEquals("/v1/posts/post-1", request.url.encodedPath)
        assertEquals(
            """{"content":{"text":"Updated text"},"location":{"name":"Hvar",""" +
                """"point":{"lat":43.1,"lng":16.4}},"mediaIds":["media-1"],"taggedUserIds":["user-1"]}""",
            request.bodyString(),
        )
    }

    @Test
    fun updateCommentRequestUsesPutOnNestedPath() {
        val request = PostsApi.updateCommentRequest("token", "post-1", "comment-9", "Edited")

        assertEquals("PUT", request.method)
        assertEquals("/v1/posts/post-1/comments/comment-9", request.url.encodedPath)
        assertEquals("""{"text":"Edited"}""", request.bodyString())
    }

    @Test
    fun reportRequestSerializesReasonAndOmitsBlankDetails() {
        val withDetails = PostsApi.reportRequest("token", "post-1", ReportReason.Spam, "ad spam")
        val withoutDetails = PostsApi.reportRequest("token", "post-1", ReportReason.Other, "  ")

        assertEquals("POST", withDetails.method)
        assertEquals("/v1/posts/post-1/reports", withDetails.url.encodedPath)
        assertEquals("""{"reason":"spam","details":"ad spam"}""", withDetails.bodyString())
        assertEquals("""{"reason":"other"}""", withoutDetails.bodyString())
    }

    @Test
    fun createRequestSerializesMediaPostWithoutRouteOrAlert() {
        val request = PostsApi.createRequest(
            accessToken = "token",
            payload = CreatePostRequest(
                content = PostContentInputDto(text = "Sunset #sailing"),
                location = PostLocationInputDto(name = "Split"),
                mediaIds = listOf("media-1"),
            ),
        )

        assertEquals("POST", request.method)
        assertEquals(
            """{"content":{"text":"Sunset #sailing"},"location":{"name":"Split"},"mediaIds":["media-1"]}""",
            request.bodyString(),
        )
    }

    @Test
    fun createRequestSerializesRoutePostWithStops() {
        val request = PostsApi.createRequest(
            accessToken = "token",
            payload = CreatePostRequest(
                content = PostContentInputDto(
                    text = "Island hopping",
                    route = RouteInputDto(
                        stops = listOf(RouteStopDto("Hvar", CoordinatesDto(43.1, 16.4))),
                        durationDays = 7,
                        lengthNm = 120.0,
                    ),
                ),
                location = PostLocationInputDto(name = "Split", point = CoordinatesDto(43.5, 16.4)),
            ),
        )

        assertEquals(
            """{"content":{"text":"Island hopping","route":{""" +
                """"stops":[{"name":"Hvar","coordinates":{"lat":43.1,"lng":16.4}}],""" +
                """"durationDays":7,"lengthNm":120.0}},""" +
                """"location":{"name":"Split","point":{"lat":43.5,"lng":16.4}}}""",
            request.bodyString(),
        )
    }

    @Test
    fun createRequestSerializesAlertPost() {
        val request = PostsApi.createRequest(
            accessToken = "token",
            payload = CreatePostRequest(
                content = PostContentInputDto(
                    text = "Submerged obstruction near the harbour entrance",
                    alert = AlertInputDto(
                        category = AlertCategory.Obstruction,
                        severity = AlertSeverity.Warning,
                    ),
                ),
            ),
        )

        assertEquals(
            """{"content":{"text":"Submerged obstruction near the harbour entrance",""" +
                """"alert":{"category":"obstruction","severity":"warning"}}}""",
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
