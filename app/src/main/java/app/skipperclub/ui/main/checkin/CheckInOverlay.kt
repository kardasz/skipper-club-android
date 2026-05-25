package app.skipperclub.ui.main.checkin

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.theme.extended

/**
 * Bottom-aligned overlay that hosts the check-in flow. It keeps the action stack
 * above global navigation while leaving the map visible.
 *
 * Pass [bottomInset] equal to the bottom-bar height (plus desired gap) so nothing
 * collides with global navigation.
 */
@Composable
fun CheckInOverlay(
    state: CheckInUiState,
    onStart: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onPermissionDenied: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onStart() else onPermissionDenied()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(
                visible = state is CheckInUiState.Active,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                val active = state as? CheckInUiState.Active
                if (active != null) {
                    LocationSummaryPill(
                        value = active.locationName,
                        isResolving = active.isResolvingName,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CheckInActions(
                state = state,
                onStart = {
                    if (context.hasLocationPermission()) {
                        onStart()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun CheckInActions(
    state: CheckInUiState,
    onStart: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CheckInActionButton(
            state = state,
            onStart = onStart,
            onConfirm = onConfirm,
        )
        AnimatedVisibility(
            visible = state is CheckInUiState.Active,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            CancelCheckInButton(
                enabled = (state as? CheckInUiState.Active)?.isSubmitting != true,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun CheckInActionButton(
    state: CheckInUiState,
    onStart: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isActive = state is CheckInUiState.Active
    val isBusy = state is CheckInUiState.Locating ||
        (state is CheckInUiState.Active && state.isSubmitting)

    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.extended.success else MaterialTheme.colorScheme.primary,
        label = "Check-in button container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.extended.onSuccess else MaterialTheme.colorScheme.onPrimary,
        label = "Check-in button content",
    )

    Button(
        onClick = {
            when (state) {
                is CheckInUiState.Idle -> onStart()
                is CheckInUiState.Locating -> Unit
                is CheckInUiState.Active -> onConfirm()
            }
        },
        enabled = !isBusy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        modifier = Modifier
            .widthIn(min = 224.dp, max = 320.dp)
            .defaultMinSize(minHeight = 60.dp),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(
                if (isActive) R.string.map_check_in_confirm else R.string.map_check_in,
            ),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CancelCheckInButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.62f),
        ),
        modifier = Modifier
            .widthIn(min = 224.dp, max = 320.dp)
            .defaultMinSize(minHeight = 56.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.map_check_in_cancel),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LocationSummaryPill(
    value: String,
    isResolving: Boolean,
) {
    val text = when {
        value.isNotBlank() -> value
        isResolving -> stringResource(R.string.map_check_in_location_resolving)
        else -> stringResource(R.string.map_check_in_location_unknown)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isResolving) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 280, locale = "en")
@Composable
private fun CheckInOverlayIdlePreview() {
    SkipperClubTheme {
        CheckInOverlay(
            state = CheckInUiState.Idle,
            onStart = {},
            onConfirm = {},
            onCancel = {},
            onPermissionDenied = {},
            bottomInset = 8.dp,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 380, locale = "pl")
@Composable
private fun CheckInOverlayActivePreviewPl() {
    SkipperClubTheme {
        CheckInOverlay(
            state = CheckInUiState.Active(
                locationName = "Marina Gdańsk",
                isResolvingName = false,
                isSubmitting = false,
            ),
            onStart = {},
            onConfirm = {},
            onCancel = {},
            onPermissionDenied = {},
            bottomInset = 8.dp,
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 380,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CheckInOverlayActivePreviewDark() {
    SkipperClubTheme {
        CheckInOverlay(
            state = CheckInUiState.Active(
                locationName = "Gen. Mariana C. Coopera 3",
                isResolvingName = true,
                isSubmitting = false,
            ),
            onStart = {},
            onConfirm = {},
            onCancel = {},
            onPermissionDenied = {},
            bottomInset = 8.dp,
        )
    }
}
