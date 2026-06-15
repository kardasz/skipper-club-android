package app.skipperclub.ui.main.cruises.wizard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test

class CruiseWizardScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun basicsShowsPrimaryTypesAndRevealsOptionalInputs() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val state = CruiseWizardState(
            scope = scope,
            accessToken = { null },
        )

        compose.setContent {
            SkipperClubTheme {
                WizardBasicsStep(state)
            }
        }

        compose.onNodeWithText(text(R.string.cruise_type_milebuilding)).assertExists()
        compose.onNodeWithText(text(R.string.cruise_type_training)).assertExists()
        compose.onNodeWithText(text(R.string.cruise_type_beginner_intro)).assertExists()
        compose.onNodeWithText(text(R.string.cruise_type_relax)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.cruise_types_more)).performClick()
        compose.onNodeWithText(text(R.string.cruise_type_relax)).assertExists()

        compose.onNodeWithText(text(R.string.cruise_required_skills_show)).performClick()
        compose.onNodeWithText(text(R.string.cruise_field_required_skills_hint)).assertExists()
    }

    @Test
    fun participantStepperDisablesIncreaseAtLimit() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val state = CruiseWizardState(
            scope = scope,
            accessToken = { null },
        )
        state.updateMaxParticipants("20")

        compose.setContent {
            SkipperClubTheme {
                WizardCrewStep(state)
            }
        }

        compose.onNodeWithTag("cruise_participants_increase").assertIsNotEnabled()
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
