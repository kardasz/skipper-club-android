package app.skipperclub.data

import kotlinx.serialization.Serializable

/**
 * Blind post-cruise review system (`docs/api/reviews/index.md`). Reviews stay
 * `pending` (hidden) until the reciprocal review is submitted, then both flip to
 * `published`. Only `published` reviews are ever returned by the list endpoints.
 */
enum class ReviewStatus(val wireValue: String) {
    Pending("pending"),
    Published("published"),
    ;

    companion object {
        fun fromWire(value: String): ReviewStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** The four 1–5 categories scored on every review, plus the server-computed average. */
data class ReviewRatings(
    val communication: Int,
    val behavior: Int,
    val skills: Int,
    val duties: Int,
    val average: Double,
)

data class ReviewUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class ReviewCruiseSummary(
    val id: String,
    val title: String,
    val departureDate: String,
)

data class Review(
    val id: String,
    val cruiseId: String,
    val reviewer: ReviewUser,
    val reviewedUser: ReviewUser,
    val cruise: ReviewCruiseSummary?,
    val ratings: ReviewRatings,
    val comment: String,
    val status: ReviewStatus,
    val createdAt: String,
    val updatedAt: String,
)

data class ReviewsPage(
    val reviews: List<Review>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

/** Body of `POST /v1/cruises/{cruiseId}/reviews`. Ratings 1–5, comment 100–1000 chars. */
@Serializable
data class CreateReviewPayload(
    val reviewedUserId: String,
    val communication: Int,
    val behavior: Int,
    val skills: Int,
    val duties: Int,
    val comment: String,
)

@Serializable
internal data class ReviewUserDto(
    val id: String,
    val name: String = "",
    val avatarUrl: String? = null,
) {
    fun toDomain(): ReviewUser = ReviewUser(id = id, name = name, avatarUrl = avatarUrl)
}

@Serializable
internal data class ReviewCruiseDto(
    val id: String,
    val title: String = "",
    val departureDate: String = "",
) {
    fun toDomain(): ReviewCruiseSummary =
        ReviewCruiseSummary(id = id, title = title, departureDate = departureDate)
}

@Serializable
internal data class ReviewRatingsDto(
    val communication: Int = 0,
    val behavior: Int = 0,
    val skills: Int = 0,
    val duties: Int = 0,
    val average: Double = 0.0,
) {
    fun toDomain(): ReviewRatings = ReviewRatings(
        communication = communication,
        behavior = behavior,
        skills = skills,
        duties = duties,
        average = average,
    )
}

@Serializable
internal data class ReviewDto(
    val id: String,
    val cruiseId: String = "",
    val reviewer: ReviewUserDto,
    val reviewedUser: ReviewUserDto,
    val cruise: ReviewCruiseDto? = null,
    val ratings: ReviewRatingsDto,
    val comment: String = "",
    val status: String,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    /** Reviews with an unknown status are dropped rather than crash the list. */
    fun toDomain(): Review? {
        val reviewStatus = ReviewStatus.fromWire(status) ?: return null
        return Review(
            id = id,
            cruiseId = cruiseId.ifBlank { cruise?.id.orEmpty() },
            reviewer = reviewer.toDomain(),
            reviewedUser = reviewedUser.toDomain(),
            cruise = cruise?.toDomain(),
            ratings = ratings.toDomain(),
            comment = comment,
            status = reviewStatus,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
internal data class ReviewsListDto(
    val reviews: List<ReviewDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): ReviewsPage = ReviewsPage(
        reviews = reviews.mapNotNull { it.toDomain() },
        total = total,
        limit = limit,
        offset = offset,
    )
}
