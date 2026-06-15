package app.skipperclub.ui.main.alert

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.MapEntryAttributes
import app.skipperclub.ui.theme.SkipperClubTheme

/** Identifies the tapped alert marker; carries the inlined alert body for the sheet. */
data class AlertDetailUiState(
    val title: String,
    val attributes: MapEntryAttributes.NavigationAlert,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailSheet(
    state: AlertDetailUiState,
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        AlertDetailContent(state = state, onOpenSource = onOpenSource)
    }
}

@Composable
internal fun AlertDetailContent(
    state: AlertDetailUiState,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attributes = state.attributes
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = attributes.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        )

        val sourceName = attributes.sourceName
        if (!sourceName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            val sourceNumber = attributes.sourceNumber
            Text(
                text = if (!sourceNumber.isNullOrBlank()) {
                    stringResource(R.string.alert_detail_source_number, sourceName, sourceNumber)
                } else {
                    stringResource(R.string.alert_detail_source, sourceName)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val sourceUrl = attributes.sourceUrl
        if (!sourceUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { onOpenSource(sourceUrl) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.alert_detail_open_source))
            }
        }
    }
}

// --- Previews ---

private val userAlert = AlertDetailUiState(
    title = "Weather alert",
    attributes = MapEntryAttributes.NavigationAlert(
        category = AlertCategory.Weather,
        content = "Gale warning in force. Winds gusting to 35 knots expected from the NW overnight.",
        source = "user",
    ),
)

private val officialAlert = AlertDetailUiState(
    title = "Navigation warning",
    attributes = MapEntryAttributes.NavigationAlert(
        category = AlertCategory.NavigationWarning,
        content = "Wreck marked by cardinal buoy at 54°30'N 18°40'E. Keep clear by at least 200 m.",
        source = "hhi_rnw",
        sourceName = "Hydrographic Institute",
        sourceNumber = "161/2026",
        sourceUrl = "https://www.hhi.hr/en/warnings",
    ),
)

@Preview(showBackground = true, locale = "en")
@Composable
private fun AlertDetailPreviewEn() {
    SkipperClubTheme {
        AlertDetailContent(state = officialAlert, onOpenSource = {})
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun AlertDetailPreviewPl() {
    SkipperClubTheme {
        AlertDetailContent(state = userAlert, onOpenSource = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlertDetailPreviewDark() {
    SkipperClubTheme {
        AlertDetailContent(state = officialAlert, onOpenSource = {})
    }
}
