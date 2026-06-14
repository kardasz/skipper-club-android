package app.skipperclub.ui.main.cruises

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
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.CruisesError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.cruises.wizard.CruiseWizardHost
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Bottom inset that keeps the list viewport clear of the floating [app.skipperclub.ui.main.SkipperBottomBar]. */
private val ListNavigationInset = 132.dp
private val ListBottomInset = 20.dp

@Composable
fun CruisesScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        CruiseListController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val currentUserId = SessionStore.session.collectAsState().value?.user?.id
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.cruise_error_network)
    val errorAuth = stringResource(R.string.cruise_error_auth)
    val errorGeneric = stringResource(R.string.cruise_error_generic)

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is CruiseListEvent.OperationFailed ->
                    notificationHostState.show(cruiseErrorMessage(event.error, errorNetwork, errorAuth, errorGeneric), InAppNotificationType.Error)

                CruiseListEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    var openCruiseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        CruiseListScreenContent(
            state = state,
            onOpenCruise = { openCruiseId = it.id },
            onCreate = { showCreate = true },
            onOpenFilters = { showFilters = true },
            onRefresh = controller::refresh,
            onLoadMore = controller::loadMore,
            onRetry = controller::refresh,
        )
        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showFilters) {
        CruiseFilterSheet(
            filters = state.filters,
            onApply = { filters ->
                showFilters = false
                controller.applyFilters(filters)
            },
            onDismiss = { showFilters = false },
        )
    }

    openCruiseId?.let { cruiseId ->
        Dialog(
            onDismissRequest = { openCruiseId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CruiseDetailScreen(
                cruiseId = cruiseId,
                currentUserId = currentUserId,
                onClose = { openCruiseId = null },
                onCruiseChanged = controller::onCruiseChanged,
                onCruiseDeleted = {
                    controller.onCruiseDeleted(it)
                    openCruiseId = null
                },
            )
        }
    }

    if (showCreate) {
        Dialog(
            onDismissRequest = { showCreate = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CruiseWizardHost(
                existing = null,
                onClose = { showCreate = false },
                onPublished = { cruise ->
                    showCreate = false
                    controller.onCruiseCreated(cruise)
                    openCruiseId = cruise.id
                },
            )
        }
    }
}

internal fun cruiseErrorMessage(
    error: Exception,
    network: String,
    auth: String,
    generic: String,
): String = when (error) {
    is CruisesError.Network -> network
    is CruisesError.AuthenticationRequired -> auth
    is CruisesError -> error.message ?: generic
    else -> generic
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CruiseListScreenContent(
    state: CruiseListUiState,
    onOpenCruise: (app.skipperclub.data.Cruise) -> Unit,
    onCreate: () -> Unit,
    onOpenFilters: () -> Unit,
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
                    text = stringResource(R.string.nav_cruises),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onCreate,
                    modifier = Modifier.testTag("cruises_create"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cruises_create),
                    )
                }
                IconButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.testTag("cruises_search"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.cruises_search),
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
                        modifier = Modifier.testTag("cruises_filters"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.cruises_filter),
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ListNavigationInset),
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    state.loadFailed && state.cruises.isEmpty() -> CruiseListMessage(
                        title = stringResource(R.string.cruises_load_failed),
                        actionLabel = stringResource(R.string.cruises_retry),
                        onAction = onRetry,
                    )

                    state.cruises.isEmpty() && state.hasLoadedOnce -> {
                        if (state.filters.activeCount > 0) {
                            CruiseListMessage(title = stringResource(R.string.cruises_empty_search))
                        } else {
                            CruiseListMessage(title = stringResource(R.string.cruises_empty_all_title))
                        }
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("cruises_list"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ListBottomInset),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(state.cruises, key = { it.id }) { cruise ->
                            CruiseCard(cruise = cruise, onClick = { onOpenCruise(cruise) })
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    CircularProgressIndicator(
                                        Modifier.align(Alignment.Center).size(28.dp),
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

@Composable
private fun CruiseListMessage(
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
            androidx.compose.material3.Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

private val previewListState = CruiseListUiState(
    cruises = listOf(
        previewCruise(role = app.skipperclub.data.CruiseUserRole.Organizer),
        previewCruise(id = "c2", title = "Croatia Milebuilding", participantsCount = 6),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "en")
@Composable
private fun CruiseListPreview() {
    SkipperClubTheme {
        CruiseListScreenContent(
            state = previewListState,
            onOpenCruise = {},
            onCreate = {},
            onOpenFilters = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CruiseListPreviewDark() {
    SkipperClubTheme {
        CruiseListScreenContent(
            state = previewListState,
            onOpenCruise = {},
            onCreate = {},
            onOpenFilters = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun CruiseListPreviewEmptyPl() {
    SkipperClubTheme {
        CruiseListScreenContent(
            state = CruiseListUiState(filters = CruiseFilters(scope = app.skipperclub.data.CruiseScope.Mine), hasLoadedOnce = true),
            onOpenCruise = {},
            onCreate = {},
            onOpenFilters = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
