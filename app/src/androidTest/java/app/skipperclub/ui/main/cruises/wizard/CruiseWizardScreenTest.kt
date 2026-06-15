package app.skipperclub.ui.main.cruises.wizard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CruiseWizardScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun participantStepperChangesValueAndStopsAtLimit() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val state = CruiseWizardState(
            scope = scope,
            accessToken = { null },
        )
        state.updateMaxParticipants("19")

        compose.setContent {
            SkipperClubTheme {
                WizardCrewStep(state)
            }
        }

        compose.onNodeWithTag("cruise_participants_increase").performClick()

        assertEquals("20", state.maxParticipantsText)
        compose.onNodeWithTag("cruise_participants_increase").assertIsNotEnabled()

        compose.onNodeWithTag("cruise_participants_decrease").performClick()

        assertEquals("19", state.maxParticipantsText)
    }
}
