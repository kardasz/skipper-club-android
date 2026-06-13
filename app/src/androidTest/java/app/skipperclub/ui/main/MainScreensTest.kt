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
        compose.onNodeWithTag("posts_create").assertExists()
        compose.onNodeWithTag("posts_filters").assertExists()

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
    fun invitationsMenuItemHiddenForStandardUser() {
        compose.setContent {
            SkipperClubTheme {
                MainScreen(user = user, onLogout = {})
            }
        }

        compose.onNodeWithTag("main-nav-menu").performClick()
        compose.onNodeWithText(text(R.string.menu_notifications)).assertExists()
        compose.onNodeWithText(text(R.string.menu_invitations)).assertDoesNotExist()
    }

    @Test
    fun invitationsMenuItemVisibleForAdmin() {
        compose.setContent {
            SkipperClubTheme {
                MainScreen(user = admin, onLogout = {})
            }
        }

        compose.onNodeWithTag("main-nav-menu").performClick()
        compose.onNodeWithText(text(R.string.menu_invitations)).assertExists()
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
                    onConfirm = { confirmCount++ },
                    onCancel = { cancelCount++ },
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
                    onConfirm = {},
                    onCancel = {},
                    bottomInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(text(R.string.map_check_in_confirm)).assertIsNotEnabled()
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
                    onConfirm = {},
                    onCancel = {},
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
        val admin = user.copy(id = "admin-1", role = SessionUser.ROLE_ADMIN)
    }
}
