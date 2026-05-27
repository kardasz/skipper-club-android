package app.skipperclub.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.SessionUser
import app.skipperclub.ui.main.checkin.CheckInOverlay
import app.skipperclub.ui.main.checkin.CheckInUiState
import app.skipperclub.ui.main.checkin.LocationLabel
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mainScreenNavigatesBetweenTabsAndLogsOutFromMenuSheet() {
        var logoutCount = 0

        compose.setContent {
            SkipperClubTheme {
                MainScreen(
                    user = user,
                    onLogout = { logoutCount++ },
                )
            }
        }

        compose.onNodeWithTag("main-nav-posts").performClick()
        compose.onNodeWithText(text(R.string.nav_posts)).assertExists()
        compose.onNodeWithText(text(R.string.main_placeholder_subtitle)).assertExists()

        compose.onNodeWithTag("main-nav-cruises").performClick()
        compose.onNodeWithText(text(R.string.nav_cruises)).assertExists()

        compose.onNodeWithTag("main-nav-messages").performClick()
        compose.onNodeWithText(text(R.string.nav_messages)).assertExists()

        compose.onNodeWithTag("main-nav-menu").performClick()
        compose.onNodeWithText(user.name).assertExists()
        compose.onNodeWithText(user.email).assertExists()
        compose.onNodeWithText(text(R.string.menu_my_profile)).assertExists()
        compose.onNodeWithText(text(R.string.menu_logout)).performClick()

        assertEquals(1, logoutCount)
    }

    @Test
    fun checkInOverlayActiveStateConfirmsAndCancels() {
        var confirmCount = 0
        var cancelCount = 0

        compose.setContent {
            SkipperClubTheme {
                CheckInOverlay(
                    state = CheckInUiState.Active(
                        locationLabel = LocationLabel(
                            placeName = "Marina Gdansk",
                            addressLine = "Szafarnia 6",
                        ),
                        isResolvingName = false,
                        isSubmitting = false,
                    ),
                    onStart = {},
                    onConfirm = { confirmCount++ },
                    onCancel = { cancelCount++ },
                    onPermissionDenied = {},
                    bottomInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(text(R.string.map_check_in_confirm)).assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.map_check_in_cancel)).assertIsEnabled().performClick()

        assertEquals(1, confirmCount)
        assertEquals(1, cancelCount)
    }

    @Test
    fun checkInOverlayLocatingStateDisablesPrimaryAction() {
        compose.setContent {
            SkipperClubTheme {
                CheckInOverlay(
                    state = CheckInUiState.Locating,
                    onStart = {},
                    onConfirm = {},
                    onCancel = {},
                    onPermissionDenied = {},
                    bottomInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(text(R.string.map_check_in)).assertIsNotEnabled()
    }

    @Test
    fun checkInOverlaySubmittingStateDisablesActions() {
        compose.setContent {
            SkipperClubTheme {
                CheckInOverlay(
                    state = CheckInUiState.Active(
                        locationLabel = LocationLabel(addressLine = "Selected location"),
                        isResolvingName = true,
                        isSubmitting = true,
                    ),
                    onStart = {},
                    onConfirm = {},
                    onCancel = {},
                    onPermissionDenied = {},
                    bottomInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(text(R.string.map_check_in_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.map_check_in_cancel)).assertIsNotEnabled()
    }

    private fun text(id: Int): String = compose.activity.getString(id)

    private companion object {
        val user = SessionUser(
            id = "user-1",
            email = "anna@example.com",
            name = "Anna Nowak",
            avatarUrl = null,
        )
    }
}
