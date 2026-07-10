package app.skipperclub.ui.main

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.main.checkin.fetchCurrentLocation
import app.skipperclub.ui.main.checkin.hasLocationPermission
import app.skipperclub.ui.theme.SkipperClubTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val NavigationLocationZoom = 15f

/** Shared zoom and user-location controls displayed on every app map. */
@Composable
fun MapNavigationControls(
    cameraPositionState: CameraPositionState,
    onPermissionDenied: () -> Unit,
    onLocationUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    val locateUser: () -> Unit = {
        if (!isLocating) {
            isLocating = true
            scope.launch {
                try {
                    val location = try {
                        context.fetchCurrentLocation()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (location == null) {
                        onLocationUnavailable()
                    } else {
                        val target = LatLng(location.latitude, location.longitude)
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(
                                target,
                                maxOf(cameraPositionState.position.zoom, NavigationLocationZoom),
                            ),
                            durationMs = 600,
                        )
                    }
                } finally {
                    isLocating = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) locateUser() else onPermissionDenied()
    }

    MapNavigationControlsContent(
        isLocating = isLocating,
        onZoomIn = {
            scope.launch {
                cameraPositionState.animate(CameraUpdateFactory.zoomIn(), durationMs = 250)
            }
        },
        onZoomOut = {
            scope.launch {
                cameraPositionState.animate(CameraUpdateFactory.zoomOut(), durationMs = 250)
            }
        },
        onMyLocation = {
            if (context.hasLocationPermission()) {
                locateUser()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun MapNavigationControlsContent(
    isLocating: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_zoom_in"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.map_navigation_zoom_in),
                )
            }
            MapControlDivider()
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_zoom_out"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.map_navigation_zoom_out),
                )
            }
            MapControlDivider()
            IconButton(
                onClick = onMyLocation,
                enabled = !isLocating,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("map_my_location"),
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.map_navigation_my_location),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControlDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.width(32.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun MapNavigationControlsPreviewEn() {
    SkipperClubTheme {
        MapNavigationControlsContent(
            isLocating = false,
            onZoomIn = {},
            onZoomOut = {},
            onMyLocation = {},
        )
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun MapNavigationControlsPreviewPl() {
    SkipperClubTheme {
        MapNavigationControlsContent(
            isLocating = true,
            onZoomIn = {},
            onZoomOut = {},
            onMyLocation = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapNavigationControlsPreviewDark() {
    SkipperClubTheme {
        MapNavigationControlsContent(
            isLocating = false,
            onZoomIn = {},
            onZoomOut = {},
            onMyLocation = {},
        )
    }
}
