package app.skipperclub.ui.main.cruises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.skipperclub.R
import app.skipperclub.data.CruiseParticipant
import app.skipperclub.data.CruiseParticipantState

/** Organizer-only crew + invitation management (`docs/ux/flows/cruise-participant-manage-screen.md`). */
@Composable
fun CruiseParticipantManageScreen(
    controller: CruiseDetailController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showInvite by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("cruise_manage"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("cruise_manage_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.conversation_back))
                }
                Text(
                    text = stringResource(R.string.cruise_manage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (selectedTab == 1) {
                    IconButton(onClick = { showInvite = true }, modifier = Modifier.testTag("cruise_invite_open")) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.cruise_invite_user))
                    }
                }
            }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.cruise_manage_tab_crew, state.crewMembers.size)) },
                    modifier = Modifier.testTag("cruise_tab_crew"),
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.cruise_manage_tab_invitations, state.invitations.size)) },
                    modifier = Modifier.testTag("cruise_tab_invitations"),
                )
            }

            val items = if (selectedTab == 0) state.crewMembers else state.invitations
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(
                            if (selectedTab == 0) R.string.cruise_manage_crew_empty else R.string.cruise_manage_invitations_empty,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("cruise_manage_list"),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items, key = { it.id }) { participant ->
                        ParticipantRow(
                            participant = participant,
                            enabled = !state.isActing,
                            onAccept = { controller.acceptRequest(participant) },
                            onReject = { controller.rejectRequest(participant) },
                            onCancelInvitation = { controller.cancelInvitation(participant) },
                            onRemove = { controller.removeParticipant(participant) },
                        )
                    }
                }
            }
        }
    }

    if (showInvite) {
        Dialog(onDismissRequest = { showInvite = false }) {
            InviteUserDialog(
                query = state.inviteQuery,
                results = state.inviteResults,
                isSearching = state.isSearchingUsers,
                onQueryChange = controller::updateInviteQuery,
                onInvite = {
                    controller.invite(it)
                    showInvite = false
                },
                onDismiss = {
                    controller.updateInviteQuery("")
                    showInvite = false
                },
            )
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: CruiseParticipant,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancelInvitation: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CruiseAvatar(
                name = participant.user?.name ?: "?",
                avatarUrl = participant.user?.avatarUrl,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = participant.user?.name ?: stringResource(R.string.cruise_participant_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CruiseStatusBadge(
                    text = stringResource(participant.state.labelRes()),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        ParticipantActions(
            state = participant.state,
            enabled = enabled,
            onAccept = onAccept,
            onReject = onReject,
            onCancelInvitation = onCancelInvitation,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun ParticipantActions(
    state: CruiseParticipantState,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancelInvitation: () -> Unit,
    onRemove: () -> Unit,
) {
    when (state) {
        CruiseParticipantState.Pending -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onReject, enabled = enabled, modifier = Modifier.weight(1f).testTag("participant_reject")) {
                Text(stringResource(R.string.cruise_action_reject))
            }
            androidx.compose.material3.Button(onClick = onAccept, enabled = enabled, modifier = Modifier.weight(1f).testTag("participant_accept")) {
                Text(stringResource(R.string.cruise_action_accept))
            }
        }

        CruiseParticipantState.Invited -> OutlinedButton(
            onClick = onCancelInvitation,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("participant_cancel_invite"),
        ) {
            Text(stringResource(R.string.cruise_action_cancel_invitation))
        }

        CruiseParticipantState.Accepted -> OutlinedButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("participant_remove"),
        ) {
            Text(stringResource(R.string.cruise_action_remove), color = MaterialTheme.colorScheme.error)
        }

        else -> Unit
    }
}

@Composable
private fun InviteUserDialog(
    query: String,
    results: List<app.skipperclub.data.ChatUser>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onInvite: (app.skipperclub.data.ChatUser) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.cruise_invite_user), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("cruise_invite_search"),
                placeholder = { Text(stringResource(R.string.cruise_invite_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp)) {
                when {
                    isSearching -> CircularProgressIndicator(Modifier.align(Alignment.Center).padding(16.dp))
                    results.isEmpty() && query.trim().length >= 2 -> Text(
                        text = stringResource(R.string.cruise_invite_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )

                    else -> LazyColumn {
                        items(results, key = { it.id }) { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onInvite(user) }
                                    .testTag("cruise_invite_user_${user.id}").padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CruiseAvatar(name = user.name, avatarUrl = user.avatarUrl, modifier = Modifier.size(40.dp))
                                Text(user.name, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.cruise_done))
            }
        }
    }
}
