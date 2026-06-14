package app.skipperclub.ui.main.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.ReportReason

private const val REPORT_DETAILS_MAX_LENGTH = 500

/** Maps a [ReportReason] to its localized label. */
internal fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.Spam -> R.string.report_reason_spam
    ReportReason.Scam -> R.string.report_reason_scam
    ReportReason.Offensive -> R.string.report_reason_offensive
    ReportReason.Misinformation -> R.string.report_reason_misinformation
    ReportReason.Danger -> R.string.report_reason_danger
    ReportReason.Other -> R.string.report_reason_other
}

/**
 * Bottom sheet for reporting a post: a single-choice reason list plus an optional
 * free-text details field (max 500 chars per the API). Stateless content is split
 * out so previews and tests can drive it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPostSheet(
    onSubmit: (ReportReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ReportPostSheetContent(
            onSubmit = onSubmit,
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

@Composable
internal fun ReportPostSheetContent(
    onSubmit: (ReportReason, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedReason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.report_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.report_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReportReason.entries.forEach { reason ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedReason == reason,
                        role = Role.RadioButton,
                        onClick = { selectedReason = reason },
                    )
                    .padding(vertical = 4.dp)
                    .testTag("report_reason_${reason.wireValue}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = selectedReason == reason,
                    onClick = { selectedReason = reason },
                )
                Text(
                    text = stringResource(reason.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        OutlinedTextField(
            value = details,
            onValueChange = { details = it.take(REPORT_DETAILS_MAX_LENGTH) },
            label = { Text(stringResource(R.string.report_details_label)) },
            supportingText = { Text("${details.length}/$REPORT_DETAILS_MAX_LENGTH") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_details"),
        )
        Button(
            onClick = { selectedReason?.let { onSubmit(it, details.trim().ifBlank { null }) } },
            enabled = selectedReason != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_submit"),
        ) {
            Text(stringResource(R.string.report_submit))
        }
    }
}
