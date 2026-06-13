package app.skipperclub.ui.main.alert

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.AlertCategory
import app.skipperclub.ui.theme.SkipperClubTheme

/**
 * Full-screen alert form, rendered as an opaque overlay (matching the project's
 * full-screen wizard pattern rather than an Android [android.app.Dialog]). The
 * location is already fixed (the coordinates live in [state]); here the user only
 * picks a category and writes a description.
 */
@Composable
fun AlertFormDialog(
    state: AlertUiState.Form,
    onCategorySelected: (AlertCategory) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = !state.isSubmitting) { onDismiss() }
    AlertFormContent(
        category = state.category,
        content = state.content,
        contentError = state.contentError,
        isSubmitting = state.isSubmitting,
        onCategorySelected = onCategorySelected,
        onContentChange = onContentChange,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}

@Composable
private fun AlertFormContent(
    category: AlertCategory,
    content: String,
    contentError: AlertContentError?,
    isSubmitting: Boolean,
    onCategorySelected: (AlertCategory) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            // Swallow stray taps so they don't reach the map behind this overlay.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.alert_form_close),
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.alert_form_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AlertCategoryField(
                selected = category,
                enabled = !isSubmitting,
                onSelected = onCategorySelected,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val errorText = when (contentError) {
                AlertContentError.Required -> stringResource(R.string.alert_error_content_required)
                is AlertContentError.Server -> contentError.message
                null -> null
            }
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                enabled = !isSubmitting,
                isError = contentError != null,
                label = { Text(stringResource(R.string.alert_form_content_label)) },
                placeholder = { Text(stringResource(R.string.alert_form_content_placeholder)) },
                supportingText = errorText?.let { { Text(it) } },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_form_content"),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSave,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("alert_form_save"),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.alert_form_save),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertCategoryField(
    selected: AlertCategory,
    enabled: Boolean,
    onSelected: (AlertCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { AlertCategory.entries }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.alert_form_category_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("alert_form_category"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun AlertFormPreviewEn() {
    SkipperClubTheme {
        AlertFormContent(
            category = AlertCategory.Weather,
            content = "Strong bora expected near the Velebit channel.",
            contentError = null,
            isSubmitting = false,
            onCategorySelected = {},
            onContentChange = {},
            onSave = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun AlertFormPreviewPl() {
    SkipperClubTheme {
        AlertFormContent(
            category = AlertCategory.Obstruction,
            content = "",
            contentError = AlertContentError.Required,
            isSubmitting = false,
            onCategorySelected = {},
            onContentChange = {},
            onSave = {},
            onDismiss = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AlertFormPreviewDark() {
    SkipperClubTheme {
        AlertFormContent(
            category = AlertCategory.MilitaryExercise,
            content = "Live firing exercise in progress.",
            contentError = null,
            isSubmitting = true,
            onCategorySelected = {},
            onContentChange = {},
            onSave = {},
            onDismiss = {},
        )
    }
}
