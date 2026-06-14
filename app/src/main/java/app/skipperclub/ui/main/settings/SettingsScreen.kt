package app.skipperclub.ui.main.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.NotificationSettings
import app.skipperclub.data.SessionStore
import app.skipperclub.data.SettingsError
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/**
 * Full-screen "Settings" view launched from the main menu. Today it surfaces the
 * notification channel preferences; more sections are expected to land here.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        SettingsController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.settings_error_network)
    val errorAuth = stringResource(R.string.settings_error_auth)
    val errorGeneric = stringResource(R.string.settings_error_generic)

    fun errorMessage(error: Exception): String = when (error) {
        is SettingsError.Network -> errorNetwork
        is SettingsError.AuthenticationRequired -> errorAuth
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is SettingsEvent.LoadFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                is SettingsEvent.SaveFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                SettingsEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreenContent(
                state = state,
                onClose = onClose,
                onRetry = controller::retry,
                onEmailEnabled = controller::setEmailEnabled,
                onPushEnabled = controller::setPushEnabled,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
internal fun SettingsScreenContent(
    state: SettingsUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onEmailEnabled: (Boolean) -> Unit,
    onPushEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("settings_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            state.notifications == null && state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.notifications == null && state.loadFailed -> SettingsMessage(
                title = stringResource(R.string.settings_load_failed),
                actionLabel = stringResource(R.string.settings_retry),
                onAction = onRetry,
            )

            state.notifications != null -> SettingsList(
                notifications = state.notifications,
                onEmailEnabled = onEmailEnabled,
                onPushEnabled = onPushEnabled,
            )
        }
    }
}

@Composable
private fun SettingsList(
    notifications: NotificationSettings,
    onEmailEnabled: (Boolean) -> Unit,
    onPushEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
            .testTag("settings_list"),
    ) {
        SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_email_title),
                description = stringResource(R.string.settings_notifications_email_description),
                checked = notifications.emailNotificationsEnabled,
                onCheckedChange = onEmailEnabled,
                testTag = "settings_email_switch",
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_push_title),
                description = stringResource(R.string.settings_notifications_push_description),
                checked = notifications.pushNotificationsEnabled,
                onCheckedChange = onPushEnabled,
                testTag = "settings_push_switch",
            )
            Text(
                text = stringResource(R.string.settings_notifications_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
    content()
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun SettingsMessage(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

// --- Previews ---

private val previewSettings = NotificationSettings(
    emailNotificationsEnabled = true,
    pushNotificationsEnabled = false,
)

@Preview(showBackground = true, widthDp = 380, heightDp = 740, locale = "en")
@Composable
private fun SettingsPreview() {
    SkipperClubTheme {
        SettingsScreenContent(
            state = SettingsUiState(notifications = previewSettings, hasLoadedOnce = true),
            onClose = {},
            onRetry = {},
            onEmailEnabled = {},
            onPushEnabled = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 740,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsPreviewDark() {
    SkipperClubTheme {
        SettingsScreenContent(
            state = SettingsUiState(notifications = previewSettings, hasLoadedOnce = true),
            onClose = {},
            onRetry = {},
            onEmailEnabled = {},
            onPushEnabled = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 740, locale = "pl")
@Composable
private fun SettingsPreviewPl() {
    SkipperClubTheme {
        SettingsScreenContent(
            state = SettingsUiState(
                notifications = NotificationSettings(
                    emailNotificationsEnabled = true,
                    pushNotificationsEnabled = true,
                ),
                hasLoadedOnce = true,
            ),
            onClose = {},
            onRetry = {},
            onEmailEnabled = {},
            onPushEnabled = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 740, locale = "pl")
@Composable
private fun SettingsFailedPreviewPl() {
    SkipperClubTheme {
        SettingsScreenContent(
            state = SettingsUiState(loadFailed = true, hasLoadedOnce = true),
            onClose = {},
            onRetry = {},
            onEmailEnabled = {},
            onPushEnabled = {},
        )
    }
}
