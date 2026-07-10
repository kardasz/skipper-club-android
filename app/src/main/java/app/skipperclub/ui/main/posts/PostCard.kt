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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipperclub.R
import app.skipperclub.data.AlertSeverity
import app.skipperclub.data.Post
import app.skipperclub.data.PostRoute
import app.skipperclub.data.PostUser
import app.skipperclub.data.ReactionType
import app.skipperclub.data.ValidityVoteType
import app.skipperclub.ui.main.alert.labelRes
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

/**
 * A feed card. Two visual identities share one entry point: [AlertPostCard] for
 * navigation alerts (a calm "notice" — severity rail, area, details tucked behind
 * "more") and [CommunityPostCard] for everything else (photo-forward, reactions).
 */
@Composable
fun PostCard(
    post: Post,
    nowMillis: Long,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    if (post.hasAlert) {
        AlertPostCard(post = post, nowMillis = nowMillis, actions = actions, modifier = modifier)
    } else {
        CommunityPostCard(post = post, nowMillis = nowMillis, actions = actions, modifier = modifier)
    }
}

// ---------------------------------------------------------------------------
// Community post
// ---------------------------------------------------------------------------

@Composable
private fun CommunityPostCard(
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
            PostAuthorHeader(post = post, nowMillis = nowMillis, actions = actions)
            val hasMedia = post.media.isNotEmpty()
            if (hasMedia) {
                Box {
                    PostMediaPager(post = post)
                    val locationName = post.location.name
                    if (!locationName.isNullOrBlank()) {
                        GlassLocationChip(
                            name = locationName,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (hasMedia) 10.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CommunityContentSection(post = post, nowMillis = nowMillis, showInlineLocation = !hasMedia)
                if (post.content.text.isNotBlank()) {
                    PostDescription(description = post.content.text)
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
private fun PostAuthorHeader(
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
        val author = post.user ?: PostUser(id = "", name = stringResource(R.string.post_system_author_name))
        PostUserAvatar(user = author, modifier = Modifier.size(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = author.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTime(post.publishedAt, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PostHeaderMenu(post = post, actions = actions)
    }
}

@Composable
private fun CommunityContentSection(
    post: Post,
    nowMillis: Long,
    showInlineLocation: Boolean,
) {
    if (post.expiresAt != null) {
        PostExpiryBadge(expiresAt = post.expiresAt, nowMillis = nowMillis)
    }
    val locationName = post.location.name
    if (showInlineLocation && !locationName.isNullOrBlank()) {
        InlineLocation(name = locationName)
    }
    post.route?.takeIf { it.stops.isNotEmpty() }?.let { route ->
        PostRouteInfo(route = route)
    }
}

@Composable
private fun InlineLocation(name: String) {
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
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Location chip floated over the media, so the story starts right under the photo. */
@Composable
private fun GlassLocationChip(name: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.52f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Alert post
// ---------------------------------------------------------------------------

@Composable
private fun AlertPostCard(
    post: Post,
    nowMillis: Long,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    val alert = post.alert ?: return
    var expanded by remember(post.id) { mutableStateOf(false) }
    val railColor = severityRailColor(alert.severity)
    val railWidthPx = with(LocalDensity.current) { 4.dp.toPx() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                // Severity rail drawn over the left edge (incl. media) — a "notice" marker.
                .drawWithContent {
                    drawContent()
                    drawRect(color = railColor, topLeft = Offset.Zero, size = Size(railWidthPx, size.height))
                },
        ) {
            AlertHeader(post = post, actions = actions)
            if (post.media.isNotEmpty()) {
                Box(modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)) {
                    PostMediaPager(post = post)
                }
            }
            if (post.content.text.isNotBlank()) {
                Text(
                    text = post.content.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                )
            }
            MoreLessToggle(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
            if (expanded) {
                AlertDetails(
                    post = post,
                    nowMillis = nowMillis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                )
                if (showsValidityVote(post)) {
                    AlertValidityVote(
                        post = post,
                        actions = actions,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
            }
            AlertActionsRow(
                post = post,
                actions = actions,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun AlertHeader(
    post: Post,
    actions: PostCardActions,
) {
    val alert = post.alert ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(severityContainerColor(alert.severity)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = severityIcon(alert.severity),
                contentDescription = null,
                tint = severityContentColor(alert.severity),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(alert.category.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val area = post.location.name
            if (!area.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = area,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PostHeaderMenu(post = post, actions = actions)
    }
}

@Composable
private fun MoreLessToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("alert_more_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(if (expanded) R.string.post_show_less else R.string.post_show_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Details tucked behind "more": when it was reported, when it expires, and the source. */
@Composable
private fun AlertDetails(
    post: Post,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val reportedLabel = stringResource(R.string.post_alert_reported)
    val expiresLabel = stringResource(R.string.post_alert_expires_label)
    val sourceLabel = stringResource(R.string.post_alert_source)
    val expiresValue = expiryText(post.expiresAt, nowMillis)
    val sourceValue = post.alert?.source?.takeIf { it.isNotBlank() }
    val rows = buildList {
        add(reportedLabel to relativeTime(post.publishedAt, nowMillis))
        if (expiresValue != null) add(expiresLabel to expiresValue)
        if (sourceValue != null) add(sourceLabel to sourceValue)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        Column {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
                if (index < rows.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }
}

/** Community confidence vote. No counts — just the choice; the user's vote highlights. */
@Composable
private fun AlertValidityVote(
    post: Post,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    val votes = post.validityVotes ?: return
    val canVote = votes.userVote == null && post.permissions.validityVote
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.post_validity_question),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteButton(
                label = stringResource(R.string.post_validity_confirm),
                icon = Icons.Filled.CheckCircle,
                selected = votes.userVote == ValidityVoteType.Confirm,
                enabled = canVote,
                selectedColor = MaterialTheme.extended.success,
                onClick = { actions.onCastVote(post, ValidityVoteType.Confirm) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("alert_vote_confirm"),
            )
            VoteButton(
                label = stringResource(R.string.post_validity_invalid),
                icon = Icons.Outlined.Cancel,
                selected = votes.userVote == ValidityVoteType.ReportInvalid,
                enabled = canVote,
                selectedColor = MaterialTheme.colorScheme.error,
                onClick = { actions.onCastVote(post, ValidityVoteType.ReportInvalid) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("alert_vote_invalid"),
            )
        }
    }
}

@Composable
private fun VoteButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) selectedColor else MaterialTheme.colorScheme.outline
    val content = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectedColor.copy(alpha = 0.1f) else Color.Transparent,
        contentColor = content,
        border = BorderStroke(if (selected) 1.4.dp else 1.dp, border),
        modifier = modifier
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Alerts engage through comments + save + the confidence vote — never "likes". */
@Composable
private fun AlertActionsRow(
    post: Post,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

private fun showsValidityVote(post: Post): Boolean {
    val votes = post.validityVotes ?: return false
    return post.permissions.validityVote || votes.userVote != null
}

@Composable
private fun severityRailColor(severity: AlertSeverity?): Color = when (severity) {
    AlertSeverity.Critical -> MaterialTheme.colorScheme.error
    AlertSeverity.Warning -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun severityContainerColor(severity: AlertSeverity?) = when (severity) {
    AlertSeverity.Critical -> MaterialTheme.colorScheme.errorContainer
    AlertSeverity.Warning -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun severityContentColor(severity: AlertSeverity?) = when (severity) {
    AlertSeverity.Critical -> MaterialTheme.colorScheme.onErrorContainer
    AlertSeverity.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onPrimaryContainer
}

private fun severityIcon(severity: AlertSeverity?): ImageVector = when (severity) {
    AlertSeverity.Critical -> Icons.Filled.Warning
    AlertSeverity.Warning -> Icons.Outlined.WarningAmber
    else -> Icons.Outlined.Info
}

// ---------------------------------------------------------------------------
// Shared header menu, avatar, media, actions
// ---------------------------------------------------------------------------

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
private fun PostExpiryBadge(expiresAt: String, nowMillis: Long) {
    val remaining = PostExpiry.remainingMillis(expiresAt, nowMillis) ?: return
    val phase = PostExpiry.phase(remaining)
    val text = expiryPhaseText(phase)
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
private fun expiryPhaseText(phase: PostExpiry.Phase): String = when (phase) {
    is PostExpiry.Phase.Expired -> stringResource(R.string.post_expired)
    is PostExpiry.Phase.Minutes -> stringResource(R.string.post_expires_in_minutes, phase.minutes)
    is PostExpiry.Phase.Hours -> stringResource(R.string.post_expires_in_hours, phase.hours, phase.minutes)
    is PostExpiry.Phase.Days -> stringResource(R.string.post_expires_in_days, phase.days, phase.hours)
}

@Composable
private fun expiryText(expiresAt: String?, nowMillis: Long): String? {
    val remaining = PostExpiry.remainingMillis(expiresAt, nowMillis) ?: return null
    return expiryPhaseText(PostExpiry.phase(remaining))
}

@Composable
private fun PostRouteInfo(route: PostRoute) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = pluralStringResource(R.plurals.post_route_stops, route.stops.size, route.stops.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            route.durationDays?.let { days ->
                Text(
                    text = pluralStringResource(R.plurals.post_route_duration_days, days, days),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            route.lengthNm?.let { length ->
                Text(
                    text = stringResource(R.string.post_route_length_nm, formatLengthNm(length)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = route.stops.joinToString(separator = " → ") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
