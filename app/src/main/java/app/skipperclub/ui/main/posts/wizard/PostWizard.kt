package app.skipperclub.ui.main.posts.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.skipperclub.data.SessionUser

/**
 * Full-screen post composer (create / edit). Content comes first: a borderless
 * text field with the attachments the user added rendered inline (location
 * chip, media strip, route card, tag chips), and an icon action bar above the
 * keyboard that opens pickers / bottom sheets. Alerts are not created here —
 * they have their own map-anchored flow; editing an alert post preserves the
 * alert payload and shows it as a read-only badge.
 *
 * State lives in [PostWizardState]; the composer body, the action bar and the
 * bottom sheets live in `PostWizardStepViews.kt`.
 */
@Composable
fun PostWizard(
    state: PostWizardState,
    onClose: () -> Unit,
    user: SessionUser? = null,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val sheets = remember { WizardSheets() }

    val requestClose: () -> Unit = {
        if (state.hasUserInput) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler { requestClose() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("post_wizard"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            WizardTopBar(state = state, onCloseRequest = requestClose)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    WizardComposer(state = state, sheets = sheets, user = user)
                }
            }
            WizardActionBar(state = state, sheets = sheets)
        }
    }

    WizardSheetHost(state = state, sheets = sheets)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.wizard_discard_title)) },
            text = { Text(stringResource(R.string.wizard_discard_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    },
                ) {
                    Text(stringResource(R.string.wizard_discard_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.post_cancel))
                }
            },
        )
    }
}

@Composable
private fun WizardTopBar(
    state: PostWizardState,
    onCloseRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCloseRequest, modifier = Modifier.testTag("wizard_close")) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.wizard_close),
            )
        }
        Text(
            text = stringResource(
                if (state.isEditing) R.string.wizard_title_edit else R.string.wizard_title_create,
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        val publishLabel = when {
            state.isPublishing && state.isEditing -> R.string.wizard_saving
            state.isPublishing -> R.string.wizard_publishing
            state.isEditing -> R.string.wizard_save
            else -> R.string.wizard_publish
        }
        Button(
            onClick = { state.publish() },
            enabled = state.canPublish,
            modifier = Modifier.testTag("wizard_publish"),
        ) {
            Text(text = stringResource(publishLabel))
        }
    }
}
