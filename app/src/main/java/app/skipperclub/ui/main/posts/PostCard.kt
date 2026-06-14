package app.skipperclub.ui.main.posts

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipperclub.R
import app.skipperclub.data.Post
import app.skipperclub.data.PostUser
import app.skipperclub.data.ReactionType
import app.skipperclub.data.ValidityVoteType
import app.skipperclub.ui.theme.extended
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.time.Instant
import java.time.format.DateTimeParseException

/** Callbacks emitted by a feed card; orchestrated by [PostsScreen]. */
data class PostCardActions(
    val onToggleReaction: (Post, ReactionType) -> Unit,
    val onOpenReactionPicker: (Post) -> Unit,
    val onOpenComments: (Post) -> Unit,
    val onToggleBookmark: (Post) -> Unit,
    val onCastVote: (Post, ValidityVoteType) -> Unit,
    val onArchive: (Post) -> Unit,
    val onResolve: (Post) -> Unit,
    val onDeleteRequest: (Post) -> Unit,
    val onEditRequest: (Post) -> Unit = {},
    val onReportRequest: (Post) -> Unit = {},
)

@Composable
fun PostCard(
    post: Post,
    nowMillis: Long,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(bottom = 14.dp)) {
            PostHeader(post = post, nowMillis = nowMillis, actions = actions)
            if (post.media.isNotEmpty()) {
                PostMediaPager(post = post)
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (post.media.isNotEmpty()) 10.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PostTypeContent(post = post, nowMillis = nowMillis, actions = actions)
                if (!post.description.isNullOrBlank()) {
                    PostDescription(description = post.description)
                }
                if (post.taggedUsers.isNotEmpty()) {
                    PostTaggedUsers(taggedUsers = post.taggedUsers)
                }
                PostActionsRow(post = post, actions = actions)
            }
        }
    }
}

@Composable
private fun PostHeader(
    post: Post,
    nowMillis: Long,
    actions: PostCardActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PostUserAvatar(user = post.user, modifier = Modifier.size(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.user.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTime(post.createdAt, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PostTypeBadge(post = post)
        PostHeaderMenu(post = post, actions = actions)
    }
}

@Composable
private fun PostTypeBadge(post: Post) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = post.type.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(post.type.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PostHeaderMenu(
    post: Post,
    actions: PostCardActions,
) {
    val permissions = post.permissions
    val hasMenuItems = permissions.edit || permissions.archive || permissions.resolve ||
        permissions.delete || permissions.report
    if (!hasMenuItems) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("post_menu"),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.post_more_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (permissions.edit) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.post_action_edit)) },
                    onClick = {
                        expanded = false
                        actions.onEditRequest(post)
                    },
                )
            }
            if (permissions.archive) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.post_action_archive)) },
                    onClick = {
                        expanded = false
                        actions.onArchive(post)
                    },
                )
            }
            if (permissions.resolve) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.post_action_resolve)) },
                    onClick = {
                        expanded = false
                        actions.onResolve(post)
                    },
                )
            }
            if (permissions.report) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.post_action_report)) },
                    onClick = {
                        expanded = false
                        actions.onReportRequest(post)
                    },
                )
            }
            if (permissions.delete) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.post_action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        expanded = false
                        actions.onDeleteRequest(post)
                    },
                )
            }
        }
    }
}

@Composable
fun PostUserAvatar(
    user: PostUser,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (user.avatarUrl.isNullOrBlank()) {
            Text(
                text = user.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user.avatarUrl)
                    .crossfade(enable = true)
                    .build(),
                contentDescription = user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PostMediaPager(post: Post) {
    val pagerState = rememberPagerState(pageCount = { post.media.size })
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }
    Box {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) { page ->
            val media = post.media[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (media.isVideo) {
                            Modifier
                                .clickable { playingVideoUrl = media.url }
                                .testTag("post_video_play")
                        } else {
                            Modifier
                        },
                    ),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.url)
                        .crossfade(enable = true)
                        .build(),
                    contentDescription = stringResource(R.string.post_media_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (media.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = stringResource(R.string.post_video_play),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp),
                    )
                }
            }
        }
        if (post.media.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(post.media.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.56f)),
                    )
                }
            }
        }
    }

    playingVideoUrl?.let { url ->
        VideoPlayerDialog(url = url, onDismiss = { playingVideoUrl = null })
    }
}

@Composable
private fun PostTypeContent(
    post: Post,
    nowMillis: Long,
    actions: PostCardActions,
) {
    if (post.type.isTimeSensitive) {
        PostExpiryBadge(post = post, nowMillis = nowMillis)
    }
    if (!post.locationName.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = post.locationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (post.stops.isNotEmpty()) {
        PostRouteInfo(post = post)
    }
    if (post.type.isVotable && post.validityVotes != null) {
        PostValidityRow(post = post, actions = actions)
    }
}

@Composable
private fun PostExpiryBadge(post: Post, nowMillis: Long) {
    val remaining = PostExpiry.remainingMillis(post.expiresAt, nowMillis) ?: return
    val phase = PostExpiry.phase(remaining)
    val text = when (phase) {
        is PostExpiry.Phase.Expired -> stringResource(R.string.post_expired)
        is PostExpiry.Phase.Minutes -> stringResource(R.string.post_expires_in_minutes, phase.minutes)
        is PostExpiry.Phase.Hours ->
            stringResource(R.string.post_expires_in_hours, phase.hours, phase.minutes)
        is PostExpiry.Phase.Days ->
            stringResource(R.string.post_expires_in_days, phase.days, phase.hours)
    }
    val color = when {
        phase is PostExpiry.Phase.Expired -> MaterialTheme.colorScheme.error
        PostExpiry.urgency(remaining) == PostExpiry.Urgency.Critical -> MaterialTheme.colorScheme.error
        PostExpiry.urgency(remaining) == PostExpiry.Urgency.Warning -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun PostRouteInfo(post: Post) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = pluralStringResource(R.plurals.post_route_stops, post.stops.size, post.stops.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            post.durationDays?.let { days ->
                Text(
                    text = pluralStringResource(R.plurals.post_route_duration_days, days, days),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            post.lengthNm?.let { length ->
                Text(
                    text = stringResource(R.string.post_route_length_nm, formatLengthNm(length)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = post.stops.joinToString(separator = " → ") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PostValidityRow(
    post: Post,
    actions: PostCardActions,
) {
    val votes = post.validityVotes ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (votes.userVote == null && post.permissions.validityVote) {
            Text(
                text = stringResource(R.string.post_validity_question),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        AssistChip(
            onClick = { actions.onCastVote(post, ValidityVoteType.Confirm) },
            enabled = votes.userVote == null && post.permissions.validityVote,
            label = {
                Text("${stringResource(R.string.post_validity_confirm)} · ${votes.confirmCount}")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                    tint = if (votes.userVote == ValidityVoteType.Confirm) {
                        MaterialTheme.extended.success
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
        )
        AssistChip(
            onClick = { actions.onCastVote(post, ValidityVoteType.ReportInvalid) },
            enabled = votes.userVote == null && post.permissions.validityVote,
            label = {
                Text("${stringResource(R.string.post_validity_invalid)} · ${votes.invalidCount}")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                    tint = if (votes.userVote == ValidityVoteType.ReportInvalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
        )
    }
}

@Composable
private fun PostTaggedUsers(taggedUsers: List<PostUser>) {
    val names = taggedUsers.joinToString(", ") { it.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.People,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.post_tagged_with, names),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PostDescription(description: String) {
    val hashtagColor = MaterialTheme.colorScheme.primary
    val annotated = remember(description, hashtagColor) {
        buildAnnotatedString {
            val regex = Regex("#[\\p{L}0-9_]+")
            var lastIndex = 0
            regex.findAll(description).forEach { match ->
                append(description.substring(lastIndex, match.range.first))
                withStyle(SpanStyle(color = hashtagColor, fontWeight = FontWeight.SemiBold)) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            append(description.substring(lastIndex))
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 20.sp,
    )
}

@Composable
private fun PostActionsRow(
    post: Post,
    actions: PostCardActions,
) {
    val topReactions = post.reactions.byType.entries
        .sortedByDescending { it.value }
        .take(3)
    val totalReactions = post.reactions.byType.values.sum()
    val userReaction = post.reactions.userReactions.firstOrNull()
    val reactionSummary = topReactions
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "") { it.key.emoji }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (post.permissions.react || totalReactions > 0) {
            PostActionPill(
                label = reactionSummary ?: "♡",
                count = totalReactions.takeIf { it > 0 },
                selected = userReaction != null,
                enabled = post.permissions.react,
                onClick = {
                    if (userReaction != null) {
                        actions.onToggleReaction(post, userReaction)
                    } else {
                        actions.onOpenReactionPicker(post)
                    }
                },
                modifier = Modifier.widthIn(min = 58.dp, max = 116.dp),
            )
        }
        if (post.permissions.react) {
            PostIconAction(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.AddReaction,
                        contentDescription = stringResource(R.string.post_add_reaction),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { actions.onOpenReactionPicker(post) },
            )
        }
        PostIconAction(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.post_comments),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            count = post.commentsCount.takeIf { it > 0 },
            onClick = { actions.onOpenComments(post) },
        )
        Spacer(modifier = Modifier.weight(1f))
        if (post.permissions.bookmark) {
            PostIconAction(
                icon = {
                    Icon(
                        imageVector = if (post.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(
                            if (post.bookmarked) R.string.post_action_unbookmark else R.string.post_action_bookmark,
                        ),
                        tint = if (post.bookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                selected = post.bookmarked,
                onClick = { actions.onToggleBookmark(post) },
            )
        }
    }
}

@Composable
private fun PostActionPill(
    label: String,
    count: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.heightIn(min = 42.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, fontSize = 16.sp, maxLines = 1)
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PostIconAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    count: Int? = null,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .padding(horizontal = if (count == null) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ReactionChip(
    reaction: ReactionType,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = reaction.emoji, fontSize = 14.sp)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

internal fun relativeTime(createdAtIso: String, nowMillis: Long): String =
    try {
        DateUtils.getRelativeTimeSpanString(
            Instant.parse(createdAtIso).toEpochMilli(),
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } catch (_: DateTimeParseException) {
        ""
    }

internal fun formatLengthNm(length: Double): String =
    if (length == length.toLong().toDouble()) {
        length.toLong().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f", length)
    }
