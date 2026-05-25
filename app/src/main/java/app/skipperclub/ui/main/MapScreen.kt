package app.skipperclub.ui.main

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
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
    val isActive = checkInState is CheckInUiState.Active

    // Re-geocode when the map stops moving (camera target = pin position).
    // While the camera is moving the user is still aiming; only fire after a brief
    // settle delay so we don't burn quota on every micro-pan.
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        // Mark resolving immediately so the spinner appears as soon as the user
        // starts dragging the map.
        (checkInState as? CheckInUiState.Active)?.let {
            checkInState = it.copy(isResolvingName = true)
        }
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collect { moving ->
                val current = checkInState as? CheckInUiState.Active ?: return@collect
                if (moving) {
                    if (!current.isResolvingName) {
                        checkInState = current.copy(isResolvingName = true)
                    }
                    return@collect
                }
                // Camera settled — debounce a touch, then geocode the new target.
                delay(300)
                val target = cameraPositionState.position.target
                val resolved = runCatching { context.reverseGeocode(target.latitude, target.longitude) }
                    .getOrNull()
                val latest = checkInState as? CheckInUiState.Active ?: return@collect
                checkInState = latest.copy(
                    locationName = resolved.orEmpty(),
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
        )

        // Static pin overlay, fixed to the centre of the *visible* map area
        // (= the area not covered by our bottom bar). The map's contentPadding
        // makes `cameraPositionState.position.target` resolve to this same point,
        // so what the user sees is what we send.
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomBarMapPadding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CenterMapPin(modifier = Modifier.align(Alignment.Center))
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
                    val target = LatLng(location.latitude, location.longitude)
                    checkInState = CheckInUiState.Active(
                        locationName = "",
                        isResolvingName = true,
                        isSubmitting = false,
                    )
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngZoom(target, ACTIVE_ZOOM),
                        durationMs = 600,
                    )
                    // After animate() returns the camera has settled, which means
                    // snapshotFlow above won't re-trigger; kick off geocoding now.
                    val resolved = runCatching { context.reverseGeocode(target.latitude, target.longitude) }
                        .getOrNull()
                    val latest = checkInState as? CheckInUiState.Active ?: return@launch
                    checkInState = latest.copy(
                        locationName = resolved.orEmpty(),
                        isResolvingName = false,
                    )
                }
            },
            onConfirm = {
                val active = checkInState as? CheckInUiState.Active ?: return@CheckInOverlay
                val target = cameraPositionState.position.target
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
                            lat = target.latitude,
                            lng = target.longitude,
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
                        snackbarHostState.showSnackbar(
                            e.userMessage(networkErrorMessage, authErrorMessage, genericErrorMessage),
                        )
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

/**
 * The pin used to mark the camera target. The icon's "tip" points downward, so we
 * shift the whole icon up by half its height — that way the geometric centre of the
 * map (= the camera's target LatLng) sits exactly under the tip.
 */
@Composable
private fun CenterMapPin(modifier: Modifier = Modifier) {
    val pinSize = 56.dp
    Box(modifier = modifier.size(pinSize)) {
        // Small ground anchor at the true centre so the user can see exactly which
        // pixel will be sent as their location.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
        )
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = stringResource(R.string.map_check_in_pin_content_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(pinSize / 2))
                .size(pinSize),
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
