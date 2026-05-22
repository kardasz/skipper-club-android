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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.turnstile.TurnstileDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingInvitationCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

@PreviewScreenSizes
@Composable
fun SkipperClubApp(
    invitationCodeFromDeepLink: MutableState<String?> = remember { mutableStateOf(null) },
) {
    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
    var authDestination by rememberSaveable(stateSaver = AuthDestinationSaver) {
        mutableStateOf<AuthDestination>(AuthDestination.Login)
    }
    var pendingAction by remember { mutableStateOf<PendingAuthAction?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val networkErrorMessage = stringResource(R.string.auth_error_network)
    val captchaErrorMessage = stringResource(R.string.auth_error_captcha)
    val rateLimitErrorMessage = stringResource(R.string.auth_error_rate_limit)
    val invalidOtpErrorMessage = stringResource(R.string.auth_error_invalid_otp)
    val invalidCredentialsErrorMessage = stringResource(R.string.auth_error_invalid_credentials)
    val validationErrorMessage = stringResource(R.string.auth_error_invalid_email)
    val invalidInvitationMessage = stringResource(R.string.auth_error_invalid_invitation)
    val invitationEmailMismatchMessage = stringResource(R.string.auth_error_invitation_email_mismatch)
    val emailAlreadyRegisteredMessage = stringResource(R.string.auth_error_email_already_registered)
    val genericErrorMessage = stringResource(R.string.auth_error_generic)

    fun showError(error: AuthError) {
        val message = when (error) {
            is AuthError.Network -> networkErrorMessage
            is AuthError.CaptchaFailed -> captchaErrorMessage
            is AuthError.RateLimited -> rateLimitErrorMessage
            is AuthError.InvalidOtpCode -> invalidOtpErrorMessage
            is AuthError.InvalidCredentials -> invalidCredentialsErrorMessage
            is AuthError.Validation -> validationErrorMessage
            is AuthError.InvalidInvitation -> invalidInvitationMessage
            is AuthError.InvitationEmailMismatch -> invitationEmailMismatchMessage
            is AuthError.EmailAlreadyRegistered -> emailAlreadyRegisteredMessage
            is AuthError.Server -> genericErrorMessage
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val pendingCode = invitationCodeFromDeepLink.value
    LaunchedEffect(pendingCode, isAuthenticated) {
        if (pendingCode != null && !isAuthenticated) {
            authDestination = AuthDestination.JoinByInvitation(pendingCode)
            invitationCodeFromDeepLink.value = null
        }
    }

    if (isAuthenticated) {
        MainScaffold()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val destination = authDestination) {
            AuthDestination.Login -> LoginScreen(
                onContinueWithPassword = { email ->
                    authDestination = AuthDestination.Password(email)
                },
                onSendLoginCode = { email ->
                    pendingAction = PendingAuthAction.SendOtp(email)
                },
                onJoinByInvitation = {
                    authDestination = AuthDestination.JoinByInvitation()
                },
            )

            is AuthDestination.Password -> {
                BackHandler { authDestination = AuthDestination.Login }
                PasswordScreen(
                    email = destination.email,
                    onBack = { authDestination = AuthDestination.Login },
                    onContinue = { email, password ->
                        pendingAction = PendingAuthAction.LoginPassword(email, password)
                    },
                    onForgotPassword = { /* TODO */ },
                )
            }

            is AuthDestination.OtpVerify -> {
                BackHandler { authDestination = AuthDestination.Login }
                OtpVerifyScreen(
                    email = destination.email,
                    onBack = { authDestination = AuthDestination.Login },
                    onVerify = { email, code ->
                        pendingAction = PendingAuthAction.VerifyOtp(email, code)
                    },
                    onResend = { email ->
                        pendingAction = PendingAuthAction.SendOtp(email)
                    },
                )
            }

            is AuthDestination.JoinByInvitation -> {
                BackHandler { authDestination = AuthDestination.Login }
                InvitationRegisterScreen(
                    initialCode = destination.code,
                    onBack = { authDestination = AuthDestination.Login },
                    onSubmit = { code, name, email, password ->
                        pendingAction = PendingAuthAction.RegisterByInvitation(
                            code = code,
                            name = name,
                            email = email,
                            password = password,
                        )
                    },
                )
            }
        }

        if (isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    pendingAction?.let { action ->
        TurnstileDialog(
            action = action.turnstileAction,
            onSuccess = { token ->
                pendingAction = null
                isBusy = true
                scope.launch {
                    try {
                        when (action) {
                            is PendingAuthAction.SendOtp -> {
                                AuthApi.sendOtp(action.email, token)
                                authDestination = AuthDestination.OtpVerify(action.email)
                            }
                            is PendingAuthAction.VerifyOtp -> {
                                val session = AuthApi.verifyOtp(action.email, action.code, token)
                                SessionStore.save(session)
                                isAuthenticated = true
                            }
                            is PendingAuthAction.LoginPassword -> {
                                val session = AuthApi.login(action.email, action.password, token)
                                SessionStore.save(session)
                                isAuthenticated = true
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
                                isAuthenticated = true
                            }
                        }
                    } catch (e: AuthError) {
                        showError(e)
                    } finally {
                        isBusy = false
                    }
                }
            },
            onError = {
                pendingAction = null
                scope.launch { snackbarHostState.showSnackbar(captchaErrorMessage) }
            },
            onDismiss = { pendingAction = null },
        )
    }
}

@Composable
private fun MainScaffold() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SkipperClubTheme {
        Greeting("Android")
    }
}
