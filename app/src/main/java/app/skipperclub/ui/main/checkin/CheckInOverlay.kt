package app.skipperclub.ui.main.checkin

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.theme.extended

/**
 * Bottom-aligned overlay that hosts the active check-in actions (confirm /
 * cancel). The idle entry point now lives in the map "+" speed-dial
 * ([app.skipperclub.ui.main.MapAddMenu]); this overlay renders nothing while the
 * flow is [CheckInUiState.Idle].
 *
 * Pass [bottomInset] equal to the bottom-bar height (plus desired gap) so nothing
 * collides with global navigation.
 */
@Composable
fun CheckInOverlay(
    state: CheckInUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state !is CheckInUiState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomInset),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                CheckInActions(
                    state = state,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun CheckInActions(
    state: CheckInUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConfirmCheckInButton(state = state, onConfirm = onConfirm)
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
private fun ConfirmCheckInButton(
    state: CheckInUiState,
    onConfirm: () -> Unit,
) {
    val isBusy = state is CheckInUiState.Locating ||
        (state is CheckInUiState.Active && state.isSubmitting)

    Button(
        onClick = { if (state is CheckInUiState.Active) onConfirm() },
        enabled = !isBusy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.extended.success,
            contentColor = MaterialTheme.extended.onSuccess,
            disabledContainerColor = MaterialTheme.extended.success,
            disabledContentColor = MaterialTheme.extended.onSuccess,
        ),
        modifier = Modifier
            .widthIn(min = 224.dp, max = 320.dp)
            .defaultMinSize(minHeight = 60.dp),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = MaterialTheme.extended.onSuccess,
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
            text = stringResource(R.string.map_check_in_confirm),
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

@Preview(showBackground = true, widthDp = 360, heightDp = 380, locale = "pl")
@Composable
private fun CheckInOverlayActivePreviewPl() {
    SkipperClubTheme {
        CheckInOverlay(
            state = CheckInUiState.Active(
                locationLabel = LocationLabel(placeName = "Marina Gdańsk"),
                isResolvingName = false,
                isSubmitting = false,
            ),
            onConfirm = {},
            onCancel = {},
            bottomInset = 8.dp,
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CheckInOverlayLocatingPreviewDark() {
    SkipperClubTheme {
        CheckInOverlay(
            state = CheckInUiState.Locating,
            onConfirm = {},
            onCancel = {},
            bottomInset = 8.dp,
        )
    }
}
