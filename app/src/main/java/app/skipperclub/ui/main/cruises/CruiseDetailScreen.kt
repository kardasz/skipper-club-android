package app.skipperclub.ui.main.cruises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.cruises.wizard.CruiseWizardHost
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Full-screen, role-aware cruise detail. Hosts edit wizard + participant manage as nested dialogs. */
@Composable
fun CruiseDetailScreen(
    cruiseId: String,
    currentUserId: String?,
    onClose: () -> Unit,
    onCruiseChanged: (Cruise) -> Unit,
    onCruiseDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope, cruiseId) {
        CruiseDetailController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            currentUserId = { currentUserId },
            cruiseId = cruiseId,
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.cruise_error_network)
    val errorAuth = stringResource(R.string.cruise_error_auth)
    val errorGeneric = stringResource(R.string.cruise_error_generic)

    LaunchedEffect(controller) { controller.load() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is CruiseDetailEvent.CruiseChanged -> onCruiseChanged(event.cruise)
                is CruiseDetailEvent.Deleted -> onCruiseDeleted(cruiseId)
                is CruiseDetailEvent.OperationFailed ->
                    notificationHostState.show(cruiseErrorMessage(event.error, errorNetwork, errorAuth, errorGeneric), InAppNotificationType.Error)

                CruiseDetailEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    var showEdit by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<CruiseConfirmAction?>(null) }

    BackHandler(onBack = onClose)

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize().testTag("cruise_detail"),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                CruiseDetailTopBar(onClose = onClose)
                when {
                    state.isLoading && state.cruise == null ->
                        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }

                    state.cruise == null ->
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(stringResource(R.string.cruise_detail_load_failed))
                                Button(onClick = { controller.load() }, modifier = Modifier.padding(top = 12.dp)) {
                                    Text(stringResource(R.string.cruises_retry))
                                }
                            }
                        }

                    else -> {
                        val cruise = state.cruise!!
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            CruiseDetailBody(cruise)
                        }
                        CruiseDetailActions(
                            state = state,
                            onJoin = { pendingAction = CruiseConfirmAction.Join },
                            onCancelRequest = { pendingAction = CruiseConfirmAction.CancelRequest },
                            onAcceptInvitation = controller::acceptInvitation,
                            onRejectInvitation = { pendingAction = CruiseConfirmAction.RejectInvitation },
                            onLeave = { pendingAction = CruiseConfirmAction.Leave },
                            onEdit = { showEdit = true },
                            onManage = { showManage = true },
                            onDelete = { pendingAction = CruiseConfirmAction.Delete },
                        )
                    }
                }
            }
        }
        InAppNotificationHost(hostState = notificationHostState, modifier = Modifier.align(Alignment.TopCenter))
    }

    pendingAction?.let { action ->
        CruiseConfirmDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onConfirm = {
                when (action) {
                    CruiseConfirmAction.Join -> controller.join()
                    CruiseConfirmAction.CancelRequest -> controller.withdrawJoinRequest()
                    CruiseConfirmAction.RejectInvitation -> controller.rejectInvitation()
                    CruiseConfirmAction.Leave -> controller.leave()
                    CruiseConfirmAction.Delete -> controller.deleteCruise()
                }
                pendingAction = null
            },
        )
    }

    if (showEdit) {
        val editing = state.cruise
        if (editing != null) {
            Dialog(
                onDismissRequest = { showEdit = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                CruiseWizardHost(
                    existing = editing,
                    onClose = { showEdit = false },
                    onPublished = {
                        controller.onCruiseEdited(it)
                        showEdit = false
                    },
                )
            }
        }
    }

    if (showManage) {
        Dialog(
            onDismissRequest = { showManage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CruiseParticipantManageScreen(controller = controller, onClose = { showManage = false })
        }
    }
}

@Composable
private fun CruiseDetailTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("cruise_detail_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.conversation_back))
        }
        Text(
            text = stringResource(R.string.cruise_detail_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CruiseDetailBody(cruise: Cruise) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CruiseAvatar(name = cruise.organizer.name, avatarUrl = cruise.organizer.avatarUrl, modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(cruise.organizer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.cruise_organizer_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = cruise.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        cruise.type?.let {
            CruiseTagChip(text = stringResource(it.labelRes()), modifier = Modifier.padding(top = 8.dp))
        }

        if (cruise.description.isNotBlank()) {
            Text(
                text = cruise.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        DetailSection(stringResource(R.string.cruise_section_schedule)) {
            CruiseInfoRow(Icons.Outlined.CalendarMonth, formatDateRange(cruise.departureDate, cruise.arrivalDate))
            cruiseNights(cruise)?.let { nights ->
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(R.plurals.cruise_nights, nights, nights),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            CruiseInfoRow(Icons.Outlined.Place, "${cruise.departurePort.name} → ${cruise.arrivalPort.name}")
            cruise.stops.forEach { stop ->
                Text(
                    text = "• ${stop.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp, top = 2.dp),
                )
            }
        }

        DetailSection(stringResource(R.string.cruise_section_vessel)) {
            CruiseInfoRow(Icons.Outlined.Sailing, "${stringResource(cruise.vesselType.labelRes())} • ${cruise.vessel}")
            val specs = buildList {
                listOfNotNull(cruise.vesselBrand, cruise.vesselModel).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" ")) }
                cruise.vesselYear?.let { add(it.toString()) }
                cruise.vesselLength?.let { add(stringResource(R.string.cruise_vessel_length_value, it)) }
                cruise.vesselCabins?.let { add(androidx.compose.ui.res.pluralStringResource(R.plurals.cruise_vessel_cabins_value, it, it)) }
            }
            if (specs.isNotEmpty()) {
                Text(
                    text = specs.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp, top = 2.dp),
                )
            }
        }

        DetailSection(stringResource(R.string.cruise_section_crew)) {
            CruiseInfoRow(Icons.Outlined.Payments, "${formatPrice(cruise.costPerPerson, cruise.currency)} /${stringResource(R.string.cruise_per_person)}")
            Spacer(Modifier.size(8.dp))
            CruiseInfoRow(Icons.Outlined.Group, "${cruise.participantsCount}/${cruise.maxParticipants}")
        }

        if (!cruise.requiredSkills.isNullOrBlank()) {
            DetailSection(stringResource(R.string.cruise_section_requirements)) {
                Text(cruise.requiredSkills, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val rules = cruiseRuleLabels(cruise)
        if (rules.isNotEmpty()) {
            DetailSection(stringResource(R.string.cruise_section_rules)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rules.forEach { (labelRes, allowed) ->
                        CruiseStatusBadge(
                            text = stringResource(labelRes),
                            container = if (allowed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            content = if (allowed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val hashtags = cruiseHashtags(cruise)
        if (hashtags.isNotEmpty()) {
            CruiseHashtagRow(hashtags = hashtags, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun cruiseRuleLabels(cruise: Cruise): List<Pair<Int, Boolean>> = buildList {
    cruise.smokingAllowed?.let { add((if (it) R.string.cruise_rule_smoking_yes else R.string.cruise_rule_smoking_no) to it) }
    cruise.alcoholAllowed?.let { add((if (it) R.string.cruise_rule_alcohol_yes else R.string.cruise_rule_alcohol_no) to it) }
    cruise.petsAllowed?.let { add((if (it) R.string.cruise_rule_pets_yes else R.string.cruise_rule_pets_no) to it) }
    cruise.childrenAllowed?.let { add((if (it) R.string.cruise_rule_children_yes else R.string.cruise_rule_children_no) to it) }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
    content()
}

@Composable
private fun CruiseDetailActions(
    state: CruiseDetailUiState,
    onJoin: () -> Unit,
    onCancelRequest: () -> Unit,
    onAcceptInvitation: () -> Unit,
    onRejectInvitation: () -> Unit,
    onLeave: () -> Unit,
    onEdit: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
) {
    val cruise = state.cruise ?: return
    val enabled = !state.isActing
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.viewerRole) {
                CruiseViewerRole.Organizer -> {
                    OutlinedButton(onClick = onDelete, enabled = enabled, modifier = Modifier.weight(1f).testTag("cruise_delete")) {
                        Text(stringResource(R.string.cruise_action_delete), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.weight(1f).testTag("cruise_edit")) {
                        Text(stringResource(R.string.cruise_action_edit))
                    }
                    Button(onClick = onManage, enabled = enabled, modifier = Modifier.weight(1.4f).testTag("cruise_manage")) {
                        Text(stringResource(R.string.cruise_action_manage))
                    }
                }

                CruiseViewerRole.Participant -> {
                    Button(
                        onClick = onLeave,
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth().testTag("cruise_leave"),
                    ) {
                        Text(stringResource(R.string.cruise_action_leave))
                    }
                }

                CruiseViewerRole.Visitor -> VisitorActions(
                    cruise = cruise,
                    enabled = enabled,
                    onJoin = onJoin,
                    onCancelRequest = onCancelRequest,
                    onAcceptInvitation = onAcceptInvitation,
                    onRejectInvitation = onRejectInvitation,
                )
            }
            if (state.isActing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.VisitorActions(
    cruise: Cruise,
    enabled: Boolean,
    onJoin: () -> Unit,
    onCancelRequest: () -> Unit,
    onAcceptInvitation: () -> Unit,
    onRejectInvitation: () -> Unit,
) {
    val participation = cruise.currentUserParticipation
    when (participation?.state) {
        CruiseParticipantState.Pending -> OutlinedButton(
            onClick = onCancelRequest,
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag("cruise_cancel_request"),
        ) { Text(stringResource(R.string.cruise_action_cancel_request)) }

        CruiseParticipantState.Invited -> {
            OutlinedButton(onClick = onRejectInvitation, enabled = enabled, modifier = Modifier.weight(1f).testTag("cruise_decline")) {
                Text(stringResource(R.string.cruise_action_decline))
            }
            Button(onClick = onAcceptInvitation, enabled = enabled, modifier = Modifier.weight(1.4f).testTag("cruise_accept")) {
                Text(stringResource(R.string.cruise_action_accept))
            }
        }

        else -> Button(
            onClick = onJoin,
            enabled = enabled && !cruise.isFull,
            modifier = Modifier.fillMaxWidth().testTag("cruise_join"),
        ) {
            Text(stringResource(if (cruise.isFull) R.string.cruise_availability_full else R.string.cruise_action_join))
        }
    }
}

// --- Preview ---

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "en")
@Composable
private fun CruiseDetailBodyPreview() {
    SkipperClubTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            CruiseDetailBody(
                previewCruise().copy(
                    requiredSkills = "ISSA Inshore Skipper or equivalent.",
                    smokingAllowed = false,
                    alcoholAllowed = true,
                    petsAllowed = false,
                    childrenAllowed = true,
                ),
            )
        }
    }
}
