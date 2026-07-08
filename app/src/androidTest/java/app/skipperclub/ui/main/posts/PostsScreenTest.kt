package app.skipperclub.ui.main.posts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import app.skipperclub.R
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.Post
import app.skipperclub.data.PostContainsFilter
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.ReactionType
import app.skipperclub.data.ValidityVoteType
import app.skipperclub.ui.main.posts.wizard.PostWizard
import app.skipperclub.ui.main.posts.wizard.PostWizardState
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PostsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val nowMillis = 1_765_000_000_000L

    private fun actions(
        onToggleReaction: (Post, ReactionType) -> Unit = { _, _ -> },
        onOpenComments: (Post) -> Unit = {},
        onCastVote: (Post, ValidityVoteType) -> Unit = { _, _ -> },
    ) = PostCardActions(
        onToggleReaction = onToggleReaction,
        onOpenReactionPicker = {},
        onOpenComments = onOpenComments,
        onToggleBookmark = {},
        onCastVote = onCastVote,
        onArchive = {},
        onResolve = {},
        onDeleteRequest = {},
    )

    @Test
    fun feedRendersProvidedPosts() {
        compose.setContent {
            SkipperClubTheme {
                PostsScreenContent(
                    state = PostsFeedUiState(posts = previewPosts, hasLoadedOnce = true),
                    nowMillis = nowMillis,
                    cardActions = actions(),
                    onOpenFilters = {},
                    onOpenBookmarks = {},
                    onCreate = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        // Feed renders multiple posts from different authors (note, photo, route, alert).
        compose.onNodeWithText("Anna Nowak").assertExists()
        compose.onNodeWithTag("posts_list")
            .performScrollToNode(hasText("Marek Wiśniewski"))
        compose.onNodeWithText("Marek Wiśniewski").assertExists()
    }

    @Test
    fun emptyFeedShowsEmptyState() {
        compose.setContent {
            SkipperClubTheme {
                PostsScreenContent(
                    state = PostsFeedUiState(hasLoadedOnce = true),
                    nowMillis = nowMillis,
                    cardActions = actions(),
                    onOpenFilters = {},
                    onOpenBookmarks = {},
                    onCreate = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.posts_empty_title)).assertExists()
        compose.onNodeWithText(text(R.string.posts_empty_subtitle)).assertExists()
    }

    @Test
    fun reactionChipClickEmitsToggleCallback() {
        var toggled: Pair<Post, ReactionType>? = null
        compose.setContent {
            SkipperClubTheme {
                PostCard(
                    post = previewPhotoPost,
                    nowMillis = nowMillis,
                    actions = actions(onToggleReaction = { post, reaction ->
                        toggled = post to reaction
                    }),
                )
            }
        }

        // photo post shows the heart reaction chip with count 10
        compose.onNodeWithText("10").performClick()

        assertEquals(previewPhotoPost.id, toggled?.first?.id)
        assertEquals(ReactionType.Heart, toggled?.second)
    }

    @Test
    fun tappingVideoOpensFullscreenPlayer() {
        compose.setContent {
            SkipperClubTheme {
                PostCard(
                    post = previewVideoPost,
                    nowMillis = nowMillis,
                    actions = actions(),
                )
            }
        }

        compose.onNodeWithTag("video_player").assertDoesNotExist()
        compose.onNodeWithTag("post_video_play").performClick()
        compose.onNodeWithTag("video_player").assertExists()
        compose.onNodeWithTag("video_player_close").performClick()
        compose.onNodeWithTag("video_player").assertDoesNotExist()
    }

    @Test
    fun reactionPickerShowsBothSectionsAndSelects() {
        var selected: ReactionType? = null
        compose.setContent {
            SkipperClubTheme {
                ReactionPickerContent(
                    userReactions = setOf(ReactionType.Heart),
                    onSelect = { selected = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.reaction_section_standard)).assertExists()
        compose.onNodeWithText(text(R.string.reaction_section_sailing)).assertExists()
        compose.onNodeWithTag("reaction_anchor").performClick()

        assertEquals(ReactionType.Anchor, selected)
    }

    @Test
    fun validityVoteChipsEmitVoteCallback() {
        var vote: ValidityVoteType? = null
        val post = previewAlertPost.copy(
            validityVotes = app.skipperclub.data.VoteSummary(confirmCount = 1, invalidCount = 0, userVote = null),
        )
        compose.setContent {
            SkipperClubTheme {
                PostCard(
                    post = post,
                    nowMillis = nowMillis,
                    actions = actions(onCastVote = { _, voteType -> vote = voteType }),
                )
            }
        }

        compose.onNodeWithText("${text(R.string.post_validity_invalid)} · 0").performClick()

        assertEquals(ValidityVoteType.ReportInvalid, vote)
    }

    @Test
    fun commentsSheetSendsTrimmedComment() {
        var sent: String? = null
        compose.setContent {
            SkipperClubTheme {
                CommentsSheetContent(
                    state = CommentsUiState(),
                    currentUserId = "u1",
                    canComment = true,
                    nowMillis = nowMillis,
                    onLoadMore = {},
                    onSend = { sent = it },
                    onEdit = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithTag("comment_send").assertIsNotEnabled()
        compose.onNodeWithTag("comment_input").performTextInput("Ahoy!")
        compose.onNodeWithTag("comment_send").assertIsEnabled().performClick()

        assertEquals("Ahoy!", sent)
    }

    @Test
    fun filterSheetAppliesSelectedContainsAndSort() {
        var applied: PostFilters? = null
        compose.setContent {
            SkipperClubTheme {
                PostFilterSheetContent(
                    filters = PostFilters(),
                    currentUserId = "u1",
                    onSearchLocations = { emptyList() },
                    onApply = { applied = it },
                )
            }
        }

        compose.onNodeWithTag("filter_contains_${PostContainsFilter.Alert.wireValue}").performClick()
        compose.onNodeWithText(text(R.string.filter_order_asc)).performClick()
        compose.onNodeWithTag("filter_apply").performClick()

        assertEquals(setOf(PostContainsFilter.Alert), applied?.contains)
        assertEquals(app.skipperclub.data.SortOrder.Asc, applied?.order)
    }

    @Test
    fun wizardKeepsPublishDisabledUntilTextEntered() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val wizardState = PostWizardState(
            scope = scope,
            accessToken = { null },
        )
        compose.setContent {
            SkipperClubTheme {
                PostWizard(state = wizardState, onClose = {})
            }
        }

        // Single-form wizard: publish stays disabled until the required text is present.
        compose.onNodeWithTag("wizard_publish").assertIsNotEnabled()
        compose.onNodeWithTag("wizard_text").performTextInput("Anchored safely for the night")
        compose.onNodeWithTag("wizard_publish").assertIsEnabled()
        assertTrue(wizardState.text.isNotBlank())
    }

    @Test
    fun wizardRouteSheetSavesRouteAsInlineCard() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val wizardState = PostWizardState(
            scope = scope,
            accessToken = { null },
        )
        wizardState.addStop(
            GeocodedLocation(
                name = "Gdynia",
                formattedAddress = "Gdynia, Polska",
                coordinates = PostCoordinates(54.52, 18.55),
            ),
        )
        compose.setContent {
            SkipperClubTheme {
                PostWizard(state = wizardState, onClose = {})
            }
        }

        // The route only becomes a post attachment once saved from the sheet.
        compose.onNodeWithTag("wizard_route_card").assertDoesNotExist()
        compose.onNodeWithTag("wizard_action_route").performClick()
        compose.onNodeWithTag("wizard_route_save").performClick()

        compose.onNodeWithTag("wizard_route_card").assertExists()
        assertTrue(wizardState.routeEnabled)
    }

    @Test
    fun editingAlertPostShowsBadgeAndBlocksRoute() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val alertPost = previewPosts.first { it.content.alert != null }
        val wizardState = PostWizardState(
            scope = scope,
            accessToken = { null },
            editingPost = alertPost,
        )
        compose.setContent {
            SkipperClubTheme {
                PostWizard(state = wizardState, onClose = {})
            }
        }

        // Alerts are managed by the map flow: the edited alert stays as a
        // read-only badge and the route action is unavailable.
        compose.onNodeWithTag("wizard_alert_badge").assertExists()
        compose.onNodeWithTag("wizard_action_route").assertIsNotEnabled()
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
