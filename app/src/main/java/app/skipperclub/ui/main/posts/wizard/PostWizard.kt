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

/**
 * Full-screen post creation / edit form. Since API v8.0.0 there is no post-type
 * chooser: one scrolling form covers every post (text, location, optional route
 * OR alert, media, tags). State lives in [PostWizardState]; the section bodies
 * live in `PostWizardStepViews.kt`.
 */
@Composable
fun PostWizard(
    state: PostWizardState,
    onClose: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    WizardForm(state)
                }
            }
            WizardBottomBar(state = state)
        }
    }

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
            .padding(start = 4.dp, end = 20.dp, top = 4.dp),
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
    }
}

@Composable
private fun WizardBottomBar(state: PostWizardState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        val publishLabel = when {
            state.isPublishing && state.isEditing -> R.string.wizard_saving
            state.isPublishing -> R.string.wizard_publishing
            state.isEditing -> R.string.wizard_save
            else -> R.string.wizard_publish
        }
        Button(
            onClick = { state.publish() },
            enabled = state.canPublish,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_publish"),
        ) {
            Text(text = stringResource(publishLabel))
        }
    }
}
