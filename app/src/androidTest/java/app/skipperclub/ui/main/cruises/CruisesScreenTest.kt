package app.skipperclub.ui.main.cruises

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CruisesScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val loadedState = CruiseListUiState(
        cruises = listOf(
            previewCruise(role = app.skipperclub.data.CruiseUserRole.Organizer),
            previewCruise(id = "c2", title = "Croatia Milebuilding"),
        ),
        hasLoadedOnce = true,
    )

    @Test
    fun cruiseListUsesFeedHeaderActionAndRendersCards() {
        var createClicked = false
        var filtersClicked = false

        compose.setContent {
            SkipperClubTheme {
                CruiseListScreenContent(
                    state = loadedState,
                    onOpenCruise = {},
                    onCreate = { createClicked = true },
                    onSearchChange = {},
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

    @Test
    fun searchIconOpensSearchBarSeparateFromFilters() {
        val searches = mutableListOf<String>()
        var filtersClicked = false

        compose.setContent {
            SkipperClubTheme {
                CruiseListScreenContent(
                    state = loadedState,
                    onOpenCruise = {},
                    onCreate = {},
                    onSearchChange = { searches += it },
                    onOpenFilters = { filtersClicked = true },
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        // Tapping the search icon reveals a dedicated search field, not the filter sheet.
        compose.onNodeWithTag("cruises_search").performClick()
        compose.onNodeWithTag("cruises_search_field").assertExists()
        compose.onNodeWithTag("cruises_search_field").performTextInput("Holandia")
        assertTrue(searches.isNotEmpty())
        assertEquals("Holandia", searches.last())
        assertTrue("search must not open the filter sheet", !filtersClicked)

        // Back closes the search bar and restores the header.
        compose.onNodeWithTag("cruises_search_back").performClick()
        compose.onNodeWithTag("cruises_search_field").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.nav_cruises)).assertExists()
    }

    @Test
    fun activeSearchShowsChipThatClears() {
        val searches = mutableListOf<String>()

        compose.setContent {
            SkipperClubTheme {
                CruiseListScreenContent(
                    state = loadedState.copy(filters = CruiseFilters(search = "Holandia")),
                    onOpenCruise = {},
                    onCreate = {},
                    onSearchChange = { searches += it },
                    onOpenFilters = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithTag("cruises_search_chip").assertExists()
        compose.onNodeWithTag("cruises_search_chip").performClick()
        compose.onNodeWithTag("cruises_search_field").assertExists()
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
