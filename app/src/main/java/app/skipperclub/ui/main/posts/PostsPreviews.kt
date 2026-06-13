package app.skipperclub.ui.main.posts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.skipperclub.data.Post
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostMedia
import app.skipperclub.data.PostPermissions
import app.skipperclub.data.PostRouteStop
import app.skipperclub.data.PostStatus
import app.skipperclub.data.PostType
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

internal val previewPhotoPost = Post(
    id = "p1",
    type = PostType.Photo,
    status = PostStatus.Published,
    regionCode = "ADR-HR",
    user = previewAuthor,
    description = "Beautiful sunset over the Adriatic! #sailing #sunset",
    locationName = "Split",
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
    createdAt = "2025-12-05T18:30:00Z",
    updatedAt = "2025-12-05T18:30:00Z",
)

internal val previewVideoPost = Post(
    id = "p1v",
    type = PostType.Photo,
    status = PostStatus.Published,
    regionCode = "ADR-HR",
    user = previewAuthor,
    description = "Downwind under spinnaker 🌊 #sailing",
    locationName = "Trogir",
    hashtags = listOf("sailing"),
    media = listOf(
        PostMedia(id = "mv1", type = "video", url = "https://example.com/downwind.mp4"),
    ),
    commentsCount = 2,
    reactions = ReactionSummary(total = 2, byType = mapOf(ReactionType.Laugh to 1, ReactionType.Heart to 1)),
    permissions = previewPermissions,
    createdAt = "2025-12-05T18:30:00Z",
    updatedAt = "2025-12-05T18:30:00Z",
)

internal val previewRoutePost = Post(
    id = "p2",
    type = PostType.Route,
    status = PostStatus.Published,
    regionCode = "ADR-HR",
    user = PostUser(id = "u2", name = "Jan Kowalski"),
    description = "Tygodniowa trasa po Dalmacji #chorwacja",
    locationName = "Split",
    coordinates = PostCoordinates(43.5081, 16.4402),
    stops = listOf(
        PostRouteStop("Split", PostCoordinates(43.5081, 16.4402)),
        PostRouteStop("Hvar", PostCoordinates(43.1724, 16.4411)),
        PostRouteStop("Dubrovnik", PostCoordinates(42.6507, 18.0944)),
    ),
    durationDays = 7,
    lengthNm = 120.0,
    commentsCount = 2,
    reactions = ReactionSummary(total = 3, byType = mapOf(ReactionType.Sailboat to 3)),
    permissions = previewPermissions,
    createdAt = "2025-12-04T10:00:00Z",
    updatedAt = "2025-12-04T10:00:00Z",
)

internal val previewBerthPost = Post(
    id = "p3",
    type = PostType.Berth,
    status = PostStatus.Published,
    regionCode = "ADR-HR",
    user = PostUser(id = "u3", name = "Marek Wiśniewski"),
    description = "3 wolne miejsca przy kei miejskiej",
    locationName = "Hvar Town Quay",
    coordinates = PostCoordinates(43.1724, 16.4411),
    commentsCount = 0,
    reactions = ReactionSummary(),
    validityVotes = VoteSummary(confirmCount = 4, invalidCount = 0, userVote = ValidityVoteType.Confirm),
    permissions = previewPermissions.copy(validityVote = true, resolve = true, delete = true, archive = true),
    expiresAt = "2025-12-05T23:30:00Z",
    createdAt = "2025-12-05T17:30:00Z",
    updatedAt = "2025-12-05T17:30:00Z",
)

internal val previewPosts = listOf(previewPhotoPost, previewRoutePost, previewBerthPost)

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
                filters = PostFilters(types = setOf(PostType.Berth), regionCode = "ADR-HR"),
            ),
            nowMillis = previewNow,
            cardActions = previewActions,
            onOpenFilters = {},
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
private fun PostCardBerthPreviewPl() {
    SkipperClubTheme {
        PostCard(post = previewBerthPost, nowMillis = previewNow, actions = previewActions)
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
            filters = PostFilters(types = setOf(PostType.Photo, PostType.Berth)),
            regions = emptyList(),
            regionsLoadFailed = false,
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
            regions = emptyList(),
            regionsLoadFailed = true,
            onApply = {},
        )
    }
}
