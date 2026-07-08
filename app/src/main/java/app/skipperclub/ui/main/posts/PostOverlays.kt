package app.skipperclub.ui.main.posts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.Post
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.posts.wizard.PostWizard
import app.skipperclub.ui.main.posts.wizard.PostWizardEvent
import app.skipperclub.ui.main.posts.wizard.PostWizardState
import app.skipperclub.ui.notification.InAppNotificationHostState
import app.skipperclub.ui.notification.InAppNotificationType

/**
 * Hoisted selection for the shared post interaction overlays. Kept as Compose
 * snapshot state so a host screen can drive them and build [PostCardActions].
 */
class PostOverlayState {
    var reactionPickerPostId by mutableStateOf<String?>(null)
    var commentsPostId by mutableStateOf<String?>(null)
    var postPendingDelete by mutableStateOf<Post?>(null)
    var postPendingReport by mutableStateOf<Post?>(null)
    var editingPost by mutableStateOf<Post?>(null)
}

@Composable
fun rememberPostOverlayState(): PostOverlayState = remember { PostOverlayState() }

/** Builds [PostCardActions] that drive the [overlay] + [controller] mutations. */
fun postCardActions(controller: PostsFeedController, overlay: PostOverlayState): PostCardActions =
    PostCardActions(
        onToggleReaction = { post, reaction -> controller.toggleReaction(post, reaction) },
        onOpenReactionPicker = { post -> overlay.reactionPickerPostId = post.id },
        onOpenComments = { post -> overlay.commentsPostId = post.id },
        onToggleBookmark = { post -> controller.toggleBookmark(post) },
        onCastVote = { post, vote -> controller.castValidityVote(post, vote) },
        onArchive = { post -> controller.archivePost(post) },
        onResolve = { post -> controller.resolvePost(post) },
        onDeleteRequest = { post -> overlay.postPendingDelete = post },
        onEditRequest = { post -> overlay.editingPost = post },
        onReportRequest = { post -> overlay.postPendingReport = post },
    )

/**
 * Reaction picker, comments sheet, report sheet, delete confirmation and the edit
 * wizard — the interaction surfaces shared by the feed and the bookmarks list.
 */
@Composable
fun PostOverlays(
    controller: PostsFeedController,
    overlay: PostOverlayState,
    posts: List<Post>,
    currentUserId: String?,
    nowMillis: Long,
    notificationHostState: InAppNotificationHostState,
) {
    val scope = rememberCoroutineScope()
    val sessionUser = SessionStore.session.collectAsState().value?.user
    val errorAuthMessage = stringResource(R.string.posts_error_auth)
    val commentsErrorMessage = stringResource(R.string.comments_error_generic)
    val mediaUploadFailedMessage = stringResource(R.string.wizard_media_failed)

    overlay.reactionPickerPostId?.let { postId ->
        val post = posts.firstOrNull { it.id == postId }
        if (post == null) {
            overlay.reactionPickerPostId = null
        } else {
            ReactionPickerSheet(
                userReactions = post.reactions.userReactions,
                onSelect = { reaction ->
                    controller.toggleReaction(post, reaction)
                    overlay.reactionPickerPostId = null
                },
                onDismiss = { overlay.reactionPickerPostId = null },
            )
        }
    }

    overlay.commentsPostId?.let { postId ->
        val post = posts.firstOrNull { it.id == postId }
        if (post == null) {
            overlay.commentsPostId = null
        } else {
            val commentsController = remember(postId) {
                CommentsController(
                    scope = scope,
                    accessToken = { SessionStore.validSession()?.accessToken },
                    postId = postId,
                )
            }
            val commentsState by commentsController.state.collectAsState()
            LaunchedEffect(commentsController) { commentsController.load() }
            LaunchedEffect(commentsController) {
                commentsController.events.collect { event ->
                    when (event) {
                        is CommentsEvent.OperationFailed ->
                            notificationHostState.show(commentsErrorMessage, InAppNotificationType.Error)

                        CommentsEvent.SessionExpired ->
                            notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)

                        CommentsEvent.CommentAdded -> controller.adjustCommentsCount(postId, +1)
                        CommentsEvent.CommentUpdated -> Unit
                        CommentsEvent.CommentDeleted -> controller.adjustCommentsCount(postId, -1)
                    }
                }
            }
            CommentsSheet(
                state = commentsState,
                currentUserId = currentUserId,
                canComment = post.permissions.comment,
                nowMillis = nowMillis,
                onLoadMore = commentsController::loadMore,
                onSend = commentsController::send,
                onEdit = { comment, text -> commentsController.edit(comment.id, text) },
                onDelete = { comment -> commentsController.delete(comment.id) },
                onDismiss = { overlay.commentsPostId = null },
            )
        }
    }

    overlay.postPendingReport?.let { post ->
        ReportPostSheet(
            onSubmit = { reason, details ->
                overlay.postPendingReport = null
                controller.reportPost(post, reason, details)
            },
            onDismiss = { overlay.postPendingReport = null },
        )
    }

    overlay.postPendingDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { overlay.postPendingDelete = null },
            title = { Text(stringResource(R.string.post_delete_confirm_title)) },
            text = { Text(stringResource(R.string.post_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        overlay.postPendingDelete = null
                        controller.deletePost(post)
                    },
                ) {
                    Text(stringResource(R.string.post_action_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { overlay.postPendingDelete = null }) {
                    Text(stringResource(R.string.post_cancel))
                }
            },
        )
    }

    overlay.editingPost?.let { post ->
        val editState = remember(post.id) {
            PostWizardState(
                scope = scope,
                accessToken = { SessionStore.validSession()?.accessToken },
                editingPost = post,
            )
        }
        LaunchedEffect(editState) {
            editState.events.collect { event ->
                when (event) {
                    is PostWizardEvent.Updated -> {
                        controller.editPost(event.postId, event.request)
                        overlay.editingPost = null
                    }

                    is PostWizardEvent.MediaUploadFailed ->
                        notificationHostState.show(mediaUploadFailedMessage, InAppNotificationType.Error)

                    PostWizardEvent.SessionExpired ->
                        notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)

                    is PostWizardEvent.Published,
                    is PostWizardEvent.PublishFailed,
                    -> Unit
                }
            }
        }
        Dialog(
            onDismissRequest = { overlay.editingPost = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            PostWizard(
                state = editState,
                onClose = { overlay.editingPost = null },
                user = sessionUser,
            )
        }
    }
}
