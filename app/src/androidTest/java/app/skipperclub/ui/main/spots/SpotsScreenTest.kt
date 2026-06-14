package app.skipperclub.ui.main.spots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.skipperclub.R
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.RadioChannelKind
import app.skipperclub.data.Spot
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpotsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val neptun = previewSpot(
        "s1",
        "Neptun Marina",
        phoneContacts = listOf(PhoneContact("c1", "Harbour master", "+48581234567", null)),
        radioChannels = listOf(RadioChannel("r1", "Port", RadioChannelKind.Vhf, 12, null, true)),
    )
    private val sopot = previewSpot("s2", "Sopot Pier")

    @Test
    fun rendersSpotRows() {
        content(SpotsUiState(spots = listOf(neptun, sopot), hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.spots_title)).assertExists()
        compose.onNodeWithTag("spot_item_s1").assertExists()
        compose.onNodeWithTag("spot_item_s2").assertExists()
    }

    @Test
    fun emptyStateShownWhenNoSpots() {
        content(SpotsUiState(hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.spots_empty_title)).assertExists()
    }

    @Test
    fun tappingRowOpensDetailSheetWithActions() {
        content(SpotsUiState(spots = listOf(neptun), hasLoadedOnce = true))

        compose.onNodeWithTag("spot_item_s1").performClick()

        compose.onNodeWithTag("spot_detail_sheet").assertExists()
        compose.onNodeWithTag("spot_edit").assertExists()
        compose.onNodeWithTag("spot_delete").assertExists()
    }

    @Test
    fun editFromDetailEmitsCallback() {
        var edited: Spot? = null
        content(
            state = SpotsUiState(spots = listOf(neptun), hasLoadedOnce = true),
            onEditSpot = { edited = it },
        )

        compose.onNodeWithTag("spot_item_s1").performClick()
        compose.onNodeWithTag("spot_edit").performClick()

        assertEquals("s1", edited?.id)
    }

    @Test
    fun deleteFromDetailRequiresConfirmation() {
        var deleted: Spot? = null
        content(
            state = SpotsUiState(spots = listOf(neptun), hasLoadedOnce = true),
            onDelete = { deleted = it },
        )

        compose.onNodeWithTag("spot_item_s1").performClick()
        compose.onNodeWithTag("spot_delete").performClick()
        assertEquals(null, deleted)

        compose.onNodeWithTag("spot_delete_confirm").performClick()
        assertEquals("s1", deleted?.id)
    }

    @Test
    fun createFabEmitsCallback() {
        var clicks = 0
        content(
            state = SpotsUiState(spots = listOf(neptun), hasLoadedOnce = true),
            onCreateClick = { clicks++ },
        )

        compose.onNodeWithTag("spots_create_fab").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun backButtonEmitsCloseCallback() {
        var closed = 0
        content(
            state = SpotsUiState(spots = listOf(neptun), hasLoadedOnce = true),
            onClose = { closed++ },
        )

        compose.onNodeWithTag("spots_back").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun formSaveDisabledUntilRequiredFieldsValid() {
        var submitted: SpotForm? = null
        compose.setContent {
            SkipperClubTheme {
                SpotFormContent(
                    initial = SpotForm(),
                    isEditing = false,
                    isSaving = false,
                    errorMessage = null,
                    onErrorConsumed = {},
                    onCancel = {},
                    onSubmit = { submitted = it },
                )
            }
        }

        compose.onNodeWithTag("spot_form_save").assertIsNotEnabled()

        compose.onNodeWithTag("spot_form_name").performTextInput("Neptun")
        compose.onNodeWithTag("spot_form_lat").performTextInput("54.35")
        compose.onNodeWithTag("spot_form_lng").performTextInput("18.65")

        compose.onNodeWithTag("spot_form_save").assertIsEnabled().performClick()

        assertEquals("Neptun", submitted?.name)
        assertEquals("54.35", submitted?.lat)
    }

    @Test
    fun formPrefillsExistingSpotForEditing() {
        var submitted: SpotForm? = null
        compose.setContent {
            SkipperClubTheme {
                SpotFormContent(
                    initial = SpotForm.fromSpot(neptun),
                    isEditing = true,
                    isSaving = false,
                    errorMessage = null,
                    onErrorConsumed = {},
                    onCancel = {},
                    onSubmit = { submitted = it },
                )
            }
        }

        compose.onNodeWithTag("spot_form_name").performTextReplacement("Neptun Marina Renamed")
        compose.onNodeWithTag("spot_form_save").assertIsEnabled().performClick()

        assertEquals("Neptun Marina Renamed", submitted?.name)
    }

    @Test
    fun formShowsInlineError() {
        compose.setContent {
            SkipperClubTheme {
                SpotFormContent(
                    initial = SpotForm(name = "Neptun", lat = "54.35", lng = "18.65"),
                    isEditing = false,
                    isSaving = false,
                    errorMessage = "A nearby spot already exists",
                    onErrorConsumed = {},
                    onCancel = {},
                    onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("A nearby spot already exists").assertExists()
    }

    private fun content(
        state: SpotsUiState,
        onClose: () -> Unit = {},
        onCreateClick: () -> Unit = {},
        onEditSpot: (Spot) -> Unit = {},
        onDelete: (Spot) -> Unit = {},
    ) {
        compose.setContent {
            SkipperClubTheme {
                SpotsScreenContent(
                    state = state,
                    onClose = onClose,
                    onSearch = {},
                    onCreateClick = onCreateClick,
                    onEditSpot = onEditSpot,
                    onDelete = onDelete,
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
