package app.skipperclub.data

import kotlinx.serialization.Serializable

/** Geographic coordinates (WGS84) as rendered/edited by the spots UI. */
data class Coordinates(
    val lat: Double,
    val lng: Double,
)

/** A marina phone contact attached to a spot. */
data class PhoneContact(
    val id: String,
    val label: String?,
    val phone: String,
    val extension: String?,
)

/** Whether a radio channel is identified by VHF channel number or by MHz frequency. */
enum class RadioChannelKind(val wireValue: String) {
    Vhf("vhf"),
    Mhz("mhz"),
    ;

    companion object {
        fun fromWire(value: String): RadioChannelKind =
            entries.firstOrNull { it.wireValue == value } ?: Vhf
    }
}

/** A marina radio channel attached to a spot. */
data class RadioChannel(
    val id: String,
    val name: String,
    val channelKind: RadioChannelKind,
    val vhfChannel: Int?,
    val frequencyMhz: String?,
    val isPrimary: Boolean,
)

/** A spot (sailing location) as rendered by the admin UI. */
data class Spot(
    val id: String,
    val name: String,
    val coordinates: Coordinates,
    val phoneContacts: List<PhoneContact>,
    val radioChannels: List<RadioChannel>,
    val createdAt: String,
    val updatedAt: String,
)

/** A nearby spot returned by the 409 duplicate problem when creating/updating. */
data class NearbySpot(
    val id: String,
    val name: String,
    val coordinates: Coordinates,
    val distanceMeters: Int,
)

/** Query parameters for `GET /v1/spots`. */
data class SpotListQuery(
    val name: String? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)

data class SpotsPage(
    val spots: List<Spot>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + spots.size < total
}

// ─── Wire DTOs (responses) ───────────────────────────────────────────────────

@Serializable
data class SpotCoordinatesDto(
    val lat: Double,
    val lng: Double,
) {
    fun toDomain(): Coordinates = Coordinates(lat = lat, lng = lng)
}

@Serializable
internal data class PhoneContactDto(
    val id: String,
    val label: String? = null,
    val phone: String,
    val extension: String? = null,
) {
    fun toDomain(): PhoneContact = PhoneContact(
        id = id,
        label = label,
        phone = phone,
        extension = extension,
    )
}

@Serializable
internal data class RadioChannelDto(
    val id: String,
    val name: String,
    val channelKind: String,
    val vhfChannel: Int? = null,
    val frequencyMhz: String? = null,
    val isPrimary: Boolean = false,
) {
    fun toDomain(): RadioChannel = RadioChannel(
        id = id,
        name = name,
        channelKind = RadioChannelKind.fromWire(channelKind),
        vhfChannel = vhfChannel,
        frequencyMhz = frequencyMhz,
        isPrimary = isPrimary,
    )
}

@Serializable
internal data class SpotDto(
    val id: String,
    val name: String,
    val coordinates: SpotCoordinatesDto,
    val phoneContacts: List<PhoneContactDto> = emptyList(),
    val radioChannels: List<RadioChannelDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
) {
    fun toDomain(): Spot = Spot(
        id = id,
        name = name,
        coordinates = coordinates.toDomain(),
        phoneContacts = phoneContacts.map { it.toDomain() },
        radioChannels = radioChannels.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Serializable
internal data class PaginationMetaDto(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
internal data class SpotsListDto(
    val data: List<SpotDto> = emptyList(),
    val meta: PaginationMetaDto = PaginationMetaDto(),
) {
    fun toDomain(): SpotsPage = SpotsPage(
        spots = data.map { it.toDomain() },
        total = meta.total,
        limit = meta.limit,
        offset = meta.offset,
    )
}

@Serializable
internal data class NearbySpotDto(
    val id: String,
    val name: String,
    val coordinates: SpotCoordinatesDto,
    val distanceMeters: Int = 0,
) {
    fun toDomain(): NearbySpot = NearbySpot(
        id = id,
        name = name,
        coordinates = coordinates.toDomain(),
        distanceMeters = distanceMeters,
    )
}

@Serializable
internal data class SpotDuplicateProblemDto(
    val detail: String? = null,
    val title: String? = null,
    val nearbySpots: List<NearbySpotDto> = emptyList(),
)

// ─── Wire DTOs (requests) ────────────────────────────────────────────────────

@Serializable
data class CreatePhoneContactPayload(
    val phone: String,
    val label: String? = null,
    val extension: String? = null,
)

@Serializable
data class CreateRadioChannelPayload(
    val name: String,
    val vhfChannel: Int? = null,
    val frequencyMhz: Double? = null,
    val isPrimary: Boolean = false,
)

@Serializable
data class CreateSpotRequest(
    val name: String,
    val coordinates: SpotCoordinatesDto,
    val phoneContacts: List<CreatePhoneContactPayload> = emptyList(),
    val radioChannels: List<CreateRadioChannelPayload> = emptyList(),
)

@Serializable
data class UpdatePhoneContactPayload(
    val contactId: String,
    val label: String? = null,
    val phone: String? = null,
    val extension: String? = null,
)

@Serializable
data class UpdateRadioChannelPayload(
    val channelId: String,
    val name: String? = null,
    val vhfChannel: Int? = null,
    val frequencyMhz: Double? = null,
    val isPrimary: Boolean? = null,
)

@Serializable
data class PhoneContactsUpdatePayload(
    val create: List<CreatePhoneContactPayload>? = null,
    val update: List<UpdatePhoneContactPayload>? = null,
    val delete: List<String>? = null,
)

@Serializable
data class RadioChannelsUpdatePayload(
    val create: List<CreateRadioChannelPayload>? = null,
    val update: List<UpdateRadioChannelPayload>? = null,
    val delete: List<String>? = null,
)

@Serializable
data class UpdateSpotAggregateRequest(
    val name: String? = null,
    val coordinates: SpotCoordinatesDto? = null,
    val phoneContacts: PhoneContactsUpdatePayload? = null,
    val radioChannels: RadioChannelsUpdatePayload? = null,
)
