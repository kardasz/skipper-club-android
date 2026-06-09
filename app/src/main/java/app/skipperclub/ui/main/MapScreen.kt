package app.skipperclub.ui.main

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.LocationOn
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
import app.skipperclub.R
import app.skipperclub.data.CheckInError
import app.skipperclub.data.CheckInsApi
import app.skipperclub.data.MapEntry
import app.skipperclub.data.MapEntryAttributes
import app.skipperclub.data.MapEntryKind
import app.skipperclub.data.MapEntryType
import app.skipperclub.data.MapItemsApi
import app.skipperclub.data.MapItemsError
import app.skipperclub.data.MapUserProjection
import app.skipperclub.data.MapViewportBounds
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.checkin.CheckInOverlay
import app.skipperclub.ui.main.checkin.CheckInUiState
import app.skipperclub.ui.main.checkin.fetchCurrentLocation
import app.skipperclub.ui.main.checkin.LocationLabel
import app.skipperclub.ui.main.checkin.reverseGeocode
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
import com.google.maps.android.compose.MarkerComposable
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
    val isActive = checkInState is CheckInUiState.Active
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapEntries by remember { mutableStateOf(emptyList<MapEntry>()) }
    var selectedMapEntryKey by remember { mutableStateOf<String?>(null) }

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
            mapEntries.forEach { entry ->
                key("${entry.kind}:${entry.id}") {
                    val markerKey = entry.markerKey
                    MapEntryMarker(
                        entry = entry,
                        selected = selectedMapEntryKey == markerKey,
                        onClick = {
                            selectedMapEntryKey = if (selectedMapEntryKey == markerKey) {
                                null
                            } else {
                                markerKey
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
            visible = isActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomBarMapPadding),
        ) {
            val active = checkInState as? CheckInUiState.Active
            Box(modifier = Modifier.fillMaxSize()) {
                LocationTargetOverlay(
                    locationLabel = active?.locationLabel ?: LocationLabel(),
                    isResolvingName = active?.isResolvingName == true,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        CheckInOverlay(
            state = checkInState,
            onStart = {
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
            onPermissionDenied = {
                checkInState = CheckInUiState.Idle
                notificationHostState.show(permissionErrorMessage, InAppNotificationType.Error)
            },
            bottomInset = BottomBarMapPadding + 28.dp,
        )

        InAppNotificationHost(
            hostState = notificationHostState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
@GoogleMapComposable
private fun MapEntryMarker(
    entry: MapEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val checkInAttributes = entry.checkInAttributes
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
        zIndex = if (entry.kind == MapEntryKind.Cluster) 2f else 1f,
    ) {
        if (entry.type == MapEntryType.CheckIn && checkInAttributes != null) {
            CheckInMapMarker(
                name = entry.name,
                attributes = checkInAttributes,
                selected = selected,
                avatarPainter = avatarPainter,
                isAvatarLoaded = isAvatarLoaded,
            )
        } else {
            MapEntryMarkerLabel(entry = entry)
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
private fun CenterMapPin(modifier: Modifier = Modifier) {
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
            contentDescription = stringResource(R.string.map_check_in_pin_content_description),
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

private val MapEntry.markerKey: String
    get() = "${kind.name}:$id"

private val MapEntry.checkInAttributes: MapEntryAttributes.CheckIn?
    get() = attributes as? MapEntryAttributes.CheckIn

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

private val GDANSK_BAY = LatLng(54.4877, 18.6654)
private const val DEFAULT_ZOOM = 10f
private const val ACTIVE_ZOOM = 16f
private val BottomBarMapPadding: Dp = 114.dp
