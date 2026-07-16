package app.skipperclub.ui.main.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.data.ChatUser
import app.skipperclub.ui.theme.extended
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Circular avatar for a chat participant: photo when available, initials otherwise. */
@Composable
internal fun ChatAvatar(
    user: ChatUser,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!user.avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user.avatarUrl)
                    .crossfade(enable = true)
                    .build(),
                contentDescription = user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = user.initials(),
                style = textStyle,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Chat-list avatar: a single circle for 1:1 chats, two overlapping circles for
 * chats with several other participants. [isOnline] renders a small dot (only
 * meaningful for a single participant — group presence isn't tracked per-chat).
 */
@Composable
internal fun ChatListAvatar(
    participants: List<ChatUser>,
    size: Dp,
    modifier: Modifier = Modifier,
    isOnline: Boolean = false,
) {
    when {
        participants.size >= 2 -> {
            val small = size * 0.68f
            Box(modifier = modifier.size(size)) {
                ChatAvatar(
                    user = participants[1],
                    modifier = Modifier
                        .size(small)
                        .align(Alignment.TopEnd),
                    textStyle = MaterialTheme.typography.labelSmall,
                )
                ChatAvatar(
                    user = participants[0],
                    modifier = Modifier
                        .size(small)
                        .align(Alignment.BottomStart)
                        .offset(x = (-1).dp, y = 1.dp),
                    textStyle = MaterialTheme.typography.labelSmall,
                )
            }
        }

        participants.size == 1 -> Box(modifier = modifier.size(size)) {
            ChatAvatar(
                user = participants.first(),
                modifier = Modifier.fillMaxSize(),
            )
            if (isOnline) {
                PresenceDot(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    size = (size.value * 0.32f).dp,
                )
            }
        }

        else -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
    }
}

/** Small green ring-and-dot marking a user online, meant to sit on an avatar's corner. */
@Composable
private fun PresenceDot(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.extended.success),
    )
}
