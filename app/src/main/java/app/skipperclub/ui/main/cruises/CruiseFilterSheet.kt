package app.skipperclub.ui.main.cruises

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.CruiseScope
import app.skipperclub.data.CruiseSortField
import app.skipperclub.data.CruiseType
import app.skipperclub.data.SortOrder
import app.skipperclub.data.VesselType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruiseFilterSheet(
    filters: CruiseFilters,
    onApply: (CruiseFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CruiseFilterSheetContent(
            filters = filters,
            onApply = onApply,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CruiseFilterSheetContent(
    filters: CruiseFilters,
    onApply: (CruiseFilters) -> Unit,
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
                // Clear filters only; the free-text search is owned by the search bar.
                onClick = { draft = CruiseFilters(search = draft.search) },
                enabled = draft.filterCount > 0,
            ) {
                Text(stringResource(R.string.filter_clear))
            }
        }

        CruiseFilterSectionLabel(R.string.cruises_filter_scope)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CruiseScope.entries.forEach { scope ->
                SingleSelectChip(
                    selected = draft.scope == scope,
                    onClick = { draft = draft.copy(scope = scope) },
                    label = stringResource(scope.labelRes()),
                    modifier = Modifier.testTag("cruise_filter_scope_${scope.wireValue}"),
                )
            }
        }

        CruiseFilterSectionLabel(R.string.cruises_filter_type)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CruiseType.entries.forEach { type ->
                SingleSelectChip(
                    selected = draft.type == type,
                    onClick = { draft = draft.copy(type = draft.type.toggle(type)) },
                    label = stringResource(type.labelRes()),
                    modifier = Modifier.testTag("cruise_filter_type_${type.wireValue}"),
                )
            }
        }

        CruiseFilterSectionLabel(R.string.cruises_filter_vessel_type)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VesselType.entries.forEach { vesselType ->
                SingleSelectChip(
                    selected = draft.vesselType == vesselType,
                    onClick = { draft = draft.copy(vesselType = draft.vesselType.toggle(vesselType)) },
                    label = stringResource(vesselType.labelRes()),
                    modifier = Modifier.testTag("cruise_filter_vessel_${vesselType.wireValue}"),
                )
            }
        }

        CruiseFilterSectionLabel(R.string.filter_section_dates)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = draft.fromDate.orEmpty(),
                onValueChange = { draft = draft.copy(fromDate = it.ifBlank { null }) },
                singleLine = true,
                label = { Text(stringResource(R.string.cruises_filter_from_date)) },
                placeholder = { Text(stringResource(R.string.cruises_filter_date_hint)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("cruise_filter_from_date"),
            )
            OutlinedTextField(
                value = draft.toDate.orEmpty(),
                onValueChange = { draft = draft.copy(toDate = it.ifBlank { null }) },
                singleLine = true,
                label = { Text(stringResource(R.string.cruises_filter_to_date)) },
                placeholder = { Text(stringResource(R.string.cruises_filter_date_hint)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("cruise_filter_to_date"),
            )
        }

        CruiseFilterSectionLabel(R.string.filter_section_sort)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CruiseSortField.entries.forEach { sort ->
                SingleSelectChip(
                    selected = draft.sort == sort,
                    onClick = { draft = draft.copy(sort = sort) },
                    label = stringResource(sort.labelRes()),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleSelectChip(
                selected = draft.order == SortOrder.Desc,
                onClick = { draft = draft.copy(order = SortOrder.Desc) },
                label = stringResource(R.string.filter_order_desc),
            )
            SingleSelectChip(
                selected = draft.order == SortOrder.Asc,
                onClick = { draft = draft.copy(order = SortOrder.Asc) },
                label = stringResource(R.string.filter_order_asc),
            )
        }

        Button(
            onClick = { onApply(draft) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cruise_filter_apply"),
        ) {
            Text(stringResource(R.string.filter_apply))
        }
    }
}

@Composable
private fun CruiseFilterSectionLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SingleSelectChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        modifier = modifier,
    )
}

private fun <T> T?.toggle(value: T): T? = if (this == value) null else value

private fun CruiseScope.labelRes(): Int = when (this) {
    CruiseScope.All -> R.string.cruises_scope_all
    CruiseScope.Mine -> R.string.cruises_scope_mine
    CruiseScope.Organized -> R.string.cruises_scope_organized
    CruiseScope.Participating -> R.string.cruises_scope_participating
}

private fun CruiseSortField.labelRes(): Int = when (this) {
    CruiseSortField.CreatedAt -> R.string.cruises_sort_created
    CruiseSortField.DepartureDate -> R.string.cruises_sort_departure
    CruiseSortField.Title -> R.string.cruises_sort_title
    CruiseSortField.CostPerPerson -> R.string.cruises_sort_cost
}
