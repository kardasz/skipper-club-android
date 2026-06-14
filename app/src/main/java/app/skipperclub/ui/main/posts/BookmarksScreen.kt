package app.skipperclub.ui.main.posts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.BookmarksQuery
import app.skipperclub.data.PostsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import kotlinx.coroutines.delay

/**
 * Saved-posts list (`GET /v1/profile/bookmarks/posts`). Reuses [PostsFeedController]
 * via its [PostsFeedController] page-loader hook plus the shared [PostOverlays], so
 * reactions/comments/bookmark/edit/report all behave exactly like the main feed.
 */
@Composable
fun BookmarksScreen(
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
            pageLoader = { token, offset, limit ->
                gateway.listBookmarks(token, BookmarksQuery(limit = limit, offset = offset))
            },
        )
    }
    val state by controller.state.collectAsState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id
    val notificationHostState = rememberInAppNotificationHostState()
    val overlay = rememberPostOverlayState()

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
    val postReportedMessage = stringResource(R.string.posts_reported)
    val postUpdatedMessage = stringResource(R.string.posts_updated)

    fun errorMessage(error: Exception): String = when (error) {
        is PostsError.Network -> errorNetworkMessage
        is PostsError.AuthenticationRequired -> errorAuthMessage
        else -> errorGenericMessage
    }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is PostsFeedEvent.OperationFailed ->
                    notificationHostState.show(errorMessage(event.error), InAppNotificationType.Error)

                PostsFeedEvent.SessionExpired ->
                    notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)

                PostsFeedEvent.PostDeleted ->
                    notificationHostState.show(postDeletedMessage, InAppNotificationType.Success)

                PostsFeedEvent.PostArchived ->
                    notificationHostState.show(postArchivedMessage, InAppNotificationType.Success)

                PostsFeedEvent.PostResolved ->
                    notificationHostState.show(postResolvedMessage, InAppNotificationType.Success)

                PostsFeedEvent.PostReported ->
                    notificationHostState.show(postReportedMessage, InAppNotificationType.Success)

                is PostsFeedEvent.PostUpdated ->
                    notificationHostState.show(postUpdatedMessage, InAppNotificationType.Success)
            }
        }
    }

    val cardActions = remember(controller, overlay) { postCardActions(controller, overlay) }

    BackHandler(onBack = onClose)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("bookmarks_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.conversation_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.bookmarks_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                BookmarksList(
                    state = state,
                    nowMillis = nowMillis,
                    cardActions = cardActions,
                    onRefresh = controller::refresh,
                    onLoadMore = controller::loadMore,
                    onRetry = controller::refresh,
                )
            }
            InAppNotificationHost(hostState = notificationHostState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    PostOverlays(
        controller = controller,
        overlay = overlay,
        posts = state.posts,
        currentUserId = currentUserId,
        nowMillis = nowMillis,
        notificationHostState = notificationHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksList(
    state: PostsFeedUiState,
    nowMillis: Long,
    cardActions: PostCardActions,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.hasMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.loadFailed && state.posts.isEmpty() -> BookmarksMessage(
                title = stringResource(R.string.posts_load_failed),
                actionLabel = stringResource(R.string.posts_retry),
                onAction = onRetry,
            )

            state.posts.isEmpty() && state.hasLoadedOnce -> BookmarksMessage(
                title = stringResource(R.string.bookmarks_empty_title),
                subtitle = stringResource(R.string.bookmarks_empty_subtitle),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("bookmarks_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.posts, key = { it.id }) { post ->
                    PostCard(post = post, nowMillis = nowMillis, actions = cardActions)
                }
                if (state.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksMessage(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}
