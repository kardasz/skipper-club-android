package app.skipperclub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import app.skipperclub.ui.auth.AuthDestination
import app.skipperclub.ui.auth.AuthDestinationSaver
import app.skipperclub.ui.auth.LoginScreen
import app.skipperclub.ui.auth.OtpVerifyScreen
import app.skipperclub.ui.auth.PasswordScreen
import app.skipperclub.ui.theme.SkipperClubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkipperClubTheme {
                SkipperClubApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun SkipperClubApp() {
    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
    var authDestination by rememberSaveable(stateSaver = AuthDestinationSaver) {
        mutableStateOf<AuthDestination>(AuthDestination.Login)
    }

    if (isAuthenticated) {
        MainScaffold()
        return
    }

    when (val destination = authDestination) {
        AuthDestination.Login -> LoginScreen(
            onContinueWithPassword = { email ->
                authDestination = AuthDestination.Password(email)
            },
            onSendLoginCode = { email ->
                // TODO: call POST /auth/otp before navigating
                authDestination = AuthDestination.OtpVerify(email)
            },
            onJoinByInvitation = { /* TODO: navigate to invitation flow */ },
        )

        is AuthDestination.Password -> {
            BackHandler { authDestination = AuthDestination.Login }
            PasswordScreen(
                email = destination.email,
                onBack = { authDestination = AuthDestination.Login },
                onContinue = { _, _ ->
                    // TODO: call POST /auth/login
                    isAuthenticated = true
                },
                onForgotPassword = { /* TODO */ },
            )
        }

        is AuthDestination.OtpVerify -> {
            BackHandler { authDestination = AuthDestination.Login }
            OtpVerifyScreen(
                email = destination.email,
                onBack = { authDestination = AuthDestination.Login },
                onVerify = { _, _ ->
                    // TODO: call POST /auth/otp/verify
                    isAuthenticated = true
                },
                onResend = { /* TODO: call POST /auth/otp again */ },
            )
        }
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
