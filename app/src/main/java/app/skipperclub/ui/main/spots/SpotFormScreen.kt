package app.skipperclub.ui.main.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.PlacePrediction
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.RadioChannelKind
import app.skipperclub.data.ResolvedPlace
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debounced-less search field that fires [onSearch] on submit/clear. */
@Composable
internal fun SpotsSearchField(
    query: String,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(query) { mutableStateOf(query) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onSearch("")
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.spots_search_clear))
                }
            }
        },
        placeholder = { Text(stringResource(R.string.spots_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(text) }),
        modifier = modifier.testTag("spots_search"),
    )
}

/**
 * Stateless create/edit form for a spot. State is hoisted internally from
 * [initial] so previews and tests can drive it; [onSubmit] hands back the
 * current [SpotForm] for the controller to validate and persist.
 *
 * The location is set by typing a name and picking a place from Google Places
 * autocomplete ([searchPlaces] + [onResolvePlace]) — coordinates are never typed
 * by hand. Both default to no-ops so previews and unit tests render without the
 * Places SDK.
 */
@Composable
internal fun SpotFormContent(
    initial: SpotForm,
    isEditing: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onErrorConsumed: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: (SpotForm) -> Unit,
    modifier: Modifier = Modifier,
    searchPlaces: suspend (String) -> List<PlacePrediction> = { emptyList() },
    onResolvePlace: suspend (PlacePrediction) -> ResolvedPlace? = { null },
) {
    var form by remember(initial) { mutableStateOf(initial) }
    fun update(transform: (SpotForm) -> SpotForm) {
        form = transform(form)
        if (errorMessage != null) onErrorConsumed()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("spot_form"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel, modifier = Modifier.testTag("spot_form_back")) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.spots_cancel))
            }
            Text(
                text = stringResource(
                    if (isEditing) R.string.spot_form_edit_title else R.string.spot_form_create_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onSubmit(form) },
                enabled = form.isValid && !isSaving,
                modifier = Modifier.testTag("spot_form_save"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.spot_form_save))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("spot_form_error"),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            PlaceAutocompleteField(
                name = form.name,
                isNameValid = form.isNameValid,
                hasLocation = form.hasLocation,
                locationLabel = form.locationLabel,
                coordinatesText = form.takeIf { it.hasLocation }
                    ?.let { "%.5f, %.5f".format(it.parsedLat, it.parsedLng) },
                enabled = !isSaving,
                searchPlaces = searchPlaces,
                onResolvePlace = onResolvePlace,
                onNameChange = { name -> update { it.copy(name = name) } },
                onPlaceResolved = { resolved -> update { it.withResolvedPlace(resolved) } },
                onChangeLocation = { update { it.clearLocation() } },
            )

            SectionHeader(
                title = stringResource(R.string.spot_form_contacts_title),
                actionLabel = stringResource(R.string.spot_form_add_contact),
                actionTag = "spot_form_add_contact",
                onAction = { update { it.copy(phoneContacts = it.phoneContacts + EditablePhoneContact()) } },
            )
            form.phoneContacts.forEachIndexed { index, contact ->
                PhoneContactEditor(
                    contact = contact,
                    enabled = !isSaving,
                    onChange = { changed ->
                        update { it.copy(phoneContacts = it.phoneContacts.replaceAt(index, changed)) }
                    },
                    onRemove = { update { it.copy(phoneContacts = it.phoneContacts.removeAt(index)) } },
                    modifier = Modifier.testTag("spot_form_contact_$index"),
                )
            }

            SectionHeader(
                title = stringResource(R.string.spot_form_channels_title),
                actionLabel = stringResource(R.string.spot_form_add_channel),
                actionTag = "spot_form_add_channel",
                onAction = { update { it.copy(radioChannels = it.radioChannels + EditableRadioChannel()) } },
            )
            form.radioChannels.forEachIndexed { index, channel ->
                RadioChannelEditor(
                    channel = channel,
                    enabled = !isSaving,
                    onChange = { changed ->
                        update { it.copy(radioChannels = it.radioChannels.replaceAt(index, changed)) }
                    },
                    onRemove = { update { it.copy(radioChannels = it.radioChannels.removeAt(index)) } },
                    modifier = Modifier.testTag("spot_form_channel_$index"),
                )
            }

            Box(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

/**
 * Name field that doubles as a Google Places search. While no location is set,
 * keystrokes (debounced) fetch autocomplete predictions; picking one resolves
 * its coordinates and shows a confirmation card. Once a place is chosen the name
 * stays freely editable (so the admin can tweak the spot's display name) without
 * re-triggering search until they tap "change location".
 */
@Composable
private fun PlaceAutocompleteField(
    name: String,
    isNameValid: Boolean,
    hasLocation: Boolean,
    locationLabel: String?,
    coordinatesText: String?,
    enabled: Boolean,
    searchPlaces: suspend (String) -> List<PlacePrediction>,
    onResolvePlace: suspend (PlacePrediction) -> ResolvedPlace?,
    onNameChange: (String) -> Unit,
    onPlaceResolved: (ResolvedPlace) -> Unit,
    onChangeLocation: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    // Debounced search — only while a location has not yet been picked.
    LaunchedEffect(name, hasLocation) {
        if (hasLocation) {
            predictions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        val query = name.trim()
        if (query.length < MinAutocompleteChars) {
            predictions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(AutocompleteDebounceMillis)
        predictions = runCatching { searchPlaces(query) }.getOrDefault(emptyList())
        searching = false
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.spot_form_name_label)) },
            singleLine = true,
            isError = name.isNotEmpty() && !isNameValid,
            enabled = enabled,
            trailingIcon = {
                when {
                    searching || resolving -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    hasLocation -> Icon(Icons.Filled.Place, contentDescription = null)
                    else -> Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("spot_form_name"),
        )

        when {
            hasLocation -> SelectedLocationCard(
                label = locationLabel,
                coordinatesText = coordinatesText,
                enabled = enabled && !resolving,
                onChange = onChangeLocation,
            )

            predictions.isNotEmpty() -> Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("spot_form_predictions"),
            ) {
                Column {
                    predictions.forEachIndexed { index, prediction ->
                        if (index > 0) HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        PredictionRow(
                            prediction = prediction,
                            enabled = enabled && !resolving,
                            onClick = {
                                resolving = true
                                scope.launch {
                                    val resolved = runCatching { onResolvePlace(prediction) }.getOrNull()
                                    resolving = false
                                    if (resolved != null) {
                                        predictions = emptyList()
                                        onPlaceResolved(resolved)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("spot_form_prediction_$index"),
                        )
                    }
                }
            }

            else -> Text(
                text = stringResource(R.string.spot_form_location_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun PredictionRow(
    prediction: PlacePrediction,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = prediction.primaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (prediction.secondaryText != null) {
                Text(
                    text = prediction.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectedLocationCard(
    label: String?,
    coordinatesText: String?,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("spot_form_location"),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (coordinatesText != null) {
                    Text(
                        text = coordinatesText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = onChange,
                enabled = enabled,
                modifier = Modifier.testTag("spot_form_change_location"),
            ) {
                Text(stringResource(R.string.spot_form_change_location))
            }
        }
    }
}

private const val MinAutocompleteChars = 2
private const val AutocompleteDebounceMillis = 250L

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    actionTag: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction, modifier = Modifier.testTag(actionTag)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = actionLabel, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun PhoneContactEditor(
    contact: EditablePhoneContact,
    enabled: Boolean,
    onChange: (EditablePhoneContact) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.spot_form_contact_phone),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                RemoveButton(enabled = enabled, onRemove = onRemove)
            }
            val phoneInvalid = !contact.isBlank && !contact.isPhoneValid
            OutlinedTextField(
                value = contact.phone,
                onValueChange = { onChange(contact.copy(phone = it)) },
                label = { Text(stringResource(R.string.spot_form_contact_phone)) },
                singleLine = true,
                enabled = enabled,
                isError = phoneInvalid,
                supportingText = if (phoneInvalid) {
                    { Text(stringResource(R.string.spot_form_contact_phone_hint)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = contact.label,
                    onValueChange = { onChange(contact.copy(label = it)) },
                    label = { Text(stringResource(R.string.spot_form_contact_label)) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = contact.extension,
                    onValueChange = { onChange(contact.copy(extension = it)) },
                    label = { Text(stringResource(R.string.spot_form_contact_extension)) },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadioChannelEditor(
    channel: EditableRadioChannel,
    enabled: Boolean,
    onChange: (EditableRadioChannel) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.spot_form_channel_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                RemoveButton(enabled = enabled, onRemove = onRemove)
            }
            OutlinedTextField(
                value = channel.name,
                onValueChange = { onChange(channel.copy(name = it)) },
                label = { Text(stringResource(R.string.spot_form_channel_name)) },
                singleLine = true,
                enabled = enabled,
                isError = !channel.isBlank && channel.name.isBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = channel.kind == RadioChannelKind.Vhf,
                    onClick = { onChange(channel.copy(kind = RadioChannelKind.Vhf)) },
                    label = { Text(stringResource(R.string.spot_form_channel_kind_vhf)) },
                    enabled = enabled,
                )
                FilterChip(
                    selected = channel.kind == RadioChannelKind.Mhz,
                    onClick = { onChange(channel.copy(kind = RadioChannelKind.Mhz)) },
                    label = { Text(stringResource(R.string.spot_form_channel_kind_mhz)) },
                    enabled = enabled,
                )
            }
            when (channel.kind) {
                RadioChannelKind.Vhf -> OutlinedTextField(
                    value = channel.vhfChannel,
                    onValueChange = { onChange(channel.copy(vhfChannel = it)) },
                    label = { Text(stringResource(R.string.spot_form_channel_vhf_label)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = channel.vhfChannel.isNotBlank() &&
                        channel.vhfChannel.trim().toIntOrNull().let { it == null || it !in 1..88 },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                RadioChannelKind.Mhz -> OutlinedTextField(
                    value = channel.frequencyMhz,
                    onValueChange = { onChange(channel.copy(frequencyMhz = it)) },
                    label = { Text(stringResource(R.string.spot_form_channel_mhz_label)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = channel.frequencyMhz.isNotBlank() &&
                        channel.frequencyMhz.trim().toDoubleOrNull().let { it == null || it <= 0.0 },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.spot_form_channel_primary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = channel.isPrimary,
                    onCheckedChange = { onChange(channel.copy(isPrimary = it)) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun RemoveButton(enabled: Boolean, onRemove: () -> Unit) {
    IconButton(onClick = onRemove, enabled = enabled) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.spot_form_remove),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    toMutableList().also { it.removeAt(index) }

// --- Previews ---

private val previewContacts = listOf(
    PhoneContact("c1", "Harbour master", "+48581234567", "12"),
)
private val previewChannels = listOf(
    RadioChannel("r1", "Port control", RadioChannelKind.Vhf, 12, null, true),
)

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "en")
@Composable
private fun SpotFormCreatePreview() {
    SkipperClubTheme {
        SpotFormContent(
            initial = SpotForm(),
            isEditing = false,
            isSaving = false,
            errorMessage = null,
            onErrorConsumed = {},
            onCancel = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, locale = "pl")
@Composable
private fun SpotFormEditPreviewPl() {
    SkipperClubTheme {
        SpotFormContent(
            initial = SpotForm.fromSpot(
                previewSpot("s1", "Neptun Marina", phoneContacts = previewContacts, radioChannels = previewChannels),
            ).copy(locationLabel = "ul. Szafarnia 11, Gdańsk"),
            isEditing = true,
            isSaving = false,
            errorMessage = "Istnieje już miejsce w pobliżu.",
            onErrorConsumed = {},
            onCancel = {},
            onSubmit = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SpotFormEditPreviewDark() {
    SkipperClubTheme {
        SpotFormContent(
            initial = SpotForm.fromSpot(
                previewSpot("s1", "Neptun Marina", phoneContacts = previewContacts, radioChannels = previewChannels),
            ).copy(locationLabel = "ul. Szafarnia 11, Gdańsk"),
            isEditing = true,
            isSaving = false,
            errorMessage = null,
            onErrorConsumed = {},
            onCancel = {},
            onSubmit = {},
        )
    }
}
