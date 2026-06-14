package app.skipperclub.ui.main.spots

import app.skipperclub.data.SpotCoordinatesDto
import app.skipperclub.data.CreatePhoneContactPayload
import app.skipperclub.data.CreateRadioChannelPayload
import app.skipperclub.data.CreateSpotRequest
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.PhoneContactsUpdatePayload
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.RadioChannelKind
import app.skipperclub.data.RadioChannelsUpdatePayload
import app.skipperclub.data.Spot
import app.skipperclub.data.UpdatePhoneContactPayload
import app.skipperclub.data.UpdateRadioChannelPayload
import app.skipperclub.data.UpdateSpotAggregateRequest

/** Editable phone contact row in the spot form. A null [id] means a brand-new contact. */
data class EditablePhoneContact(
    val id: String? = null,
    val label: String = "",
    val phone: String = "",
    val extension: String = "",
) {
    val isBlank: Boolean get() = phone.isBlank() && label.isBlank() && extension.isBlank()
}

/** Editable radio channel row in the spot form. A null [id] means a brand-new channel. */
data class EditableRadioChannel(
    val id: String? = null,
    val name: String = "",
    val kind: RadioChannelKind = RadioChannelKind.Vhf,
    val vhfChannel: String = "",
    val frequencyMhz: String = "",
    val isPrimary: Boolean = false,
) {
    val isBlank: Boolean get() = name.isBlank() && vhfChannel.isBlank() && frequencyMhz.isBlank()
}

/**
 * Editable representation of a spot used by the create/edit form. Kept as plain
 * strings so text fields bind directly; conversion + validation live here so the
 * logic is unit-testable without Compose.
 */
data class SpotForm(
    val name: String = "",
    val lat: String = "",
    val lng: String = "",
    val phoneContacts: List<EditablePhoneContact> = emptyList(),
    val radioChannels: List<EditableRadioChannel> = emptyList(),
) {
    val parsedLat: Double? get() = lat.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val parsedLng: Double? get() = lng.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }

    val isNameValid: Boolean get() = name.trim().isNotEmpty() && name.trim().length <= 255
    val isLatValid: Boolean get() = parsedLat != null
    val isLngValid: Boolean get() = parsedLng != null

    /** Non-blank contacts whose required phone is still empty cannot be submitted. */
    private val arePhoneContactsValid: Boolean
        get() = phoneContacts.none { !it.isBlank && it.phone.isBlank() }

    /** A non-blank channel needs a name and a value matching its kind. */
    private val areRadioChannelsValid: Boolean
        get() = radioChannels.none { channel ->
            if (channel.isBlank) return@none false
            val nameMissing = channel.name.isBlank()
            val valueInvalid = when (channel.kind) {
                RadioChannelKind.Vhf -> channel.vhfChannel.trim().toIntOrNull().let { it == null || it !in 1..88 }
                RadioChannelKind.Mhz -> channel.frequencyMhz.trim().toDoubleOrNull().let { it == null || it <= 0.0 }
            }
            nameMissing || valueInvalid
        }

    val isValid: Boolean
        get() = isNameValid && isLatValid && isLngValid && arePhoneContactsValid && areRadioChannelsValid

    companion object {
        fun fromSpot(spot: Spot): SpotForm = SpotForm(
            name = spot.name,
            lat = spot.coordinates.lat.toString(),
            lng = spot.coordinates.lng.toString(),
            phoneContacts = spot.phoneContacts.map {
                EditablePhoneContact(
                    id = it.id,
                    label = it.label.orEmpty(),
                    phone = it.phone,
                    extension = it.extension.orEmpty(),
                )
            },
            radioChannels = spot.radioChannels.map {
                EditableRadioChannel(
                    id = it.id,
                    name = it.name,
                    kind = it.channelKind,
                    vhfChannel = it.vhfChannel?.toString().orEmpty(),
                    frequencyMhz = it.frequencyMhz.orEmpty(),
                    isPrimary = it.isPrimary,
                )
            },
        )
    }
}

private fun EditablePhoneContact.toCreatePayload() = CreatePhoneContactPayload(
    phone = phone.trim(),
    label = label.trim().ifBlank { null },
    extension = extension.trim().ifBlank { null },
)

private fun EditableRadioChannel.toCreatePayload() = CreateRadioChannelPayload(
    name = name.trim(),
    vhfChannel = if (kind == RadioChannelKind.Vhf) vhfChannel.trim().toIntOrNull() else null,
    frequencyMhz = if (kind == RadioChannelKind.Mhz) frequencyMhz.trim().toDoubleOrNull() else null,
    isPrimary = isPrimary,
)

/** Builds the `POST /v1/spots` body from a validated form. */
fun SpotForm.toCreateRequest(): CreateSpotRequest = CreateSpotRequest(
    name = name.trim(),
    coordinates = SpotCoordinatesDto(lat = parsedLat ?: 0.0, lng = parsedLng ?: 0.0),
    phoneContacts = phoneContacts.filterNot { it.isBlank }.map { it.toCreatePayload() },
    radioChannels = radioChannels.filterNot { it.isBlank }.map { it.toCreatePayload() },
)

private fun EditablePhoneContact.matches(original: PhoneContact): Boolean =
    label.trim().ifBlank { null } == original.label &&
        phone.trim() == original.phone &&
        extension.trim().ifBlank { null } == original.extension

private fun EditableRadioChannel.matches(original: RadioChannel): Boolean =
    name.trim() == original.name &&
        kind == original.channelKind &&
        isPrimary == original.isPrimary &&
        when (kind) {
            RadioChannelKind.Vhf -> vhfChannel.trim().toIntOrNull() == original.vhfChannel
            RadioChannelKind.Mhz -> frequencyMhz.trim().toDoubleOrNull() == original.frequencyMhz?.toDoubleOrNull()
        }

private fun EditablePhoneContact.toUpdatePayload(id: String) = UpdatePhoneContactPayload(
    contactId = id,
    label = label.trim().ifBlank { null },
    phone = phone.trim(),
    extension = extension.trim().ifBlank { null },
)

private fun EditableRadioChannel.toUpdatePayload(id: String) = UpdateRadioChannelPayload(
    channelId = id,
    name = name.trim(),
    vhfChannel = if (kind == RadioChannelKind.Vhf) vhfChannel.trim().toIntOrNull() else null,
    frequencyMhz = if (kind == RadioChannelKind.Mhz) frequencyMhz.trim().toDoubleOrNull() else null,
    isPrimary = isPrimary,
)

private fun phoneContactsDiff(original: List<PhoneContact>, edited: List<EditablePhoneContact>): PhoneContactsUpdatePayload? {
    val originalById = original.associateBy { it.id }
    val keptIds = edited.mapNotNull { it.id }.toSet()

    val create = edited.filter { it.id == null && !it.isBlank }.map { it.toCreatePayload() }
    val update = edited.mapNotNull { contact ->
        val id = contact.id ?: return@mapNotNull null
        val origin = originalById[id] ?: return@mapNotNull null
        if (contact.matches(origin)) null else contact.toUpdatePayload(id)
    }
    val delete = original.map { it.id }.filterNot { it in keptIds }

    if (create.isEmpty() && update.isEmpty() && delete.isEmpty()) return null
    return PhoneContactsUpdatePayload(
        create = create.ifEmpty { null },
        update = update.ifEmpty { null },
        delete = delete.ifEmpty { null },
    )
}

private fun radioChannelsDiff(original: List<RadioChannel>, edited: List<EditableRadioChannel>): RadioChannelsUpdatePayload? {
    val originalById = original.associateBy { it.id }
    val keptIds = edited.mapNotNull { it.id }.toSet()

    val create = edited.filter { it.id == null && !it.isBlank }.map { it.toCreatePayload() }
    val update = edited.mapNotNull { channel ->
        val id = channel.id ?: return@mapNotNull null
        val origin = originalById[id] ?: return@mapNotNull null
        if (channel.matches(origin)) null else channel.toUpdatePayload(id)
    }
    val delete = original.map { it.id }.filterNot { it in keptIds }

    if (create.isEmpty() && update.isEmpty() && delete.isEmpty()) return null
    return RadioChannelsUpdatePayload(
        create = create.ifEmpty { null },
        update = update.ifEmpty { null },
        delete = delete.ifEmpty { null },
    )
}

/**
 * Diffs [form] against the [original] spot and builds the minimal
 * `PATCH /v1/spots/:id` aggregate request — only changed fields and the
 * create/update/delete sets for contacts and channels are populated.
 *
 * Note: clearing a previously-set optional `label`/`extension` is not propagated
 * (a blank value maps to `null`, which is dropped from the JSON body).
 */
fun buildUpdateRequest(original: Spot, form: SpotForm): UpdateSpotAggregateRequest {
    val nameChanged = form.name.trim() != original.name
    val newLat = form.parsedLat
    val newLng = form.parsedLng
    val coordinatesChanged = newLat != null && newLng != null &&
        (newLat != original.coordinates.lat || newLng != original.coordinates.lng)

    return UpdateSpotAggregateRequest(
        name = if (nameChanged) form.name.trim() else null,
        coordinates = if (coordinatesChanged) SpotCoordinatesDto(lat = newLat!!, lng = newLng!!) else null,
        phoneContacts = phoneContactsDiff(original.phoneContacts, form.phoneContacts),
        radioChannels = radioChannelsDiff(original.radioChannels, form.radioChannels),
    )
}

/** True when [form] carries no change relative to [original] — lets the UI skip a no-op PATCH. */
fun hasChanges(original: Spot, form: SpotForm): Boolean {
    val request = buildUpdateRequest(original, form)
    return request.name != null ||
        request.coordinates != null ||
        request.phoneContacts != null ||
        request.radioChannels != null
}
