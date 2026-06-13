package app.skipperclub.ui.main.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.skipperclub.R
import app.skipperclub.data.ProfileError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState

/**
 * Full-screen, read-only public profile of another member (`GET /v1/users/{userId}`),
 * opened from the friends list. Reuses [ProfileScreenContent] (no email, no edit).
 */
@Composable
fun PublicProfileScreen(
    userId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope, userId) {
        PublicProfileController(
            scope = scope,
            userId = userId,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.profile_error_network)
    val errorAuth = stringResource(R.string.profile_error_auth)
    val errorGeneric = stringResource(R.string.profile_error_generic)

    fun errorMessage(error: Exception): String = when (error) {
        is ProfileError.Network -> errorNetwork
        is ProfileError.AuthenticationRequired -> errorAuth
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is ProfileEvent.LoadFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                ProfileEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("public_profile_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProfileScreenContent(
                state = state,
                title = state.profile?.name ?: stringResource(R.string.public_profile_title),
                onClose = onClose,
                onRefresh = controller::refresh,
                onRetry = controller::refresh,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
