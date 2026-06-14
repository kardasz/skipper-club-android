package app.skipperclub.data

import kotlinx.serialization.Serializable

/**
 * Post type classification. Wire values follow `docs/api/openapi.yaml` (`PostType`),
 * with one caveat: the spec's enum lists `spot` while every create schema, the
 * human-readable docs and the iOS client use `marina`. We follow `marina` and the
 * contract gap is called out in the PR description.
 */
enum class PostType(val wireValue: String) {
    Photo("photo"),
    Place("place"),
    Food("food"),
    Marina("marina"),
    Tips("tips"),
    Route("route"),
    Berth("berth"),
    Weather("weather"),
    NavigationWarning("navigation_warning"),
    Help("help"),
    ;

    val isTimeSensitive: Boolean
        get() = this == Berth || this == Weather || this == NavigationWarning || this == Help

    val isEvergreen: Boolean
        get() = !isTimeSensitive

    /** Community validity voting; `help` is author-resolved only. */
    val isVotable: Boolean
        get() = this == Berth || this == Weather || this == NavigationWarning

    val requiresDescription: Boolean
        get() = this != Photo

    val requiresLocation: Boolean
        get() = this != Photo && this != Tips

    val requiresMedia: Boolean
        get() = this == Photo

    val requiresStops: Boolean
        get() = this == Route

    companion object {
        fun fromWire(value: String): PostType? = entries.firstOrNull { it.wireValue == value }
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

data class PostMedia(
    val id: String,
    val type: String,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
) {
    /** True for `video` attachments; drives the play affordance and frame poster. */
    val isVideo: Boolean
        get() = type.equals("video", ignoreCase = true)
}

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
    val type: PostType,
    val status: PostStatus,
    val regionCode: String,
    val user: PostUser,
    val description: String? = null,
    val locationName: String? = null,
    val coordinates: PostCoordinates? = null,
    val hashtags: List<String> = emptyList(),
    val media: List<PostMedia> = emptyList(),
    val taggedUsers: List<PostUser> = emptyList(),
    val stops: List<PostRouteStop> = emptyList(),
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
    val commentsCount: Int = 0,
    val reactions: ReactionSummary = ReactionSummary(),
    val bookmarked: Boolean = false,
    val validityVotes: VoteSummary? = null,
    val permissions: PostPermissions = PostPermissions(),
    val expiresAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

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

/**
 * Polymorphic `POST /v1/posts` payload. With `explicitNulls = false` absent optional
 * fields are omitted, so a single DTO covers all ten per-type create schemas.
 */
@Serializable
data class CreatePostRequest(
    val type: String,
    val regionCode: String,
    val description: String? = null,
    val locationName: String? = null,
    val coordinates: CoordinatesDto? = null,
    val mediaIds: List<String>? = null,
    val taggedUserIds: List<String>? = null,
    val stops: List<RouteStopDto>? = null,
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
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

/**
 * Full content update for `PUT /v1/posts/{id}` (`PostUpdate` schema). `type` is
 * immutable so it is intentionally absent. With `explicitNulls = false` omitted
 * optional fields are dropped, matching the spec's "only valid for route posts"
 * route fields. Route-stop/duration/length only set for route posts.
 */
@Serializable
data class UpdatePostRequest(
    val regionCode: String,
    val status: String? = null,
    val description: String? = null,
    val locationName: String? = null,
    val coordinates: CoordinatesDto? = null,
    val mediaIds: List<String>? = null,
    val taggedUserIds: List<String>? = null,
    val stops: List<RouteStopDto>? = null,
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
)

@Serializable
internal data class PostReportRequest(
    val reason: String,
    val details: String? = null,
)

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

@Serializable
internal data class PostUserDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
) {
    fun toDomain(): PostUser = PostUser(id = id, name = name, avatarUrl = avatarUrl)
}

@Serializable
internal data class PostMediaDto(
    val id: String,
    val type: String = "image",
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
) {
    fun toDomain(): PostMedia =
        PostMedia(id = id, type = type, url = url, width = width, height = height)
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
    val type: String,
    val status: String = "published",
    val regionCode: String = "",
    val user: PostUserDto,
    val description: String? = null,
    val locationName: String? = null,
    val coordinates: CoordinatesDto? = null,
    val hashtags: List<String> = emptyList(),
    val media: List<PostMediaDto> = emptyList(),
    val taggedUsers: List<PostUserDto> = emptyList(),
    val stops: List<RouteStopDto> = emptyList(),
    val durationDays: Int? = null,
    val lengthNm: Double? = null,
    val commentsCount: Int = 0,
    val reactions: ReactionSummaryDto = ReactionSummaryDto(),
    val bookmarked: Boolean = false,
    val validityVotes: VoteSummaryDto? = null,
    val permissions: PostPermissionsDto = PostPermissionsDto(),
    val expiresAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    /** Posts with unknown type or status are dropped rather than crash the feed. */
    fun toDomain(): Post? {
        val postType = PostType.fromWire(type) ?: return null
        val postStatus = PostStatus.fromWire(status) ?: return null
        return Post(
            id = id,
            type = postType,
            status = postStatus,
            regionCode = regionCode,
            user = user.toDomain(),
            description = description,
            locationName = locationName,
            coordinates = coordinates?.toDomain(),
            hashtags = hashtags,
            media = media.map { it.toDomain() },
            taggedUsers = taggedUsers.map { it.toDomain() },
            stops = stops.map { it.toDomain() },
            durationDays = durationDays,
            lengthNm = lengthNm,
            commentsCount = commentsCount,
            reactions = reactions.toDomain(),
            bookmarked = bookmarked,
            validityVotes = validityVotes?.toDomain(),
            permissions = permissions.toDomain(),
            expiresAt = expiresAt,
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
