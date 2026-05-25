package app.skipperclub.ui.main.checkin

/**
 * The pin's geographical position is no longer part of the state — it is read on
 * demand from the map's camera target, since the pin is rendered as a static overlay
 * fixed to the centre of the visible map area.
 */
sealed interface CheckInUiState {
    data object Idle : CheckInUiState
    data object Locating : CheckInUiState
    data class Active(
        val locationName: String,
        val isResolvingName: Boolean,
        val isSubmitting: Boolean,
    ) : CheckInUiState
}
