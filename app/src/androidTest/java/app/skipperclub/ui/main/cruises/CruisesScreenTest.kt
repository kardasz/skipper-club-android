package app.skipperclub.ui.main.cruises

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CruisesScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cruiseListUsesFeedHeaderActionAndRendersCards() {
        var createClicked = false
        var filtersClicked = false

        compose.setContent {
            SkipperClubTheme {
                CruiseListScreenContent(
                    state = CruiseListUiState(
                        cruises = listOf(
                            previewCruise(role = app.skipperclub.data.CruiseUserRole.Organizer),
                            previewCruise(id = "c2", title = "Croatia Milebuilding"),
                        ),
                        hasLoadedOnce = true,
                    ),
                    onOpenCruise = {},
                    onCreate = { createClicked = true },
                    onOpenFilters = { filtersClicked = true },
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.nav_cruises)).assertExists()
        compose.onNodeWithTag("cruises_create").assertExists().performClick()
        compose.onNodeWithTag("cruises_create_fab").assertDoesNotExist()
        compose.onNodeWithTag("cruises_search").assertExists()
        compose.onNodeWithTag("cruises_filters").assertExists().performClick()
        compose.onNodeWithText("Mediterranean Summer Sailing").assertExists()
        assertTrue(createClicked)
        assertTrue(filtersClicked)
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
