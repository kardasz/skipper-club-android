package app.skipperclub.ui.main.posts.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.SessionUser
import app.skipperclub.ui.main.UserAvatar
import app.skipperclub.ui.main.alert.labelRes
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

/** Visibility of the composer's bottom sheets + transient media-picker feedback. */
internal class WizardSheets {
    var location by mutableStateOf(false)
    var locationMap by mutableStateOf(false)
    var route by mutableStateOf(false)
    var tags by mutableStateOf(false)
    var mediaOversizeRejected by mutableStateOf(false)
}

/**
 * The composer body: who-row, borderless text and the attachments added so far
 * (location chip, media strip, route card, alert badge, tag chips) rendered
 * inline in post order.
 */
@Composable
internal fun WizardComposer(
    state: PostWizardState,
    sheets: WizardSheets,
    user: SessionUser?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (user != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UserAvatar(user = user, selected = false, modifier = Modifier.size(36.dp))
                Text(text = user.name, style = MaterialTheme.typography.titleSmall)
            }
        }
        WizardTextField(state)
        if (
            state.locationName != null ||
            state.coordinates != null ||
            PostWizardError.AlertLocationRequired in state.visibleErrors
        ) {
            WizardLocationChip(
                state = state,
                onEdit = { sheets.location = true },
                onOpenMap = { sheets.locationMap = true },
            )
        }
        if (state.media.isNotEmpty() || sheets.mediaOversizeRejected) {
            WizardMediaStrip(state = state, sheets = sheets)
        }
        if (state.routeEnabled) {
            WizardRouteCard(state = state, onEdit = { sheets.route = true })
        }
        if (state.editingAlert != null) {
            WizardAlertBadge(state)
        }
        if (state.taggedUsers.isNotEmpty()) {
            WizardTagChips(state)
        }
    }
}

@Composable
private fun WizardTextField(state: PostWizardState) {
    val textStyle = MaterialTheme.typography.bodyLarge
    Column {
        Box {
            BasicTextField(
                value = state.text,
                onValueChange = state::updateText,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp)
                    .testTag("wizard_text"),
            )
            if (state.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.wizard_text_label),
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (PostWizardError.TextRequired in state.visibleErrors) {
            Text(
                text = stringResource(R.string.wizard_error_text_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WizardLocationChip(
    state: PostWizardState,
    onEdit: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Column {
        state.locationName?.let { name ->
            InputChip(
                selected = false,
                onClick = onEdit,
                label = { Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.coordinates != null) {
                            IconButton(
                                onClick = onOpenMap,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("wizard_location_map"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Map,
                                    contentDescription = stringResource(R.string.wizard_location_map),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = state::clearLocation,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.wizard_location_clear),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("wizard_location_chip"),
            )
        }
        if (PostWizardError.AlertLocationRequired in state.visibleErrors) {
            Text(
                text = stringResource(R.string.wizard_error_alert_location_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WizardMediaStrip(
    state: PostWizardState,
    sheets: WizardSheets,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            state.media.forEach { item ->
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    when {
                        item.isUploading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(26.dp))
                            }
                        }

                        item.failed -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BrokenImage,
                                    contentDescription = stringResource(R.string.wizard_media_failed),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        else -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.publicUrl)
                                    .crossfade(enable = true)
                                    .build(),
                                contentDescription = item.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (item.isVideo) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = stringResource(R.string.post_video_badge),
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(30.dp),
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { state.removeMedia(item.localId) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.wizard_media_remove),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
        if (sheets.mediaOversizeRejected) {
            Text(
                text = stringResource(R.string.wizard_media_too_large),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WizardRouteCard(
    state: PostWizardState,
    onEdit: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_route_card"),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AltRoute,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = state.stops.joinToString(separator = " → ") { it.name },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = routeSummary(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.wizard_route_edit),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun routeSummary(state: PostWizardState): String {
    val stops = pluralStringResource(
        R.plurals.wizard_route_stops_count,
        state.stops.size,
        state.stops.size,
    )
    val days = state.durationDaysText.toIntOrNull()?.let {
        pluralStringResource(R.plurals.wizard_route_days_count, it, it)
    }
    val length = state.lengthNmText.toDoubleOrNull()?.let {
        stringResource(R.string.wizard_route_length_nm, state.lengthNmText)
    }
    return listOfNotNull(stops, days, length).joinToString(separator = " · ")
}

/** Read-only reminder that the edited post carries an alert which stays as-is. */
@Composable
private fun WizardAlertBadge(state: PostWizardState) {
    val alert = state.editingAlert ?: return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_alert_badge"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                val severity = alert.severity?.let { stringResource(it.labelRes()) }
                Text(
                    text = listOfNotNull(stringResource(alert.category.labelRes()), severity)
                        .joinToString(separator = " · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.wizard_alert_preserved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardTagChips(state: PostWizardState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.taggedUsers.forEach { user ->
            InputChip(
                selected = true,
                onClick = { state.removeTag(user.id) },
                label = { Text(user.name) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.wizard_tags_remove),
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.testTag("wizard_tag_chip_${user.id}"),
            )
        }
    }
}

/**
 * Icon row pinned above the keyboard: add media (system picker), tag people,
 * location and route (bottom sheets), plus the character-budget ring.
 */
@Composable
internal fun WizardActionBar(
    state: PostWizardState,
    sheets: WizardSheets,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = POST_MEDIA_MAX_COUNT),
    ) { uris ->
        sheets.mediaOversizeRejected = false
        val available = POST_MEDIA_MAX_COUNT - state.media.size
        uris.take(available).forEach { uri ->
            scope.launch {
                val picked = readPickedMedia(context, uri)
                if (picked == null) {
                    sheets.mediaOversizeRejected = true
                } else {
                    state.uploadMedia(
                        fileName = picked.fileName,
                        mimeType = picked.mimeType,
                        bytes = picked.bytes,
                        meta = picked.meta,
                    )
                }
            }
        }
    }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WizardActionIcon(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.wizard_media_add),
                        tint = tint,
                    )
                },
                onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
                enabled = state.media.size < POST_MEDIA_MAX_COUNT,
                testTag = "wizard_media_add",
            )
            WizardActionIcon(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = stringResource(R.string.wizard_action_tags),
                        tint = tint,
                    )
                },
                onClick = { sheets.tags = true },
                active = state.taggedUsers.isNotEmpty(),
                testTag = "wizard_action_tags",
            )
            WizardActionIcon(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = stringResource(R.string.wizard_field_location),
                        tint = tint,
                    )
                },
                onClick = { sheets.location = true },
                active = state.locationName != null || state.coordinates != null,
                testTag = "wizard_action_location",
            )
            WizardActionIcon(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.AltRoute,
                        contentDescription = stringResource(R.string.wizard_add_route),
                        tint = tint,
                    )
                },
                onClick = { sheets.route = true },
                enabled = state.editingAlert == null,
                active = state.routeEnabled,
                testTag = "wizard_action_route",
            )
            Spacer(modifier = Modifier.weight(1f))
            WizardCharCounter(length = state.text.length)
        }
    }
}

@Composable
private fun WizardActionIcon(
    icon: @Composable (tint: Color) -> Unit,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(testTag),
    ) {
        val tint = when {
            !enabled -> LocalContentColor.current
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        icon(tint)
    }
}

@Composable
private fun WizardCharCounter(length: Int) {
    val nearLimit = POST_TEXT_MAX_LENGTH - length <= 100
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        if (length > 0) {
            Text(
                text = length.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (nearLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        CircularProgressIndicator(
            progress = { length / POST_TEXT_MAX_LENGTH.toFloat() },
            color = if (nearLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Hosts the location / route / tags bottom sheets on top of the composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WizardSheetHost(
    state: PostWizardState,
    sheets: WizardSheets,
) {
    if (sheets.location) {
        WizardSheet(onDismiss = { sheets.location = false }) {
            WizardLocationSheetContent(
                state = state,
                onOpenMap = { sheets.locationMap = true },
                onDone = { sheets.location = false },
            )
        }
    }
    if (sheets.locationMap) {
        state.coordinates?.let { coordinates ->
            PostLocationMapPicker(
                initialCoordinates = coordinates,
                locationName = state.locationName,
                onConfirm = { refinedCoordinates ->
                    state.updateLocationCoordinates(refinedCoordinates)
                    sheets.locationMap = false
                },
                onDismiss = { sheets.locationMap = false },
            )
        }
    }
    if (sheets.route) {
        WizardSheet(onDismiss = { sheets.route = false }) {
            WizardRouteSheetContent(state = state, onDone = { sheets.route = false })
        }
    }
    if (sheets.tags) {
        WizardSheet(onDismiss = { sheets.tags = false }) {
            WizardTagsSheetContent(state = state, onDone = { sheets.tags = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun WizardLocationSheetContent(
    state: PostWizardState,
    onOpenMap: () -> Unit,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.wizard_field_location),
        style = MaterialTheme.typography.titleMedium,
    )
    OutlinedTextField(
        value = state.locationQuery,
        onValueChange = state::updateLocationQuery,
        label = { Text(stringResource(R.string.wizard_field_location_optional)) },
        placeholder = { Text(stringResource(R.string.wizard_location_hint)) },
        singleLine = true,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isSearchingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (state.coordinates != null) {
                    IconButton(
                        onClick = onOpenMap,
                        modifier = Modifier.testTag("wizard_location_sheet_map"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = stringResource(R.string.wizard_location_map),
                        )
                    }
                }
                if (state.locationQuery.isNotEmpty() || state.coordinates != null) {
                    IconButton(onClick = state::clearLocation) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.wizard_location_clear),
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_location"),
    )
    state.locationResults.forEach { result ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text(result.displayName) },
            supportingContent = {
                Text(
                    text = result.formattedAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        state.selectLocation(result)
                        onOpenMap()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Map,
                        contentDescription = stringResource(R.string.wizard_location_map),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    state.selectLocation(result)
                    onDone()
                },
        )
    }
}

@Composable
private fun WizardRouteSheetContent(
    state: PostWizardState,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.wizard_route_sheet_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.wizard_stops_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.stops.forEachIndexed { index, stop ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = { state.moveStop(index, -1) },
                    enabled = index > 0,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.wizard_stop_move_up),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { state.moveStop(index, 1) },
                    enabled = index < state.stops.lastIndex,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(R.string.wizard_stop_move_down),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { state.removeStop(index) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.wizard_stop_remove),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
    OutlinedTextField(
        value = state.stopQuery,
        onValueChange = state::updateStopQuery,
        label = { Text(stringResource(R.string.wizard_stops_add)) },
        placeholder = { Text(stringResource(R.string.wizard_location_hint)) },
        singleLine = true,
        isError = PostWizardError.StopsRequired in state.visibleErrors,
        supportingText = if (PostWizardError.StopsRequired in state.visibleErrors) {
            { Text(stringResource(R.string.wizard_error_stops_required)) }
        } else {
            null
        },
        trailingIcon = {
            if (state.isSearchingStops) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_stop_search"),
    )
    state.stopResults.forEach { result ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text(result.displayName) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.addStop(result) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.durationDaysText,
            onValueChange = state::updateDurationDays,
            label = { Text(stringResource(R.string.wizard_field_duration)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.lengthNmText,
            onValueChange = state::updateLengthNm,
            label = { Text(stringResource(R.string.wizard_field_length)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Button(
        onClick = {
            state.updateRouteEnabled(true)
            onDone()
        },
        enabled = state.stops.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_route_save"),
    ) {
        Text(stringResource(R.string.wizard_route_save))
    }
    if (state.routeEnabled) {
        TextButton(
            onClick = {
                state.removeRoute()
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_route_remove"),
        ) {
            Text(
                text = stringResource(R.string.wizard_route_remove),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardTagsSheetContent(
    state: PostWizardState,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.wizard_action_tags),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.wizard_tags_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.tagQuery,
        onValueChange = state::updateTagQuery,
        label = { Text(stringResource(R.string.wizard_tags_search)) },
        enabled = state.taggedUsers.size < POST_TAGGED_USERS_MAX_COUNT,
        singleLine = true,
        trailingIcon = {
            if (state.isSearchingTags) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_tag_search"),
    )
    state.tagResults.forEach { user ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(user.name) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.addTag(user) },
        )
    }
    if (state.taggedUsers.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.taggedUsers.forEach { user ->
                InputChip(
                    selected = true,
                    onClick = { state.removeTag(user.id) },
                    label = { Text(user.name) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.wizard_tags_remove),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_tags_done"),
    ) {
        Text(stringResource(R.string.wizard_sheet_done))
    }
}
