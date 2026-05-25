package app.skipperclub.ui.main.checkin

import com.google.android.gms.maps.model.LatLng

sealed interface CheckInUiState {
    data object Idle : CheckInUiState
    data object Locating : CheckInUiState
    data class Active(
        val pin: LatLng,
        val locationName: String,
        val isResolvingName: Boolean,
        val isSubmitting: Boolean,
    ) : CheckInUiState
}
