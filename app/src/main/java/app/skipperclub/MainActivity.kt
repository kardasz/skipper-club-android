package app.skipperclub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import app.skipperclub.data.AuthApi
import app.skipperclub.data.AuthError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.auth.AuthDestination
import app.skipperclub.ui.auth.AuthDestinationSaver
import app.skipperclub.ui.auth.InvitationRegisterScreen
import app.skipperclub.ui.auth.LoginScreen
import app.skipperclub.ui.auth.OtpVerifyScreen
import app.skipperclub.ui.auth.PasswordResetCompleteScreen
import app.skipperclub.ui.auth.PasswordResetRequestScreen
import app.skipperclub.ui.auth.PasswordResetScreen
import app.skipperclub.ui.auth.PasswordScreen
import app.skipperclub.ui.main.MainScreen
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.turnstile.TurnstileDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingInvitationCode = mutableStateOf<String?>(null)
    private val pendingPasswordResetLink = mutableStateOf<PasswordResetDeepLink?>(null)
    private val pendingCruiseReviews = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionStore.initialize(applicationContext)
        consumeDeepLink(intent)
        setContent {
            SkipperClubTheme {
                SkipperClubApp(pendingInvitationCode, pendingPasswordResetLink, pendingCruiseReviews)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        intent.extractInvitationCode()?.let { pendingInvitationCode.value = it }
        intent.extractPasswordResetLink()?.let { pendingPasswordResetLink.value = it }
        intent.extractCruiseReviewsId()?.let { pendingCruiseReviews.value = it }
    }
}

data class PasswordResetDeepLink(
    val email: String,
    val code: String,
)

private fun Intent?.extractInvitationCode(): String? {
    if (this == null) return null
    if (action != Intent.ACTION_VIEW) return null
    val uri = data ?: return null
    if (!uri.path.orEmpty().endsWith("/register")) return null
    return uri.getQueryParameter("invitation")?.takeIf { it.isNotBlank() }
}

private fun Intent?.extractPasswordResetLink(): PasswordResetDeepLink? {
    if (this == null) return null
    if (action != Intent.ACTION_VIEW) return null
    val uri = data ?: return null
    if (!uri.path.orEmpty().endsWith("/password-reset")) return null
    val email = uri.getQueryParameter("email")?.takeIf { it.isNotBlank() } ?: return null
    val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return null
    return PasswordResetDeepLink(email = email, code = code)
}

/**
 * Extracts the cruise id from a `…/cruises/{cruiseId}/reviews` deep link (the link
 * embedded in post-cruise review emails). Returns null for any other path.
 */
internal fun Intent?.extractCruiseReviewsId(): String? {
    if (this == null) return null
    if (action != Intent.ACTION_VIEW) return null
    val uri = data ?: return null
    return parseCruiseReviewsId(uri.pathSegments.orEmpty())
}

/**
 * Pure path matcher for `…/cruises/{cruiseId}/reviews` links. Locale prefixes
 * (`/en`, `/pl`) are ignored — only the `cruises/{id}/reviews` shape matters.
 */
internal fun parseCruiseReviewsId(segments: List<String>): String? {
    val cruisesIndex = segments.indexOf("cruises")
    if (cruisesIndex < 0 || cruisesIndex + 2 >= segments.size) return null
    if (segments[cruisesIndex + 2] != "reviews") return null
    return segments[cruisesIndex + 1].takeIf { it.isNotBlank() }
}

private sealed class PendingAuthAction(val turnstileAction: String?) {
    data class SendOtp(val email: String) : PendingAuthAction("otp")
    data class VerifyOtp(val email: String, val code: String) : PendingAuthAction("otp-verify")
    data class LoginPassword(val email: String, val password: String) : PendingAuthAction("login")
    data class RequestPasswordReset(val email: String) : PendingAuthAction("password-reset-request")
    data class ResetPassword(val email: String, val code: String, val password: String) : PendingAuthAction(null)
    data class RegisterByInvitation(
        val code: String,
        val name: String,
        val email: String,
        val password: String,
    ) : PendingAuthAction("invitation-register")
}

private enum class AuthErrorTarget {
    Form,
    LoginEmail,
    Password,
    PasswordResetRequestEmail,
    PasswordResetCode,
    PasswordResetPassword,
    OtpCode,
    InvitationCode,
    InvitationName,
    InvitationEmail,
    InvitationPassword,
}

private data class AuthUiError(
    val target: AuthErrorTarget,
    val message: String,
)

@PreviewScreenSizes
@Composable
fun SkipperClubApp(
    invitationCodeFromDeepLink: MutableState<String?> = remember { mutableStateOf(null) },
    passwordResetLinkFromDeepLink: MutableState<PasswordResetDeepLink?> = remember {
        mutableStateOf(null)
    },
    cruiseReviewsLinkFromDeepLink: MutableState<String?> = remember { mutableStateOf(null) },
) {
    val session by SessionStore.session.collectAsState()
    val isRestoringSession by SessionStore.isRestoring.collectAsState()
    val isAuthenticated = session != null
    val authDestinationState = rememberSaveable(stateSaver = AuthDestinationSaver) {
        mutableStateOf(AuthDestination.Login)
    }
    val pendingActionState = remember { mutableStateOf<PendingAuthAction?>(null) }
    val isBusyState = remember { mutableStateOf(value = false) }
    val authUiErrorState = remember { mutableStateOf<AuthUiError?>(null) }
    val scope = rememberCoroutineScope()

    val networkErrorMessage = stringResource(R.string.auth_error_network)
    val captchaErrorMessage = stringResource(R.string.auth_error_captcha)
    val rateLimitErrorMessage = stringResource(R.string.auth_error_rate_limit)
    val invalidOtpErrorMessage = stringResource(R.string.auth_error_invalid_otp)
    val invalidPasswordResetCodeErrorMessage = stringResource(R.string.auth_error_invalid_password_reset_code)
    val invalidCredentialsErrorMessage = stringResource(R.string.auth_error_invalid_credentials)
    val validationErrorMessage = stringResource(R.string.auth_error_invalid_email)
    val invalidPasswordErrorMessage = stringResource(R.string.auth_error_invalid_password)
    val invalidNameErrorMessage = stringResource(R.string.auth_error_invalid_name)
    val invalidInvitationCodeErrorMessage = stringResource(R.string.auth_error_invalid_invitation_code)
    val invalidOtpFormatErrorMessage = stringResource(R.string.auth_error_invalid_otp_format)
    val invalidInvitationMessage = stringResource(R.string.auth_error_invalid_invitation)
    val invitationEmailMismatchMessage = stringResource(R.string.auth_error_invitation_email_mismatch)
    val emailAlreadyRegisteredMessage = stringResource(R.string.auth_error_email_already_registered)
    val genericErrorMessage = stringResource(R.string.auth_error_generic)

    if (isRestoringSession) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    fun showError(error: AuthError, action: PendingAuthAction) {
        fun AuthError.Validation.hasField(name: String): Boolean = fields.contains(name)

        val message = when (error) {
            is AuthError.Network -> networkErrorMessage
            is AuthError.CaptchaFailed -> captchaErrorMessage
            is AuthError.RateLimited -> rateLimitErrorMessage
            is AuthError.InvalidOtpCode -> invalidOtpErrorMessage
            is AuthError.InvalidPasswordResetCode -> invalidPasswordResetCodeErrorMessage
            is AuthError.InvalidCredentials -> invalidCredentialsErrorMessage
            is AuthError.InvalidRefreshToken,
            is AuthError.RefreshTokenExpired,
            is AuthError.AuthenticationRequired,
            -> {
                genericErrorMessage
            }
            is AuthError.Validation -> when (action) {
                is PendingAuthAction.VerifyOtp -> {
                    if (error.hasField("code")) invalidOtpFormatErrorMessage else validationErrorMessage
                }
                is PendingAuthAction.RegisterByInvitation -> when {
                    error.hasField("code") -> invalidInvitationCodeErrorMessage
                    error.hasField("name") -> invalidNameErrorMessage
                    error.hasField("email") -> validationErrorMessage
                    error.hasField("password") -> invalidPasswordErrorMessage
                    else -> validationErrorMessage
                }
                is PendingAuthAction.LoginPassword -> {
                    if (error.hasField("password")) invalidPasswordErrorMessage else validationErrorMessage
                }
                is PendingAuthAction.RequestPasswordReset -> validationErrorMessage
                is PendingAuthAction.ResetPassword -> when {
                    error.hasField("code") -> invalidPasswordResetCodeErrorMessage
                    error.hasField("password") -> invalidPasswordErrorMessage
                    else -> validationErrorMessage
                }
                is PendingAuthAction.SendOtp -> validationErrorMessage
            }
            is AuthError.InvalidInvitation -> invalidInvitationMessage
            is AuthError.InvitationEmailMismatch -> invitationEmailMismatchMessage
            is AuthError.EmailAlreadyRegistered -> emailAlreadyRegisteredMessage
            is AuthError.Server -> genericErrorMessage
        }
        val target = when (action) {
            is PendingAuthAction.SendOtp -> when (error) {
                is AuthError.Validation -> AuthErrorTarget.LoginEmail
                else -> AuthErrorTarget.Form
            }
            is PendingAuthAction.VerifyOtp -> when (error) {
                is AuthError.InvalidOtpCode,
                is AuthError.Validation,
                -> AuthErrorTarget.OtpCode

                else -> AuthErrorTarget.Form
            }
            is PendingAuthAction.LoginPassword -> when (error) {
                is AuthError.InvalidCredentials -> AuthErrorTarget.Password
                is AuthError.Validation -> {
                    if (error.fields.contains("password")) AuthErrorTarget.Password else AuthErrorTarget.Form
                }
                else -> AuthErrorTarget.Form
            }
            is PendingAuthAction.RequestPasswordReset -> when (error) {
                is AuthError.Validation -> AuthErrorTarget.PasswordResetRequestEmail
                else -> AuthErrorTarget.Form
            }
            is PendingAuthAction.ResetPassword -> when (error) {
                is AuthError.InvalidPasswordResetCode -> AuthErrorTarget.PasswordResetCode
                is AuthError.Validation -> when {
                    error.fields.contains("code") -> AuthErrorTarget.PasswordResetCode
                    error.fields.contains("password") -> AuthErrorTarget.PasswordResetPassword
                    error.fields.contains("email") -> AuthErrorTarget.Form
                    else -> AuthErrorTarget.Form
                }
                else -> AuthErrorTarget.Form
            }
            is PendingAuthAction.RegisterByInvitation -> when (error) {
                is AuthError.InvalidInvitation -> AuthErrorTarget.InvitationCode
                is AuthError.InvitationEmailMismatch,
                is AuthError.EmailAlreadyRegistered,
                -> AuthErrorTarget.InvitationEmail
                is AuthError.Validation -> when {
                    error.fields.contains("code") -> AuthErrorTarget.InvitationCode
                    error.fields.contains("name") -> AuthErrorTarget.InvitationName
                    error.fields.contains("email") -> AuthErrorTarget.InvitationEmail
                    error.fields.contains("password") -> AuthErrorTarget.InvitationPassword
                    else -> AuthErrorTarget.Form
                }
                else -> AuthErrorTarget.Form
            }
        }
        authUiErrorState.value = AuthUiError(target, message)
    }

    val pendingCode = invitationCodeFromDeepLink.value
    LaunchedEffect(pendingCode, isAuthenticated) {
        if ((pendingCode != null) && !isAuthenticated) {
            authDestinationState.value = AuthDestination.JoinByInvitation(pendingCode)
            invitationCodeFromDeepLink.value = null
        }
    }

    val pendingPasswordResetLink = passwordResetLinkFromDeepLink.value
    LaunchedEffect(pendingPasswordResetLink) {
        if (pendingPasswordResetLink != null) {
            authUiErrorState.value = null
            if (isAuthenticated) {
                SessionStore.clear()
            }
            authDestinationState.value = AuthDestination.PasswordReset(
                email = pendingPasswordResetLink.email,
                code = pendingPasswordResetLink.code,
            )
            passwordResetLinkFromDeepLink.value = null
        }
    }

    val activeSession = session
    if (activeSession != null) {
        MainScreen(
            user = activeSession.user,
            onLogout = {
                scope.launch {
                    SessionStore.clear()
                }
            },
            pendingReviewsCruiseId = cruiseReviewsLinkFromDeepLink.value,
            onPendingReviewsConsumed = { cruiseReviewsLinkFromDeepLink.value = null },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val destination = authDestinationState.value) {
            AuthDestination.Login -> LoginScreen(
                onContinueWithPassword = { email ->
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.Password(email)
                },
                onSendLoginCode = { email ->
                    authUiErrorState.value = null
                    pendingActionState.value = PendingAuthAction.SendOtp(email)
                },
                onJoinByInvitation = {
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.JoinByInvitation()
                },
                emailErrorMessage = authUiErrorState.value
                    ?.takeIf { it.target == AuthErrorTarget.LoginEmail }
                    ?.message,
                formErrorMessage = authUiErrorState.value
                    ?.takeIf { it.target == AuthErrorTarget.Form }
                    ?.message,
            ) {
                authUiErrorState.value = null
            }

            is AuthDestination.Password -> {
                BackHandler {
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.Login
                }
                PasswordScreen(
                    email = destination.email,
                    onBack = {
                        authUiErrorState.value = null
                        authDestinationState.value = AuthDestination.Login
                    },
                    onContinue = { email, password ->
                        authUiErrorState.value = null
                        pendingActionState.value = PendingAuthAction.LoginPassword(email, password)
                    },
                    onForgotPassword = { email ->
                        authUiErrorState.value = null
                        authDestinationState.value = AuthDestination.PasswordResetRequest(email = email)
                    },
                    passwordErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.Password }
                        ?.message,
                    formErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.Form }
                        ?.message,
                ) {
                    authUiErrorState.value = null
                }
            }

            is AuthDestination.PasswordResetRequest -> {
                BackHandler {
                    authUiErrorState.value = null
                    authDestinationState.value = if (destination.email.isNotBlank()) {
                        AuthDestination.Password(destination.email)
                    } else {
                        AuthDestination.Login
                    }
                }
                PasswordResetRequestScreen(
                    initialEmail = destination.email,
                    linkSent = destination.linkSent,
                    onBack = {
                        authUiErrorState.value = null
                        authDestinationState.value = if (destination.email.isNotBlank()) {
                            AuthDestination.Password(destination.email)
                        } else {
                            AuthDestination.Login
                        }
                    },
                    onSubmit = { email ->
                        authUiErrorState.value = null
                        pendingActionState.value = PendingAuthAction.RequestPasswordReset(email)
                    },
                    onSignIn = { email ->
                        authUiErrorState.value = null
                        authDestinationState.value = if (email.isNotBlank()) {
                            AuthDestination.Password(email)
                        } else {
                            AuthDestination.Login
                        }
                    },
                    emailErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.PasswordResetRequestEmail }
                        ?.message,
                    formErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.Form }
                        ?.message,
                ) {
                    authUiErrorState.value = null
                }
            }

            is AuthDestination.PasswordReset -> {
                BackHandler {
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.Login
                }
                PasswordResetScreen(
                    email = destination.email,
                    code = destination.code,
                    onBack = {
                        authUiErrorState.value = null
                        authDestinationState.value = AuthDestination.Login
                    },
                    onSubmit = { email, code, password ->
                        authUiErrorState.value = null
                        pendingActionState.value = PendingAuthAction.ResetPassword(email, code, password)
                    },
                    passwordErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.PasswordResetPassword }
                        ?.message,
                    codeErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.PasswordResetCode }
                        ?.message,
                    formErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.Form }
                        ?.message,
                ) {
                    authUiErrorState.value = null
                }
            }

            is AuthDestination.PasswordResetComplete -> {
                BackHandler {
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.Password(destination.email)
                }
                PasswordResetCompleteScreen(
                    email = destination.email,
                    onSignIn = { email ->
                        authUiErrorState.value = null
                        authDestinationState.value = AuthDestination.Password(email)
                    },
                )
            }

            is AuthDestination.OtpVerify -> {
                BackHandler {
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.Login
                }
                OtpVerifyScreen(
                    email = destination.email,
                    onBack = {
                        authUiErrorState.value = null
                        authDestinationState.value = AuthDestination.Login
                    },
                    onVerify = { email, code ->
                        authUiErrorState.value = null
                        pendingActionState.value = PendingAuthAction.VerifyOtp(email, code)
                    },
                    onResend = { email ->
                        authUiErrorState.value = null
                        pendingActionState.value = PendingAuthAction.SendOtp(email)
                    },
                    codeErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.OtpCode }
                        ?.message,
                    formErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.Form }
                        ?.message,
                ) {
                    authUiErrorState.value = null
                }
            }

            is AuthDestination.JoinByInvitation -> {
                BackHandler {
                    authUiErrorState.value = null
                    authDestinationState.value = AuthDestination.Login
                }
                InvitationRegisterScreen(
                    initialCode = destination.code,
                    onBack = {
                        authUiErrorState.value = null
                        authDestinationState.value = AuthDestination.Login
                    },
                    onSubmit = { code, name, email, password ->
                        authUiErrorState.value = null
                        pendingActionState.value = PendingAuthAction.RegisterByInvitation(
                            code = code,
                            name = name,
                            email = email,
                            password = password,
                        )
                    },
                    codeErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.InvitationCode }
                        ?.message,
                    nameErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.InvitationName }
                        ?.message,
                    emailErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.InvitationEmail }
                        ?.message,
                    passwordErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.InvitationPassword }
                        ?.message,
                    formErrorMessage = authUiErrorState.value
                        ?.takeIf { it.target == AuthErrorTarget.Form }
                        ?.message,
                ) {
                    authUiErrorState.value = null
                }
            }
        }

        if (isBusyState.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

    }

    suspend fun executeAuthAction(action: PendingAuthAction, turnstileToken: String? = null) {
        try {
            isBusyState.value = true
            when (action) {
                is PendingAuthAction.SendOtp -> {
                    AuthApi.sendOtp(action.email, checkNotNull(turnstileToken))
                    authDestinationState.value = AuthDestination.OtpVerify(action.email)
                }

                is PendingAuthAction.VerifyOtp -> {
                    val session = AuthApi.verifyOtp(action.email, action.code, checkNotNull(turnstileToken))
                    SessionStore.save(session)
                }

                is PendingAuthAction.LoginPassword -> {
                    val session = AuthApi.login(action.email, action.password, checkNotNull(turnstileToken))
                    SessionStore.save(session)
                }

                is PendingAuthAction.RequestPasswordReset -> {
                    AuthApi.requestPasswordReset(action.email, checkNotNull(turnstileToken))
                    authDestinationState.value = AuthDestination.PasswordResetRequest(
                        email = action.email,
                        linkSent = true,
                    )
                }

                is PendingAuthAction.ResetPassword -> {
                    AuthApi.resetPassword(action.email, action.code, action.password)
                    SessionStore.clear()
                    authDestinationState.value = AuthDestination.PasswordResetComplete(action.email)
                }

                is PendingAuthAction.RegisterByInvitation -> {
                    val session = AuthApi.registerByInvitation(
                        code = action.code,
                        name = action.name,
                        email = action.email,
                        password = action.password,
                        turnstileToken = checkNotNull(turnstileToken),
                    )
                    SessionStore.save(session)
                }
            }
        } catch (e: AuthError) {
            showError(e, action)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            authUiErrorState.value = AuthUiError(AuthErrorTarget.Form, genericErrorMessage)
        } finally {
            pendingActionState.value = null
            isBusyState.value = false
        }
    }

    val pendingAction = pendingActionState.value
    LaunchedEffect(pendingAction) {
        if ((pendingAction != null) && (pendingAction.turnstileAction == null)) {
            executeAuthAction(pendingAction)
        }
    }

    pendingActionState.value?.let { action ->
        val turnstileAction = action.turnstileAction ?: return@let
        TurnstileDialog(
            action = turnstileAction,
            onSuccess = { token ->
                scope.launch {
                    executeAuthAction(action, token)
                }
            },
            onError = {
                authUiErrorState.value = AuthUiError(AuthErrorTarget.Form, captchaErrorMessage)
                pendingActionState.value = null
            },
        ) {
            pendingActionState.value = null
        }
    }
}
