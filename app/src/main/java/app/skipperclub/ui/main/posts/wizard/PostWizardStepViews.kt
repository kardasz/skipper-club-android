package app.skipperclub.ui.main.posts.wizard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.PostType
import app.skipperclub.ui.main.posts.descriptionRes
import app.skipperclub.ui.main.posts.icon
import app.skipperclub.ui.main.posts.labelRes
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

@Composable
internal fun WizardTypeStep(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.wizard_type_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TypeSection(
            title = stringResource(R.string.wizard_category_evergreen),
            types = PostType.entries.filter { it.isEvergreen },
            state = state,
        )
        TypeSection(
            title = stringResource(R.string.wizard_category_time_sensitive),
            types = PostType.entries.filter { it.isTimeSensitive },
            state = state,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeSection(
    title: String,
    types: List<PostType>,
    state: PostWizardState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2,
        ) {
            types.forEach { type ->
                val selected = state.selectedType == type
                Surface(
                    onClick = { state.selectType(type) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (selected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wizard_type_${type.wireValue}"),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = type.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(type.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(type.descriptionRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WizardDetailsStep(state: PostWizardState) {
    val type = state.selectedType ?: return
    state.loadRegionsIfNeeded()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.description,
            onValueChange = state::updateDescription,
            label = {
                Text(
                    stringResource(
                        if (type.requiresDescription) {
                            R.string.wizard_field_description
                        } else {
                            R.string.wizard_field_description_optional
                        },
                    ),
                )
            },
            isError = PostWizardError.DescriptionRequired in state.visibleErrors,
            supportingText = {
                if (PostWizardError.DescriptionRequired in state.visibleErrors) {
                    Text(stringResource(R.string.wizard_error_description_required))
                } else {
                    Text("${state.description.length} / $POST_DESCRIPTION_MAX_LENGTH")
                }
            },
            minLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_description"),
        )

        WizardLocationField(
            state = state,
            label = stringResource(
                if (type.requiresLocation) {
                    R.string.wizard_field_location
                } else {
                    R.string.wizard_field_location_optional
                },
            ),
            isError = PostWizardError.LocationRequired in state.visibleErrors,
            errorText = stringResource(R.string.wizard_error_location_required),
            onSelect = state::selectLocation,
        )

        WizardRegionField(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardRegionField(state: PostWizardState) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = state.regions.firstOrNull { it.code == state.regionCode }?.localizedName
        ?: state.regionCode
        ?: ""

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.wizard_field_region)) },
                placeholder = { Text(stringResource(R.string.wizard_region_placeholder)) },
                isError = PostWizardError.RegionRequired in state.visibleErrors,
                supportingText = when {
                    PostWizardError.RegionRequired in state.visibleErrors -> {
                        { Text(stringResource(R.string.wizard_error_region_required)) }
                    }

                    state.regionsLoadFailed -> {
                        { Text(stringResource(R.string.filter_regions_load_failed)) }
                    }

                    else -> null
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .testTag("wizard_region"),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                state.regions.forEach { region ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(region.localizedName)
                                if (region.localizedParents.isNotEmpty()) {
                                    Text(
                                        text = region.localizedParents.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            state.selectRegion(region.code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardLocationField(
    state: PostWizardState,
    label: String,
    isError: Boolean,
    errorText: String,
    onSelect: (app.skipperclub.data.GeocodedLocation) -> Unit,
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
internal fun WizardRouteStopsStep(state: PostWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.wizard_stops_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
internal fun WizardMediaStep(state: PostWizardState) {
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
                        width = picked.width,
                        height = picked.height,
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(
                if (state.selectedType?.requiresMedia == true) {
                    R.string.wizard_media_subtitle_required
                } else {
                    R.string.wizard_media_subtitle_optional
                },
            ),
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
        if (PostWizardError.MediaRequired in state.visibleErrors) {
            Text(
                text = stringResource(R.string.wizard_error_media_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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

@Composable
internal fun WizardSummaryStep(state: PostWizardState) {
    val type = state.selectedType ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryRow(
            label = stringResource(R.string.wizard_summary_type),
            value = stringResource(type.labelRes()),
        )
        SummaryRow(
            label = stringResource(R.string.wizard_field_region),
            value = state.regions.firstOrNull { it.code == state.regionCode }?.localizedName
                ?: state.regionCode.orEmpty(),
        )
        if (state.locationName != null) {
            SummaryRow(
                label = stringResource(R.string.wizard_field_location),
                value = state.locationName.orEmpty(),
            )
        }
        if (state.description.isNotBlank()) {
            SummaryRow(
                label = stringResource(R.string.wizard_field_description),
                value = state.description,
            )
        }
        if (state.stops.isNotEmpty()) {
            SummaryRow(
                label = stringResource(R.string.wizard_step_route),
                value = state.stops.joinToString(" → ") { it.name },
            )
        }
        if (state.media.isNotEmpty()) {
            SummaryRow(
                label = stringResource(R.string.wizard_step_media),
                value = pluralStringResource(
                    R.plurals.wizard_summary_media,
                    state.media.size,
                    state.media.size,
                ),
            )
        }
        expiryNoteRes(type)?.let { noteRes ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(noteRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun expiryNoteRes(type: PostType): Int? = when (type) {
    PostType.Berth -> R.string.post_expiry_info_berth
    PostType.Weather -> R.string.post_expiry_info_weather
    PostType.NavigationWarning -> R.string.post_expiry_info_navigation_warning
    PostType.Help -> R.string.post_expiry_info_help
    else -> null
}
