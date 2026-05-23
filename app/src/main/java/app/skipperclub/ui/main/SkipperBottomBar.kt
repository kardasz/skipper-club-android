package app.skipperclub.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.skipperclub.data.SessionUser
import app.skipperclub.ui.theme.SkipperClubTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun SkipperBottomBar(
    selected: MainDestination,
    user: SessionUser,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
            ),
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainDestination.entries.forEach { destination ->
                        SkipperNavItem(
                            destination = destination,
                            selected = destination == selected,
                            user = user.takeIf { destination == MainDestination.MENU },
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun SkipperNavItem(
    destination: MainDestination,
    selected: Boolean,
    user: SessionUser?,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "Nav icon tint",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "Nav indicator color",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 48.dp else 40.dp,
        animationSpec = tween(durationMillis = 180),
        label = "Nav indicator width",
    )

    val label = stringResource(destination.labelRes)
    Column(
        modifier = modifier
            .height(64.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = true,
                onClick = { onSelect(destination) },
                role = Role.Tab,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(34.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(indicatorColor),
            contentAlignment = Alignment.Center,
        ) {
            if (user != null) {
                UserAvatar(
                    user = user,
                    selected = selected,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Icon(
                    painter = painterResource(destination.iconRes),
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(if (destination == MainDestination.MAP) 25.dp else 23.dp),
                )
            }
        }
    }
}

@Composable
fun UserAvatar(
    user: SessionUser,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backgroundColor)
            .border(1.dp, borderColor, MaterialTheme.shapes.extraLarge),
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
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun SessionUser.initials(): String {
    val nameInitials = name
        .trim()
        .split(Regex("\\s+"))
        .asSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .toList()
        .joinToString("")
    if (nameInitials.isNotBlank()) return nameInitials

    return email
        .substringBefore("@")
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "SC"
}

private val previewUser = SessionUser(
    id = "preview-user",
    email = "anna.nowak@example.com",
    name = "Anna Nowak",
    avatarUrl = "https://i.pravatar.cc/150?u=anna",
)

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun SkipperBottomBarPreviewMapSelected() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.MAP, user = previewUser, onSelect = {})
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun SkipperBottomBarPreviewPostsSelected() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.POSTS, user = previewUser, onSelect = {})
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun SkipperBottomBarPreviewPl() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.MESSAGES, user = previewUser, onSelect = {})
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SkipperBottomBarPreviewDark() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.MAP, user = previewUser, onSelect = {})
    }
}
