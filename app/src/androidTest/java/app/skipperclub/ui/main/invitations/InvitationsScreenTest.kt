package app.skipperclub.ui.main.invitations

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.skipperclub.R
import app.skipperclub.data.Invitation
import app.skipperclub.data.InvitationStatus
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InvitationsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pending = previewInvitation("i1", "friend@example.com", InvitationStatus.Pending)
    private val accepted = previewInvitation("i2", "jan@example.com", InvitationStatus.Accepted)

    @Test
    fun rendersInvitationRows() {
        content(InvitationsUiState(invitations = listOf(pending, accepted), hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.invitations_title)).assertExists()
        compose.onNodeWithTag("invitation_item_i1").assertExists()
        compose.onNodeWithTag("invitation_item_i2").assertExists()
    }

    @Test
    fun emptyStateShownWhenNoInvitations() {
        content(InvitationsUiState(hasLoadedOnce = true))

        compose.onNodeWithText(text(R.string.invitations_empty_title)).assertExists()
        compose.onNodeWithText(text(R.string.invitations_empty_subtitle)).assertExists()
    }

    @Test
    fun tappingRowOpensDetailSheetWithActions() {
        content(InvitationsUiState(invitations = listOf(pending), hasLoadedOnce = true))

        compose.onNodeWithTag("invitation_item_i1").performClick()

        compose.onNodeWithTag("invitation_detail_sheet").assertExists()
        compose.onNodeWithTag("invitation_resend").assertExists()
        compose.onNodeWithTag("invitation_delete").assertExists()
    }

    @Test
    fun resendFromDetailEmitsCallback() {
        var resent: Invitation? = null
        content(
            state = InvitationsUiState(invitations = listOf(pending), hasLoadedOnce = true),
            onResend = { resent = it },
        )

        compose.onNodeWithTag("invitation_item_i1").performClick()
        compose.onNodeWithTag("invitation_resend").performClick()

        assertEquals("i1", resent?.id)
    }

    @Test
    fun deleteFromDetailRequiresConfirmation() {
        var deleted: Invitation? = null
        content(
            state = InvitationsUiState(invitations = listOf(pending), hasLoadedOnce = true),
            onDelete = { deleted = it },
        )

        compose.onNodeWithTag("invitation_item_i1").performClick()
        compose.onNodeWithTag("invitation_delete").performClick()
        // Nothing deleted until the confirmation dialog is accepted.
        assertEquals(null, deleted)

        compose.onNodeWithTag("invitation_delete_confirm").performClick()
        assertEquals("i1", deleted?.id)
    }

    @Test
    fun backButtonEmitsCloseCallback() {
        var closed = 0
        content(
            state = InvitationsUiState(invitations = listOf(pending), hasLoadedOnce = true),
            onClose = { closed++ },
        )

        compose.onNodeWithTag("invitations_back").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun createFabEmitsCallback() {
        var createClicks = 0
        content(
            state = InvitationsUiState(invitations = listOf(pending), hasLoadedOnce = true),
            onCreateClick = { createClicks++ },
        )

        compose.onNodeWithTag("invitations_create_fab").performClick()
        assertEquals(1, createClicks)
    }

    @Test
    fun createDialogSubmitDisabledUntilEmailIsValid() {
        var email by mutableStateOf("")
        var submitted = 0
        compose.setContent {
            SkipperClubTheme {
                CreateInvitationDialog(
                    email = email,
                    isSending = false,
                    errorMessage = null,
                    onEmailChange = { email = it },
                    onSubmit = { submitted++ },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("invitation_create_submit").assertIsNotEnabled()

        compose.onNodeWithTag("invitation_create_email").performTextInput("friend@example.com")
        compose.onNodeWithTag("invitation_create_submit").assertIsEnabled().performClick()

        assertEquals(1, submitted)
    }

    @Test
    fun createDialogShowsInlineError() {
        compose.setContent {
            SkipperClubTheme {
                CreateInvitationDialog(
                    email = "taken@example.com",
                    isSending = false,
                    errorMessage = "Email already registered",
                    onEmailChange = {},
                    onSubmit = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Email already registered").assertExists()
    }

    private fun content(
        state: InvitationsUiState,
        onClose: () -> Unit = {},
        onCreateClick: () -> Unit = {},
        onResend: (Invitation) -> Unit = {},
        onDelete: (Invitation) -> Unit = {},
    ) {
        compose.setContent {
            SkipperClubTheme {
                InvitationsScreenContent(
                    state = state,
                    onClose = onClose,
                    onCreateClick = onCreateClick,
                    onResend = onResend,
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
