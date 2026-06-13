package app.skipperclub.ui.main.invitations

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Invitation
import app.skipperclub.data.InvitationStatus
import app.skipperclub.data.Inviter
import app.skipperclub.data.InvitationsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Full-screen admin invitations center launched from the main menu. */
@Composable
fun InvitationsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        InvitationsController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    val resources = LocalResources.current

    val errorNetwork = stringResource(R.string.invitations_error_network)
    val errorAuth = stringResource(R.string.invitations_error_auth)
    val errorForbidden = stringResource(R.string.invitations_error_forbidden)
    val errorGeneric = stringResource(R.string.invitations_error_generic)

    fun errorMessage(error: Exception): String = when (error) {
        is InvitationsError.Network -> errorNetwork
        is InvitationsError.AuthenticationRequired -> errorAuth
        is InvitationsError.Forbidden -> errorForbidden
        else -> errorGeneric
    }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var createEmail by rememberSaveable { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is InvitationsEvent.OperationFailed -> {
                    val message = errorMessage(event.error)
                    // Keep create errors inline in the form; everything else is a top toast.
                    if (showCreate) createError = message
                    else notificationHostState.show(message, InAppNotificationType.Error)
                }

                is InvitationsEvent.InvitationCreated -> {
                    showCreate = false
                    createEmail = ""
                    createError = null
                    notificationHostState.show(
                        resources.getString(R.string.invitations_created, event.email),
                        InAppNotificationType.Success,
                    )
                }

                is InvitationsEvent.InvitationResent ->
                    notificationHostState.show(
                        resources.getString(R.string.invitations_resent, event.email),
                        InAppNotificationType.Success,
                    )

                InvitationsEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("invitations_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            InvitationsScreenContent(
                state = state,
                onClose = onClose,
                onCreateClick = {
                    createError = null
                    showCreate = true
                },
                onResend = controller::resend,
                onDelete = controller::delete,
                onRefresh = controller::refresh,
                onLoadMore = controller::loadMore,
                onRetry = controller::refresh,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (showCreate) {
        CreateInvitationDialog(
            email = createEmail,
            isSending = state.isSending,
            errorMessage = createError,
            onEmailChange = {
                createEmail = it
                createError = null
            },
            onSubmit = { controller.createInvitation(createEmail.trim()) },
            onDismiss = { if (!state.isSending) showCreate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvitationsScreenContent(
    state: InvitationsUiState,
    onClose: () -> Unit,
    onCreateClick: () -> Unit,
    onResend: (Invitation) -> Unit,
    onDelete: (Invitation) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.hasMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    var selected by remember { mutableStateOf<Invitation?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("invitations_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.invitations_back),
                )
            }
            Text(
                text = stringResource(R.string.invitations_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.loadFailed && state.invitations.isEmpty() -> InvitationsMessage(
                    title = stringResource(R.string.invitations_load_failed),
                    actionLabel = stringResource(R.string.invitations_retry),
                    onAction = onRetry,
                )

                state.invitations.isEmpty() && state.hasLoadedOnce -> InvitationsMessage(
                    title = stringResource(R.string.invitations_empty_title),
                    subtitle = stringResource(R.string.invitations_empty_subtitle),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("invitations_list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    items(state.invitations, key = { it.id }) { invitation ->
                        InvitationRow(
                            invitation = invitation,
                            onClick = { selected = invitation },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
      }

      ExtendedFloatingActionButton(
          onClick = onCreateClick,
          icon = { Icon(Icons.Filled.Add, contentDescription = null) },
          text = { Text(stringResource(R.string.invitation_create)) },
          modifier = Modifier
              .align(Alignment.BottomEnd)
              .navigationBarsPadding()
              .padding(16.dp)
              .testTag("invitations_create_fab"),
      )
    }

    selected?.let { invitation ->
        val current = state.invitations.firstOrNull { it.id == invitation.id } ?: invitation
        InvitationDetailSheet(
            invitation = current,
            isResending = state.resendingId == current.id,
            onDismiss = { selected = null },
            onResend = {
                onResend(current)
                selected = null
            },
            onDelete = {
                onDelete(current)
                selected = null
            },
        )
    }
}

@Composable
private fun InvitationRow(
    invitation: Invitation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("invitation_item_${invitation.id}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mail,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = invitation.email,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.invitations_sent_on,
                    formatInvitationDate(invitation.createdAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        InvitationStatusChip(
            status = invitation.status,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun InvitationStatusChip(
    status: InvitationStatus,
    modifier: Modifier = Modifier,
) {
    val accent = status.accentColor()
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvitationDetailSheet(
    invitation: Invitation,
    isResending: Boolean,
    onDismiss: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                .testTag("invitation_detail_sheet"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = invitation.email,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                InvitationStatusChip(status = invitation.status, modifier = Modifier.padding(start = 12.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            DetailRow(
                label = stringResource(R.string.invitation_detail_email),
                value = invitation.email,
            )
            DetailRow(
                label = stringResource(R.string.invitation_detail_sent),
                value = formatInvitationDateTime(invitation.createdAt),
            )
            DetailRow(
                label = stringResource(R.string.invitation_detail_expires),
                value = formatInvitationDateTime(invitation.expiresAt),
            )
            DetailRow(
                label = stringResource(R.string.invitation_detail_status),
                value = invitation.status.label(),
            )
            DetailRow(
                label = stringResource(R.string.invitation_detail_inviter),
                value = invitation.inviter.name,
            )

            FilledTonalButton(
                onClick = onResend,
                enabled = !isResending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .testTag("invitation_resend"),
            ) {
                if (isResending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.invitation_resend),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("invitation_delete"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.invitation_delete),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.invitation_delete_confirm_title)) },
            text = { Text(stringResource(R.string.invitation_delete_confirm_message, invitation.email)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("invitation_delete_confirm"),
                ) {
                    Text(
                        text = stringResource(R.string.invitation_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.invitations_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun InvitationsMessage(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

/** Bottom-sheet form for sending a brand-new invitation to an email address. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateInvitationDialog(
    email: String,
    isSending: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isValid = remember(email) {
        val trimmed = email.trim()
        trimmed.isNotEmpty() && trimmed.length <= 320 && Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                .testTag("invitation_create_sheet"),
        ) {
            Text(
                text = stringResource(R.string.invitation_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.invitation_create_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.invitation_create_email_label)) },
                singleLine = true,
                enabled = !isSending,
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .testTag("invitation_create_email"),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Button(
                onClick = onSubmit,
                enabled = isValid && !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .testTag("invitation_create_submit"),
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.invitation_create_submit),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

// --- Previews ---

internal fun previewInvitation(
    id: String,
    email: String,
    status: InvitationStatus = InvitationStatus.Pending,
    createdAt: String = "2026-06-10T09:00:00Z",
    expiresAt: String = "2026-06-17T09:00:00Z",
): Invitation = Invitation(
    id = id,
    email = email,
    status = status,
    expiresAt = expiresAt,
    createdAt = createdAt,
    inviter = Inviter(id = "admin-1", name = "Anna Nowak"),
)

private val previewState = InvitationsUiState(
    invitations = listOf(
        previewInvitation("i1", "friend@example.com", InvitationStatus.Pending),
        previewInvitation("i2", "jan.kowalski@example.com", InvitationStatus.Accepted),
        previewInvitation(
            "i3",
            "piotr.wisniewski@example.com",
            InvitationStatus.Expired,
            createdAt = "2026-05-01T12:00:00Z",
            expiresAt = "2026-05-08T12:00:00Z",
        ),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "en")
@Composable
private fun InvitationsPreview() {
    SkipperClubTheme {
        InvitationsScreenContent(
            state = previewState,
            onClose = {},
            onCreateClick = {},
            onResend = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun InvitationsPreviewDark() {
    SkipperClubTheme {
        InvitationsScreenContent(
            state = previewState,
            onClose = {},
            onCreateClick = {},
            onResend = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun InvitationsPreviewPl() {
    SkipperClubTheme {
        InvitationsScreenContent(
            state = previewState,
            onClose = {},
            onCreateClick = {},
            onResend = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun InvitationsEmptyPreviewPl() {
    SkipperClubTheme {
        InvitationsScreenContent(
            state = InvitationsUiState(hasLoadedOnce = true),
            onClose = {},
            onCreateClick = {},
            onResend = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
