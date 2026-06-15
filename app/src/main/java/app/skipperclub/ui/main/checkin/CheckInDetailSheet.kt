package app.skipperclub.ui.main.checkin

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.ChatsError
import app.skipperclub.data.MapUserProjection
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** Identifies the tapped check-in marker and carries its inlined presence data. */
data class CheckInDetailUiState(
    val user: MapUserProjection,
    val checkedInAt: String,
    val locationName: String?,
    val fallbackName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInDetailSheet(
    state: CheckInDetailUiState,
    onDismiss: () -> Unit,
    onViewProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope, state.user.id) {
        CheckInMessageController(
            scope = scope,
            userId = state.user.id,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val chatState by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.messages_error_network)
    val errorAuth = stringResource(R.string.messages_error_auth)
    val errorGeneric = stringResource(R.string.check_in_detail_message_failed)

    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is CheckInMessageEvent.OpenChat -> onOpenChat(event.chatId)

                CheckInMessageEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)

                is CheckInMessageEvent.Failed -> notificationHostState.show(
                    when (event.error) {
                        is ChatsError.Network -> errorNetwork
                        is ChatsError.AuthenticationRequired -> errorAuth
                        else -> errorGeneric
                    },
                    InAppNotificationType.Error,
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CheckInDetailContent(
                state = state,
                isOpeningChat = chatState.isOpeningChat,
                onWriteMessage = { controller.openChat() },
                onViewProfile = onViewProfile,
            )
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
internal fun CheckInDetailContent(
    state: CheckInDetailUiState,
    isOpeningChat: Boolean,
    onWriteMessage: () -> Unit,
    onViewProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = state.user.displayName.ifBlank { state.fallbackName }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckInAvatar(user = state.user, fallbackName = state.fallbackName)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = checkInRelativeStatus(state.checkedInAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val location = state.locationName?.takeIf { it.isNotBlank() }
                if (location != null) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onWriteMessage,
            enabled = !isOpeningChat,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("check_in_detail_write_message"),
        ) {
            if (isOpeningChat) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.check_in_detail_write_message))
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onViewProfile(state.user.id) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("check_in_detail_view_profile"),
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.check_in_detail_view_profile))
        }
    }
}

@Composable
private fun CheckInAvatar(user: MapUserProjection, fallbackName: String) {
    val colors = MaterialTheme.colorScheme
    val avatarName = user.displayName.ifBlank { fallbackName }
    val avatarUrl = user.avatarUrl?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    val painter = if (avatarUrl != null && !LocalInspectionMode.current) {
        rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .allowHardware(false)
                .build(),
            contentScale = ContentScale.Crop,
        )
    } else {
        null
    }
    val isLoaded = painter?.let { (it.state.collectAsState().value) is AsyncImagePainter.State.Success } == true

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null && isLoaded) {
            Image(
                painter = painter,
                contentDescription = avatarName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = avatarName.initials(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun checkInRelativeStatus(checkedInAt: String): String {
    var nowMillis by remember(checkedInAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(checkedInAt) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val elapsedMinutes = remember(checkedInAt, nowMillis) {
        runCatching {
            Duration.between(Instant.parse(checkedInAt), Instant.ofEpochMilli(nowMillis))
                .toMinutes()
                .coerceAtLeast(0)
        }.getOrNull()
    } ?: return stringResource(R.string.map_check_in_bubble_recent)

    return when {
        elapsedMinutes < 1 -> stringResource(R.string.map_check_in_bubble_now)
        elapsedMinutes < 60 -> pluralStringResource(
            R.plurals.map_check_in_bubble_minutes,
            elapsedMinutes.toInt(),
            elapsedMinutes,
        )

        elapsedMinutes < 1_440 -> {
            val hours = (elapsedMinutes / 60).coerceAtLeast(1)
            pluralStringResource(R.plurals.map_check_in_bubble_hours, hours.toInt(), hours)
        }

        else -> {
            val days = (elapsedMinutes / 1_440).coerceAtLeast(1)
            pluralStringResource(R.plurals.map_check_in_bubble_days, days.toInt(), days)
        }
    }
}

private fun String.initials(): String {
    val initials = trim()
        .split(Regex("\\s+"))
        .asSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .toList()
        .joinToString("")

    return initials.ifBlank { "SC" }
}

// --- Previews ---

@Preview(showBackground = true, locale = "en")
@Composable
private fun CheckInDetailPreviewEn() {
    SkipperClubTheme {
        CheckInDetailContent(
            state = CheckInDetailUiState(
                user = MapUserProjection(id = "u1", displayName = "Krzysztof Kardasz", avatarUrl = null),
                checkedInAt = Instant.now().minusSeconds(900).toString(),
                locationName = "Marina Kornati",
                fallbackName = "Krzysztof",
            ),
            isOpeningChat = false,
            onWriteMessage = {},
            onViewProfile = {},
        )
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun CheckInDetailPreviewPl() {
    SkipperClubTheme {
        CheckInDetailContent(
            state = CheckInDetailUiState(
                user = MapUserProjection(id = "u1", displayName = "Anna Nowak", avatarUrl = null),
                checkedInAt = Instant.now().minusSeconds(7200).toString(),
                locationName = "Górki Zachodnie",
                fallbackName = "Anna",
            ),
            isOpeningChat = false,
            onWriteMessage = {},
            onViewProfile = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CheckInDetailPreviewDark() {
    SkipperClubTheme {
        CheckInDetailContent(
            state = CheckInDetailUiState(
                user = MapUserProjection(id = "u1", displayName = "Krzysztof", avatarUrl = null),
                checkedInAt = Instant.now().minusSeconds(30).toString(),
                locationName = null,
                fallbackName = "Krzysztof",
            ),
            isOpeningChat = true,
            onWriteMessage = {},
            onViewProfile = {},
        )
    }
}
