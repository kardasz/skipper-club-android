package app.skipperclub.ui.main.spots

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Coordinates
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.RadioChannelKind
import app.skipperclub.data.RealPlaceSearch
import app.skipperclub.data.SessionStore
import app.skipperclub.data.Spot
import app.skipperclub.data.SpotsError
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Distinguishes whether the form is creating a new spot or editing an existing one. */
internal sealed interface SpotFormTarget {
    data object Create : SpotFormTarget
    data class Edit(val spot: Spot) : SpotFormTarget
}

/** Full-screen admin spots management surface launched from the main menu. */
@Composable
fun SpotsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        SpotsController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()
    val resources = LocalResources.current
    val context = LocalContext.current
    val placeSearch = remember(context) { RealPlaceSearch(context) }

    val errorNetwork = stringResource(R.string.spots_error_network)
    val errorAuth = stringResource(R.string.spots_error_auth)
    val errorForbidden = stringResource(R.string.spots_error_forbidden)
    val errorValidation = stringResource(R.string.spots_error_validation)
    val errorGeneric = stringResource(R.string.spots_error_generic)
    val errorDuplicate = stringResource(R.string.spots_error_duplicate)

    fun errorMessage(error: Exception): String = when (error) {
        is SpotsError.Network -> errorNetwork
        is SpotsError.AuthenticationRequired -> errorAuth
        is SpotsError.Forbidden -> errorForbidden
        is SpotsError.Duplicate -> error.message ?: errorDuplicate
        is SpotsError.Validation ->
            error.fieldErrors.values.firstOrNull { it.isNotBlank() }
                ?: error.message ?: errorValidation
        else -> errorGeneric
    }

    var formTarget by remember { mutableStateOf<SpotFormTarget?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) { controller.loadInitialIfNeeded() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is SpotsEvent.OperationFailed -> {
                    val message = errorMessage(event.error)
                    if (formTarget != null) formError = message
                    else notificationHostState.show(message, InAppNotificationType.Error)
                }

                is SpotsEvent.SpotCreated -> {
                    formTarget = null
                    formError = null
                    notificationHostState.show(
                        resources.getString(R.string.spots_created, event.spot.name),
                        InAppNotificationType.Success,
                    )
                }

                is SpotsEvent.SpotUpdated -> {
                    formTarget = null
                    formError = null
                    notificationHostState.show(
                        resources.getString(R.string.spots_updated, event.spot.name),
                        InAppNotificationType.Success,
                    )
                }

                is SpotsEvent.SpotDeleted ->
                    notificationHostState.show(
                        resources.getString(R.string.spots_deleted, event.name),
                        InAppNotificationType.Success,
                    )

                SpotsEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(enabled = formTarget != null, onBack = { if (!state.isSaving) formTarget = null })

    Surface(
        modifier = modifier.fillMaxSize().testTag("spots_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val target = formTarget
            if (target == null) {
                SpotsScreenContent(
                    state = state,
                    onClose = onClose,
                    onSearch = controller::search,
                    onCreateClick = {
                        formError = null
                        formTarget = SpotFormTarget.Create
                    },
                    onEditSpot = {
                        formError = null
                        formTarget = SpotFormTarget.Edit(it)
                    },
                    onDelete = controller::delete,
                    onRefresh = controller::refresh,
                    onLoadMore = controller::loadMore,
                    onRetry = controller::refresh,
                )
            } else {
                SpotFormContent(
                    initial = when (target) {
                        SpotFormTarget.Create -> SpotForm()
                        is SpotFormTarget.Edit -> SpotForm.fromSpot(target.spot)
                    },
                    isEditing = target is SpotFormTarget.Edit,
                    isSaving = state.isSaving,
                    errorMessage = formError,
                    onErrorConsumed = { formError = null },
                    onCancel = { if (!state.isSaving) formTarget = null },
                    onSubmit = { form ->
                        formError = null
                        when (target) {
                            SpotFormTarget.Create -> controller.createSpot(form)
                            is SpotFormTarget.Edit -> controller.updateSpot(target.spot, form)
                        }
                    },
                    searchPlaces = placeSearch::autocomplete,
                    onResolvePlace = { prediction -> placeSearch.resolve(prediction.placeId) },
                )
            }
            InAppNotificationHost(
                hostState = notificationHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotsScreenContent(
    state: SpotsUiState,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onCreateClick: () -> Unit,
    onEditSpot: (Spot) -> Unit,
    onDelete: (Spot) -> Unit,
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

    var selected by remember { mutableStateOf<Spot?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("spots_back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.spots_back),
                    )
                }
                Text(
                    text = stringResource(R.string.spots_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            SpotsSearchField(
                query = state.query,
                onSearch = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    state.loadFailed && state.spots.isEmpty() -> SpotsMessage(
                        title = stringResource(R.string.spots_load_failed),
                        actionLabel = stringResource(R.string.spots_retry),
                        onAction = onRetry,
                    )

                    state.spots.isEmpty() && state.hasLoadedOnce -> SpotsMessage(
                        title = stringResource(R.string.spots_empty_title),
                        subtitle = stringResource(R.string.spots_empty_subtitle),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("spots_list"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    ) {
                        items(state.spots, key = { it.id }) { spot ->
                            SpotRow(spot = spot, onClick = { selected = spot })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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

        ExtendedFloatingActionButton(
            onClick = onCreateClick,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.spot_create)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag("spots_create_fab"),
        )
    }

    selected?.let { spot ->
        val current = state.spots.firstOrNull { it.id == spot.id } ?: spot
        SpotDetailSheet(
            spot = current,
            onDismiss = { selected = null },
            onEdit = {
                selected = null
                onEditSpot(current)
            },
            onDelete = {
                selected = null
                onDelete(current)
            },
        )
    }
}

@Composable
private fun SpotRow(
    spot: Spot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("spot_item_${spot.id}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Anchor,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = spot.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatCoordinates(spot.coordinates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        SpotMetaChips(spot = spot)
    }
}

@Composable
private fun SpotMetaChips(spot: Spot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spot.phoneContacts.isNotEmpty()) {
            CountChip(text = "☎ ${spot.phoneContacts.size}")
        }
        if (spot.radioChannels.isNotEmpty()) {
            CountChip(text = "📻 ${spot.radioChannels.size}", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun CountChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotDetailSheet(
    spot: Spot,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                .testTag("spot_detail_sheet"),
        ) {
            Text(
                text = spot.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DetailRow(
                icon = Icons.Filled.Place,
                text = formatCoordinates(spot.coordinates),
            )

            if (spot.phoneContacts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.spot_form_contacts_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                spot.phoneContacts.forEach { contact ->
                    Text(
                        text = formatPhoneContact(contact),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (spot.radioChannels.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.spot_form_channels_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                spot.radioChannels.forEach { channel ->
                    Text(
                        text = formatRadioChannel(channel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            FilledTonalButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .testTag("spot_edit"),
            ) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(R.string.spot_edit), modifier = Modifier.padding(start = 8.dp))
            }

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("spot_delete"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.spot_delete),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.spot_delete_confirm_title)) },
            text = { Text(stringResource(R.string.spot_delete_confirm_message, spot.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("spot_delete_confirm"),
                ) {
                    Text(text = stringResource(R.string.spot_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.spots_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SpotsMessage(
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
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

// --- Formatting helpers ---

internal fun formatCoordinates(coordinates: Coordinates): String =
    "%.5f, %.5f".format(coordinates.lat, coordinates.lng)

internal fun formatPhoneContact(contact: PhoneContact): String {
    val ext = contact.extension?.takeIf { it.isNotBlank() }?.let { " ext. $it" }.orEmpty()
    val label = contact.label?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
    return "$label${contact.phone}$ext"
}

internal fun formatRadioChannel(channel: RadioChannel): String {
    val value = when (channel.channelKind) {
        RadioChannelKind.Vhf -> channel.vhfChannel?.let { "VHF $it" }.orEmpty()
        RadioChannelKind.Mhz -> channel.frequencyMhz?.let { "$it MHz" }.orEmpty()
    }
    val primary = if (channel.isPrimary) " ★" else ""
    return "${channel.name} · $value$primary".trim()
}

// --- Previews ---

internal fun previewSpot(
    id: String,
    name: String,
    lat: Double = 54.352,
    lng: Double = 18.653,
    phoneContacts: List<PhoneContact> = emptyList(),
    radioChannels: List<RadioChannel> = emptyList(),
): Spot = Spot(
    id = id,
    name = name,
    coordinates = Coordinates(lat, lng),
    phoneContacts = phoneContacts,
    radioChannels = radioChannels,
    createdAt = "2026-06-10T09:00:00Z",
    updatedAt = "2026-06-10T09:00:00Z",
)

private val previewState = SpotsUiState(
    spots = listOf(
        previewSpot(
            "s1",
            "Neptun Marina",
            phoneContacts = listOf(PhoneContact("c1", "Harbour master", "+48581234567", null)),
            radioChannels = listOf(RadioChannel("r1", "Port", RadioChannelKind.Vhf, 12, null, true)),
        ),
        previewSpot("s2", "Sopot Pier", lat = 54.441, lng = 18.567),
        previewSpot("s3", "Gdynia Marina", lat = 54.519, lng = 18.552),
    ),
    hasLoadedOnce = true,
)

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "en")
@Composable
private fun SpotsPreview() {
    SkipperClubTheme {
        SpotsScreenContent(
            state = previewState,
            onClose = {},
            onSearch = {},
            onCreateClick = {},
            onEditSpot = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SpotsPreviewDark() {
    SkipperClubTheme {
        SpotsScreenContent(
            state = previewState,
            onClose = {},
            onSearch = {},
            onCreateClick = {},
            onEditSpot = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun SpotsPreviewPl() {
    SkipperClubTheme {
        SpotsScreenContent(
            state = previewState,
            onClose = {},
            onSearch = {},
            onCreateClick = {},
            onEditSpot = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800, locale = "pl")
@Composable
private fun SpotsEmptyPreviewPl() {
    SkipperClubTheme {
        SpotsScreenContent(
            state = SpotsUiState(hasLoadedOnce = true),
            onClose = {},
            onSearch = {},
            onCreateClick = {},
            onEditSpot = {},
            onDelete = {},
            onRefresh = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
