package app.skipperclub.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Vessel classification. Wire values follow `docs/api/openapi.yaml` (`VesselType`). */
enum class VesselType(val wireValue: String) {
    SailingYacht("SAILING_YACHT"),
    Catamaran("CATAMARAN"),
    Motorboat("MOTORBOAT"),
    Trimaran("TRIMARAN"),
    Gulet("GULET"),
    Schooner("SCHOONER"),
    ;

    companion object {
        fun fromWire(value: String): VesselType? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Cruise character/theme. Wire values follow `docs/api/reference/enums/cruise-types.md`. */
enum class CruiseType(val wireValue: String) {
    BeginnerIntro("BEGINNER_INTRO"),
    Training("TRAINING"),
    Milebuilding("MILEBUILDING"),
    Advanced("ADVANCED"),
    SportRegatta("SPORT_REGATTA"),
    Family("FAMILY"),
    Singles("SINGLES"),
    Couples("COUPLES"),
    Seniors("SENIORS"),
    WomenOnly("WOMEN_ONLY"),
    MenOnly("MEN_ONLY"),
    Party("PARTY"),
    Relax("RELAX"),
    Survival("SURVIVAL"),
    Photography("PHOTOGRAPHY"),
    Culinary("CULINARY"),
    CulturalHistorical("CULTURAL_HISTORICAL"),
    Exploration("EXPLORATION"),
    ;

    companion object {
        fun fromWire(value: String): CruiseType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class CruiseCurrency(val wireValue: String) {
    Pln("PLN"),
    Eur("EUR"),
    Usd("USD"),
    ;

    companion object {
        fun fromWire(value: String): CruiseCurrency? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Crew membership state machine (`docs/ux/flows/cruise-participant-state-flow.md`):
 * `pending`/`invited` are entry states, `accepted` is the only active state and the
 * remaining six are terminal.
 */
enum class CruiseParticipantState(val wireValue: String) {
    Pending("pending"),
    Invited("invited"),
    Accepted("accepted"),
    RejectedByParticipant("rejected_by_participant"),
    RejectedByOrganizer("rejected_by_organizer"),
    WithdrawnByParticipant("withdrawn_by_participant"),
    WithdrawnByOrganizer("withdrawn_by_organizer"),
    CanceledByParticipant("canceled_by_participant"),
    CanceledByOrganizer("canceled_by_organizer"),
    ;

    val isTerminal: Boolean
        get() = this != Pending && this != Invited && this != Accepted

    companion object {
        fun fromWire(value: String): CruiseParticipantState? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class CruiseUserRole(val wireValue: String) {
    Organizer("organizer"),
    Participant("participant"),
    None("none"),
    ;

    companion object {
        fun fromWire(value: String): CruiseUserRole = entries.firstOrNull { it.wireValue == value } ?: None
    }
}

enum class CruiseParticipantRole(val wireValue: String) {
    Organizer("organizer"),
    Participant("participant"),
    ;

    companion object {
        fun fromWire(value: String): CruiseParticipantRole? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** `scope` query parameter of `GET /v1/cruises`. */
enum class CruiseScope(val wireValue: String) {
    All("all"),
    Mine("mine"),
    Organized("organized"),
    Participating("participating"),
    ;
}

enum class CruiseSortField(val wireValue: String) {
    CreatedAt("createdAt"),
    DepartureDate("departureDate"),
    Title("title"),
    CostPerPerson("costPerPerson"),
    ;
}

/** Query parameters for `GET /v1/cruises`. */
data class CruiseListQuery(
    val scope: CruiseScope = CruiseScope.All,
    val state: CruiseParticipantState? = null,
    val search: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val type: CruiseType? = null,
    val vesselType: VesselType? = null,
    // Spatial filter: matches cruises whose departure OR arrival port is within
    // [distance] km of ([lat], [lng]). Sent all-or-none; incomplete triples are omitted.
    val lat: Double? = null,
    val lng: Double? = null,
    val distance: Int? = null,
    val sort: CruiseSortField = CruiseSortField.DepartureDate,
    val order: SortOrder = SortOrder.Desc,
    val limit: Int = 20,
    val offset: Int = 0,
) {
    /** True only when the full lat/lng/distance triple is present. */
    val hasSpatialFilter: Boolean
        get() = lat != null && lng != null && distance != null
}

data class CruiseUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class CruisePort(
    val name: String,
    val coordinates: PostCoordinates,
)

data class CruiseParticipant(
    val id: String,
    val cruiseId: String,
    val userId: String,
    val user: CruiseUser? = null,
    val role: CruiseParticipantRole,
    val state: CruiseParticipantState,
    val createdAt: String,
    val updatedAt: String,
)

data class Cruise(
    val id: String,
    val title: String,
    val description: String,
    val hashtags: List<String> = emptyList(),
    val departureDate: String,
    val departurePort: CruisePort,
    val arrivalDate: String,
    val arrivalPort: CruisePort,
    val stops: List<CruisePort> = emptyList(),
    val requiredSkills: String? = null,
    val costPerPerson: Double,
    val currency: CruiseCurrency,
    val maxParticipants: Int,
    val participantsCount: Int = 0,
    val isPrivate: Boolean = false,
    val vessel: String,
    val vesselBrand: String? = null,
    val vesselModel: String? = null,
    val vesselYear: Int? = null,
    val vesselLength: Double? = null,
    val vesselCabins: Int? = null,
    val vesselType: VesselType,
    val type: CruiseType? = null,
    val smokingAllowed: Boolean? = null,
    val alcoholAllowed: Boolean? = null,
    val petsAllowed: Boolean? = null,
    val childrenAllowed: Boolean? = null,
    val media: List<PostMedia> = emptyList(),
    val organizer: CruiseUser,
    val participants: List<CruiseUser> = emptyList(),
    val currentUserRole: CruiseUserRole = CruiseUserRole.None,
    val currentUserParticipation: CruiseParticipant? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    val isFull: Boolean
        get() = participantsCount >= maxParticipants
}

data class CruisesPage(
    val cruises: List<Cruise>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + cruises.size < total
}

data class CruiseParticipantsPage(
    val participants: List<CruiseParticipant>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

/**
 * Body of `POST /v1/cruises` and `PUT /v1/cruises/{id}`. The wire field `private`
 * is a Kotlin keyword, hence the [SerialName] mapping.
 */
@Serializable
data class CruisePayload(
    val title: String,
    val description: String,
    val departureDate: String,
    val departurePort: CruisePortDto,
    val arrivalDate: String,
    val arrivalPort: CruisePortDto,
    val stops: List<CruisePortDto>? = null,
    val requiredSkills: String? = null,
    val costPerPerson: Double,
    val currency: String,
    val maxParticipants: Int,
    @SerialName("private") val isPrivate: Boolean,
    val vessel: String,
    val vesselBrand: String? = null,
    val vesselModel: String? = null,
    val vesselYear: Int? = null,
    val vesselLength: Double? = null,
    val vesselCabins: Int? = null,
    val vesselType: String,
    val mediaIds: List<String>? = null,
    val type: String? = null,
    val smokingAllowed: Boolean? = null,
    val alcoholAllowed: Boolean? = null,
    val petsAllowed: Boolean? = null,
    val childrenAllowed: Boolean? = null,
)

/**
 * AI-extracted cruise draft (domain), produced from a free-form description via
 * `POST /v1/cruises/ai-draft`. Every field mirrors a cruise wizard input so the
 * draft can pre-fill the form; nulls mean "AI did not extract this — leave the
 * field as-is / let the user fill it" (see `docs/api/cruises/ai-draft.md`).
 */
data class CruiseAiDraft(
    val title: String? = null,
    val description: String = "",
    val departureDate: String? = null,
    val departurePort: CruisePort? = null,
    val arrivalDate: String? = null,
    val arrivalPort: CruisePort? = null,
    val stops: List<CruisePort> = emptyList(),
    val requiredSkills: String? = null,
    val costPerPerson: Double? = null,
    val currency: CruiseCurrency? = null,
    val isPrivate: Boolean = false,
    val vessel: String? = null,
    val vesselBrand: String? = null,
    val vesselModel: String? = null,
    val vesselYear: Int? = null,
    val vesselLength: Double? = null,
    val vesselCabins: Int? = null,
    val vesselType: VesselType? = null,
    val maxParticipants: Int? = null,
    val type: CruiseType? = null,
    val smokingAllowed: Boolean? = null,
    val alcoholAllowed: Boolean? = null,
    val petsAllowed: Boolean? = null,
    val childrenAllowed: Boolean? = null,
)

/** Body of `POST /v1/cruises/ai-draft`. */
@Serializable
data class CruiseAiDraftRequest(
    val description: String,
)

/**
 * Response of `POST /v1/cruises/ai-draft`. The endpoint always returns 200 with a
 * full structure; unknown enum values are dropped to `null` in [toDomain] so the
 * wizard simply leaves those fields untouched.
 */
@Serializable
internal data class CruiseAiDraftResponseDto(
    val title: String? = null,
    val description: String = "",
    val departureDate: String? = null,
    val departurePort: CruisePortDto? = null,
    val arrivalDate: String? = null,
    val arrivalPort: CruisePortDto? = null,
    val stops: List<CruisePortDto> = emptyList(),
    val requiredSkills: String? = null,
    val costPerPerson: Double? = null,
    val currency: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val vessel: String? = null,
    val vesselBrand: String? = null,
    val vesselModel: String? = null,
    val vesselYear: Int? = null,
    val vesselLength: Double? = null,
    val vesselCabins: Int? = null,
    val vesselType: String? = null,
    val maxParticipants: Int? = null,
    val type: String? = null,
    val smokingAllowed: Boolean? = null,
    val alcoholAllowed: Boolean? = null,
    val petsAllowed: Boolean? = null,
    val childrenAllowed: Boolean? = null,
) {
    fun toDomain(): CruiseAiDraft = CruiseAiDraft(
        title = title?.takeIf { it.isNotBlank() },
        description = description,
        departureDate = departureDate,
        departurePort = departurePort?.toDomain(),
        arrivalDate = arrivalDate,
        arrivalPort = arrivalPort?.toDomain(),
        stops = stops.map { it.toDomain() },
        requiredSkills = requiredSkills?.takeIf { it.isNotBlank() },
        costPerPerson = costPerPerson,
        currency = currency?.let { CruiseCurrency.fromWire(it) },
        isPrivate = isPrivate,
        vessel = vessel?.takeIf { it.isNotBlank() },
        vesselBrand = vesselBrand?.takeIf { it.isNotBlank() },
        vesselModel = vesselModel?.takeIf { it.isNotBlank() },
        vesselYear = vesselYear,
        vesselLength = vesselLength,
        vesselCabins = vesselCabins,
        vesselType = vesselType?.let { VesselType.fromWire(it) },
        maxParticipants = maxParticipants,
        type = type?.let { CruiseType.fromWire(it) },
        smokingAllowed = smokingAllowed,
        alcoholAllowed = alcoholAllowed,
        petsAllowed = petsAllowed,
        childrenAllowed = childrenAllowed,
    )
}

@Serializable
data class CruisePortDto(
    val name: String,
    val coordinates: CoordinatesDto,
) {
    fun toDomain(): CruisePort = CruisePort(name = name, coordinates = coordinates.toDomain())

    companion object {
        fun from(port: CruisePort): CruisePortDto =
            CruisePortDto(name = port.name, coordinates = CoordinatesDto.from(port.coordinates))
    }
}

@Serializable
internal data class CruiseParticipantCreateRequest(
    val userId: String,
)

@Serializable
internal data class CruiseParticipantStateUpdateRequest(
    val state: String,
)

@Serializable
internal data class CruiseUserDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
) {
    fun toDomain(): CruiseUser = CruiseUser(id = id, name = name, avatarUrl = avatarUrl)
}

@Serializable
internal data class CruiseParticipantDto(
    val id: String,
    val cruiseId: String,
    val userId: String,
    val user: CruiseUserDto? = null,
    val role: String,
    val state: String,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    /** Participants with unknown role/state are dropped rather than crash the screen. */
    fun toDomain(): CruiseParticipant? {
        val participantRole = CruiseParticipantRole.fromWire(role) ?: return null
        val participantState = CruiseParticipantState.fromWire(state) ?: return null
        return CruiseParticipant(
            id = id,
            cruiseId = cruiseId,
            userId = userId,
            user = user?.toDomain(),
            role = participantRole,
            state = participantState,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
internal data class CruiseDto(
    val id: String,
    val title: String,
    val description: String = "",
    val hashtags: List<String> = emptyList(),
    val departureDate: String,
    val departurePort: CruisePortDto,
    val arrivalDate: String,
    val arrivalPort: CruisePortDto,
    val stops: List<CruisePortDto> = emptyList(),
    val requiredSkills: String? = null,
    val costPerPerson: Double = 0.0,
    val currency: String = "EUR",
    val maxParticipants: Int = 1,
    val participantsCount: Int = 0,
    @SerialName("private") val isPrivate: Boolean = false,
    val vessel: String = "",
    val vesselBrand: String? = null,
    val vesselModel: String? = null,
    val vesselYear: Int? = null,
    val vesselLength: Double? = null,
    val vesselCabins: Int? = null,
    val vesselType: String,
    val type: String? = null,
    val smokingAllowed: Boolean? = null,
    val alcoholAllowed: Boolean? = null,
    val petsAllowed: Boolean? = null,
    val childrenAllowed: Boolean? = null,
    val media: List<PostMediaDto> = emptyList(),
    val organizer: CruiseUserDto,
    val participants: List<CruiseUserDto> = emptyList(),
    val currentUserRole: String = "none",
    val currentUserParticipation: CruiseParticipantDto? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    /** Cruises with an unknown vessel type or currency are dropped rather than crash the list. */
    fun toDomain(): Cruise? {
        val vesselTypeEnum = VesselType.fromWire(vesselType) ?: return null
        val cruiseCurrency = CruiseCurrency.fromWire(currency) ?: return null
        return Cruise(
            id = id,
            title = title,
            description = description,
            hashtags = hashtags,
            departureDate = departureDate,
            departurePort = departurePort.toDomain(),
            arrivalDate = arrivalDate,
            arrivalPort = arrivalPort.toDomain(),
            stops = stops.map { it.toDomain() },
            requiredSkills = requiredSkills,
            costPerPerson = costPerPerson,
            currency = cruiseCurrency,
            maxParticipants = maxParticipants,
            participantsCount = participantsCount,
            isPrivate = isPrivate,
            vessel = vessel,
            vesselBrand = vesselBrand,
            vesselModel = vesselModel,
            vesselYear = vesselYear,
            vesselLength = vesselLength,
            vesselCabins = vesselCabins,
            vesselType = vesselTypeEnum,
            type = type?.let { CruiseType.fromWire(it) },
            smokingAllowed = smokingAllowed,
            alcoholAllowed = alcoholAllowed,
            petsAllowed = petsAllowed,
            childrenAllowed = childrenAllowed,
            media = media.map { it.toDomain() },
            organizer = organizer.toDomain(),
            participants = participants.map { it.toDomain() },
            currentUserRole = CruiseUserRole.fromWire(currentUserRole),
            currentUserParticipation = currentUserParticipation?.toDomain(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
internal data class CruisesListDto(
    val cruises: List<CruiseDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): CruisesPage =
        CruisesPage(
            cruises = cruises.mapNotNull { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}

@Serializable
internal data class CruiseParticipantsListDto(
    val participants: List<CruiseParticipantDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): CruiseParticipantsPage =
        CruiseParticipantsPage(
            participants = participants.mapNotNull { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}
