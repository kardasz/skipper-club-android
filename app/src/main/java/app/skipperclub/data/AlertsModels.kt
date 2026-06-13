package app.skipperclub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Navigation alert category. Mirrors the backend `AlertCategory` enum
 * (see docs/api/reference/enums/alert-categories.md). The wire values use
 * snake_case; the UI maps each entry to a localized label.
 */
@Serializable
enum class AlertCategory {
    @SerialName("navigation_warning")
    NavigationWarning,

    @SerialName("navtex")
    Navtex,

    @SerialName("notice_to_mariners")
    NoticeToMariners,

    @SerialName("obstruction")
    Obstruction,

    @SerialName("works")
    Works,

    @SerialName("regatta")
    Regatta,

    @SerialName("diving")
    Diving,

    @SerialName("military_exercise")
    MilitaryExercise,

    @SerialName("weather")
    Weather,

    @SerialName("other")
    Other,
}

/**
 * GeoJSON geometry for an alert. The MVP only creates `Point` geometries, so
 * [coordinates] is a single `[lng, lat]` pair (GeoJSON longitude-first order).
 */
@Serializable
data class AlertGeometry(
    val type: String,
    val coordinates: List<Double>,
) {
    companion object {
        /** Builds a GeoJSON `Point` from the given latitude/longitude. */
        fun point(lat: Double, lng: Double): AlertGeometry =
            AlertGeometry(type = "Point", coordinates = listOf(lng, lat))
    }
}

@Serializable
internal data class CreateAlertRequest(
    val category: AlertCategory,
    val content: String,
    val geometry: AlertGeometry? = null,
)

/**
 * Alert as returned by `POST /v1/alerts`. Only the fields the client reads back
 * are modeled; the response carries more (see openapi `AlertResponse`).
 */
@Serializable
data class Alert(
    val id: String? = null,
    val category: AlertCategory? = null,
    val content: String? = null,
    val language: String? = null,
    val source: String? = null,
    val geometry: AlertGeometry? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
