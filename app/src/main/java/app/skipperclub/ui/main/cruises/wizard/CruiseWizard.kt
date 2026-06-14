package app.skipperclub.ui.main.cruises.wizard

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Cruise
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState

/**
 * Hosts the cruise create/edit [CruiseWizardState]: wires the access token,
 * surfaces publish errors as in-app notifications and forwards the published
 * cruise back to the caller. Pass [existing] to edit instead of create.
 */
@Composable
fun CruiseWizardHost(
    existing: Cruise?,
    onClose: () -> Unit,
    onPublished: (Cruise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember(scope, existing?.id) {
        CruiseWizardState(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            existing = existing,
        )
    }
    val notificationHostState = rememberInAppNotificationHostState()

    val publishFailed = stringResource(R.string.cruise_wizard_publish_failed)
    val sessionExpired = stringResource(R.string.cruise_error_auth)
    val draftGenerated = stringResource(R.string.cruise_ai_generated)
    val draftFailed = stringResource(R.string.cruise_ai_failed)

    LaunchedEffect(state) {
        state.events.collect { event ->
            when (event) {
                is CruiseWizardEvent.Published -> onPublished(event.cruise)
                is CruiseWizardEvent.PublishFailed ->
                    notificationHostState.show(event.error.message ?: publishFailed, InAppNotificationType.Error)

                CruiseWizardEvent.DraftGenerated ->
                    notificationHostState.show(draftGenerated, InAppNotificationType.Success)

                is CruiseWizardEvent.DraftFailed ->
                    notificationHostState.show(draftFailed, InAppNotificationType.Error)

                CruiseWizardEvent.SessionExpired ->
                    notificationHostState.show(sessionExpired, InAppNotificationType.Error)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CruiseWizard(state = state, onClose = onClose)
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Full-screen cruise wizard chrome; step bodies live in `CruiseWizardStepViews.kt`. */
@Composable
fun CruiseWizard(
    state: CruiseWizardState,
    onClose: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    val requestClose: () -> Unit = {
        if (state.hasUserInput) showDiscardDialog = true else onClose()
    }

    BackHandler {
        if (!state.back()) requestClose()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cruise_wizard"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            WizardTopBar(state = state, onCloseRequest = requestClose)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    when (state.step) {
                        CruiseWizardStep.AiDraft -> WizardAiDraftStep(state)
                        CruiseWizardStep.Basics -> WizardBasicsStep(state)
                        CruiseWizardStep.Route -> WizardRouteStep(state)
                        CruiseWizardStep.Vessel -> WizardVesselStep(state)
                        CruiseWizardStep.Crew -> WizardCrewStep(state)
                        CruiseWizardStep.Summary -> WizardSummaryStep(state)
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
                Button(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) { Text(stringResource(R.string.wizard_discard_confirm)) }
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
    state: CruiseWizardState,
    onCloseRequest: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCloseRequest, modifier = Modifier.testTag("cruise_wizard_close")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wizard_close))
            }
            Text(
                text = stringResource(
                    if (state.isEditing) R.string.cruise_wizard_title_edit else R.string.cruise_wizard_title_create,
                ),
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
        Text(
            text = stringResource(state.step.titleRes()),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )
    }
}

@Composable
private fun WizardBottomBar(state: CruiseWizardState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.step == CruiseWizardStep.AiDraft) {
            OutlinedButton(
                onClick = { state.next() },
                enabled = !state.isGeneratingDraft,
                modifier = Modifier.weight(1f).testTag("cruise_wizard_ai_skip"),
            ) {
                Text(stringResource(R.string.cruise_ai_skip))
            }
            Button(
                onClick = { state.generateDraft() },
                enabled = state.canGenerateDraft,
                modifier = Modifier.weight(2f).testTag("cruise_wizard_ai_generate"),
            ) {
                if (state.isGeneratingDraft) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.cruise_ai_generate))
                }
            }
            return@Row
        }
        if (state.stepIndex > 0) {
            OutlinedButton(
                onClick = { state.back() },
                modifier = Modifier.weight(1f).testTag("cruise_wizard_back"),
            ) {
                Text(stringResource(R.string.wizard_back))
            }
        }
        if (state.step == CruiseWizardStep.Summary) {
            Button(
                onClick = { state.publish() },
                enabled = !state.isPublishing,
                modifier = Modifier.weight(2f).testTag("cruise_wizard_publish"),
            ) {
                if (state.isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.cruise_wizard_save else R.string.wizard_publish,
                        ),
                    )
                }
            }
        } else {
            Button(
                onClick = { state.next() },
                enabled = state.canGoNext,
                modifier = Modifier.weight(2f).testTag("cruise_wizard_next"),
            ) {
                Text(stringResource(R.string.wizard_next))
            }
        }
    }
}

internal fun CruiseWizardStep.titleRes(): Int = when (this) {
    CruiseWizardStep.AiDraft -> R.string.cruise_wizard_step_ai
    CruiseWizardStep.Basics -> R.string.cruise_wizard_step_basics
    CruiseWizardStep.Route -> R.string.cruise_wizard_step_route
    CruiseWizardStep.Vessel -> R.string.cruise_wizard_step_vessel
    CruiseWizardStep.Crew -> R.string.cruise_wizard_step_crew
    CruiseWizardStep.Summary -> R.string.cruise_wizard_step_summary
}
