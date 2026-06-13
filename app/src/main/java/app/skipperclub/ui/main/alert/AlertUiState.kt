package app.skipperclub.ui.main.alert

import app.skipperclub.data.AlertCategory

/**
 * Drives the navigation-alert creation flow on the map.
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

    /** The location is locked; the user fills in category + description. */
    data class Form(
        val lat: Double,
        val lng: Double,
        val category: AlertCategory,
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
