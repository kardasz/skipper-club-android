package app.skipperclub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * What a post "contains". Server-derived (read-only) — never sent on create/update.
 * `text` is on every post so it is never a key; drives feed filtering (`contains`)
 * and UI rendering. See `docs/api/MIGRATION_8.0.md` §3.1.
 */
enum class PostContentKey(val wireValue: String) {
    Alert("alert"),
    Media("media"),
    Route("route"),
    ;

    companion object {
        fun fromWire(value: String): PostContentKey? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class PostStatus(val wireValue: String) {
    Published("published"),
    Archived("archived"),
    Expired("expired"),
    Resolved("resolved"),
    Deleted("deleted"),
    ;

    companion object {
        fun fromWire(value: String): PostStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Curated set of 20 emoji reactions (10 standard + 10 sailing). */
enum class ReactionType(val wireValue: String, val isSailing: Boolean) {
    Heart("heart", false),
    ThumbsUp("thumbs_up", false),
    ThumbsDown("thumbs_down", false),
    Laugh("laugh", false),
    Wow("wow", false),
    Sad("sad", false),
    Fire("fire", false),
    Clap("clap", false),
    Party("party", false),
    Thinking("thinking", false),
    Anchor("anchor", true),
    Sailboat("sailboat", true),
    Wave("wave", true),
    Sun("sun", true),
    Compass("compass", true),
    Fish("fish", true),
    Whale("whale", true),
    Dolphin("dolphin", true),
    Wind("wind", true),
    Lifesaver("lifesaver", true),
    ;

    companion object {
        fun fromWire(value: String): ReactionType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class ValidityVoteType(val wireValue: String) {
    Confirm("confirm"),
    ReportInvalid("report_invalid"),
    ;

    companion object {
        fun fromWire(value: String): ValidityVoteType? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Moderation report reasons for `POST /v1/posts/{id}/reports`. */
enum class ReportReason(val wireValue: String) {
    Spam("spam"),
    Scam("scam"),
    Offensive("offensive"),
    Misinformation("misinformation"),
    Danger("danger"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String): ReportReason? = entries.firstOrNull { it.wireValue == value }
    }
}

data class PostUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class PostCoordinates(
    val lat: Double,
    val lng: Double,
)

data class PostRouteStop(
    val name: String,
    val coordinates: PostCoordinates,
)

/** Route recommendation carried by `content.route`. */
data class PostRoute(
    val stops: List<PostRouteStop> = emptyList(),
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
)

/**
 * Alert carried by `content.alert`. [category] and [severity] are user-settable;
 * the remaining fields are read-only attribution present on imported (system)
 * alerts (see `PostContentAlert` in the OpenAPI spec).
 */
data class PostAlert(
    val category: AlertCategory,
    val severity: AlertSeverity? = null,
    val language: String? = null,
    val source: String? = null,
    val externalNumber: String? = null,
    val externalPublishedAt: String? = null,
    val externalUpdatedAt: String? = null,
    val externalExpiresAt: String? = null,
)

/** Structured post body. [text] is required (1–2200 chars); route/alert are exclusive. */
data class PostContent(
    val text: String,
    val route: PostRoute? = null,
    val alert: PostAlert? = null,
)

/**
 * Where a post is anchored. [point] drives the map marker/distance; [area] is a raw
 * GeoJSON Polygon/MultiPolygon present only on alert posts.
 */
data class PostLocation(
    val name: String? = null,
    val point: PostCoordinates? = null,
    val area: JsonObject? = null,
)

data class PostMedia(
    val id: String,
    val type: String,
    val url: String,
    val orderIndex: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val status: String? = null,
) {
    /** True for `video` attachments; drives the play affordance and frame poster. */
    val isVideo: Boolean
        get() = type.equals("video", ignoreCase = true)
}

/** Attribution for system-generated posts (imported alerts). */
data class PostSource(
    val type: String,
    val id: String,
)

data class ReactionSummary(
    val total: Int = 0,
    val byType: Map<ReactionType, Int> = emptyMap(),
    val userReactions: Set<ReactionType> = emptySet(),
)

data class VoteSummary(
    val confirmCount: Int = 0,
    val invalidCount: Int = 0,
    val userVote: ValidityVoteType? = null,
)

data class PostPermissions(
    val edit: Boolean = false,
    val delete: Boolean = false,
    val archive: Boolean = false,
    val resolve: Boolean = false,
    val comment: Boolean = false,
    val react: Boolean = false,
    val bookmark: Boolean = false,
    val report: Boolean = false,
    val validityVote: Boolean = false,
)

data class Post(
    val id: String,
    /** Post author. `null` on system-generated posts (imported alerts); see [isSystemGenerated]. */
    val user: PostUser?,
    val contentKeys: Set<PostContentKey>,
    val status: PostStatus,
    val content: PostContent,
    val location: PostLocation = PostLocation(),
    val hashtags: List<String> = emptyList(),
    val media: List<PostMedia> = emptyList(),
    val taggedUsers: List<PostUser> = emptyList(),
    val commentsCount: Int = 0,
    val reactions: ReactionSummary = ReactionSummary(),
    val bookmarked: Boolean = false,
    val validityVotes: VoteSummary? = null,
    val permissions: PostPermissions = PostPermissions(),
    val source: PostSource? = null,
    val publishedAt: String,
    val expiresAt: String? = null,
    val resolvedAt: String? = null,
    val archivedAt: String? = null,
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    val alert: PostAlert? get() = content.alert
    val route: PostRoute? get() = content.route
    val hasAlert: Boolean get() = content.alert != null || contentKeys.contains(PostContentKey.Alert)
    val hasRoute: Boolean get() = content.route != null || contentKeys.contains(PostContentKey.Route)
    val hasMedia: Boolean get() = media.isNotEmpty() || contentKeys.contains(PostContentKey.Media)

    /** System-generated posts (imported alerts) cannot be edited/voted by users. */
    val isSystemGenerated: Boolean get() = source != null
}

data class PageMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
)

data class PostsPage(
    val posts: List<Post>,
    val meta: PageMeta,
)

data class PostComment(
    val id: String,
    val user: PostUser,
    val text: String,
    val createdAt: String,
    val updatedAt: String,
)

data class CommentsPage(
    val comments: List<PostComment>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

data class ValidityVoteResult(
    val postId: String,
    val voteType: ValidityVoteType?,
    val confirmCount: Int,
    val invalidCount: Int,
)

// ---------------------------------------------------------------------------
// Request DTOs (`POST`/`PUT`/`PATCH` /v1/posts). There is no `type` anymore: one
// body covers every post. With `explicitNulls = false` absent optional fields are
// omitted. `contentKeys` is derived server-side and never sent.
// ---------------------------------------------------------------------------

@Serializable
data class CreatePostRequest(
    val content: PostContentInputDto,
    val location: PostLocationInputDto? = null,
    val mediaIds: List<String>? = null,
    val taggedUserIds: List<String>? = null,
    val publishedAt: String? = null,
    val expiresAt: String? = null,
)

/** Full content replace for `PUT /v1/posts/{id}` (`PostUpdate`). */
@Serializable
data class UpdatePostRequest(
    val content: PostContentInputDto,
    val location: PostLocationInputDto? = null,
    val mediaIds: List<String>? = null,
    val taggedUserIds: List<String>? = null,
    val publishedAt: String? = null,
    val expiresAt: String? = null,
    val status: String? = null,
)

@Serializable
data class PostContentInputDto(
    val text: String,
    val route: RouteInputDto? = null,
    val alert: AlertInputDto? = null,
)

@Serializable
data class RouteInputDto(
    val stops: List<RouteStopDto>,
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
) {
    companion object {
        fun from(route: PostRoute): RouteInputDto =
            RouteInputDto(
                stops = route.stops.map { RouteStopDto.from(it) },
                durationDays = route.durationDays,
                lengthNm = route.lengthNm,
            )
    }
}

/** Only `category` and `severity` are user-settable; source fields are rejected. */
@Serializable
data class AlertInputDto(
    val category: AlertCategory,
    val severity: AlertSeverity? = null,
) {
    companion object {
        fun from(alert: PostAlert): AlertInputDto =
            AlertInputDto(category = alert.category, severity = alert.severity)
    }
}

@Serializable
data class PostLocationInputDto(
    val name: String? = null,
    val point: CoordinatesDto? = null,
    val area: JsonObject? = null,
)

@Serializable
data class CoordinatesDto(
    val lat: Double,
    val lng: Double,
) {
    fun toDomain(): PostCoordinates = PostCoordinates(lat = lat, lng = lng)

    companion object {
        fun from(coordinates: PostCoordinates): CoordinatesDto =
            CoordinatesDto(lat = coordinates.lat, lng = coordinates.lng)
    }
}

@Serializable
data class RouteStopDto(
    val name: String,
    val coordinates: CoordinatesDto,
) {
    fun toDomain(): PostRouteStop = PostRouteStop(name = name, coordinates = coordinates.toDomain())

    companion object {
        fun from(stop: PostRouteStop): RouteStopDto =
            RouteStopDto(name = stop.name, coordinates = CoordinatesDto.from(stop.coordinates))
    }
}

@Serializable
internal data class PostReportRequest(
    val reason: String,
    val details: String? = null,
)

/** Minimal `PATCH /v1/posts/{id}` body for a status-only transition. */
@Serializable
internal data class PostStatusPatchRequest(
    val status: String,
)

@Serializable
internal data class CommentRequest(
    val text: String,
)

@Serializable
internal data class ValidityVoteRequestDto(
    val voteType: String,
)

// ---------------------------------------------------------------------------
// Response DTOs.
// ---------------------------------------------------------------------------

@Serializable
internal data class PostUserDto(
    val id: String,
    val displayName: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
) {
    fun toDomain(): PostUser =
        PostUser(id = id, name = displayName ?: name ?: "", avatarUrl = avatarUrl)
}

@Serializable
internal data class PostMediaDto(
    val id: String,
    val type: String = "image",
    val url: String,
    val orderIndex: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val status: String? = null,
) {
    fun toDomain(): PostMedia =
        PostMedia(
            id = id,
            type = type,
            url = url,
            orderIndex = orderIndex,
            width = width,
            height = height,
            size = size,
            status = status,
        )
}

@Serializable
internal data class RouteContentDto(
    val stops: List<RouteStopDto> = emptyList(),
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
) {
    fun toDomain(): PostRoute =
        PostRoute(
            stops = stops.map { it.toDomain() },
            durationDays = durationDays,
            lengthNm = lengthNm,
        )
}

@Serializable
internal data class AlertContentDto(
    val category: AlertCategory,
    val severity: AlertSeverity? = null,
    val language: String? = null,
    val source: String? = null,
    val externalNumber: String? = null,
    val externalPublishedAt: String? = null,
    val externalUpdatedAt: String? = null,
    val externalExpiresAt: String? = null,
) {
    fun toDomain(): PostAlert =
        PostAlert(
            category = category,
            severity = severity,
            language = language,
            source = source,
            externalNumber = externalNumber,
            externalPublishedAt = externalPublishedAt,
            externalUpdatedAt = externalUpdatedAt,
            externalExpiresAt = externalExpiresAt,
        )
}

@Serializable
internal data class PostContentDto(
    val text: String = "",
    val route: RouteContentDto? = null,
    val alert: AlertContentDto? = null,
) {
    fun toDomain(): PostContent =
        PostContent(
            text = text,
            route = route?.toDomain(),
            alert = alert?.toDomain(),
        )
}

@Serializable
internal data class PostLocationDto(
    val name: String? = null,
    val point: CoordinatesDto? = null,
    val area: JsonObject? = null,
) {
    fun toDomain(): PostLocation =
        PostLocation(name = name, point = point?.toDomain(), area = area)
}

@Serializable
internal data class PostSourceDto(
    val type: String,
    val id: String,
) {
    fun toDomain(): PostSource = PostSource(type = type, id = id)
}

@Serializable
internal data class ReactionSummaryDto(
    val total: Int = 0,
    val byType: Map<String, Int> = emptyMap(),
    val userReactions: List<String> = emptyList(),
) {
    fun toDomain(): ReactionSummary =
        ReactionSummary(
            total = total,
            byType = byType.entries.mapNotNull { (key, count) ->
                ReactionType.fromWire(key)?.let { it to count }
            }.toMap(),
            userReactions = userReactions.mapNotNull { ReactionType.fromWire(it) }.toSet(),
        )
}

@Serializable
internal data class VoteSummaryDto(
    val confirmCount: Int = 0,
    val invalidCount: Int = 0,
    val userVote: String? = null,
) {
    fun toDomain(): VoteSummary =
        VoteSummary(
            confirmCount = confirmCount,
            invalidCount = invalidCount,
            userVote = userVote?.let { ValidityVoteType.fromWire(it) },
        )
}

@Serializable
internal data class PostPermissionsDto(
    val edit: Boolean = false,
    val delete: Boolean = false,
    val archive: Boolean = false,
    val resolve: Boolean = false,
    val comment: Boolean = false,
    val react: Boolean = false,
    val bookmark: Boolean = false,
    val report: Boolean = false,
    val validityVote: Boolean = false,
) {
    fun toDomain(): PostPermissions =
        PostPermissions(
            edit = edit,
            delete = delete,
            archive = archive,
            resolve = resolve,
            comment = comment,
            react = react,
            bookmark = bookmark,
            report = report,
            validityVote = validityVote,
        )
}

@Serializable
internal data class PostDto(
    val id: String,
    val user: PostUserDto? = null,
    val contentKeys: List<String> = emptyList(),
    val status: String = "published",
    val content: PostContentDto = PostContentDto(),
    val location: PostLocationDto = PostLocationDto(),
    val hashtags: List<String> = emptyList(),
    val media: List<PostMediaDto> = emptyList(),
    val taggedUsers: List<PostUserDto> = emptyList(),
    val commentsCount: Int = 0,
    val reactions: ReactionSummaryDto = ReactionSummaryDto(),
    val bookmarked: Boolean = false,
    val validityVotes: VoteSummaryDto? = null,
    val permissions: PostPermissionsDto = PostPermissionsDto(),
    val source: PostSourceDto? = null,
    val publishedAt: String? = null,
    val expiresAt: String? = null,
    val resolvedAt: String? = null,
    val archivedAt: String? = null,
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    /** Posts with an unknown status are dropped rather than crash the feed. */
    fun toDomain(): Post? {
        val postStatus = PostStatus.fromWire(status) ?: return null
        return Post(
            id = id,
            user = user?.toDomain(),
            contentKeys = contentKeys.mapNotNull { PostContentKey.fromWire(it) }.toSet(),
            status = postStatus,
            content = content.toDomain(),
            location = location.toDomain(),
            hashtags = hashtags,
            media = media.map { it.toDomain() }.sortedBy { it.orderIndex },
            taggedUsers = taggedUsers.map { it.toDomain() },
            commentsCount = commentsCount,
            reactions = reactions.toDomain(),
            bookmarked = bookmarked,
            validityVotes = validityVotes?.toDomain(),
            permissions = permissions.toDomain(),
            source = source?.toDomain(),
            publishedAt = publishedAt ?: createdAt,
            expiresAt = expiresAt,
            resolvedAt = resolvedAt,
            archivedAt = archivedAt,
            deletedAt = deletedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
internal data class PageMetaDto(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = false,
) {
    fun toDomain(): PageMeta =
        PageMeta(total = total, limit = limit, offset = offset, hasMore = hasMore)
}

@Serializable
internal data class PostsListDto(
    val data: List<PostDto> = emptyList(),
    val meta: PageMetaDto = PageMetaDto(),
) {
    fun toDomain(): PostsPage =
        PostsPage(
            posts = data.mapNotNull { it.toDomain() },
            meta = meta.toDomain(),
        )
}

@Serializable
internal data class CommentDto(
    val id: String,
    val user: PostUserDto,
    val text: String,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toDomain(): PostComment =
        PostComment(
            id = id,
            user = user.toDomain(),
            text = text,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

@Serializable
internal data class CommentsListDto(
    val comments: List<CommentDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): CommentsPage =
        CommentsPage(
            comments = comments.map { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}

@Serializable
internal data class ValidityVoteResponseDto(
    val postId: String,
    val voteType: String? = null,
    val confirmCount: Int = 0,
    val invalidCount: Int = 0,
) {
    fun toDomain(): ValidityVoteResult =
        ValidityVoteResult(
            postId = postId,
            voteType = voteType?.let { ValidityVoteType.fromWire(it) },
            confirmCount = confirmCount,
            invalidCount = invalidCount,
        )
}
