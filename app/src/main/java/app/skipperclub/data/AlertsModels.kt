package app.skipperclub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Navigation alert category. Mirrors the backend `AlertCategory` enum. Since API
 * v8.0.0 alerts are no longer a standalone resource — they are carried by posts
 * inside `content.alert` (see [PostAlert] / [AlertInputDto]) and surfaced on the
 * map as `post` items whose `contentKeys` contain `alert`. The wire values use
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
 * User-settable severity of an alert post (`content.alert.severity`). Official
 * imported alerts may omit it. The UI maps each entry to a localized label and a
 * marker/badge accent.
 */
@Serializable
enum class AlertSeverity {
    @SerialName("info")
    Info,

    @SerialName("warning")
    Warning,

    @SerialName("critical")
    Critical,
}
