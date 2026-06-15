package app.skipperclub.ui.main.cruises.wizard

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruiseType
import app.skipperclub.data.VesselType
import app.skipperclub.ui.main.cruises.formatLocalDate
import app.skipperclub.ui.main.cruises.labelRes
import app.skipperclub.ui.main.posts.wizard.readPickedMedia
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Step 0 — AI draft (create-only)
// ---------------------------------------------------------------------------

/**
 * Free-text / voice description that the AI turns into a pre-filled draft. Speech
 * input uses the system recognizer ([RecognizerIntent]) so no microphone permission
 * or extra library is needed; the mic button hides on devices without recognition.
 */
@Composable
internal fun WizardAiDraftStep(state: CruiseWizardState) {
    val context = LocalContext.current
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechPrompt = stringResource(R.string.cruise_ai_speech_prompt)

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
        if (spoken != null) {
            val merged = if (state.aiDescription.isBlank()) {
                spoken
            } else {
                state.aiDescription.trimEnd() + " " + spoken
            }
            state.updateAiDescription(merged)
        }
    }

    val launchSpeech: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
        }
        runCatching { speechLauncher.launch(intent) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(R.string.cruise_ai_headline),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Spacer(Modifier.size(6.dp))
    Text(
        text = stringResource(R.string.cruise_ai_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.size(12.dp))
    OutlinedTextField(
        value = state.aiDescription,
        onValueChange = state::updateAiDescription,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .testTag("cruise_ai_description"),
        enabled = !state.isGeneratingDraft,
        placeholder = { Text(stringResource(R.string.cruise_ai_hint)) },
        trailingIcon = if (speechAvailable) {
            {
                IconButton(
                    onClick = launchSpeech,
                    enabled = !state.isGeneratingDraft,
                    modifier = Modifier.testTag("cruise_ai_mic"),
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cruise_ai_speak),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            null
        },
    )
    Text(
        text = "${state.aiDescription.trim().length}/$CRUISE_AI_DESCRIPTION_MAX_LENGTH",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = TextAlign.End,
    )

    if (speechAvailable) {
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.cruise_ai_speak_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Step 1 — Basics
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardBasicsStep(state: CruiseWizardState) {
    val primaryTypes = remember {
        listOf(CruiseType.Milebuilding, CruiseType.Training, CruiseType.BeginnerIntro)
    }
    var showAllTypes by rememberSaveable {
        mutableStateOf(state.type != null && state.type !in primaryTypes)
    }
    var showRequiredSkills by rememberSaveable {
        mutableStateOf(state.requiredSkills.isNotBlank())
    }

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

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_description))
    OutlinedTextField(
        value = state.description,
        onValueChange = state::updateDescription,
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp),
        placeholder = { Text(stringResource(R.string.cruise_field_description_hint)) },
        isError = CruiseWizardError.DescriptionTooShort in state.visibleErrors,
    )
    WizardFieldError(CruiseWizardError.DescriptionTooShort, state, R.string.cruise_error_description)

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_dates))
    val departureInvalid = CruiseWizardError.DatesInvalid in state.visibleErrors ||
        CruiseWizardError.DepartureNotInFuture in state.visibleErrors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = stringResource(R.string.cruise_field_departure_date),
            date = state.departureDate,
            isError = departureInvalid,
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
    WizardFieldError(CruiseWizardError.DepartureNotInFuture, state, R.string.cruise_error_departure_future)

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_type))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val visibleTypes = if (showAllTypes) CruiseType.entries else primaryTypes
        visibleTypes.forEach { type ->
            FilterChip(
                selected = state.type == type,
                onClick = { state.selectType(if (state.type == type) null else type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
        FilterChip(
            selected = showAllTypes,
            onClick = { showAllTypes = !showAllTypes },
            label = {
                Text(
                    stringResource(
                        if (showAllTypes) R.string.cruise_types_less else R.string.cruise_types_more,
                    ),
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (showAllTypes) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }

    Spacer(Modifier.size(8.dp))
    TextButton(onClick = { showRequiredSkills = !showRequiredSkills }) {
        Icon(
            imageVector = if (showRequiredSkills) Icons.Filled.ExpandLess else Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(
                if (showRequiredSkills) {
                    R.string.cruise_required_skills_hide
                } else {
                    R.string.cruise_required_skills_show
                },
            ),
        )
    }
    AnimatedVisibility(visible = showRequiredSkills) {
        OutlinedTextField(
            value = state.requiredSkills,
            onValueChange = state::updateRequiredSkills,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            placeholder = { Text(stringResource(R.string.cruise_field_required_skills_hint)) },
        )
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Route
// ---------------------------------------------------------------------------

@Composable
internal fun WizardRouteStep(state: CruiseWizardState) {
    var showStopInput by rememberSaveable { mutableStateOf(false) }

    PortAutocompleteField(
        state = state,
        target = CruisePortSearchTarget.Departure,
        labelRes = R.string.cruise_field_departure_port,
        placeholderRes = R.string.cruise_field_departure_port_hint,
        isError = CruiseWizardError.DeparturePortRequired in state.visibleErrors,
    )
    WizardFieldError(CruiseWizardError.DeparturePortRequired, state, R.string.cruise_error_departure_port)

    Spacer(Modifier.size(10.dp))
    PortAutocompleteField(
        state = state,
        target = CruisePortSearchTarget.Arrival,
        labelRes = R.string.cruise_field_arrival_port,
        placeholderRes = R.string.cruise_field_arrival_port_hint,
        isError = CruiseWizardError.ArrivalPortRequired in state.visibleErrors,
    )
    WizardFieldError(CruiseWizardError.ArrivalPortRequired, state, R.string.cruise_error_arrival_port)

    Spacer(Modifier.size(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        WizardSectionLabel(stringResource(R.string.cruise_field_stops))
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showStopInput = !showStopInput }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.cruise_stops_add))
        }
    }
    AnimatedVisibility(visible = showStopInput) {
        PortAutocompleteField(
            state = state,
            target = CruisePortSearchTarget.Stop,
            labelRes = R.string.cruise_field_stops,
            placeholderRes = R.string.cruise_port_search_hint,
            isError = false,
        )
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
}

@Composable
private fun PortAutocompleteField(
    state: CruiseWizardState,
    target: CruisePortSearchTarget,
    labelRes: Int,
    placeholderRes: Int,
    isError: Boolean,
) {
    val query = state.portQueryFor(target)
    val active = state.activePortSearchTarget == target
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { state.updatePortQuery(target, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(labelRes)) },
            placeholder = { Text(stringResource(placeholderRes)) },
            leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
            trailingIcon = {
                when {
                    active && state.isSearchingPorts -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                    query.isNotEmpty() -> IconButton(onClick = { state.updatePortQuery(target, "") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.wizard_location_clear),
                        )
                    }
                }
            },
            isError = isError,
            singleLine = true,
        )
        if (active && (state.portResults.isNotEmpty() || query.trim().length >= 3 || state.isSearchingPorts)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column {
                    if (!state.isSearchingPorts && state.portResults.isEmpty() && query.trim().length >= 3) {
                        Text(
                            text = stringResource(R.string.cruise_port_search_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    state.portResults.forEach { location ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = location.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = location.formattedAddress,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { state.selectPort(target, location) },
                        )
                    }
                }
            }
        }
    }
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
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = { showPicker = true },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = date?.let { formatLocalDate(it) } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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

// ---------------------------------------------------------------------------
// Step 3 — Vessel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardVesselStep(state: CruiseWizardState) {
    val primaryVesselTypes = remember { listOf(VesselType.SailingYacht, VesselType.Catamaran) }
    val hasOptionalDetails = listOf(
        state.vesselBrand,
        state.vesselModel,
        state.vesselYearText,
        state.vesselLengthText,
        state.vesselCabinsText,
    ).any { it.isNotBlank() }
    var showOptionalDetails by rememberSaveable { mutableStateOf(hasOptionalDetails) }
    var showOtherTypes by rememberSaveable {
        mutableStateOf(state.vesselType != null && state.vesselType !in primaryVesselTypes)
    }

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

    Spacer(Modifier.size(8.dp))
    TextButton(
        onClick = { showOptionalDetails = !showOptionalDetails },
    ) {
        Text(
            stringResource(
                if (showOptionalDetails) {
                    R.string.cruise_vessel_details_hide
                } else {
                    R.string.cruise_vessel_details_show
                },
            ),
        )
        Icon(
            imageVector = if (showOptionalDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }

    AnimatedVisibility(visible = showOptionalDetails) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
    }

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_vessel_type))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val visibleTypes = if (showOtherTypes) VesselType.entries else primaryVesselTypes
        visibleTypes.forEach { type ->
            FilterChip(
                selected = state.vesselType == type,
                onClick = { state.selectVesselType(type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
        FilterChip(
            selected = showOtherTypes,
            onClick = { showOtherTypes = !showOtherTypes },
            label = {
                Text(
                    stringResource(
                        if (showOtherTypes) R.string.cruise_vessel_types_less else R.string.cruise_vessel_types_more,
                    ),
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = if (showOtherTypes) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
    WizardFieldError(CruiseWizardError.VesselTypeRequired, state, R.string.cruise_error_vessel_type)
}

// ---------------------------------------------------------------------------
// Step 4 — Crew, cost, rules
// ---------------------------------------------------------------------------

@Composable
internal fun WizardCrewStep(state: CruiseWizardState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
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
        Column(modifier = Modifier.weight(1f)) {
            WizardSectionLabel(stringResource(R.string.cruise_field_currency))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_max_participants))
    ParticipantStepper(
        valueText = state.maxParticipantsText,
        value = state.maxParticipantsValue,
        isError = CruiseWizardError.MaxParticipantsInvalid in state.visibleErrors,
        onDecrement = state::decrementMaxParticipants,
        onIncrement = state::incrementMaxParticipants,
    )
    WizardFieldError(CruiseWizardError.MaxParticipantsInvalid, state, R.string.cruise_error_max_participants)

    Spacer(Modifier.size(12.dp))
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

    Spacer(Modifier.size(12.dp))
    WizardSectionLabel(stringResource(R.string.cruise_field_rules))
    RuleRow(R.string.cruise_rule_smoking, state.smokingAllowed, state::updateSmokingAllowed)
    RuleRow(R.string.cruise_rule_alcohol, state.alcoholAllowed, state::updateAlcoholAllowed)
    RuleRow(R.string.cruise_rule_pets, state.petsAllowed, state::updatePetsAllowed)
    RuleRow(R.string.cruise_rule_children, state.childrenAllowed, state::updateChildrenAllowed)
}

@Composable
private fun ParticipantStepper(
    valueText: String,
    value: Int?,
    isError: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.widthIn(min = 168.dp, max = 220.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = onDecrement,
                enabled = (value ?: 1) > 1,
                modifier = Modifier.size(40.dp).testTag("cruise_participants_decrease"),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cruise_participants_decrease))
            }
            Text(
                text = valueText.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onIncrement,
                enabled = (value ?: 1) < CRUISE_MAX_PARTICIPANTS_LIMIT,
                modifier = Modifier.size(40.dp).testTag("cruise_participants_increase"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cruise_participants_increase))
            }
        }
    }
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
// Step 5 — Media
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardCruiseMediaStep(state: CruiseWizardState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var oversizeRejected by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = CRUISE_MEDIA_MAX_COUNT),
    ) { uris ->
        oversizeRejected = false
        val available = CRUISE_MEDIA_MAX_COUNT - state.media.size
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
            text = stringResource(R.string.cruise_media_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            onClick = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            enabled = state.media.size < CRUISE_MEDIA_MAX_COUNT,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cruise_media_add"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(9.dp).size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cruise_media_add),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.wizard_media_limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (oversizeRejected) {
            Text(
                text = stringResource(R.string.wizard_media_too_large),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.media.isEmpty()) {
            Text(
                text = stringResource(R.string.cruise_media_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 3,
        ) {
            state.media.forEach { item ->
                CruiseMediaTile(item = item, onRemove = { state.removeMedia(item.localId) })
            }
        }
    }
}

@Composable
private fun CruiseMediaTile(
    item: CruiseWizardMedia,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth(0.31f)
            .aspectRatio(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                item.isUploading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 2.dp,
                )

                item.failed -> Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = stringResource(R.string.wizard_media_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
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
                            modifier = Modifier.align(Alignment.Center).size(34.dp),
                        )
                    }
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
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

// ---------------------------------------------------------------------------
// Step 6 — Summary
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardSummaryStep(state: CruiseWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryBlock(title = state.title, subtitle = state.type?.let { stringResource(it.labelRes()) })
        SummaryBlock(
            title = "${state.departurePort?.name.orEmpty()} → ${state.arrivalPort?.name.orEmpty()}",
            subtitle = listOfNotNull(
                state.departureDate?.let { formatLocalDate(it) },
                state.arrivalDate?.let { formatLocalDate(it) },
            ).joinToString(" – "),
            label = stringResource(R.string.cruise_field_route),
        )
        state.vesselType?.let {
            SummaryBlock(
                title = "${stringResource(it.labelRes())} • ${state.vessel}",
                subtitle = listOf(state.vesselBrand, state.vesselModel).filterNot { value -> value.isNullOrBlank() }
                    .joinToString(" ")
                    .takeIf { value -> value.isNotBlank() },
                label = stringResource(R.string.cruise_field_vessel),
            )
        }
        if (state.media.isNotEmpty()) {
            SummaryMediaPreview(media = state.media)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryChip(stringResource(R.string.cruise_field_cost), "${state.costText} ${state.currency.wireValue}")
            SummaryChip(stringResource(R.string.cruise_field_max_participants), state.maxParticipantsText)
            SummaryChip(
                stringResource(R.string.cruise_field_private),
                stringResource(if (state.isPrivate) R.string.cruise_rule_yes else R.string.cruise_rule_no),
            )
            if (state.uploadedMediaCount > 0) {
                SummaryChip(
                    stringResource(R.string.cruise_wizard_step_media),
                    pluralStringResource(
                        R.plurals.wizard_summary_media,
                        state.uploadedMediaCount,
                        state.uploadedMediaCount,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryMediaPreview(media: List<CruiseWizardMedia>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.cruise_wizard_step_media),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            media.take(6).forEach { item ->
                SummaryMediaTile(item = item)
            }
        }
    }
}

@Composable
private fun SummaryMediaTile(item: CruiseWizardMedia) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(86.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                item.failed -> Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = stringResource(R.string.wizard_media_failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
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
                            modifier = Modifier.align(Alignment.Center).size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(
    title: String,
    subtitle: String? = null,
    label: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
        }
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
