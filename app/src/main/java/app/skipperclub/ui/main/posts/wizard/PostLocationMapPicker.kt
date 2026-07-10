package app.skipperclub.ui.main.posts.wizard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.PostCoordinates
import app.skipperclub.ui.theme.SkipperClubTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

private const val LocationPickerZoom = 16f

/** Full-screen map used to refine the point attached to a post. */
@Composable
internal fun PostLocationMapPicker(
    initialCoordinates: PostCoordinates,
    locationName: String?,
    onConfirm: (PostCoordinates) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PostLocationMapPickerContent(
            initialCoordinates = initialCoordinates,
            locationName = locationName,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun PostLocationMapPickerContent(
    initialCoordinates: PostCoordinates,
    locationName: String?,
    onConfirm: (PostCoordinates) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialTarget = remember(initialCoordinates) {
        LatLng(initialCoordinates.lat, initialCoordinates.lng)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialTarget, LocationPickerZoom)
    }
    val uiSettings = remember {
        MapUiSettings(
            compassEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("post_location_map_picker"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (LocalInspectionMode.current) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    contentDescription = stringResource(R.string.wizard_location_map_content_description),
                    mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
                    uiSettings = uiSettings,
                )
            }

            PostLocationMapTarget(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.wizard_location_map_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            locationName?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.wizard_location_map_cancel),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        onConfirm(PostCoordinates(lat = target.latitude, lng = target.longitude))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_location_map_confirm"),
                ) {
                    Text(stringResource(R.string.wizard_location_map_confirm))
                }
            }
        }
    }
}

/** Pin stays fixed; moving the map changes the camera target under its tip. */
@Composable
private fun PostLocationMapTarget(modifier: Modifier = Modifier) {
    val pinSize = 56.dp
    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-96).dp)
                .widthIn(max = 300.dp),
        ) {
            Text(
                text = stringResource(R.string.wizard_location_map_hint),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(pinSize),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
            )
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = stringResource(R.string.wizard_location_map_pin),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -(pinSize / 2))
                    .size(pinSize),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun PostLocationMapPickerPreviewEn() {
    SkipperClubTheme {
        PostLocationMapPickerContent(
            initialCoordinates = PostCoordinates(54.5189, 18.5305),
            locationName = "Gdynia",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760, locale = "pl")
@Composable
private fun PostLocationMapPickerPreviewPl() {
    SkipperClubTheme {
        PostLocationMapPickerContent(
            initialCoordinates = PostCoordinates(54.5189, 18.5305),
            locationName = "Gdynia",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostLocationMapPickerPreviewDark() {
    SkipperClubTheme {
        PostLocationMapPickerContent(
            initialCoordinates = PostCoordinates(54.5189, 18.5305),
            locationName = "Gdynia",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
