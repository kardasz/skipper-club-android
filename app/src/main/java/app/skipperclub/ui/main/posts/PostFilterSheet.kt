package app.skipperclub.ui.main.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.PostSortField
import app.skipperclub.data.PostType
import app.skipperclub.data.Region
import app.skipperclub.data.SortOrder

/**
 * Feed filter sheet: post-type chips, region dropdown (loaded lazily from
 * `/v1/regions`), sort field and order. Edits are local until Apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostFilterSheet(
    filters: PostFilters,
    regions: List<Region>,
    regionsLoadFailed: Boolean,
    onApply: (PostFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PostFilterSheetContent(
            filters = filters,
            regions = regions,
            regionsLoadFailed = regionsLoadFailed,
            onApply = onApply,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PostFilterSheetContent(
    filters: PostFilters,
    regions: List<Region>,
    regionsLoadFailed: Boolean,
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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

        Text(
            text = stringResource(R.string.filter_section_types),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PostType.entries.forEach { type ->
                val selected = type in draft.types
                FilterChip(
                    selected = selected,
                    onClick = {
                        draft = draft.copy(
                            types = if (selected) draft.types - type else draft.types + type,
                        )
                    },
                    label = { Text(stringResource(type.labelRes())) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(0.dp).then(
                                    Modifier,
                                ),
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(),
                    modifier = Modifier.testTag("filter_type_${type.wireValue}"),
                )
            }
        }

        Text(
            text = stringResource(R.string.filter_section_region),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RegionDropdown(
            regions = regions,
            regionsLoadFailed = regionsLoadFailed,
            selectedCode = draft.regionCode,
            onSelect = { draft = draft.copy(regionCode = it) },
        )

        Text(
            text = stringResource(R.string.filter_section_sort),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.sort == PostSortField.CreatedAt,
                onClick = { draft = draft.copy(sort = PostSortField.CreatedAt) },
                label = { Text(stringResource(R.string.filter_sort_created)) },
            )
            FilterChip(
                selected = draft.sort == PostSortField.UpdatedAt,
                onClick = { draft = draft.copy(sort = PostSortField.UpdatedAt) },
                label = { Text(stringResource(R.string.filter_sort_updated)) },
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionDropdown(
    regions: List<Region>,
    regionsLoadFailed: Boolean,
    selectedCode: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allRegionsLabel = stringResource(R.string.filter_all_regions)
    val selectedLabel = regions.firstOrNull { it.code == selectedCode }?.localizedName
        ?: selectedCode
        ?: allRegionsLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = if (regionsLoadFailed) {
                { Text(stringResource(R.string.filter_regions_load_failed)) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(allRegionsLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            regions.forEach { region ->
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
                        onSelect(region.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
