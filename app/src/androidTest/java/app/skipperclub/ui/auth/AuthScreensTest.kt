package app.skipperclub.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loginScreenEnablesActionsAndTrimsSubmittedEmail() {
        var passwordEmail: String? = null
        var codeEmail: String? = null

        compose.setContent {
            SkipperClubTheme {
                LoginScreen(
                    onContinueWithPassword = { passwordEmail = it },
                    onSendLoginCode = { codeEmail = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.login_continue_with_password)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.login_send_login_code)).assertIsNotEnabled()

        compose.onNodeWithTag("login-email").performTextInput("  sailor@example.com  ")

        compose.onNodeWithText(text(R.string.login_continue_with_password)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.login_send_login_code)).assertIsEnabled()

        compose.onNodeWithText(text(R.string.login_continue_with_password)).performClick()
        compose.onNodeWithText(text(R.string.login_send_login_code)).performClick()

        assertEquals("sailor@example.com", passwordEmail)
        assertEquals("sailor@example.com", codeEmail)
    }

    @Test
    fun passwordScreenRequiresMinimumLengthAndSubmitsEmailWithPassword() {
        var submittedEmail: String? = null
        var submittedPassword: String? = null
        var forgotPasswordEmail: String? = null

        compose.setContent {
            SkipperClubTheme {
                PasswordScreen(
                    email = "sailor@example.com",
                    onBack = {},
                    onContinue = { email, password ->
                        submittedEmail = email
                        submittedPassword = password
                    },
                    onForgotPassword = { forgotPasswordEmail = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.password_continue)).assertIsNotEnabled()
        compose.onNodeWithTag("password-field").performTextInput("secret1")
        compose.onNodeWithText(text(R.string.password_continue)).assertIsNotEnabled()
        compose.onNodeWithTag("password-field").performTextInput("2")
        compose.onNodeWithText(text(R.string.password_continue)).assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.password_forgot)).performClick()

        assertEquals("sailor@example.com", submittedEmail)
        assertEquals("secret12", submittedPassword)
        assertEquals("sailor@example.com", forgotPasswordEmail)
    }

    @Test
    fun otpScreenSanitizesInputAndAutoVerifiesCompleteCode() {
        var verifiedEmail: String? = null
        var verifiedCode: String? = null

        compose.setContent {
            SkipperClubTheme {
                OtpVerifyScreen(
                    email = "sailor@example.com",
                    onBack = {},
                    onVerify = { email, code ->
                        verifiedEmail = email
                        verifiedCode = code
                    },
                    onResend = {},
                )
            }
        }

        compose.onNodeWithContentDescription(text(R.string.otp_input_content_description))
            .performTextInput("12a345678")

        compose.waitUntil(timeoutMillis = 2_000) { verifiedCode != null }

        assertEquals("sailor@example.com", verifiedEmail)
        assertEquals("123456", verifiedCode)
    }

    @Test
    fun passwordResetScreenShowsMismatchErrorAndBlocksSubmit() {
        var submitted = false

        compose.setContent {
            SkipperClubTheme {
                PasswordResetScreen(
                    email = "sailor@example.com",
                    code = "123456",
                    onBack = {},
                    onSubmit = { _, _, _ -> submitted = true },
                )
            }
        }

        compose.onNodeWithTag("password-reset-new-password").performTextInput("secret12")
        compose.onNodeWithTag("password-reset-repeat-password").performTextInput("secret13")
        compose.onNodeWithText(text(R.string.password_reset_submit)).assertIsEnabled().performClick()

        compose.onNodeWithText(text(R.string.password_reset_error_password_mismatch)).assertExists()
        assertFalse(submitted)
    }

    @Test
    fun passwordResetScreenSubmitsMatchingPasswords() {
        var submittedEmail: String? = null
        var submittedCode: String? = null
        var submittedPassword: String? = null

        compose.setContent {
            SkipperClubTheme {
                PasswordResetScreen(
                    email = "sailor@example.com",
                    code = "123456",
                    onBack = {},
                    onSubmit = { email, code, password ->
                        submittedEmail = email
                        submittedCode = code
                        submittedPassword = password
                    },
                )
            }
        }

        compose.onNodeWithTag("password-reset-new-password").performTextInput("secret12")
        compose.onNodeWithTag("password-reset-repeat-password").performTextInput("secret12")
        compose.onNodeWithText(text(R.string.password_reset_submit)).assertIsEnabled().performClick()

        assertEquals("sailor@example.com", submittedEmail)
        assertEquals("123456", submittedCode)
        assertEquals("secret12", submittedPassword)
        compose.onAllNodesWithText(text(R.string.password_reset_error_password_mismatch)).assertCountEquals(0)
    }

    @Test
    fun invitationRegisterScreenSanitizesInputAndSubmitsCompletedForm() {
        var submittedCode: String? = null
        var submittedName: String? = null
        var submittedEmail: String? = null
        var submittedPassword: String? = null

        compose.setContent {
            SkipperClubTheme {
                InvitationRegisterScreen(
                    initialCode = " ab-12_ć345678 ",
                    onBack = {},
                    onSubmit = { code, name, email, password ->
                        submittedCode = code
                        submittedName = name
                        submittedEmail = email
                        submittedPassword = password
                    },
                )
            }
        }

        compose.onNodeWithText(text(R.string.invitation_submit)).assertIsNotEnabled()
        compose.onNodeWithTag("invitation-name").performScrollTo().performTextInput("  Anna Nowak  ")
        compose.onNodeWithTag("invitation-email").performScrollTo().performTextInput("  anna@example.com  ")
        compose.onNodeWithTag("invitation-password").performScrollTo().performTextInput("secret12")
        compose.onNodeWithText(text(R.string.invitation_submit)).performScrollTo().assertIsEnabled().performClick()

        assertEquals("AB123456", submittedCode)
        assertEquals("Anna Nowak", submittedName)
        assertEquals("anna@example.com", submittedEmail)
        assertEquals("secret12", submittedPassword)
    }

    @Test
    fun invitationRegisterScreenClearsErrorsAndTogglesPasswordVisibility() {
        var clearCount = 0

        compose.setContent {
            SkipperClubTheme {
                InvitationRegisterScreen(
                    onBack = {},
                    onSubmit = { _, _, _, _ -> },
                    codeErrorMessage = text(R.string.auth_error_invalid_invitation_code),
                    nameErrorMessage = text(R.string.auth_error_invalid_name),
                    emailErrorMessage = text(R.string.auth_error_invalid_email),
                    passwordErrorMessage = text(R.string.auth_error_invalid_password),
                    formErrorMessage = text(R.string.auth_error_captcha),
                    onClearError = { clearCount++ },
                )
            }
        }

        compose.onNodeWithText(text(R.string.auth_error_captcha)).assertExists()
        compose.onNodeWithText(text(R.string.auth_error_invalid_invitation_code)).assertExists()
        compose.onNodeWithTag("invitation-code").performScrollTo().performTextInput("abc12345")
        compose.onNodeWithTag("invitation-password").performScrollTo().performTextInput("secret12")
        compose.onNodeWithContentDescription(text(R.string.invitation_password_show))
            .performScrollTo()
            .performClick()
        compose.onNodeWithContentDescription(text(R.string.invitation_password_hide)).assertExists()

        assertEquals(2, clearCount)
    }

    @Test
    fun passwordResetRequestScreenSubmitsTrimmedEmailAndShowsSentState() {
        var submittedEmail: String? = null

        compose.setContent {
            SkipperClubTheme {
                PasswordResetRequestScreen(
                    onBack = {},
                    onSubmit = { submittedEmail = it },
                    onSignIn = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.password_reset_request_submit)).assertIsNotEnabled()
        compose.onNodeWithTag("password-reset-request-email").performTextInput("  sailor@example.com  ")
        compose.onNodeWithText(text(R.string.password_reset_request_submit)).assertIsEnabled().performClick()
        assertEquals("sailor@example.com", submittedEmail)
    }

    @Test
    fun passwordResetRequestScreenSentStateReturnsToSignIn() {
        var signInEmail: String? = null

        compose.setContent {
            SkipperClubTheme {
                PasswordResetRequestScreen(
                    initialEmail = "sailor@example.com",
                    linkSent = true,
                    onBack = {},
                    onSubmit = {},
                    onSignIn = { signInEmail = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.password_reset_request_sent_body)).assertExists()
        compose.onNodeWithText(text(R.string.password_reset_back_to_sign_in)).performClick()
        assertEquals("sailor@example.com", signInEmail)
    }

    @Test
    fun passwordResetCompleteScreenReturnsToSignInWithEmail() {
        var signInEmail: String? = null

        compose.setContent {
            SkipperClubTheme {
                PasswordResetCompleteScreen(
                    email = "sailor@example.com",
                    onSignIn = { signInEmail = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.password_reset_complete_body)).assertExists()
        compose.onNodeWithText(text(R.string.password_reset_back_to_sign_in)).performClick()

        assertEquals("sailor@example.com", signInEmail)
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
