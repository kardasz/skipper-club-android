package app.skipperclub.ui.main.spotdetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Coordinates
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.RadioChannelKind
import app.skipperclub.data.Spot
import app.skipperclub.ui.main.spots.formatCoordinates
import app.skipperclub.ui.main.spots.formatRadioChannel
import app.skipperclub.ui.theme.SkipperClubTheme

/**
 * UI state for the spot detail sheet opened from a spot marker tap.
 *
 * The marker only carries lightweight attributes, so the sheet opens in
 * [Loading] (showing the name we already know) while the full [Spot] — including
 * phone contacts and radio channels — is fetched via `GET /v1/spots/{id}`.
 */
sealed interface SpotDetailUiState {
    val spotId: String
    val name: String

    data class Loading(override val spotId: String, override val name: String) : SpotDetailUiState

    data class Ready(val spot: Spot) : SpotDetailUiState {
        override val spotId: String get() = spot.id
        override val name: String get() = spot.name
    }

    data class Failed(
        override val spotId: String,
        override val name: String,
    ) : SpotDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailSheet(
    state: SpotDetailUiState,
    onDismiss: () -> Unit,
    onCall: (PhoneContact) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        SpotDetailContent(
            state = state,
            onCall = onCall,
            onRetry = onRetry,
        )
    }
}

@Composable
internal fun SpotDetailContent(
    state: SpotDetailUiState,
    onCall: (PhoneContact) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        SpotDetailHeader(
            name = state.name,
            coordinates = (state as? SpotDetailUiState.Ready)?.spot?.coordinates,
        )

        when (state) {
            is SpotDetailUiState.Loading -> {
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            is SpotDetailUiState.Failed -> {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.spot_detail_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.spot_detail_retry))
                }
            }

            is SpotDetailUiState.Ready -> SpotDetailBody(spot = state.spot, onCall = onCall)
        }
    }
}

@Composable
private fun SpotDetailHeader(name: String, coordinates: Coordinates?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Anchor,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (coordinates != null) {
                Text(
                    text = formatCoordinates(coordinates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpotDetailBody(spot: Spot, onCall: (PhoneContact) -> Unit) {
    if (spot.phoneContacts.isEmpty() && spot.radioChannels.isEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.spot_detail_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (spot.phoneContacts.isNotEmpty()) {
        SpotDetailSectionTitle(stringResource(R.string.spot_detail_contacts_title))
        spot.phoneContacts.forEach { contact ->
            PhoneContactRow(contact = contact, onCall = { onCall(contact) })
        }
    }

    if (spot.radioChannels.isNotEmpty()) {
        SpotDetailSectionTitle(stringResource(R.string.spot_detail_channels_title))
        spot.radioChannels.forEach { channel ->
            RadioChannelRow(channel = channel)
        }
    }
}

@Composable
private fun SpotDetailSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun PhoneContactRow(contact: PhoneContact, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val label = contact.label?.takeIf { it.isNotBlank() }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = displayPhone(contact),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        FilledTonalIconButton(onClick = onCall) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = stringResource(R.string.spot_detail_call, displayPhone(contact)),
            )
        }
    }
}

@Composable
private fun RadioChannelRow(channel: RadioChannel) {
    SpotDetailIconRow(
        icon = Icons.Outlined.Radio,
        text = formatRadioChannel(channel),
    )
}

@Composable
private fun SpotDetailIconRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun displayPhone(contact: PhoneContact): String {
    val ext = contact.extension?.takeIf { it.isNotBlank() }
    return if (ext != null) "${contact.phone} · ext. $ext" else contact.phone
}

// --- Previews ---

private val previewSpot = Spot(
    id = "spot-1",
    name = "Marina Sopot",
    coordinates = Coordinates(lat = 54.4416, lng = 18.5674),
    phoneContacts = listOf(
        PhoneContact(id = "p1", label = "Bosman", phone = "+48 58 555 12 34", extension = null),
        PhoneContact(id = "p2", label = "Kapitanat", phone = "+48 58 555 99 00", extension = "12"),
    ),
    radioChannels = listOf(
        RadioChannel(id = "c1", name = "Marina", channelKind = RadioChannelKind.Vhf, vhfChannel = 10, frequencyMhz = null, isPrimary = true),
    ),
    createdAt = "2026-01-01T10:00:00Z",
    updatedAt = "2026-01-01T10:00:00Z",
)

@Preview(showBackground = true, locale = "en")
@Composable
private fun SpotDetailReadyPreviewEn() {
    SkipperClubTheme {
        SpotDetailContent(
            state = SpotDetailUiState.Ready(previewSpot),
            onCall = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun SpotDetailReadyPreviewPl() {
    SkipperClubTheme {
        SpotDetailContent(
            state = SpotDetailUiState.Ready(previewSpot),
            onCall = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpotDetailFailedPreviewDark() {
    SkipperClubTheme {
        SpotDetailContent(
            state = SpotDetailUiState.Failed(spotId = "spot-1", name = "Marina Sopot"),
            onCall = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun SpotDetailLoadingPreview() {
    SkipperClubTheme {
        SpotDetailContent(
            state = SpotDetailUiState.Loading(spotId = "spot-1", name = "Marina Sopot"),
            onCall = {},
            onRetry = {},
        )
    }
}
