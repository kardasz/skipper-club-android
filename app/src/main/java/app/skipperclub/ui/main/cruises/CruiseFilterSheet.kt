package app.skipperclub.ui.main.cruises

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.CruiseScope
import app.skipperclub.data.CruiseSortField
import app.skipperclub.data.CruiseType
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.SortOrder
import app.skipperclub.data.VesselType
import app.skipperclub.ui.main.checkin.fetchCurrentLocation
import app.skipperclub.ui.main.checkin.hasLocationPermission
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.launch

/** Radius options (km) offered by the "near me" cruise filter. */
private val NearMeDistancesKm = listOf(25, 50, 100, 200)

/** Radius applied when the user first enables "use my location". */
private const val DefaultNearMeKm = 50

/**
 * Resolves a fresh device location, prompting for permission when needed, and hands
 * the coordinates (or `null` on denial/failure) to [onResult]. Callers use this to
 * populate the "near me" spatial filter.
 */
private typealias LocationResolver = (onResult: (PostCoordinates?) -> Unit) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruiseFilterSheet(
    filters: CruiseFilters,
    onApply: (CruiseFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // Location + permission machinery lives here (never rendered by @Preview) so the
    // stateless *Content stays preview-safe.
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingResult by remember { mutableStateOf<((PostCoordinates?) -> Unit)?>(null) }

    fun resolveNow(onResult: (PostCoordinates?) -> Unit) {
        coroutineScope.launch {
            val location = context.fetchCurrentLocation()
            onResult(location?.let { PostCoordinates(it.latitude, it.longitude) })
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val callback = pendingResult
        pendingResult = null
        if (granted && callback != null) resolveNow(callback) else callback?.invoke(null)
    }

    val locationResolver: LocationResolver = { onResult ->
        if (context.hasLocationPermission()) {
            resolveNow(onResult)
        } else {
            pendingResult = onResult
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        CruiseFilterSheetContent(
            filters = filters,
            onApply = onApply,
            onResolveLocation = locationResolver,
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
    onResolveLocation: LocationResolver = { it(null) },
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    var locating by remember(filters) { mutableStateOf(false) }
    var locationUnavailable by remember(filters) { mutableStateOf(false) }

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
                onClick = {
                    draft = CruiseFilters(search = draft.search)
                    locationUnavailable = false
                },
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

        CruiseFilterSectionLabel(R.string.cruises_filter_near_me)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = draft.hasNearMe,
                onClick = {
                    locationUnavailable = false
                    if (draft.hasNearMe) {
                        draft = draft.copy(lat = null, lng = null, distanceKm = null)
                    } else {
                        locating = true
                        onResolveLocation { coordinates ->
                            locating = false
                            if (coordinates != null) {
                                draft = draft.copy(
                                    lat = coordinates.lat,
                                    lng = coordinates.lng,
                                    distanceKm = draft.distanceKm ?: DefaultNearMeKm,
                                )
                            } else {
                                locationUnavailable = true
                            }
                        }
                    }
                },
                label = { Text(stringResource(R.string.cruises_filter_use_my_location)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                },
                modifier = Modifier.testTag("cruise_filter_near_me"),
            )
            if (locating) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        if (draft.hasNearMe) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NearMeDistancesKm.forEach { km ->
                    SingleSelectChip(
                        selected = draft.distanceKm == km,
                        onClick = { draft = draft.copy(distanceKm = km) },
                        label = stringResource(R.string.cruises_filter_distance_km, km),
                        modifier = Modifier.testTag("cruise_filter_distance_$km"),
                    )
                }
            }
        }
        if (locationUnavailable) {
            Text(
                text = stringResource(R.string.cruises_filter_location_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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

// --- Previews ---

@Preview(showBackground = true, widthDp = 380, locale = "en")
@Composable
private fun CruiseFilterSheetContentPreview() {
    SkipperClubTheme {
        CruiseFilterSheetContent(
            filters = CruiseFilters(),
            onApply = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    locale = "en",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CruiseFilterSheetContentPreviewDark() {
    SkipperClubTheme {
        CruiseFilterSheetContent(
            filters = CruiseFilters(
                scope = CruiseScope.Organized,
                type = CruiseType.Training,
                lat = 43.5081,
                lng = 16.4402,
                distanceKm = 50,
            ),
            onApply = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 380, locale = "pl")
@Composable
private fun CruiseFilterSheetContentPreviewPl() {
    SkipperClubTheme {
        CruiseFilterSheetContent(
            filters = CruiseFilters(
                scope = CruiseScope.Mine,
                search = "Chorwacja",
                vesselType = VesselType.Catamaran,
                fromDate = "2026-07-01",
                toDate = "2026-07-08",
                lat = 54.441,
                lng = 18.567,
                distanceKm = 100,
                sort = CruiseSortField.CostPerPerson,
                order = SortOrder.Asc,
            ),
            onApply = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
