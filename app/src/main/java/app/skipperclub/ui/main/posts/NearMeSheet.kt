package app.skipperclub.ui.main.posts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.PostCoordinates
import app.skipperclub.ui.main.checkin.fetchCurrentLocation
import app.skipperclub.ui.main.checkin.hasLocationPermission
import kotlin.math.roundToInt

private enum class LocateStatus { NeedsPermission, Denied, Locating, Ready, Failed }

/**
 * "Near me" distance filter. Picks a radius (1–50 NM) and centers it on a fresh device
 * fix (via the check-in [fetchCurrentLocation] provider), then hands both back so the
 * caller can apply them to the feed. The device position never leaves the client —
 * only the resulting `lat/lng/distance` query does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearMeSheet(
    initialRadiusNm: Int,
    isActive: Boolean,
    onApply: (center: PostCoordinates, radiusNm: Int, label: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        NearMeSheetContent(
            initialRadiusNm = initialRadiusNm,
            isActive = isActive,
            onApply = onApply,
            onClear = onClear,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun NearMeSheetContent(
    initialRadiusNm: Int,
    isActive: Boolean,
    onApply: (center: PostCoordinates, radiusNm: Int, label: String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val myLocationLabel = stringResource(R.string.near_me_my_location)

    var radiusNm by remember {
        mutableStateOf(initialRadiusNm.coerceIn(NearMeMinNm, NearMeMaxNm))
    }
    var status by remember {
        mutableStateOf(if (context.hasLocationPermission()) LocateStatus.Locating else LocateStatus.NeedsPermission)
    }
    var coordinates by remember { mutableStateOf<PostCoordinates?>(null) }
    var locateKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(locateKey) {
        if (!context.hasLocationPermission()) {
            status = LocateStatus.NeedsPermission
            return@LaunchedEffect
        }
        status = LocateStatus.Locating
        val fix = context.fetchCurrentLocation()
        if (fix == null) {
            status = LocateStatus.Failed
        } else {
            coordinates = PostCoordinates(lat = fix.latitude, lng = fix.longitude)
            status = LocateStatus.Ready
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) locateKey++ else status = LocateStatus.Denied
    }
    fun requestPermission() {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.near_me_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (isActive) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("near_me_clear"),
                ) {
                    Text(stringResource(R.string.near_me_clear))
                }
            }
        }
        Text(
            text = stringResource(R.string.near_me_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Radius picker.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.near_me_radius_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.near_me_radius_value, radiusNm),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = radiusNm.toFloat(),
            onValueChange = { radiusNm = it.roundToInt().coerceIn(NearMeMinNm, NearMeMaxNm) },
            valueRange = NearMeMinNm.toFloat()..NearMeMaxNm.toFloat(),
            steps = NearMeMaxNm - NearMeMinNm - 1,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("near_me_slider"),
        )
        Row {
            Text(
                text = stringResource(R.string.near_me_radius_value, NearMeMinNm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.near_me_radius_value, NearMeMaxNm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LocationStatusBlock(
            status = status,
            onRequestPermission = ::requestPermission,
            onRetry = { locateKey++ },
        )

        Button(
            onClick = {
                val center = coordinates ?: return@Button
                onApply(center, radiusNm, myLocationLabel)
            },
            enabled = status == LocateStatus.Ready && coordinates != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("near_me_apply"),
        ) {
            Text(stringResource(R.string.near_me_apply, radiusNm))
        }
    }
}

@Composable
private fun LocationStatusBlock(
    status: LocateStatus,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        LocateStatus.NeedsPermission, LocateStatus.Denied -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == LocateStatus.Denied) {
                    StatusNote(
                        icon = { Icon(Icons.Outlined.LocationOff, contentDescription = null) },
                        text = stringResource(R.string.near_me_permission_denied),
                    )
                }
                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("near_me_grant"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.near_me_grant),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        LocateStatus.Locating -> StatusNote(
            icon = { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) },
            text = stringResource(R.string.near_me_locating),
        )

        LocateStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusNote(
                icon = { Icon(Icons.Outlined.LocationOff, contentDescription = null) },
                text = stringResource(R.string.near_me_failed),
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.testTag("near_me_retry"),
            ) {
                Text(stringResource(R.string.near_me_retry))
            }
        }

        LocateStatus.Ready -> StatusNote(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            text = stringResource(R.string.near_me_permission_note),
        )
    }
}

@Composable
private fun StatusNote(
    icon: @Composable () -> Unit,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
