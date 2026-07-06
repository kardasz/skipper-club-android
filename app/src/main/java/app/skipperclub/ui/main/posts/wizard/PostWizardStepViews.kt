package app.skipperclub.ui.main.posts.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertSeverity
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.ui.main.alert.labelRes
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

/**
 * The whole post form on one scrolling surface: text (required) → location →
 * two mutually exclusive optional sections (route / alert) → media → tags.
 */
@Composable
internal fun WizardForm(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        WizardTextSection(state)
        WizardLocationField(
            state = state,
            label = stringResource(R.string.wizard_field_location_optional),
            isError = PostWizardError.AlertLocationRequired in state.visibleErrors,
            errorText = stringResource(R.string.wizard_error_alert_location_required),
            onSelect = state::selectLocation,
        )
        WizardOptionalSections(state)
        HorizontalDivider()
        WizardMediaSection(state)
        HorizontalDivider()
        WizardTagsSection(state)
    }
}

@Composable
private fun WizardTextSection(state: PostWizardState) {
    OutlinedTextField(
        value = state.text,
        onValueChange = state::updateText,
        label = { Text(stringResource(R.string.wizard_text_label)) },
        isError = PostWizardError.TextRequired in state.visibleErrors,
        supportingText = {
            if (PostWizardError.TextRequired in state.visibleErrors) {
                Text(stringResource(R.string.wizard_error_text_required))
            } else {
                Text("${state.text.length} / $POST_TEXT_MAX_LENGTH")
            }
        },
        minLines = 4,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wizard_text"),
    )
}

@Composable
private fun WizardLocationField(
    state: PostWizardState,
    label: String,
    isError: Boolean,
    errorText: String,
    onSelect: (GeocodedLocation) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = state.locationQuery,
            onValueChange = state::updateLocationQuery,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.wizard_location_hint)) },
            isError = isError,
            supportingText = if (isError) {
                { Text(errorText) }
            } else {
                null
            },
            trailingIcon = {
                when {
                    state.isSearchingLocation -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )

                    state.locationQuery.isNotEmpty() -> IconButton(onClick = state::clearLocation) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.wizard_location_clear),
                        )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(result) },
            )
        }
    }
}

@Composable
private fun WizardOptionalSections(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionToggle(
            icon = Icons.Filled.AltRoute,
            title = stringResource(R.string.wizard_add_route),
            description = stringResource(R.string.wizard_add_route_desc),
            checked = state.routeEnabled,
            onCheckedChange = state::updateRouteEnabled,
            testTag = "wizard_toggle_route",
        )
        if (state.routeEnabled) {
            WizardRouteSubForm(state)
        }
        SectionToggle(
            icon = Icons.Filled.Warning,
            title = stringResource(R.string.wizard_add_alert),
            description = stringResource(R.string.wizard_add_alert_desc),
            checked = state.alertEnabled,
            onCheckedChange = state::updateAlertEnabled,
            testTag = "wizard_toggle_alert",
        )
        if (state.alertEnabled) {
            WizardAlertSubForm(state)
        }
    }
}

@Composable
private fun SectionToggle(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun WizardRouteSubForm(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WizardLocationField(
            state = state,
            label = stringResource(R.string.wizard_stops_add),
            isError = PostWizardError.StopsRequired in state.visibleErrors,
            errorText = stringResource(R.string.wizard_error_stops_required),
            onSelect = { location ->
                state.addStop(location)
                state.clearLocation()
            },
        )
        if (state.stops.isEmpty()) {
            Text(
                text = stringResource(R.string.wizard_stops_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.durationDaysText,
                onValueChange = state::updateDurationDays,
                label = { Text(stringResource(R.string.wizard_field_duration)) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.lengthNmText,
                onValueChange = state::updateLengthNm,
                label = { Text(stringResource(R.string.wizard_field_length)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardAlertSubForm(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_alert_category_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AlertCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.alertCategory == category,
                    onClick = { state.selectAlertCategory(category) },
                    label = { Text(stringResource(category.labelRes())) },
                    modifier = Modifier.testTag("wizard_alert_category_${category.name}"),
                )
            }
        }
        if (PostWizardError.AlertCategoryRequired in state.visibleErrors) {
            Text(
                text = stringResource(R.string.wizard_error_alert_category_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.wizard_alert_severity_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.alertSeverity == null,
                onClick = { state.selectAlertSeverity(null) },
                label = { Text(stringResource(R.string.wizard_alert_severity_none)) },
            )
            AlertSeverity.entries.forEach { severity ->
                FilterChip(
                    selected = state.alertSeverity == severity,
                    onClick = { state.selectAlertSeverity(severity) },
                    label = { Text(stringResource(severity.labelRes())) },
                    modifier = Modifier.testTag("wizard_alert_severity_${severity.name}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardMediaSection(state: PostWizardState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var oversizeRejected by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = POST_MEDIA_MAX_COUNT),
    ) { uris ->
        oversizeRejected = false
        val available = POST_MEDIA_MAX_COUNT - state.media.size
        uris.take(available).forEach { uri ->
            scope.launch {
                val picked = readPickedMedia(context, uri)
                if (picked == null) {
                    oversizeRejected = true
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_media_subtitle_optional),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            enabled = state.media.size < POST_MEDIA_MAX_COUNT,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_media_add"),
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.wizard_media_add))
        }
        if (oversizeRejected) {
            Text(
                text = stringResource(R.string.wizard_media_too_large),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.wizard_media_limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 3,
        ) {
            state.media.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    when {
                        item.isUploading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
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
                                        .size(32.dp),
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { state.removeMedia(item.localId) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.wizard_media_remove),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardTagsSection(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_tags_subtitle),
            style = MaterialTheme.typography.bodyMedium,
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
        if (state.taggedUsers.isEmpty()) {
            Text(
                text = stringResource(R.string.wizard_tags_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
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
    }
}
