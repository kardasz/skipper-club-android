package app.skipperclub.ui.main.posts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
fun PostsScreen(
    modifier: Modifier = Modifier,
    onCreateAlert: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        PostsFeedController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    val sessionUser = SessionStore.session.collectAsState().value?.user
    val currentUserId = sessionUser?.id

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
    var showNearMe by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showCreateChooser by remember { mutableStateOf(false) }
    var showWizard by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }

    val cardActions = remember(controller, overlay) { postCardActions(controller, overlay) }

    Box(modifier = modifier.fillMaxSize()) {
        PostsScreenContent(
            state = state,
            nowMillis = nowMillis,
            cardActions = cardActions,
            searchActive = searchActive,
            searchQuery = searchText,
            nearMeActive = state.filters.isNearMeActive,
            onOpenSearch = {
                searchText = state.filters.query.orEmpty()
                searchActive = true
            },
            onSearchQueryChange = { searchText = it },
            onSearchSubmit = {
                controller.applyFilters(state.filters.copy(query = searchText.ifBlank { null }))
            },
            onCloseSearch = {
                searchActive = false
                searchText = ""
                if (state.filters.query != null) {
                    controller.applyFilters(state.filters.copy(query = null))
                }
            },
            onOpenNearMe = { showNearMe = true },
            onOpenBookmarks = { showBookmarks = true },
            onCreate = { showCreateChooser = true },
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

    if (showNearMe) {
        NearMeSheet(
            initialRadiusNm = state.filters.nearMeRadiusNm ?: NearMeDefaultNm,
            isActive = state.filters.isNearMeActive,
            onApply = { center, radiusNm, label ->
                showNearMe = false
                controller.applyFilters(state.filters.withNearMe(center, radiusNm, label))
            },
            onClear = {
                showNearMe = false
                controller.applyFilters(state.filters.clearNearMe())
            },
            onDismiss = { showNearMe = false },
        )
    }

    if (showCreateChooser) {
        CreatePostChooserSheet(
            onCreatePost = {
                showCreateChooser = false
                showWizard = true
            },
            onCreateAlert = {
                showCreateChooser = false
                onCreateAlert()
            },
            onDismiss = { showCreateChooser = false },
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
                user = sessionUser,
            )
        }
    }
}

/** "Create" chooser: a regular post opens the composer, an alert goes to the map flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostChooserSheet(
    onCreatePost: () -> Unit,
    onCreateAlert: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.posts_create_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                headlineContent = { Text(stringResource(R.string.posts_create_option_post)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.posts_create_option_post_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreatePost)
                    .testTag("create_option_post"),
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                },
                headlineContent = { Text(stringResource(R.string.posts_create_option_alert)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.posts_create_option_alert_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateAlert)
                    .testTag("create_option_alert"),
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
    onOpenBookmarks: () -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    searchActive: Boolean = false,
    searchQuery: String = "",
    nearMeActive: Boolean = false,
    onOpenSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onOpenNearMe: () -> Unit = {},
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
            if (searchActive) {
                PostsSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSubmit = onSearchSubmit,
                    onClose = onCloseSearch,
                )
            } else {
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
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("posts_search"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.posts_search),
                        )
                    }
                    IconButton(
                        onClick = onOpenNearMe,
                        modifier = Modifier.testTag("posts_near_me"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NearMe,
                            contentDescription = stringResource(R.string.posts_near_me),
                            tint = if (nearMeActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
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
                    IconButton(
                        onClick = onCreate,
                        modifier = Modifier.testTag("posts_create"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.posts_create),
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

/** Expanding search field that replaces the title bar; applies the feed's `q` filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                keyboard?.hide()
                onClose()
            },
            modifier = Modifier.testTag("posts_search_close"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.posts_search_close),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.posts_filter_search_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("posts_search_clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.posts_search_clear),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onSubmit()
                },
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .testTag("posts_search_field"),
        )
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
