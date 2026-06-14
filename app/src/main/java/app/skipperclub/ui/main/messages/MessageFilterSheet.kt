package app.skipperclub.ui.main.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.ChatType

/** Conversation filters, mirroring the cruise filter sheet for a consistent surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageFilterSheet(
    selected: ChatType?,
    onApply: (ChatType?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MessageFilterSheetContent(
            selected = selected,
            onApply = onApply,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageFilterSheetContent(
    selected: ChatType?,
    onApply: (ChatType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(selected) { mutableStateOf(selected) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.filter_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { draft = null },
                enabled = draft != null,
            ) {
                Text(stringResource(R.string.filter_clear))
            }
        }

        Text(
            text = stringResource(R.string.messages_filter_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MessageTypeChip(
                selected = draft == null,
                onClick = { draft = null },
                label = stringResource(R.string.messages_filter_all),
                modifier = Modifier.testTag("message_filter_type_all"),
            )
            ChatType.entries.forEach { type ->
                MessageTypeChip(
                    selected = draft == type,
                    onClick = { draft = if (draft == type) null else type },
                    label = stringResource(type.labelRes()),
                    modifier = Modifier.testTag("message_filter_type_${type.wireValue.lowercase()}"),
                )
            }
        }

        Button(
            onClick = { onApply(draft) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("message_filter_apply"),
        ) {
            Text(stringResource(R.string.filter_apply))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageTypeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        modifier = modifier,
    )
}
