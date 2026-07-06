package app.skipperclub.ui.main.alert

import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertSeverity

/**
 * Drives the alert-post creation flow on the map.
 *
 * Since API v8.0.0 navigation alerts are ordinary posts carrying `content.alert`;
 * this flow keeps the "aim on the map" UX and, on save, issues a
 * `POST /v1/posts` with an [app.skipperclub.data.AlertInputDto] and a point
 * location.
 *
 * Like the check-in flow, the chosen point is not stored while the user is still
 * aiming — it is read from the map's camera target on demand. Once the user taps
 * "Next" the coordinates are frozen into [Form] so panning the map underneath the
 * form no longer moves the alert.
 */
sealed interface AlertUiState {
    data object Idle : AlertUiState

    /** The center pin is visible; the user is aiming the map at the alert location. */
    data object PickingLocation : AlertUiState

    /** The location is locked; the user picks category + severity and writes the text. */
    data class Form(
        val lat: Double,
        val lng: Double,
        val category: AlertCategory = AlertCategory.NavigationWarning,
        val severity: AlertSeverity = AlertSeverity.Warning,
        val content: String,
        val contentError: AlertContentError? = null,
        val isSubmitting: Boolean = false,
    ) : AlertUiState
}

/** Inline validation error for the description field, resolved to text in the UI. */
sealed interface AlertContentError {
    /** The description was left empty (client-side or server `content` violation). */
    data object Required : AlertContentError

    /** A server-provided validation message for the `content` field. */
    data class Server(val message: String) : AlertContentError
}
