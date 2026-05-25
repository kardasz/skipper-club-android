package app.skipperclub.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.CheckInError
import app.skipperclub.data.CheckInsApi
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.checkin.CheckInOverlay
import app.skipperclub.ui.main.checkin.CheckInUiState
import app.skipperclub.ui.main.checkin.fetchCurrentLocation
import app.skipperclub.ui.main.checkin.reverseGeocode
import app.skipperclub.ui.theme.SkipperClubTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    MapScreenContent(modifier = modifier)
}

@Composable
private fun MapScreenContent(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        MapPreviewSurface(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionErrorMessage = stringResource(R.string.map_check_in_error_permission)
    val locationErrorMessage = stringResource(R.string.map_check_in_error_location)
    val networkErrorMessage = stringResource(R.string.map_check_in_error_network)
    val authErrorMessage = stringResource(R.string.map_check_in_error_auth)
    val genericErrorMessage = stringResource(R.string.map_check_in_error_generic)
    val successMessageTemplate = stringResource(R.string.map_check_in_success)
    val successNoNameMessage = stringResource(R.string.map_check_in_success_no_name)

    val startPosition = remember {
        CameraPosition.fromLatLngZoom(GDANSK_BAY, DEFAULT_ZOOM)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = startPosition
    }
    val mapProperties = remember {
        MapProperties(
            isBuildingEnabled = true,
            mapType = MapType.NORMAL,
            minZoomPreference = 5f,
        )
    }
    val mapUiSettings = remember {
        MapUiSettings(
            compassEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        )
    }

    var checkInState by remember { mutableStateOf<CheckInUiState>(CheckInUiState.Idle) }
    val markerState = remember { MarkerState() }
    val activePin by remember {
        derivedStateOf { (checkInState as? CheckInUiState.Active)?.pin }
    }

    // Re-geocode when the user finishes dragging the pin (debounced).
    LaunchedEffect(activePin != null) {
        if (activePin == null) return@LaunchedEffect
        snapshotFlow { markerState.position }
            .distinctUntilChanged()
            .collect { position ->
                val current = checkInState as? CheckInUiState.Active ?: return@collect
                if (current.pin == position) return@collect
                checkInState = current.copy(
                    pin = position,
                    isResolvingName = true,
                )
                delay(350)
                val resolved = runCatching { context.reverseGeocode(position.latitude, position.longitude) }
                    .getOrNull()
                val latest = checkInState as? CheckInUiState.Active ?: return@collect
                if (latest.pin != position) return@collect
                checkInState = latest.copy(
                    locationName = resolved ?: latest.locationName,
                    isResolvingName = false,
                )
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentDescription = stringResource(R.string.map_content_description),
            contentPadding = PaddingValues(bottom = BottomBarMapPadding),
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            properties = mapProperties,
            uiSettings = mapUiSettings,
        ) {
            if (activePin != null) {
                Marker(
                    state = markerState,
                    draggable = true,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    title = (checkInState as? CheckInUiState.Active)?.locationName?.takeIf { it.isNotBlank() },
                    snippet = stringResource(R.string.map_check_in_pin_snippet),
                )
            }
        }

        CheckInOverlay(
            state = checkInState,
            onStart = {
                checkInState = CheckInUiState.Locating
                scope.launch {
                    val location = runCatching { context.fetchCurrentLocation() }.getOrNull()
                    if (location == null) {
                        checkInState = CheckInUiState.Idle
                        snackbarHostState.showSnackbar(locationErrorMessage)
                        return@launch
                    }
                    val pin = LatLng(location.latitude, location.longitude)
                    markerState.position = pin
                    checkInState = CheckInUiState.Active(
                        pin = pin,
                        locationName = "",
                        isResolvingName = true,
                        isSubmitting = false,
                    )
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngZoom(pin, ACTIVE_ZOOM),
                        durationMs = 600,
                    )
                    val resolved = runCatching { context.reverseGeocode(pin.latitude, pin.longitude) }
                        .getOrNull()
                    val latest = checkInState as? CheckInUiState.Active ?: return@launch
                    if (latest.pin != pin) return@launch
                    checkInState = latest.copy(
                        locationName = resolved.orEmpty(),
                        isResolvingName = false,
                    )
                }
            },
            onConfirm = {
                val active = checkInState as? CheckInUiState.Active ?: return@CheckInOverlay
                checkInState = active.copy(isSubmitting = true)
                scope.launch {
                    val token = SessionStore.validSession()?.accessToken
                    if (token.isNullOrBlank()) {
                        checkInState = active.copy(isSubmitting = false)
                        snackbarHostState.showSnackbar(authErrorMessage)
                        return@launch
                    }
                    try {
                        CheckInsApi.upsert(
                            accessToken = token,
                            lat = active.pin.latitude,
                            lng = active.pin.longitude,
                            locationName = active.locationName.trim().ifEmpty { null },
                        )
                        val message = if (active.locationName.isNotBlank()) {
                            successMessageTemplate.format(active.locationName.trim())
                        } else {
                            successNoNameMessage
                        }
                        checkInState = CheckInUiState.Idle
                        snackbarHostState.showSnackbar(message)
                    } catch (e: CheckInError) {
                        checkInState = active.copy(isSubmitting = false)
                        snackbarHostState.showSnackbar(e.userMessage(networkErrorMessage, authErrorMessage, genericErrorMessage))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        checkInState = active.copy(isSubmitting = false)
                        snackbarHostState.showSnackbar(genericErrorMessage)
                    }
                }
            },
            onCancel = { checkInState = CheckInUiState.Idle },
            onLocationNameChanged = { newName ->
                val active = checkInState as? CheckInUiState.Active ?: return@CheckInOverlay
                checkInState = active.copy(locationName = newName)
            },
            onPermissionDenied = {
                checkInState = CheckInUiState.Idle
                scope.launch { snackbarHostState.showSnackbar(permissionErrorMessage) }
            },
            snackbarHostState = snackbarHostState,
            bottomInset = BottomBarMapPadding + 12.dp,
        )
    }
}

private fun CheckInError.userMessage(
    network: String,
    auth: String,
    generic: String,
): String = when (this) {
    is CheckInError.Network -> network
    is CheckInError.AuthenticationRequired -> auth
    is CheckInError.RateLimited,
    is CheckInError.Validation,
    is CheckInError.Server,
    -> generic
}

@Composable
private fun MapPreviewSurface(modifier: Modifier = Modifier) {
    val water = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val land = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
    val route = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(water),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (-110).dp, y = 190.dp)
                .clip(CircleShape)
                .background(land),
        )
        PreviewPoint(Modifier.offset(x = 148.dp, y = 240.dp), route)
        PreviewPoint(Modifier.offset(x = 190.dp, y = 330.dp), route)
        PreviewPoint(Modifier.offset(x = 238.dp, y = 418.dp), route)
    }
}

@Composable
private fun PreviewPoint(modifier: Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun MapScreenPreviewEn() {
    SkipperClubTheme {
        MapScreenContent()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun MapScreenPreviewPl() {
    SkipperClubTheme {
        MapScreenContent()
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MapScreenPreviewDark() {
    SkipperClubTheme {
        MapScreenContent()
    }
}

private val GDANSK_BAY = LatLng(54.4877, 18.6654)
private const val DEFAULT_ZOOM = 10f
private const val ACTIVE_ZOOM = 16f
private val BottomBarMapPadding: Dp = 114.dp
