package app.skipperclub.ui.main.cruises.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruiseType
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.VesselType
import app.skipperclub.ui.main.cruises.formatLocalDate
import app.skipperclub.ui.main.cruises.labelRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------------------
// Step 1 — Basics
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardBasicsStep(state: CruiseWizardState) {
    WizardSectionLabel(stringResource(R.string.cruise_field_title))
    OutlinedTextField(
        value = state.title,
        onValueChange = state::updateTitle,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.cruise_field_title_hint)) },
        isError = CruiseWizardError.TitleTooShort in state.visibleErrors,
        singleLine = true,
    )
    WizardFieldError(CruiseWizardError.TitleTooShort, state, R.string.cruise_error_title)

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_description))
    OutlinedTextField(
        value = state.description,
        onValueChange = state::updateDescription,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        placeholder = { Text(stringResource(R.string.cruise_field_description_hint)) },
        isError = CruiseWizardError.DescriptionTooShort in state.visibleErrors,
    )
    WizardFieldError(CruiseWizardError.DescriptionTooShort, state, R.string.cruise_error_description)

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CruiseType.entries.forEach { type ->
            FilterChip(
                selected = state.type == type,
                onClick = { state.selectType(if (state.type == type) null else type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Route
// ---------------------------------------------------------------------------

@Composable
internal fun WizardRouteStep(state: CruiseWizardState) {
    var activeTarget by remember { mutableStateOf<CruisePortTarget?>(null) }

    WizardSectionLabel(stringResource(R.string.cruise_field_departure_port))
    PortField(
        portName = state.departurePort?.name,
        placeholderRes = R.string.cruise_field_departure_port_hint,
        isError = CruiseWizardError.DeparturePortRequired in state.visibleErrors,
        onClick = { activeTarget = CruisePortTarget.Departure },
    )
    WizardFieldError(CruiseWizardError.DeparturePortRequired, state, R.string.cruise_error_departure_port)

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_arrival_port))
    PortField(
        portName = state.arrivalPort?.name,
        placeholderRes = R.string.cruise_field_arrival_port_hint,
        isError = CruiseWizardError.ArrivalPortRequired in state.visibleErrors,
        onClick = { activeTarget = CruisePortTarget.Arrival },
    )
    WizardFieldError(CruiseWizardError.ArrivalPortRequired, state, R.string.cruise_error_arrival_port)

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_dates))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = stringResource(R.string.cruise_field_departure_date),
            date = state.departureDate,
            isError = CruiseWizardError.DatesInvalid in state.visibleErrors,
            onPicked = state::selectDepartureDate,
            modifier = Modifier.weight(1f),
        )
        DateField(
            label = stringResource(R.string.cruise_field_arrival_date),
            date = state.arrivalDate,
            isError = CruiseWizardError.DatesInvalid in state.visibleErrors,
            onPicked = state::selectArrivalDate,
            modifier = Modifier.weight(1f),
        )
    }
    WizardFieldError(CruiseWizardError.DatesInvalid, state, R.string.cruise_error_dates)

    Spacer(Modifier.size(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        WizardSectionLabel(stringResource(R.string.cruise_field_stops))
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { activeTarget = CruisePortTarget.Stop }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.cruise_stops_add))
        }
    }
    state.stops.forEachIndexed { index, stop ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stop.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = { state.removeStop(index) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cruise_stop_remove), modifier = Modifier.size(18.dp))
            }
        }
    }

    activeTarget?.let { target ->
        PortSearchDialog(
            state = state,
            onDismiss = {
                activeTarget = null
                state.clearPortSearch()
            },
            onSelect = { location ->
                state.selectPort(target, location)
                if (target != CruisePortTarget.Stop) activeTarget = null
            },
        )
    }
}

@Composable
private fun PortField(
    portName: String?,
    placeholderRes: Int,
    isError: Boolean,
    onClick: () -> Unit,
) {
    OutlinedTextField(
        value = portName.orEmpty(),
        onValueChange = {},
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        enabled = false,
        readOnly = true,
        placeholder = { Text(stringResource(placeholderRes)) },
        leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
        isError = isError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    isError: Boolean,
    onPicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date?.let { formatLocalDate(it) }.orEmpty(),
        onValueChange = {},
        modifier = modifier.clickable { showPicker = true },
        enabled = false,
        readOnly = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
        isError = isError,
    )
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.cruise_date_confirm)) }
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
private fun PortSearchDialog(
    state: CruiseWizardState,
    onDismiss: () -> Unit,
    onSelect: (GeocodedLocation) -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.portQuery,
                    onValueChange = state::updatePortQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.cruise_port_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                    singleLine = true,
                )
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp)) {
                    when {
                        state.isSearchingPorts -> CircularProgressIndicator(Modifier.align(Alignment.Center).padding(16.dp))
                        state.portResults.isEmpty() && state.portQuery.length >= 3 -> Text(
                            text = stringResource(R.string.cruise_port_search_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )

                        else -> LazyColumn {
                            items(state.portResults, key = { it.displayName + it.coordinates.lat }) { loc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onSelect(loc) }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(loc.displayName, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cruise_done))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Vessel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardVesselStep(state: CruiseWizardState) {
    WizardSectionLabel(stringResource(R.string.cruise_field_vessel))
    OutlinedTextField(
        value = state.vessel,
        onValueChange = state::updateVessel,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.cruise_field_vessel_hint)) },
        isError = CruiseWizardError.VesselNameTooShort in state.visibleErrors,
        singleLine = true,
    )
    WizardFieldError(CruiseWizardError.VesselNameTooShort, state, R.string.cruise_error_vessel)

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_vessel_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VesselType.entries.forEach { type ->
            FilterChip(
                selected = state.vesselType == type,
                onClick = { state.selectVesselType(type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
    WizardFieldError(CruiseWizardError.VesselTypeRequired, state, R.string.cruise_error_vessel_type)

    Spacer(Modifier.size(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.vesselBrand,
            onValueChange = state::updateVesselBrand,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.cruise_field_vessel_brand)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.vesselModel,
            onValueChange = state::updateVesselModel,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.cruise_field_vessel_model)) },
            singleLine = true,
        )
    }
    Spacer(Modifier.size(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.vesselYearText,
            onValueChange = state::updateVesselYear,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.cruise_field_vessel_year)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.vesselLengthText,
            onValueChange = state::updateVesselLength,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.cruise_field_vessel_length)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.vesselCabinsText,
            onValueChange = state::updateVesselCabins,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.cruise_field_vessel_cabins)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_required_skills))
    OutlinedTextField(
        value = state.requiredSkills,
        onValueChange = state::updateRequiredSkills,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        placeholder = { Text(stringResource(R.string.cruise_field_required_skills_hint)) },
    )
}

// ---------------------------------------------------------------------------
// Step 4 — Crew, cost, rules
// ---------------------------------------------------------------------------

@Composable
internal fun WizardCrewStep(state: CruiseWizardState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            WizardSectionLabel(stringResource(R.string.cruise_field_cost))
            OutlinedTextField(
                value = state.costText,
                onValueChange = state::updateCost,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = CruiseWizardError.CostInvalid in state.visibleErrors,
                singleLine = true,
            )
        }
        Column {
            WizardSectionLabel(stringResource(R.string.cruise_field_currency))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CruiseCurrency.entries.forEach { currency ->
                    FilterChip(
                        selected = state.currency == currency,
                        onClick = { state.selectCurrency(currency) },
                        label = { Text(currency.wireValue) },
                    )
                }
            }
        }
    }
    WizardFieldError(CruiseWizardError.CostInvalid, state, R.string.cruise_error_cost)

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_max_participants))
    OutlinedTextField(
        value = state.maxParticipantsText,
        onValueChange = state::updateMaxParticipants,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = CruiseWizardError.MaxParticipantsInvalid in state.visibleErrors,
        singleLine = true,
    )
    WizardFieldError(CruiseWizardError.MaxParticipantsInvalid, state, R.string.cruise_error_max_participants)

    Spacer(Modifier.size(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cruise_field_private), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.cruise_field_private_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.isPrivate, onCheckedChange = state::updatePrivate)
    }

    Spacer(Modifier.size(16.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_rules))
    RuleRow(R.string.cruise_rule_smoking, state.smokingAllowed, state::updateSmokingAllowed)
    RuleRow(R.string.cruise_rule_alcohol, state.alcoholAllowed, state::updateAlcoholAllowed)
    RuleRow(R.string.cruise_rule_pets, state.petsAllowed, state::updatePetsAllowed)
    RuleRow(R.string.cruise_rule_children, state.childrenAllowed, state::updateChildrenAllowed)
}

@Composable
private fun RuleRow(
    labelRes: Int,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilterChip(
            selected = value == true,
            onClick = { onChange(if (value == true) null else true) },
            label = { Text(stringResource(R.string.cruise_rule_yes)) },
        )
        Spacer(Modifier.size(6.dp))
        FilterChip(
            selected = value == false,
            onClick = { onChange(if (value == false) null else false) },
            label = { Text(stringResource(R.string.cruise_rule_no)) },
        )
    }
}

// ---------------------------------------------------------------------------
// Step 5 — Summary
// ---------------------------------------------------------------------------

@Composable
internal fun WizardSummaryStep(state: CruiseWizardState) {
    SummaryRow(stringResource(R.string.cruise_field_title), state.title)
    state.type?.let { SummaryRow(stringResource(R.string.cruise_field_type), stringResource(it.labelRes())) }
    SummaryRow(
        stringResource(R.string.cruise_field_route),
        "${state.departurePort?.name.orEmpty()} → ${state.arrivalPort?.name.orEmpty()}",
    )
    SummaryRow(
        stringResource(R.string.cruise_field_dates),
        listOfNotNull(
            state.departureDate?.let { formatLocalDate(it) },
            state.arrivalDate?.let { formatLocalDate(it) },
        ).joinToString(" – "),
    )
    state.vesselType?.let {
        SummaryRow(stringResource(R.string.cruise_field_vessel), "${stringResource(it.labelRes())} • ${state.vessel}")
    }
    SummaryRow(
        stringResource(R.string.cruise_field_cost),
        "${state.costText} ${state.currency.wireValue}",
    )
    SummaryRow(stringResource(R.string.cruise_field_max_participants), state.maxParticipantsText)
    SummaryRow(
        stringResource(R.string.cruise_field_private),
        stringResource(if (state.isPrivate) R.string.cruise_rule_yes else R.string.cruise_rule_no),
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

@Composable
private fun WizardSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun WizardFieldError(
    error: CruiseWizardError,
    state: CruiseWizardState,
    messageRes: Int,
) {
    if (error in state.visibleErrors) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
