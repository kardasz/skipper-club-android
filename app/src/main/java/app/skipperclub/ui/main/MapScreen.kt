package app.skipperclub.ui.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.R
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertError
import app.skipperclub.data.AlertGeometry
import app.skipperclub.data.AlertsApi
import app.skipperclub.data.CheckInError
import app.skipperclub.data.CheckInsApi
import app.skipperclub.data.MapCoordinates
import app.skipperclub.data.MapEntry
import app.skipperclub.data.MapEntryAttributes
import app.skipperclub.data.MapEntryKind
import app.skipperclub.data.MapEntryType
import app.skipperclub.data.MapGeometry
import app.skipperclub.data.MapItemsApi
import app.skipperclub.data.MapItemsError
import app.skipperclub.data.MapUserProjection
import app.skipperclub.data.MapViewportBounds
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.PostType
import app.skipperclub.data.SessionStore
import app.skipperclub.data.SpotsApi
import app.skipperclub.ui.main.alert.AlertContentError
import app.skipperclub.ui.main.alert.AlertDetailSheet
import app.skipperclub.ui.main.alert.AlertDetailUiState
import app.skipperclub.ui.main.alert.AlertFormDialog
import app.skipperclub.ui.main.alert.AlertPickActions
import app.skipperclub.ui.main.alert.AlertUiState
import app.skipperclub.ui.main.posts.PostDetailScreen
import app.skipperclub.ui.main.posts.icon
import app.skipperclub.ui.main.checkin.CheckInOverlay
import app.skipperclub.ui.main.checkin.CheckInUiState
import app.skipperclub.ui.main.checkin.fetchCurrentLocation
import app.skipperclub.ui.main.checkin.LocationLabel
import app.skipperclub.ui.main.checkin.reverseGeocode
import app.skipperclub.ui.main.spotdetail.SpotDetailSheet
import app.skipperclub.ui.main.spotdetail.SpotDetailUiState
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    MapScreenContent(modifier = modifier)
}

@Composable
private fun MapScreenContent(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        MapPreviewSurface(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationHostState = rememberInAppNotificationHostState()

    val permissionErrorMessage = stringResource(R.string.map_check_in_error_permission)
    val locationErrorMessage = stringResource(R.string.map_check_in_error_location)
    val networkErrorMessage = stringResource(R.string.map_check_in_error_network)
    val authErrorMessage = stringResource(R.string.map_check_in_error_auth)
    val genericErrorMessage = stringResource(R.string.map_check_in_error_generic)
    val successMessageTemplate = stringResource(R.string.map_check_in_success)
    val successNoNameMessage = stringResource(R.string.map_check_in_success_no_name)
    val mapItemsNetworkErrorMessage = stringResource(R.string.map_items_error_network)
    val mapItemsAuthErrorMessage = stringResource(R.string.map_items_error_auth)
    val mapItemsGenericErrorMessage = stringResource(R.string.map_items_error_generic)
    val alertNetworkErrorMessage = stringResource(R.string.alert_error_network)
    val alertAuthErrorMessage = stringResource(R.string.alert_error_auth)
    val alertGenericErrorMessage = stringResource(R.string.alert_error_generic)
    val alertSuccessMessage = stringResource(R.string.alert_success)

    val startPosition = remember {
        CameraPosition.fromLatLngZoom(GDANSK_BAY, DEFAULT_ZOOM)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = startPosition
    }
    val mapProperties = remember {
        MapProperties(
            isBuildingEnabled = true,
            mapType = MapType.NORMAL,
            minZoomPreference = 5f,
        )
    }
    val mapUiSettings = remember {
        MapUiSettings(
            compassEnabled = true,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        )
    }

    var checkInState by remember { mutableStateOf<CheckInUiState>(CheckInUiState.Idle) }
    var alertState by remember { mutableStateOf<AlertUiState>(AlertUiState.Idle) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isActive = checkInState is CheckInUiState.Active
    val isAlertPicking = alertState is AlertUiState.PickingLocation
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapEntries by remember { mutableStateOf(emptyList<MapEntry>()) }
    var selectedMapEntryKey by remember { mutableStateOf<String?>(null) }
    var spotDetailState by remember { mutableStateOf<SpotDetailUiState?>(null) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var alertDetail by remember { mutableStateOf<AlertDetailUiState?>(null) }

    // Spot map items only carry counts; the full phone/radio detail is fetched
    // on demand when the user taps the marker. Guard every assignment on the
    // currently open spot id so a late response can't reopen a dismissed sheet.
    val loadSpotDetail: (String, String) -> Unit = { spotId, name ->
        spotDetailState = SpotDetailUiState.Loading(spotId, name)
        scope.launch {
            val token = SessionStore.validSession()?.accessToken
            if (token.isNullOrBlank()) {
                if (spotDetailState?.spotId == spotId) {
                    spotDetailState = SpotDetailUiState.Failed(spotId, name)
                }
                notificationHostState.show(mapItemsAuthErrorMessage, InAppNotificationType.Error)
                return@launch
            }
            try {
                val spot = SpotsApi.get(accessToken = token, spotId = spotId)
                if (spotDetailState?.spotId == spotId) {
                    spotDetailState = SpotDetailUiState.Ready(spot)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (spotDetailState?.spotId == spotId) {
                    spotDetailState = SpotDetailUiState.Failed(spotId, name)
                }
            }
        }
    }

    LaunchedEffect(isMapLoaded) {
        if (!isMapLoaded) return@LaunchedEffect
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collectLatest { moving ->
                if (moving) return@collectLatest
                delay(300)
                val bounds = cameraPositionState.visibleViewportBounds() ?: return@collectLatest
                val token = SessionStore.validSession()?.accessToken
                if (token.isNullOrBlank()) {
                    mapEntries = emptyList()
                    notificationHostState.show(mapItemsAuthErrorMessage, InAppNotificationType.Error)
                    return@collectLatest
                }
                try {
                    mapEntries = MapItemsApi.list(accessToken = token, bounds = bounds).entries
                } catch (e: MapItemsError) {
                    notificationHostState.show(
                        e.userMessage(
                            network = mapItemsNetworkErrorMessage,
                            auth = mapItemsAuthErrorMessage,
                            generic = mapItemsGenericErrorMessage,
                        ),
                        InAppNotificationType.Error,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    notificationHostState.show(mapItemsGenericErrorMessage, InAppNotificationType.Error)
                }
            }
    }

    LaunchedEffect(mapEntries, selectedMapEntryKey) {
        val selectedKey = selectedMapEntryKey ?: return@LaunchedEffect
        if (mapEntries.none { it.markerKey == selectedKey }) {
            selectedMapEntryKey = null
        }
    }

    // Re-geocode when the map stops moving (camera target = pin position).
    // While the camera is moving the user is still aiming; only fire after a brief
    // settle delay so we don't burn quota on every micro-pan.
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        // Mark resolving immediately so the spinner appears as soon as the user
        // starts dragging the map.
        (checkInState as? CheckInUiState.Active)?.let {
            checkInState = it.copy(isResolvingName = true)
        }
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collect { moving ->
                val current = checkInState as? CheckInUiState.Active ?: return@collect
                if (moving) {
                    if (!current.isResolvingName) {
                        checkInState = current.copy(isResolvingName = true)
                    }
                    return@collect
                }
                // Camera settled — debounce a touch, then geocode the new target.
                delay(300)
                val target = cameraPositionState.position.target
                val resolved = runCatching { context.reverseGeocode(target.latitude, target.longitude) }
                    .getOrNull()
                val latest = checkInState as? CheckInUiState.Active ?: return@collect
                checkInState = latest.copy(
                    locationLabel = resolved ?: LocationLabel(),
                    isResolvingName = false,
                )
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentDescription = stringResource(R.string.map_content_description),
            contentPadding = PaddingValues(bottom = BottomBarMapPadding),
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapLoaded = { isMapLoaded = true },
            onMapClick = { selectedMapEntryKey = null },
            properties = mapProperties,
            uiSettings = mapUiSettings,
        ) {
            // Area-based alerts (Polygon / MultiPolygon) are drawn as filled
            // overlays beneath the markers so the affected zone is visible, not
            // just its anchor pin.
            val alertAreaColor = MaterialTheme.colorScheme.error
            mapEntries.forEach { entry ->
                entry.alertPolygonRings().forEachIndexed { index, ring ->
                    key("poly:${entry.id}:$index") {
                        Polygon(
                            points = ring,
                            fillColor = alertAreaColor.copy(alpha = 0.14f),
                            strokeColor = alertAreaColor.copy(alpha = 0.85f),
                            strokeWidth = 4f,
                        )
                    }
                }
            }

            mapEntries.forEach { entry ->
                key("${entry.kind}:${entry.id}") {
                    val markerKey = entry.markerKey
                    MapEntryMarker(
                        entry = entry,
                        selected = selectedMapEntryKey == markerKey,
                        onClick = {
                            when {
                                entry.kind == MapEntryKind.Cluster -> {
                                    selectedMapEntryKey = null
                                    scope.launch { cameraPositionState.zoomIntoCluster(entry) }
                                }

                                entry.type == MapEntryType.Spot -> {
                                    selectedMapEntryKey = null
                                    loadSpotDetail(entry.id, entry.name)
                                }

                                entry.type == MapEntryType.Post -> {
                                    selectedMapEntryKey = null
                                    selectedPostId = entry.id
                                }

                                entry.type == MapEntryType.NavigationAlert &&
                                    entry.alertAttributes != null -> {
                                    selectedMapEntryKey = null
                                    alertDetail = AlertDetailUiState(
                                        title = entry.name,
                                        attributes = entry.alertAttributes!!,
                                    )
                                }

                                else -> {
                                    selectedMapEntryKey = if (selectedMapEntryKey == markerKey) {
                                        null
                                    } else {
                                        markerKey
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        // Static pin overlay, fixed to the centre of the *visible* map area
        // (= the area not covered by our bottom bar). The map's contentPadding
        // makes `cameraPositionState.position.target` resolve to this same point,
        // so what the user sees is what we send.
        AnimatedVisibility(
            visible = isActive || isAlertPicking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomBarMapPadding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isAlertPicking) {
                    AlertTargetOverlay(modifier = Modifier.align(Alignment.Center))
                } else {
                    val active = checkInState as? CheckInUiState.Active
                    LocationTargetOverlay(
                        locationLabel = active?.locationLabel ?: LocationLabel(),
                        isResolvingName = active?.isResolvingName == true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (checkInState is CheckInUiState.Idle && alertState is AlertUiState.Idle) {
            MapAddMenu(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                onSelectCheckIn = {
                    checkInState = CheckInUiState.Locating
                    scope.launch {
                        val location = runCatching { context.fetchCurrentLocation() }.getOrNull()
                        if (location == null) {
                            checkInState = CheckInUiState.Idle
                            notificationHostState.show(locationErrorMessage, InAppNotificationType.Error)
                            return@launch
                        }
                        val target = LatLng(location.latitude, location.longitude)
                        checkInState = CheckInUiState.Active(
                            locationLabel = LocationLabel(),
                            isResolvingName = true,
                            isSubmitting = false,
                        )
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(target, ACTIVE_ZOOM),
                            durationMs = 600,
                        )
                        // After animate() returns the camera has settled, which means
                        // snapshotFlow above won't re-trigger; kick off geocoding now.
                        val resolved = runCatching { context.reverseGeocode(target.latitude, target.longitude) }
                            .getOrNull()
                        val latest = checkInState as? CheckInUiState.Active ?: return@launch
                        checkInState = latest.copy(
                            locationLabel = resolved ?: LocationLabel(),
                            isResolvingName = false,
                        )
                    }
                },
                onPermissionDenied = {
                    notificationHostState.show(permissionErrorMessage, InAppNotificationType.Error)
                },
                onSelectAlert = { alertState = AlertUiState.PickingLocation },
                bottomInset = BottomBarMapPadding + 28.dp,
            )
        }

        CheckInOverlay(
            state = checkInState,
            onConfirm = {
                val active = checkInState as? CheckInUiState.Active ?: return@CheckInOverlay
                val target = cameraPositionState.position.target
                checkInState = active.copy(isSubmitting = true)
                scope.launch {
                    val token = SessionStore.validSession()?.accessToken
                    if (token.isNullOrBlank()) {
                        checkInState = active.copy(isSubmitting = false)
                        notificationHostState.show(authErrorMessage, InAppNotificationType.Error)
                        return@launch
                    }
                    try {
                        CheckInsApi.upsert(
                            accessToken = token,
                            lat = target.latitude,
                            lng = target.longitude,
                            locationName = active.locationLabel.submissionLabel,
                        )
                        val submittedLabel = active.locationLabel.submissionLabel
                        val message = if (submittedLabel != null) {
                            successMessageTemplate.format(submittedLabel)
                        } else {
                            successNoNameMessage
                        }
                        checkInState = CheckInUiState.Idle
                        notificationHostState.show(message, InAppNotificationType.Success)
                    } catch (e: CheckInError) {
                        checkInState = active.copy(isSubmitting = false)
                        notificationHostState.show(
                            e.userMessage(networkErrorMessage, authErrorMessage, genericErrorMessage),
                            InAppNotificationType.Error,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        checkInState = active.copy(isSubmitting = false)
                        notificationHostState.show(genericErrorMessage, InAppNotificationType.Error)
                    }
                }
            },
            onCancel = { checkInState = CheckInUiState.Idle },
            bottomInset = BottomBarMapPadding + 28.dp,
        )

        if (alertState is AlertUiState.PickingLocation) {
            AlertPickActions(
                onNext = {
                    val target = cameraPositionState.position.target
                    alertState = AlertUiState.Form(
                        lat = target.latitude,
                        lng = target.longitude,
                        category = AlertCategory.NavigationWarning,
                        content = "",
                    )
                },
                onCancel = { alertState = AlertUiState.Idle },
                bottomInset = BottomBarMapPadding + 28.dp,
            )
        }

        (alertState as? AlertUiState.Form)?.let { form ->
            AlertFormDialog(
                state = form,
                onCategorySelected = { category ->
                    (alertState as? AlertUiState.Form)?.let { alertState = it.copy(category = category) }
                },
                onContentChange = { content ->
                    (alertState as? AlertUiState.Form)?.let {
                        alertState = it.copy(content = content, contentError = null)
                    }
                },
                onSave = {
                    val current = alertState as? AlertUiState.Form ?: return@AlertFormDialog
                    if (current.content.isBlank()) {
                        alertState = current.copy(contentError = AlertContentError.Required)
                        return@AlertFormDialog
                    }
                    alertState = current.copy(isSubmitting = true, contentError = null)
                    scope.launch {
                        val token = SessionStore.validSession()?.accessToken
                        if (token.isNullOrBlank()) {
                            (alertState as? AlertUiState.Form)?.let { alertState = it.copy(isSubmitting = false) }
                            notificationHostState.show(alertAuthErrorMessage, InAppNotificationType.Error)
                            return@launch
                        }
                        try {
                            AlertsApi.create(
                                accessToken = token,
                                category = current.category,
                                content = current.content,
                                geometry = AlertGeometry.point(current.lat, current.lng),
                            )
                            alertState = AlertUiState.Idle
                            notificationHostState.show(alertSuccessMessage, InAppNotificationType.Success)
                            // Refresh so the freshly created alert shows on the map
                            // without waiting for the next camera move.
                            val bounds = cameraPositionState.visibleViewportBounds()
                            if (bounds != null) {
                                runCatching { mapEntries = MapItemsApi.list(token, bounds).entries }
                            }
                        } catch (e: AlertError.Validation) {
                            (alertState as? AlertUiState.Form)?.let {
                                if (e.fieldErrors.containsKey("content")) {
                                    alertState = it.copy(
                                        isSubmitting = false,
                                        contentError = AlertContentError.Required,
                                    )
                                } else {
                                    alertState = it.copy(isSubmitting = false)
                                    notificationHostState.show(alertGenericErrorMessage, InAppNotificationType.Error)
                                }
                            }
                        } catch (e: AlertError) {
                            (alertState as? AlertUiState.Form)?.let { alertState = it.copy(isSubmitting = false) }
                            notificationHostState.show(
                                e.userMessage(alertNetworkErrorMessage, alertAuthErrorMessage, alertGenericErrorMessage),
                                InAppNotificationType.Error,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            (alertState as? AlertUiState.Form)?.let { alertState = it.copy(isSubmitting = false) }
                            notificationHostState.show(alertGenericErrorMessage, InAppNotificationType.Error)
                        }
                    }
                },
                onDismiss = { alertState = AlertUiState.Idle },
            )
        }

        spotDetailState?.let { detail ->
            SpotDetailSheet(
                state = detail,
                onDismiss = { spotDetailState = null },
                onCall = { contact -> context.dialPhoneContact(contact) },
                onRetry = { loadSpotDetail(detail.spotId, detail.name) },
            )
        }

        alertDetail?.let { detail ->
            AlertDetailSheet(
                state = detail,
                onDismiss = { alertDetail = null },
                onOpenSource = { url -> context.openUrl(url) },
            )
        }

        selectedPostId?.let { postId ->
            Dialog(
                onDismissRequest = { selectedPostId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                PostDetailScreen(
                    postId = postId,
                    focusComments = false,
                    onClose = { selectedPostId = null },
                )
            }
        }

        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Opens the system dialer pre-filled with the contact's number. Uses
 * [Intent.ACTION_DIAL] (no `CALL_PHONE` permission required) so the user
 * confirms the call. Best-effort: silently no-ops if no dialer is available.
 */
private fun android.content.Context.dialPhoneContact(contact: PhoneContact) {
    val number = contact.phone.trim().takeIf { it.isNotBlank() } ?: return
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/** Opens an external URL (e.g. an official alert source) in the browser. */
private fun android.content.Context.openUrl(url: String) {
    val target = url.trim().takeIf { it.isNotBlank() } ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

@Composable
@GoogleMapComposable
private fun MapEntryMarker(
    entry: MapEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val checkInAttributes = entry.checkInAttributes
    val alertAttributes = entry.alertAttributes
    val avatarUrl = checkInAttributes?.user?.avatarUrl?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    val avatarPainter = avatarUrl?.let { url ->
        rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build(),
            contentScale = ContentScale.Crop,
        )
    }
    val avatarState = if (avatarPainter != null) {
        val state by avatarPainter.state.collectAsState()
        state
    } else {
        null
    }
    val isAvatarLoaded = avatarState is AsyncImagePainter.State.Success

    MarkerComposable(
        entry.kind,
        entry.id,
        entry.name,
        entry.count ?: 0,
        selected,
        avatarUrl ?: "",
        avatarState?.snapshotKey ?: "no-avatar",
        state = rememberUpdatedMarkerState(
            position = LatLng(entry.coordinates.lat, entry.coordinates.lng),
        ),
        contentDescription = entry.name,
        title = entry.name,
        onClick = {
            onClick()
            true
        },
        zIndex = when {
            selected -> 3f
            entry.kind == MapEntryKind.Cluster -> 2f
            else -> 1f
        },
    ) {
        when {
            entry.kind == MapEntryKind.Cluster -> MapEntryMarkerLabel(entry = entry)

            entry.type == MapEntryType.CheckIn && checkInAttributes != null -> CheckInMapMarker(
                name = entry.name,
                attributes = checkInAttributes,
                selected = selected,
                avatarPainter = avatarPainter,
                isAvatarLoaded = isAvatarLoaded,
            )

            entry.type == MapEntryType.NavigationAlert && alertAttributes != null -> AlertMarkerPin()

            entry.type == MapEntryType.Spot -> SpotMapMarker(name = entry.name)

            entry.type == MapEntryType.Post ->
                PostMapMarker(name = entry.name, postType = entry.postAttributes?.postType)

            else -> MapEntryMarkerLabel(entry = entry)
        }
    }
}

@Composable
private fun MapEntryMarkerLabel(entry: MapEntry) {
    val isCluster = entry.kind == MapEntryKind.Cluster
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCluster) colors.primary else colors.surface
    val contentColor = if (isCluster) colors.onPrimary else colors.onSurface

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            border = if (isCluster) null else BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.widthIn(max = 164.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(if (isCluster) 12.dp else 9.dp)
                .clip(CircleShape)
                .background(containerColor),
        )
    }
}

/**
 * Spot marker: an anchor glyph in a teardrop pin with the spot name as a label
 * above it. Colour (tertiary) keeps it visually distinct from check-in pins
 * (primary) and alert pins (error). Tapping opens the spot detail sheet.
 */
@Composable
private fun SpotMapMarker(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 164.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        SpotMarkerPin()
    }
}

@Composable
private fun SpotMarkerPin(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .width(40.dp)
            .height(46.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(13.dp)
                .rotate(45f)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.tertiary),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.tertiary)
                .border(2.dp, colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Anchor,
                contentDescription = null,
                tint = colors.onTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Post marker: a per-[PostType] icon in a teardrop pin with the post name as a
 * label above it. Colour (secondary) distinguishes posts from check-ins,
 * alerts and spots. Tapping opens the full post detail screen.
 */
@Composable
private fun PostMapMarker(name: String, postType: PostType?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 164.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        PostMarkerPin(postType = postType)
    }
}

@Composable
private fun PostMarkerPin(postType: PostType?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .width(40.dp)
            .height(46.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(13.dp)
                .rotate(45f)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.secondary),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.secondary)
                .border(2.dp, colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = (postType ?: PostType.Place).icon(),
                contentDescription = null,
                tint = colors.onSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CheckInMapMarker(
    name: String,
    attributes: MapEntryAttributes.CheckIn,
    selected: Boolean,
    avatarPainter: Painter?,
    isAvatarLoaded: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (selected) {
            CheckInInfoBubble(
                displayName = attributes.user.displayName.ifBlank { name },
                checkedInAt = attributes.checkedInAt,
                modifier = Modifier.widthIn(max = 236.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        CheckInAvatarPin(
            user = attributes.user,
            fallbackName = name,
            avatarPainter = avatarPainter,
            isAvatarLoaded = isAvatarLoaded,
        )
    }
}

@Composable
private fun CheckInInfoBubble(
    displayName: String,
    checkedInAt: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = checkInRelativeStatus(checkedInAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CheckInAvatarPin(
    user: MapUserProjection,
    fallbackName: String,
    avatarPainter: Painter?,
    isAvatarLoaded: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val avatarName = user.displayName.ifBlank { fallbackName }
    Box(
        modifier = modifier
            .width(56.dp)
            .height(62.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(16.dp)
                .rotate(45f)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.primary),
        )
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer)
                .border(3.dp, colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarPainter != null && isAvatarLoaded) {
                Image(
                    painter = avatarPainter,
                    contentDescription = avatarName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = avatarName.initials(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Alert marker: a warning icon in a teardrop pin. Visually distinct
 * from check-in pins (error colour, warning glyph) so every alert reads as an
 * alert at a glance, per the product ask.
 */
@Composable
private fun AlertMarkerPin(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .width(44.dp)
            .height(50.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(14.dp)
                .rotate(45f)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.error),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.error)
                .border(2.dp, colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.onError,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun checkInRelativeStatus(checkedInAt: String): String {
    var nowMillis by remember(checkedInAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(checkedInAt) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val elapsedMinutes = remember(checkedInAt, nowMillis) {
        runCatching {
            Duration.between(Instant.parse(checkedInAt), Instant.ofEpochMilli(nowMillis))
                .toMinutes()
                .coerceAtLeast(0)
        }.getOrNull()
    } ?: return stringResource(R.string.map_check_in_bubble_recent)

    return when {
        elapsedMinutes < 1 -> stringResource(R.string.map_check_in_bubble_now)
        elapsedMinutes < 60 -> pluralStringResource(
            R.plurals.map_check_in_bubble_minutes,
            elapsedMinutes.toInt(),
            elapsedMinutes,
        )

        elapsedMinutes < 1_440 -> {
            val hours = (elapsedMinutes / 60).coerceAtLeast(1)
            pluralStringResource(R.plurals.map_check_in_bubble_hours, hours.toInt(), hours)
        }

        else -> {
            val days = (elapsedMinutes / 1_440).coerceAtLeast(1)
            pluralStringResource(R.plurals.map_check_in_bubble_days, days.toInt(), days)
        }
    }
}

/**
 * The pin used to mark the camera target. The icon's "tip" points downward, so we
 * shift the whole icon up by half its height — that way the geometric centre of the
 * map (= the camera's target LatLng) sits exactly under the tip.
 */
@Composable
private fun CenterMapPin(
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.map_check_in_pin_content_description),
) {
    val pinSize = 56.dp
    Box(modifier = modifier.size(pinSize)) {
        // Small ground anchor at the true centre so the user can see exactly which
        // pixel will be sent as their location.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
        )
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(pinSize / 2))
                .size(pinSize),
        )
    }
}

@Composable
private fun LocationTargetOverlay(
    locationLabel: LocationLabel,
    isResolvingName: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LocationSummaryPill(
            label = locationLabel,
            isResolving = isResolvingName,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-96).dp),
        )
        CenterMapPin(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun AlertTargetOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-96).dp)
                .widthIn(max = 300.dp),
        ) {
            Text(
                text = stringResource(R.string.alert_pick_location_hint),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        CenterMapPin(
            modifier = Modifier.align(Alignment.Center),
            contentDescription = stringResource(R.string.alert_pin_content_description),
        )
    }
}

@Composable
private fun LocationSummaryPill(
    label: LocationLabel,
    isResolving: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = label.title ?: if (isResolving) {
        stringResource(R.string.map_check_in_location_resolving)
    } else {
        stringResource(R.string.map_check_in_location_unknown)
    }
    val subtitle = label.subtitle

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(max = 300.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isResolving) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun CheckInError.userMessage(
    network: String,
    auth: String,
    generic: String,
): String = when (this) {
    is CheckInError.Network -> network
    is CheckInError.AuthenticationRequired -> auth
    is CheckInError.RateLimited,
    is CheckInError.Validation,
    is CheckInError.Server,
    -> generic
}

private fun MapItemsError.userMessage(
    network: String,
    auth: String,
    generic: String,
): String = when (this) {
    is MapItemsError.Network -> network
    is MapItemsError.AuthenticationRequired -> auth
    is MapItemsError.RateLimited,
    is MapItemsError.Validation,
    is MapItemsError.Server,
    -> generic
}

private fun AlertError.userMessage(
    network: String,
    auth: String,
    generic: String,
): String = when (this) {
    is AlertError.Network -> network
    is AlertError.AuthenticationRequired -> auth
    is AlertError.RateLimited,
    is AlertError.Validation,
    is AlertError.Server,
    -> generic
}

private val MapEntry.markerKey: String
    get() = "${kind.name}:$id"

private val MapEntry.checkInAttributes: MapEntryAttributes.CheckIn?
    get() = attributes as? MapEntryAttributes.CheckIn

private val MapEntry.alertAttributes: MapEntryAttributes.NavigationAlert?
    get() = attributes as? MapEntryAttributes.NavigationAlert

private val MapEntry.postAttributes: MapEntryAttributes.Post?
    get() = attributes as? MapEntryAttributes.Post

/** Outer rings of any `Polygon` / `MultiPolygon` geometry, as map-ready points. */
private fun MapEntry.alertPolygonRings(): List<List<LatLng>> {
    if (type != MapEntryType.NavigationAlert) return emptyList()
    return when (val geom = geometry) {
        is MapGeometry.Polygon -> geom.rings.map { ring -> ring.toLatLngList() }
        is MapGeometry.MultiPolygon -> geom.polygons.mapNotNull { polygon ->
            polygon.firstOrNull()?.toLatLngList()
        }
        else -> emptyList()
    }.filter { it.size >= 3 }
}

private fun List<MapCoordinates>.toLatLngList(): List<LatLng> =
    map { LatLng(it.lat, it.lng) }

/**
 * Animates the camera to a tapped cluster so its members spread out. Prefers the
 * cluster bounds (the exact extent of its items); falls back to a fixed zoom step
 * when bounds are unavailable.
 */
private suspend fun CameraPositionState.zoomIntoCluster(entry: MapEntry) {
    val bounds = entry.bounds
    val update = if (bounds != null && bounds.north > bounds.south) {
        CameraUpdateFactory.newLatLngBounds(
            LatLngBounds(
                LatLng(bounds.south, bounds.west),
                LatLng(bounds.north, bounds.east),
            ),
            CLUSTER_BOUNDS_PADDING_PX,
        )
    } else {
        CameraUpdateFactory.newLatLngZoom(
            LatLng(entry.coordinates.lat, entry.coordinates.lng),
            (position.zoom + 2f).coerceAtMost(MAX_CLUSTER_ZOOM),
        )
    }
    runCatching { animate(update = update, durationMs = 500) }
}

private val AsyncImagePainter.State.snapshotKey: String
    get() = when (this) {
        is AsyncImagePainter.State.Empty -> "empty"
        is AsyncImagePainter.State.Loading -> "loading"
        is AsyncImagePainter.State.Success -> "success:${result.memoryCacheKey}"
        is AsyncImagePainter.State.Error -> "error:${result.throwable::class.qualifiedName}"
    }

private fun String.initials(): String {
    val initials = trim()
        .split(Regex("\\s+"))
        .asSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .toList()
        .joinToString("")

    return initials.ifBlank { "SC" }
}

private fun CameraPositionState.visibleViewportBounds(): MapViewportBounds? {
    val bounds = projection?.visibleRegion?.latLngBounds ?: return null
    return MapViewportBounds(
        north = bounds.northeast.latitude,
        south = bounds.southwest.latitude,
        east = bounds.northeast.longitude,
        west = bounds.southwest.longitude,
    )
}

@Composable
private fun MapPreviewSurface(modifier: Modifier = Modifier) {
    val water = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val land = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
    val route = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(water),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (-110).dp, y = 190.dp)
                .clip(CircleShape)
                .background(land),
        )
        PreviewPoint(Modifier.offset(x = 148.dp, y = 240.dp), route)
        PreviewPoint(Modifier.offset(x = 190.dp, y = 330.dp), route)
        PreviewPoint(Modifier.offset(x = 238.dp, y = 418.dp), route)
    }
}

@Composable
private fun PreviewPoint(modifier: Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun MapScreenPreviewEn() {
    SkipperClubTheme {
        MapScreenContent()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun MapScreenPreviewPl() {
    SkipperClubTheme {
        MapScreenContent()
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 740,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MapScreenPreviewDark() {
    SkipperClubTheme {
        MapScreenContent()
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun CheckInMapMarkerPreviewEn() {
    SkipperClubTheme {
        CheckInMarkerPreviewContent()
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun CheckInMapMarkerPreviewPl() {
    SkipperClubTheme {
        CheckInMarkerPreviewContent()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CheckInMapMarkerPreviewDark() {
    SkipperClubTheme {
        CheckInMarkerPreviewContent()
    }
}

@Composable
private fun CheckInMarkerPreviewContent() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
    ) {
        CheckInMapMarker(
            name = "Krzysztof",
            attributes = MapEntryAttributes.CheckIn(
                user = MapUserProjection(
                    id = "preview-user",
                    displayName = "Krzysztof",
                    avatarUrl = null,
                ),
                checkedInAt = Instant.now().minusSeconds(65).toString(),
                locationName = "Marina Kornati",
            ),
            selected = true,
            avatarPainter = null,
            isAvatarLoaded = false,
        )
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun SpotMapMarkerPreviewEn() {
    SkipperClubTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        ) {
            SpotMapMarker(name = "Marina Sopot")
        }
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun SpotMapMarkerPreviewPl() {
    SkipperClubTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        ) {
            SpotMapMarker(name = "Przystań Górki Zachodnie")
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpotMapMarkerPreviewDark() {
    SkipperClubTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        ) {
            SpotMapMarker(name = "Marina Sopot")
        }
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun PostMapMarkerPreviewEn() {
    SkipperClubTheme {
        MarkerPreviewBox { PostMapMarker(name = "Sopocki bulwar", postType = PostType.Photo) }
    }
}

@Preview(showBackground = true, locale = "pl")
@Composable
private fun PostMapMarkerPreviewPl() {
    SkipperClubTheme {
        MarkerPreviewBox { PostMapMarker(name = "Marina Gdynia", postType = PostType.Marina) }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostMapMarkerPreviewDark() {
    SkipperClubTheme {
        MarkerPreviewBox { PostMapMarker(name = "Trasa Hel", postType = PostType.Route) }
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun AlertMarkerPinPreviewEn() {
    SkipperClubTheme {
        MarkerPreviewBox { AlertMarkerPin() }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlertMarkerPinPreviewDark() {
    SkipperClubTheme {
        MarkerPreviewBox { AlertMarkerPin() }
    }
}

@Composable
private fun MarkerPreviewBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
    ) {
        content()
    }
}

private val GDANSK_BAY = LatLng(54.4877, 18.6654)
private const val DEFAULT_ZOOM = 10f
private const val ACTIVE_ZOOM = 16f
private const val MAX_CLUSTER_ZOOM = 18f
private const val CLUSTER_BOUNDS_PADDING_PX = 120
private val BottomBarMapPadding: Dp = 114.dp
