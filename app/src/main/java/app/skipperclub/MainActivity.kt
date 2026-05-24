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
import app.skipperclub.ui.auth.PasswordScreen
import app.skipperclub.ui.main.MainScreen
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.turnstile.TurnstileDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingInvitationCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionStore.initialize(applicationContext)
        consumeInvitationLink(intent)
        setContent {
            SkipperClubTheme {
                SkipperClubApp(pendingInvitationCode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeInvitationLink(intent)
    }

    private fun consumeInvitationLink(intent: Intent?) {
        intent.extractInvitationCode()?.let { pendingInvitationCode.value = it }
    }
}

private fun Intent?.extractInvitationCode(): String? {
    if (this == null) return null
    if (action != Intent.ACTION_VIEW) return null
    val uri = data ?: return null
    if (!uri.path.orEmpty().endsWith("/register")) return null
    return uri.getQueryParameter("invitation")?.takeIf { it.isNotBlank() }
}

private sealed class PendingAuthAction(val turnstileAction: String) {
    data class SendOtp(val email: String) : PendingAuthAction("otp")
    data class VerifyOtp(val email: String, val code: String) : PendingAuthAction("otp-verify")
    data class LoginPassword(val email: String, val password: String) : PendingAuthAction("login")
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

    val activeSession = session
    if (activeSession != null) {
        MainScreen(
            user = activeSession.user,
            onLogout = {
                scope.launch {
                    SessionStore.clear()
                }
            },
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
                    onForgotPassword = { /* TODO */ },
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

    pendingActionState.value?.let { action ->
        TurnstileDialog(
            action = action.turnstileAction,
            onSuccess = { token ->
                scope.launch {
                    try {
                        isBusyState.value = true
                        when (action) {
                            is PendingAuthAction.SendOtp -> {
                                AuthApi.sendOtp(action.email, token)
                                authDestinationState.value = AuthDestination.OtpVerify(action.email)
                            }

                            is PendingAuthAction.VerifyOtp -> {
                                val session = AuthApi.verifyOtp(action.email, action.code, token)
                                SessionStore.save(session)
                            }

                            is PendingAuthAction.LoginPassword -> {
                                val session = AuthApi.login(action.email, action.password, token)
                                SessionStore.save(session)
                            }

                            is PendingAuthAction.RegisterByInvitation -> {
                                val session = AuthApi.registerByInvitation(
                                    code = action.code,
                                    name = action.name,
                                    email = action.email,
                                    password = action.password,
                                    turnstileToken = token,
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
