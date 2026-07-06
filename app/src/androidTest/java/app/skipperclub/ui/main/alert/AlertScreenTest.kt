package app.skipperclub.ui.main.alert

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.skipperclub.R
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertSeverity
import app.skipperclub.ui.main.MapAddMenu
import app.skipperclub.ui.theme.SkipperClubTheme
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlertScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int): String = compose.activity.getString(id)

    @Test
    fun addMenuExposesCheckInAndAlertOptions() {
        var alertSelected = 0

        compose.setContent {
            SkipperClubTheme {
                MapAddMenu(
                    expanded = true,
                    onExpandedChange = {},
                    onSelectCheckIn = {},
                    onPermissionDenied = {},
                    onSelectAlert = { alertSelected++ },
                    bottomInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(text(R.string.map_action_check_in)).assertExists()
        compose.onNodeWithText(text(R.string.map_action_alert)).assertExists()
        compose.onNodeWithTag("map_add_alert").performClick()

        assertEquals(1, alertSelected)
    }

    @Test
    fun formContentEntryAndSaveInvokeCallbacks() {
        var savedContent = ""
        var saveCount = 0

        compose.setContent {
            var content by remember { mutableStateOf("") }
            SkipperClubTheme {
                AlertFormDialog(
                    state = AlertUiState.Form(
                        lat = 54.4,
                        lng = 18.6,
                        category = AlertCategory.NavigationWarning,
                        content = content,
                    ),
                    onCategorySelected = {},
                    onSeveritySelected = {},
                    onContentChange = {
                        content = it
                        savedContent = it
                    },
                    onSave = { saveCount++ },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("alert_form_content").performTextInput("Wreck near the entrance")
        compose.onNodeWithTag("alert_form_save").performClick()

        assertEquals("Wreck near the entrance", savedContent)
        assertEquals(1, saveCount)
    }

    @Test
    fun selectingSeverityInvokesCallback() {
        var selected: AlertSeverity? = null

        compose.setContent {
            SkipperClubTheme {
                AlertFormDialog(
                    state = AlertUiState.Form(
                        lat = 54.4,
                        lng = 18.6,
                        category = AlertCategory.NavigationWarning,
                        severity = AlertSeverity.Warning,
                        content = "Wreck near the entrance",
                    ),
                    onCategorySelected = {},
                    onSeveritySelected = { selected = it },
                    onContentChange = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("alert_form_severity").performClick()
        compose.onNodeWithText(text(R.string.alert_severity_critical)).performClick()

        assertEquals(AlertSeverity.Critical, selected)
    }

    @Test
    fun formShowsValidationErrorAndDisablesActionsWhileSubmitting() {
        compose.setContent {
            SkipperClubTheme {
                AlertFormDialog(
                    state = AlertUiState.Form(
                        lat = 54.4,
                        lng = 18.6,
                        category = AlertCategory.Weather,
                        content = "",
                        contentError = AlertContentError.Required,
                        isSubmitting = true,
                    ),
                    onCategorySelected = {},
                    onSeveritySelected = {},
                    onContentChange = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.alert_error_content_required)).assertExists()
        compose.onNodeWithTag("alert_form_save").assertIsNotEnabled()
    }
}
