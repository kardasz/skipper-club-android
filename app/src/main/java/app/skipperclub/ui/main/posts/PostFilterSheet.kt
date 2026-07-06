package app.skipperclub.ui.main.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.PostContainsFilter
import app.skipperclub.data.PostSortField
import app.skipperclub.data.PostStatus
import app.skipperclub.data.SortOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val RADIUS_OPTIONS = listOf(10, 25, 50, 100)
private val LIFECYCLE_STATUSES = listOf(
    PostStatus.Published,
    PostStatus.Archived,
    PostStatus.Expired,
    PostStatus.Resolved,
)

private fun PostStatus.labelRes(): Int = when (this) {
    PostStatus.Published -> R.string.filter_status_published
    PostStatus.Archived -> R.string.filter_status_archived
    PostStatus.Expired -> R.string.filter_status_expired
    PostStatus.Resolved -> R.string.filter_status_resolved
    PostStatus.Deleted -> R.string.filter_status_published
}

/**
 * Feed filter sheet. Beyond the "Show" content filter and full-text search it exposes
 * the lifecycle ("My posts" + statuses), hashtag, location-name substring, a date
 * range, a radius search (geocoded center + km) and sorting. Local until Apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostFilterSheet(
    filters: PostFilters,
    currentUserId: String?,
    onSearchLocations: suspend (String) -> List<GeocodedLocation>,
    onApply: (PostFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PostFilterSheetContent(
            filters = filters,
            currentUserId = currentUserId,
            onSearchLocations = onSearchLocations,
            onApply = onApply,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

private val CONTAINS_FILTERS = listOf(
    PostContainsFilter.Alert,
    PostContainsFilter.Media,
    PostContainsFilter.Route,
    PostContainsFilter.Note,
)

private fun PostContainsFilter.labelRes(): Int = when (this) {
    PostContainsFilter.Alert -> R.string.posts_filter_contains_alerts
    PostContainsFilter.Media -> R.string.posts_filter_contains_photos
    PostContainsFilter.Route -> R.string.posts_filter_contains_routes
    PostContainsFilter.Note -> R.string.posts_filter_contains_notes
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PostFilterSheetContent(
    filters: PostFilters,
    currentUserId: String?,
    onSearchLocations: suspend (String) -> List<GeocodedLocation>,
    onApply: (PostFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(filters) { mutableStateOf(filters) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.filter_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { draft = PostFilters() },
                enabled = draft.activeCount > 0,
            ) {
                Text(stringResource(R.string.filter_clear))
            }
        }

        FilterSectionLabel(R.string.posts_filter_section_show)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CONTAINS_FILTERS.forEach { filter ->
                val selected = filter in draft.contains
                FilterChip(
                    selected = selected,
                    onClick = {
                        draft = draft.copy(
                            contains = if (selected) draft.contains - filter else draft.contains + filter,
                        )
                    },
                    label = { Text(stringResource(filter.labelRes())) },
                    leadingIcon = if (selected) {
                        { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(),
                    modifier = Modifier.testTag("filter_contains_${filter.wireValue}"),
                )
            }
        }

        FilterSectionLabel(R.string.posts_filter_section_search)
        OutlinedTextField(
            value = draft.query.orEmpty(),
            onValueChange = { value -> draft = draft.copy(query = value.ifBlank { null }) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.posts_filter_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_query"),
        )

        FilterSectionLabel(R.string.filter_section_hashtag)
        OutlinedTextField(
            value = draft.hashtag.orEmpty(),
            onValueChange = { value -> draft = draft.copy(hashtag = value.removePrefix("#").trim().ifBlank { null }) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.filter_hashtag_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_hashtag"),
        )

        FilterSectionLabel(R.string.filter_section_location)
        OutlinedTextField(
            value = draft.locationName.orEmpty(),
            onValueChange = { value -> draft = draft.copy(locationName = value.ifBlank { null }) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.filter_location_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_location"),
        )

        FilterSectionLabel(R.string.filter_section_radius)
        RadiusSearch(
            draft = draft,
            onSearchLocations = onSearchLocations,
            onDraftChange = { draft = it },
        )

        FilterSectionLabel(R.string.filter_section_dates)
        DateRangeRow(
            fromDate = draft.fromDate,
            toDate = draft.toDate,
            onFromChange = { draft = draft.copy(fromDate = it) },
            onToChange = { draft = draft.copy(toDate = it) },
        )

        if (currentUserId != null) {
            FilterSectionLabel(R.string.filter_section_lifecycle)
            FilterChip(
                selected = draft.userId != null,
                onClick = {
                    draft = if (draft.userId == null) {
                        draft.copy(userId = currentUserId)
                    } else {
                        draft.copy(userId = null, statuses = emptySet())
                    }
                },
                label = { Text(stringResource(R.string.filter_my_posts)) },
                modifier = Modifier.testTag("filter_mine"),
            )
            if (draft.userId != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LIFECYCLE_STATUSES.forEach { status ->
                        val selected = status in draft.statuses
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(
                                    statuses = if (selected) draft.statuses - status else draft.statuses + status,
                                )
                            },
                            label = { Text(stringResource(status.labelRes())) },
                            modifier = Modifier.testTag("filter_status_${status.wireValue}"),
                        )
                    }
                }
            }
        }

        FilterSectionLabel(R.string.filter_section_sort)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.sort == PostSortField.PublishedAt,
                onClick = { draft = draft.copy(sort = PostSortField.PublishedAt) },
                label = { Text(stringResource(R.string.posts_filter_sort_published)) },
            )
            FilterChip(
                selected = draft.sort == PostSortField.UpdatedAt,
                onClick = { draft = draft.copy(sort = PostSortField.UpdatedAt) },
                label = { Text(stringResource(R.string.filter_sort_updated)) },
            )
            if (draft.center != null && draft.radiusKm != null) {
                FilterChip(
                    selected = draft.sort == PostSortField.Distance,
                    onClick = { draft = draft.copy(sort = PostSortField.Distance) },
                    label = { Text(stringResource(R.string.filter_sort_distance)) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.order == SortOrder.Desc,
                onClick = { draft = draft.copy(order = SortOrder.Desc) },
                label = { Text(stringResource(R.string.filter_order_desc)) },
            )
            FilterChip(
                selected = draft.order == SortOrder.Asc,
                onClick = { draft = draft.copy(order = SortOrder.Asc) },
                label = { Text(stringResource(R.string.filter_order_asc)) },
            )
        }

        Button(
            onClick = { onApply(draft) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_apply"),
        ) {
            Text(stringResource(R.string.filter_apply))
        }
    }
}

@Composable
private fun FilterSectionLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RadiusSearch(
    draft: PostFilters,
    onSearchLocations: suspend (String) -> List<GeocodedLocation>,
    onDraftChange: (PostFilters) -> Unit,
) {
    var query by remember(draft.centerLabel) { mutableStateOf(draft.centerLabel.orEmpty()) }
    var results by remember { mutableStateOf<List<GeocodedLocation>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (draft.center != null && query == draft.centerLabel) {
            results = emptyList()
            return@LaunchedEffect
        }
        if (query.trim().length < 3) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        kotlinx.coroutines.delay(350)
        results = runCatching { onSearchLocations(query.trim()) }.getOrDefault(emptyList())
        searching = false
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.filter_radius_center_hint)) },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                draft.center != null -> IconButton(onClick = {
                    query = ""
                    results = emptyList()
                    onDraftChange(draft.copy(center = null, centerLabel = null, radiusKm = null))
                }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.filter_radius_clear),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("filter_radius_center"),
    )
    results.forEach { result ->
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    query = result.displayName
                    results = emptyList()
                    onDraftChange(
                        draft.copy(
                            center = result.coordinates,
                            centerLabel = result.displayName,
                            radiusKm = draft.radiusKm ?: RADIUS_OPTIONS.first(),
                        ),
                    )
                },
        )
    }
    if (draft.center != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADIUS_OPTIONS.forEach { km ->
                FilterChip(
                    selected = draft.radiusKm == km,
                    onClick = { onDraftChange(draft.copy(radiusKm = km)) },
                    label = { Text(stringResource(R.string.filter_radius_km, km)) },
                    modifier = Modifier.testTag("filter_radius_$km"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeRow(
    fromDate: String?,
    toDate: String?,
    onFromChange: (String?) -> Unit,
    onToChange: (String?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(
            value = fromDate,
            placeholderRes = R.string.filter_date_from,
            onChange = onFromChange,
            modifier = Modifier
                .weight(1f)
                .testTag("filter_from_date"),
        )
        DateField(
            value = toDate,
            placeholderRes = R.string.filter_date_to,
            onChange = onToChange,
            modifier = Modifier
                .weight(1f)
                .testTag("filter_to_date"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    value: String?,
    placeholderRes: Int,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.let { formatIsoDate(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        placeholder = { Text(stringResource(placeholderRes)) },
        trailingIcon = {
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.filter_date_clear),
                    )
                }
            }
        },
        modifier = modifier.clickable { showPicker = true },
        enabled = false,
    )
    if (showPicker) {
        val pickerState = rememberDatePickerStateSafe(value)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(isoFromMillis(it)) }
                    showPicker = false
                }) { Text(stringResource(R.string.filter_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.post_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDatePickerStateSafe(value: String?) =
    androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
    )

private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

private fun isoFromMillis(millis: Long): String = Instant.ofEpochMilli(millis).toString()

private fun formatIsoDate(iso: String): String =
    runCatching { ISO_DATE.format(Instant.parse(iso)) }.getOrDefault(iso)
