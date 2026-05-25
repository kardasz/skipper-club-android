package app.skipperclub.ui.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme
import app.skipperclub.ui.theme.extended
import kotlinx.coroutines.delay

enum class InAppNotificationType {
    Info,
    Success,
    Error,
}

internal data class InAppNotification(
    val id: Long,
    val message: String,
    val type: InAppNotificationType,
)

@Stable
class InAppNotificationHostState {
    private var nextId by mutableLongStateOf(0L)

    internal var current by mutableStateOf<InAppNotification?>(null)
        private set

    fun show(
        message: String,
        type: InAppNotificationType = InAppNotificationType.Info,
    ) {
        current = InAppNotification(
            id = ++nextId,
            message = message,
            type = type,
        )
    }

    fun dismiss(id: Long? = null) {
        if (id == null || current?.id == id) {
            current = null
        }
    }
}

@Composable
fun rememberInAppNotificationHostState(): InAppNotificationHostState =
    remember { InAppNotificationHostState() }

@Composable
fun InAppNotificationHost(
    hostState: InAppNotificationHostState,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 4_000L,
) {
    val notification = hostState.current

    LaunchedEffect(notification?.id) {
        val currentNotification = notification ?: return@LaunchedEffect
        delay(autoDismissMillis)
        hostState.dismiss(currentNotification.id)
    }

    AnimatedVisibility(
        visible = notification != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        notification?.let {
            InAppNotificationSurface(
                notification = it,
                onDismiss = { hostState.dismiss(it.id) },
            )
        }
    }
}

@Composable
private fun InAppNotificationSurface(
    notification: InAppNotification,
    onDismiss: () -> Unit,
) {
    val colors = notification.type.colors()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.container,
        contentColor = colors.content,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = notification.type.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.notification_dismiss),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun InAppNotificationType.colors(): NotificationColors = when (this) {
    InAppNotificationType.Info -> NotificationColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    InAppNotificationType.Success -> NotificationColors(
        container = MaterialTheme.extended.successContainer,
        content = MaterialTheme.extended.onSuccessContainer,
    )

    InAppNotificationType.Error -> NotificationColors(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
}

private val InAppNotificationType.icon: ImageVector
    get() = when (this) {
        InAppNotificationType.Info -> Icons.Filled.Info
        InAppNotificationType.Success -> Icons.Filled.CheckCircle
        InAppNotificationType.Error -> Icons.Filled.ErrorOutline
    }

private data class NotificationColors(
    val container: Color,
    val content: Color,
)

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun InAppNotificationHostPreview() {
    SkipperClubTheme {
        val hostState = rememberInAppNotificationHostState()
        LaunchedEffect(Unit) {
            hostState.show(
                message = "Zameldowano w: Boleslawa Orlinskiego 3.",
                type = InAppNotificationType.Success,
            )
        }
        InAppNotificationHost(hostState = hostState)
    }
}
