package app.skipperclub.ui.main.cruises.reviews

import androidx.annotation.StringRes
import app.skipperclub.R
import app.skipperclub.data.ReviewRatings

/** Numeric bounds shared by the submit form and local validation (`docs/api/reviews`). */
object ReviewValidation {
    const val RATING_MIN = 1
    const val RATING_MAX = 5
    const val COMMENT_MIN = 100
    const val COMMENT_MAX = 1000
}

/** The four blind-review categories, in display order. */
enum class RatingCategory {
    Communication,
    Behavior,
    Skills,
    Duties,
    ;

    @StringRes
    fun labelRes(): Int = when (this) {
        Communication -> R.string.review_category_communication
        Behavior -> R.string.review_category_behavior
        Skills -> R.string.review_category_skills
        Duties -> R.string.review_category_duties
    }

    @StringRes
    fun descriptionRes(): Int = when (this) {
        Communication -> R.string.review_category_communication_desc
        Behavior -> R.string.review_category_behavior_desc
        Skills -> R.string.review_category_skills_desc
        Duties -> R.string.review_category_duties_desc
    }

    fun valueOf(ratings: ReviewRatings): Int = when (this) {
        Communication -> ratings.communication
        Behavior -> ratings.behavior
        Skills -> ratings.skills
        Duties -> ratings.duties
    }
}

/** `"4.5"` — one-decimal average for the rating badge. */
fun formatAverage(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
