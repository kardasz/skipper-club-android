package app.skipperclub.ui.main.posts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.Post
import app.skipperclub.data.PostsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.posts.wizard.PostWizard
import app.skipperclub.ui.main.posts.wizard.PostWizardEvent
import app.skipperclub.ui.main.posts.wizard.PostWizardState
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import kotlinx.coroutines.delay

/** Bottom inset that keeps the feed viewport clear of the floating [SkipperBottomBar]. */
private val FeedNavigationInset = 132.dp
private val FeedBottomInset = 20.dp

@Composable
fun PostsScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        PostsFeedController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id

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
    val postCreatedMessage = stringResource(R.string.posts_created)
    val publishFailedMessage = stringResource(R.string.wizard_publish_failed)
    val mediaUploadFailedMessage = stringResource(R.string.wizard_media_failed)

    fun errorMessage(error: Exception): String = when (error) {
        is PostsError.Network -> errorNetworkMessage
        is PostsError.AuthenticationRequired -> errorAuthMessage
        else -> errorGenericMessage
    }

    LaunchedEffect(controller) {
        controller.loadInitialIfNeeded()
    }
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

    val overlay = rememberPostOverlayState()
    var showFilters by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showWizard by rememberSaveable { mutableStateOf(false) }

    val cardActions = remember(controller, overlay) { postCardActions(controller, overlay) }

    Box(modifier = modifier.fillMaxSize()) {
        PostsScreenContent(
            state = state,
            nowMillis = nowMillis,
            cardActions = cardActions,
            onOpenFilters = { showFilters = true },
            onOpenBookmarks = { showBookmarks = true },
            onCreate = { showWizard = true },
            onRefresh = controller::refresh,
            onLoadMore = controller::loadMore,
            onRetry = controller::refresh,
        )
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    PostOverlays(
        controller = controller,
        overlay = overlay,
        posts = state.posts,
        currentUserId = currentUserId,
        nowMillis = nowMillis,
        notificationHostState = notificationHostState,
    )

    if (showBookmarks) {
        Dialog(
            onDismissRequest = { showBookmarks = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            BookmarksScreen(onClose = { showBookmarks = false })
        }
    }

    if (showFilters) {
        PostFilterSheet(
            filters = state.filters,
            currentUserId = currentUserId,
            onSearchLocations = { query ->
                val token = SessionStore.validSession()?.accessToken
                if (token == null) emptyList() else RealPostsGateway.searchLocations(token, query)
            },
            onApply = { filters ->
                showFilters = false
                controller.applyFilters(filters)
            },
            onDismiss = { showFilters = false },
        )
    }

    if (showWizard) {
        val wizardState = remember {
            PostWizardState(
                scope = scope,
                accessToken = { SessionStore.validSession()?.accessToken },
            )
        }
        LaunchedEffect(wizardState) {
            wizardState.events.collect { event ->
                when (event) {
                    is PostWizardEvent.Published -> {
                        controller.onPostCreated(event.post)
                        showWizard = false
                        notificationHostState.show(postCreatedMessage, InAppNotificationType.Success)
                    }

                    is PostWizardEvent.PublishFailed ->
                        notificationHostState.show(publishFailedMessage, InAppNotificationType.Error)

                    is PostWizardEvent.MediaUploadFailed ->
                        notificationHostState.show(mediaUploadFailedMessage, InAppNotificationType.Error)

                    is PostWizardEvent.Updated -> Unit

                    PostWizardEvent.SessionExpired ->
                        notificationHostState.show(errorAuthMessage, InAppNotificationType.Error)
                }
            }
        }
        Dialog(
            onDismissRequest = { showWizard = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            PostWizard(
                state = wizardState,
                onClose = { showWizard = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostsScreenContent(
    state: PostsFeedUiState,
    nowMillis: Long,
    cardActions: PostCardActions,
    onOpenFilters: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onCreate: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nav_posts),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onCreate,
                    modifier = Modifier.testTag("posts_create"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.posts_create),
                    )
                }
                IconButton(
                    onClick = onOpenBookmarks,
                    modifier = Modifier.testTag("posts_bookmarks"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmarks,
                        contentDescription = stringResource(R.string.bookmarks_open),
                    )
                }
                BadgedBox(
                    badge = {
                        if (state.filters.activeCount > 0) {
                            Badge { Text(state.filters.activeCount.toString()) }
                        }
                    },
                ) {
                    IconButton(
                        onClick = onOpenFilters,
                        modifier = Modifier.testTag("posts_filters"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.posts_filter),
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = FeedNavigationInset),
            ) {
                when {
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    state.loadFailed && state.posts.isEmpty() -> {
                        FeedMessage(
                            title = stringResource(R.string.posts_load_failed),
                            actionLabel = stringResource(R.string.posts_retry),
                            onAction = onRetry,
                        )
                    }

                    state.posts.isEmpty() && state.hasLoadedOnce -> {
                        FeedMessage(
                            title = stringResource(R.string.posts_empty_title),
                            subtitle = stringResource(R.string.posts_empty_subtitle),
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("posts_list"),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = FeedBottomInset,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(state.posts, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    nowMillis = nowMillis,
                                    actions = cardActions,
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(28.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedMessage(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
