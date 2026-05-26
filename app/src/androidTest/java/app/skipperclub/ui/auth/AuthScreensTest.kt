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

    private fun text(id: Int): String = compose.activity.getString(id)
}
