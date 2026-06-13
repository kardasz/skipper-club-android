package app.skipperclub.ui.main.cruises

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.skipperclub.R

/** Destructive / state-changing detail actions that require a confirmation modal. */
enum class CruiseConfirmAction { Join, CancelRequest, RejectInvitation, Leave, Delete }

@Composable
fun CruiseConfirmDialog(
    action: CruiseConfirmAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val copy = when (action) {
        CruiseConfirmAction.Join -> ConfirmCopy(R.string.cruise_confirm_join_title, R.string.cruise_confirm_join_text, R.string.cruise_action_join, false)
        CruiseConfirmAction.CancelRequest -> ConfirmCopy(R.string.cruise_confirm_cancel_title, R.string.cruise_confirm_cancel_text, R.string.cruise_action_cancel_request, true)
        CruiseConfirmAction.RejectInvitation -> ConfirmCopy(R.string.cruise_confirm_decline_title, R.string.cruise_confirm_decline_text, R.string.cruise_action_decline, true)
        CruiseConfirmAction.Leave -> ConfirmCopy(R.string.cruise_confirm_leave_title, R.string.cruise_confirm_leave_text, R.string.cruise_action_leave, true)
        CruiseConfirmAction.Delete -> ConfirmCopy(R.string.cruise_confirm_delete_title, R.string.cruise_confirm_delete_text, R.string.cruise_action_delete, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(copy.title)) },
        text = { Text(stringResource(copy.text)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (copy.destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = androidx.compose.ui.Modifier.testTag("cruise_confirm"),
            ) {
                Text(stringResource(copy.confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.post_cancel)) }
        },
    )
}

private data class ConfirmCopy(val title: Int, val text: Int, val confirm: Int, val destructive: Boolean)
