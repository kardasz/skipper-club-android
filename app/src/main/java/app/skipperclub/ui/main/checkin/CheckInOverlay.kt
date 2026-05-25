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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.theme.extended

/**
 * Bottom-aligned overlay that hosts the check-in flow. A single [Column] stacks
 * (snackbar → location-name card → action button) so the right-side button shifts
 * up cleanly when the card appears and the snackbar never overlaps either.
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
    onLocationNameChanged: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    snackbarHostState: SnackbarHostState,
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth(),
            ) { data ->
                Snackbar(snackbarData = data)
            }

            AnimatedVisibility(
                visible = state is CheckInUiState.Active,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val active = state as? CheckInUiState.Active
                if (active != null) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        LocationNameCard(
                            value = active.locationName,
                            isResolving = active.isResolvingName,
                            onValueChange = onLocationNameChanged,
                            onCancel = onCancel,
                            cancelEnabled = !active.isSubmitting,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CheckInActionButton(
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
        targetValue = if (isActive) MaterialTheme.extended.success else MaterialTheme.colorScheme.secondary,
        label = "Check-in button container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.extended.onSuccess else MaterialTheme.colorScheme.onSecondary,
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
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(
                if (isActive) R.string.map_check_in_confirm else R.string.map_check_in,
            ),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LocationNameCard(
    value: String,
    isResolving: Boolean,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    cancelEnabled: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.map_check_in_location_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isResolving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 8.dp),
                    )
                }
                IconButton(
                    onClick = onCancel,
                    enabled = cancelEnabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_check_in_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(text = stringResource(R.string.map_check_in_location_name_placeholder))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 280, locale = "en")
@Composable
private fun CheckInOverlayIdlePreview() {
    SkipperClubTheme {
        val snackbar = remember { SnackbarHostState() }
        CheckInOverlay(
            state = CheckInUiState.Idle,
            onStart = {},
            onConfirm = {},
            onCancel = {},
            onLocationNameChanged = {},
            onPermissionDenied = {},
            snackbarHostState = snackbar,
            bottomInset = 8.dp,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 380, locale = "pl")
@Composable
private fun CheckInOverlayActivePreviewPl() {
    SkipperClubTheme {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            snackbar.showSnackbar("Nie udało się ustalić lokalizacji.")
        }
        CheckInOverlay(
            state = CheckInUiState.Active(
                pin = com.google.android.gms.maps.model.LatLng(54.352, 18.6466),
                locationName = "Marina Gdańsk",
                isResolvingName = false,
                isSubmitting = false,
            ),
            onStart = {},
            onConfirm = {},
            onCancel = {},
            onLocationNameChanged = {},
            onPermissionDenied = {},
            snackbarHostState = snackbar,
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
        val snackbar = remember { SnackbarHostState() }
        CheckInOverlay(
            state = CheckInUiState.Active(
                pin = com.google.android.gms.maps.model.LatLng(54.352, 18.6466),
                locationName = "Gen. Mariana C. Coopera 3",
                isResolvingName = true,
                isSubmitting = false,
            ),
            onStart = {},
            onConfirm = {},
            onCancel = {},
            onLocationNameChanged = {},
            onPermissionDenied = {},
            snackbarHostState = snackbar,
            bottomInset = 8.dp,
        )
    }
}
