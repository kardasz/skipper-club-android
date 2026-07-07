package app.skipperclub.ui.main.posts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertSeverity
import app.skipperclub.data.Post
import app.skipperclub.data.PostAlert
import app.skipperclub.data.PostContainsFilter
import app.skipperclub.data.PostContent
import app.skipperclub.data.PostContentKey
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostLocation
import app.skipperclub.data.PostMedia
import app.skipperclub.data.PostPermissions
import app.skipperclub.data.PostRoute
import app.skipperclub.data.PostRouteStop
import app.skipperclub.data.PostSource
import app.skipperclub.data.PostStatus
import app.skipperclub.data.PostUser
import app.skipperclub.data.ReactionSummary
import app.skipperclub.data.ReactionType
import app.skipperclub.data.ValidityVoteType
import app.skipperclub.data.VoteSummary
import app.skipperclub.ui.theme.SkipperClubTheme

private val previewNow = 1_765_000_000_000L // fixed clock so previews are stable

private val previewAuthor = PostUser(id = "u1", name = "Anna Nowak", avatarUrl = null)

private val previewPermissions = PostPermissions(
    comment = true,
    react = true,
    bookmark = true,
    report = true,
)

/** Plain text note — no media/route/alert; `contentKeys` is empty. */
internal val previewNotePost = Post(
    id = "p0",
    user = previewAuthor,
    contentKeys = emptySet(),
    status = PostStatus.Published,
    content = PostContent(text = "Anyone sailing out of Split this weekend? #crew #sailing"),
    location = PostLocation(name = "Split"),
    hashtags = listOf("crew", "sailing"),
    commentsCount = 1,
    reactions = ReactionSummary(total = 2, byType = mapOf(ReactionType.ThumbsUp to 2)),
    permissions = previewPermissions,
    publishedAt = "2025-12-05T19:00:00Z",
    createdAt = "2025-12-05T19:00:00Z",
    updatedAt = "2025-12-05T19:00:00Z",
)

internal val previewPhotoPost = Post(
    id = "p1",
    user = previewAuthor,
    contentKeys = setOf(PostContentKey.Media),
    status = PostStatus.Published,
    content = PostContent(text = "Beautiful sunset over the Adriatic! #sailing #sunset"),
    location = PostLocation(name = "Split", point = PostCoordinates(43.5081, 16.4402)),
    hashtags = listOf("sailing", "sunset"),
    media = listOf(
        PostMedia(id = "m1", type = "image", url = "https://example.com/sunset.jpg"),
    ),
    commentsCount = 5,
    reactions = ReactionSummary(
        total = 15,
        byType = mapOf(ReactionType.Heart to 10, ReactionType.Anchor to 5),
        userReactions = setOf(ReactionType.Heart),
    ),
    permissions = previewPermissions,
    publishedAt = "2025-12-05T18:30:00Z",
    createdAt = "2025-12-05T18:30:00Z",
    updatedAt = "2025-12-05T18:30:00Z",
)

internal val previewVideoPost = Post(
    id = "p1v",
    user = previewAuthor,
    contentKeys = setOf(PostContentKey.Media),
    status = PostStatus.Published,
    content = PostContent(text = "Downwind under spinnaker 🌊 #sailing"),
    location = PostLocation(name = "Trogir"),
    hashtags = listOf("sailing"),
    media = listOf(
        PostMedia(id = "mv1", type = "video", url = "https://example.com/downwind.mp4"),
    ),
    commentsCount = 2,
    reactions = ReactionSummary(total = 2, byType = mapOf(ReactionType.Laugh to 1, ReactionType.Heart to 1)),
    permissions = previewPermissions,
    publishedAt = "2025-12-05T18:30:00Z",
    createdAt = "2025-12-05T18:30:00Z",
    updatedAt = "2025-12-05T18:30:00Z",
)

internal val previewRoutePost = Post(
    id = "p2",
    user = PostUser(id = "u2", name = "Jan Kowalski"),
    contentKeys = setOf(PostContentKey.Route),
    status = PostStatus.Published,
    content = PostContent(
        text = "Tygodniowa trasa po Dalmacji #chorwacja",
        route = PostRoute(
            stops = listOf(
                PostRouteStop("Split", PostCoordinates(43.5081, 16.4402)),
                PostRouteStop("Hvar", PostCoordinates(43.1724, 16.4411)),
                PostRouteStop("Dubrovnik", PostCoordinates(42.6507, 18.0944)),
            ),
            durationDays = 7,
            lengthNm = 120.0,
        ),
    ),
    location = PostLocation(name = "Split", point = PostCoordinates(43.5081, 16.4402)),
    commentsCount = 2,
    reactions = ReactionSummary(total = 3, byType = mapOf(ReactionType.Sailboat to 3)),
    permissions = previewPermissions,
    publishedAt = "2025-12-04T10:00:00Z",
    createdAt = "2025-12-04T10:00:00Z",
    updatedAt = "2025-12-04T10:00:00Z",
)

internal val previewAlertPost = Post(
    id = "p3",
    user = PostUser(id = "u3", name = "Marek Wiśniewski"),
    contentKeys = setOf(PostContentKey.Alert),
    status = PostStatus.Published,
    content = PostContent(
        text = "Podwodna przeszkoda przy wejściu do portu — zachowajcie ostrożność.",
        alert = PostAlert(category = AlertCategory.Obstruction, severity = AlertSeverity.Warning),
    ),
    location = PostLocation(name = "Hvar Town Quay", point = PostCoordinates(43.1724, 16.4411)),
    commentsCount = 0,
    reactions = ReactionSummary(),
    validityVotes = VoteSummary(confirmCount = 4, invalidCount = 0, userVote = ValidityVoteType.Confirm),
    permissions = previewPermissions.copy(validityVote = true, resolve = true, delete = true, archive = true),
    expiresAt = "2025-12-05T23:30:00Z",
    publishedAt = "2025-12-05T17:30:00Z",
    createdAt = "2025-12-05T17:30:00Z",
    updatedAt = "2025-12-05T17:30:00Z",
)

/** Imported alert — system-generated, so `user` is `null` (API v8.1.0). */
internal val previewSystemAlertPost = Post(
    id = "p4",
    user = null,
    contentKeys = setOf(PostContentKey.Alert),
    status = PostStatus.Published,
    content = PostContent(
        text = "Radio navigational warning: buoy off station near the harbour entrance.",
        alert = PostAlert(category = AlertCategory.NavigationWarning, severity = AlertSeverity.Warning),
    ),
    location = PostLocation(name = "Gdańsk Bay"),
    source = PostSource(type = "alert", id = "navtex-2"),
    commentsCount = 0,
    reactions = ReactionSummary(),
    permissions = PostPermissions(),
    publishedAt = "2025-12-05T08:05:00Z",
    createdAt = "2025-12-05T08:05:00Z",
    updatedAt = "2025-12-05T08:05:00Z",
)

internal val previewPosts = listOf(previewNotePost, previewPhotoPost, previewRoutePost, previewAlertPost)

private val previewActions = PostCardActions(
    onToggleReaction = { _, _ -> },
    onOpenReactionPicker = {},
    onOpenComments = {},
    onToggleBookmark = {},
    onCastVote = { _, _ -> },
    onArchive = {},
    onResolve = {},
    onDeleteRequest = {},
)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun PostsScreenPreview() {
    SkipperClubTheme {
        PostsScreenContent(
            state = PostsFeedUiState(posts = previewPosts, hasLoadedOnce = true),
            nowMillis = previewNow,
            cardActions = previewActions,
            onOpenFilters = {},
            onOpenBookmarks = {},
            onCreate = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PostsScreenPreviewPl() {
    SkipperClubTheme {
        PostsScreenContent(
            state = PostsFeedUiState(
                posts = previewPosts,
                hasLoadedOnce = true,
                filters = PostFilters(contains = setOf(PostContainsFilter.Alert), query = "hvar"),
            ),
            nowMillis = previewNow,
            cardActions = previewActions,
            onOpenFilters = {},
            onOpenBookmarks = {},
            onCreate = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsScreenPreviewDark() {
    SkipperClubTheme {
        PostsScreenContent(
            state = PostsFeedUiState(posts = previewPosts, hasLoadedOnce = true),
            nowMillis = previewNow,
            cardActions = previewActions,
            onOpenFilters = {},
            onOpenBookmarks = {},
            onCreate = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PostsScreenEmptyPreviewPl() {
    SkipperClubTheme {
        PostsScreenContent(
            state = PostsFeedUiState(hasLoadedOnce = true),
            nowMillis = previewNow,
            cardActions = previewActions,
            onOpenFilters = {},
            onOpenBookmarks = {},
            onCreate = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun PostCardVideoPreview() {
    SkipperClubTheme {
        PostCard(post = previewVideoPost, nowMillis = previewNow, actions = previewActions)
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun PostCardRoutePreview() {
    SkipperClubTheme {
        PostCard(post = previewRoutePost, nowMillis = previewNow, actions = previewActions)
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun PostCardAlertPreviewPl() {
    SkipperClubTheme {
        PostCard(post = previewAlertPost, nowMillis = previewNow, actions = previewActions)
    }
}

/** Author-less system post (imported alert, `user == null` since API v8.1.0). */
@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun PostCardSystemAlertPreview() {
    SkipperClubTheme {
        PostCard(post = previewSystemAlertPost, nowMillis = previewNow, actions = previewActions)
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardPhotoPreviewDark() {
    SkipperClubTheme {
        PostCard(post = previewPhotoPost, nowMillis = previewNow, actions = previewActions)
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun ReactionPickerPreview() {
    SkipperClubTheme {
        ReactionPickerContent(
            userReactions = setOf(ReactionType.Heart),
            onSelect = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun FilterSheetPreviewPl() {
    SkipperClubTheme {
        PostFilterSheetContent(
            filters = PostFilters(contains = setOf(PostContainsFilter.Alert, PostContainsFilter.Media)),
            currentUserId = "u1",
            onSearchLocations = { emptyList() },
            onApply = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FilterSheetPreviewDark() {
    SkipperClubTheme {
        PostFilterSheetContent(
            filters = PostFilters(),
            currentUserId = "u1",
            onSearchLocations = { emptyList() },
            onApply = {},
        )
    }
}
