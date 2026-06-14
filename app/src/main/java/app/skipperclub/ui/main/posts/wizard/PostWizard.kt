package app.skipperclub.ui.main.posts.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
 * Full-screen post creation wizard. Steps: type → details → (route stops) →
 * media → summary. State lives in [PostWizardState]; this file is the chrome
 * and step routing, the step bodies live in `PostWizardStepViews.kt`.
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

    BackHandler {
        if (!state.back()) requestClose()
    }

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
                    when (state.step) {
                        PostWizardStep.Type -> WizardTypeStep(state)
                        PostWizardStep.Details -> WizardDetailsStep(state)
                        PostWizardStep.RouteStops -> WizardRouteStopsStep(state)
                        PostWizardStep.Media -> WizardMediaStep(state)
                        PostWizardStep.Tags -> WizardTagsStep(state)
                        PostWizardStep.Summary -> WizardSummaryStep(state)
                    }
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
    Column {
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
                text = stringResource(state.step.titleRes()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.stepIndex + 1}/${state.steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (state.stepIndex + 1f) / state.steps.size },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun WizardBottomBar(state: PostWizardState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.stepIndex > 0) {
            OutlinedButton(
                onClick = { state.back() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("wizard_back"),
            ) {
                Text(stringResource(R.string.wizard_back))
            }
        }
        if (state.step == PostWizardStep.Summary) {
            val publishLabel = when {
                state.isPublishing && state.isEditing -> R.string.wizard_saving
                state.isPublishing -> R.string.wizard_publishing
                state.isEditing -> R.string.wizard_save
                else -> R.string.wizard_publish
            }
            Button(
                onClick = { state.publish() },
                enabled = !state.isPublishing,
                modifier = Modifier
                    .weight(2f)
                    .testTag("wizard_publish"),
            ) {
                Text(text = stringResource(publishLabel))
            }
        } else {
            Button(
                onClick = { state.next() },
                enabled = state.canGoNext,
                modifier = Modifier
                    .weight(2f)
                    .testTag("wizard_next"),
            ) {
                Text(stringResource(R.string.wizard_next))
            }
        }
    }
}

private fun PostWizardStep.titleRes(): Int = when (this) {
    PostWizardStep.Type -> R.string.wizard_step_type
    PostWizardStep.Details -> R.string.wizard_step_details
    PostWizardStep.RouteStops -> R.string.wizard_step_route
    PostWizardStep.Media -> R.string.wizard_step_media
    PostWizardStep.Tags -> R.string.wizard_step_tags
    PostWizardStep.Summary -> R.string.wizard_step_summary
}
