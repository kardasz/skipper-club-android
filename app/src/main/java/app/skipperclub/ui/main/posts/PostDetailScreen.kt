package app.skipperclub.ui.main.posts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Post
import app.skipperclub.data.PostsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PostLoadPhase { Loading, Failed, Loaded }

/**
 * Single-post detail opened from a notification. Reuses [PostsFeedController] —
 * seeded with the fetched post — so reactions, comments, bookmarking and status
 * changes share the already-tested feed logic.
 */
@Composable
fun PostDetailScreen(
    postId: String,
    focusComments: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    gateway: PostsGateway = RealPostsGateway,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        PostsFeedController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            gateway = gateway,
        )
    }
    val state by controller.state.collectAsState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id
    val notificationHostState = rememberInAppNotificationHostState()

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val errorNetworkMessage = stringResource(R.string.posts_error_network)
    val errorAuthMessage = stringResource(R.string.posts_error_auth)
    val errorGenericMessage = stringResource(R.string.posts_error_generic)
    val postDeletedMessage = stringResource(R.string.posts_deleted)
    val postArchivedMessage = stringResource(R.string.posts_archived)
    val postResolvedMessage = stringResource(R.string.posts_resolved)
    val commentsErrorMessage = stringResource(R.string.comments_error_generic)

    fun errorMessage(error: Exception): String = when (error) {
        is PostsError.Network -> errorNetworkMessage
        is PostsError.AuthenticationRequired -> errorAuthMessage
        else -> errorGenericMessage
    }

    var loadPhase by remember { mutableStateOf(PostLoadPhase.Loading) }
    var reactionPickerPostId by remember { mutableStateOf<String?>(null) }
    var commentsPostId by remember { mutableStateOf<String?>(null) }
    var postPendingDelete by remember { mutableStateOf<Post?>(null) }

    suspend fun fetch() {
        loadPhase = PostLoadPhase.Loading
        val token = SessionStore.validSession()?.accessToken
        if (token == null) {
            loadPhase = PostLoadPhase.Failed
            notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)
            return
        }
        try {
            val post = gateway.get(token, postId)
            controller.onPostCreated(post)
            loadPhase = PostLoadPhase.Loaded
            if (focusComments) commentsPostId = postId
        } catch (error: PostsError) {
            loadPhase = PostLoadPhase.Failed
            notificationHostState.show(errorMessage(error), InAppNotificationType.Error)
        }
    }

    LaunchedEffect(postId) { fetch() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is PostsFeedEvent.OperationFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                PostsFeedEvent.SessionExpired ->
                    notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)

                PostsFeedEvent.PostDeleted -> {
                    notificationHostState.show(postDeletedMessage, InAppNotificationType.Success)
                    onClose()
                }

                PostsFeedEvent.PostArchived ->
                    notificationHostState.show(postArchivedMessage, InAppNotificationType.Success)

                PostsFeedEvent.PostResolved ->
                    notificationHostState.show(postResolvedMessage, InAppNotificationType.Success)
            }
        }
    }

    val cardActions = remember(controller) {
        PostCardActions(
            onToggleReaction = { post, reaction -> controller.toggleReaction(post, reaction) },
            onOpenReactionPicker = { post -> reactionPickerPostId = post.id },
            onOpenComments = { post -> commentsPostId = post.id },
            onToggleBookmark = { post -> controller.toggleBookmark(post) },
            onCastVote = { post, vote -> controller.castValidityVote(post, vote) },
            onArchive = { post -> controller.archivePost(post) },
            onResolve = { post -> controller.resolvePost(post) },
            onDeleteRequest = { post -> postPendingDelete = post },
        )
    }

    val post = state.posts.firstOrNull { it.id == postId }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("post_detail"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("post_detail_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.conversation_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.post_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                when {
                    post != null -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        PostCard(post = post, nowMillis = nowMillis, actions = cardActions)
                    }

                    loadPhase == PostLoadPhase.Loading -> Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    else -> Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.post_detail_load_failed))
                            Button(
                                onClick = { scope.launch { fetch() } },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(stringResource(R.string.posts_retry))
                            }
                        }
                    }
                }
            }
            InAppNotificationHost(hostState = notificationHostState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    reactionPickerPostId?.let { id ->
        val target = state.posts.firstOrNull { it.id == id }
        if (target == null) {
            reactionPickerPostId = null
        } else {
            ReactionPickerSheet(
                userReactions = target.reactions.userReactions,
                onSelect = { reaction ->
                    controller.toggleReaction(target, reaction)
                    reactionPickerPostId = null
                },
                onDismiss = { reactionPickerPostId = null },
            )
        }
    }

    commentsPostId?.let { id ->
        val target = state.posts.firstOrNull { it.id == id }
        if (target == null) {
            commentsPostId = null
        } else {
            val commentsController = remember(id) {
                CommentsController(
                    scope = scope,
                    accessToken = { SessionStore.validSession()?.accessToken },
                    postId = id,
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

                        CommentsEvent.CommentAdded -> controller.adjustCommentsCount(id, +1)
                        CommentsEvent.CommentDeleted -> controller.adjustCommentsCount(id, -1)
                    }
                }
            }
            CommentsSheet(
                state = commentsState,
                currentUserId = currentUserId,
                canComment = target.permissions.comment,
                nowMillis = nowMillis,
                onLoadMore = commentsController::loadMore,
                onSend = commentsController::send,
                onDelete = { comment -> commentsController.delete(comment.id) },
                onDismiss = { commentsPostId = null },
            )
        }
    }

    postPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { postPendingDelete = null },
            title = { Text(stringResource(R.string.post_delete_confirm_title)) },
            text = { Text(stringResource(R.string.post_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        postPendingDelete = null
                        controller.deletePost(target)
                    },
                ) {
                    Text(stringResource(R.string.post_action_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { postPendingDelete = null }) {
                    Text(stringResource(R.string.post_cancel))
                }
            },
        )
    }
}
