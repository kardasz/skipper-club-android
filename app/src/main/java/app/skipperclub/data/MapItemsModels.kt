package app.skipperclub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject

data class MapViewportBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

data class MapItemsResponse(
    val entries: List<MapEntry>,
    val meta: MapItemsMeta,
)

data class MapEntry(
    val kind: MapEntryKind,
    val type: MapEntryType? = null,
    val id: String,
    val name: String,
    val coordinates: MapCoordinates,
    val attributes: MapEntryAttributes? = null,
    val count: Int? = null,
)

enum class MapEntryKind {
    Item,
    Cluster,
}

enum class MapEntryType {
    Post,
    Spot,
    CheckIn,
    NavigationAlert,
}

data class MapCoordinates(
    val lat: Double,
    val lng: Double,
)

sealed interface MapEntryAttributes {
    /**
     * Lightweight metadata for a `spot` map item (see openapi `MapSpotAttributes`).
     * The map endpoint only reports counts/availability — the full phone contacts
     * and radio channels are fetched lazily via `GET /v1/spots/{id}` when the user
     * opens the spot detail sheet.
     */
    data class Spot(
        val hasPhoneContacts: Boolean,
        val hasRadioChannels: Boolean,
        val phoneContactsCount: Int,
        val radioChannelsCount: Int,
    ) : MapEntryAttributes

    data class CheckIn(
        val user: MapUserProjection,
        val checkedInAt: String,
        val locationName: String?,
    ) : MapEntryAttributes

    /**
     * Full payload for a `navigation_alert` map item. The map endpoint already
     * inlines the alert body (see openapi `MapAlertAttributes`), so the detail
     * view can render without a separate `GET /v1/alerts/{id}` fetch.
     *
     * [sourceName] / [sourceNumber] / [sourceUrl] are flattened from the
     * optional source-specific attribution (`sourceAttributes`); they are only
     * present for official imports and `null` for user-created alerts.
     */
    data class NavigationAlert(
        val category: AlertCategory,
        val content: String,
        val source: String,
        val sourceName: String? = null,
        val sourceNumber: String? = null,
        val sourceUrl: String? = null,
    ) : MapEntryAttributes
}

data class MapUserProjection(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class MapItemsMeta(
    val hasMoreDetail: Boolean,
)

@Serializable
internal data class MapItemsResponseDto(
    val data: List<MapEntryDto>,
    val meta: MapItemsMetaDto,
) {
    fun toDomain(): MapItemsResponse =
        MapItemsResponse(
            entries = data.mapNotNull { it.toDomain() },
            meta = meta.toDomain(),
        )
}

@Serializable
internal data class MapEntryDto(
    val kind: String,
    val type: String? = null,
    val id: String,
    val name: String,
    val coordinates: MapCoordinatesDto,
    val geometry: JsonObject? = null,
    val attributes: JsonObject? = null,
    val count: Int? = null,
) {
    fun toDomain(): MapEntry? {
        val entryKind = when (kind) {
            "item" -> MapEntryKind.Item
            "cluster" -> MapEntryKind.Cluster
            else -> return null
        }
        val entryType = type?.toMapEntryType()
        return MapEntry(
            kind = entryKind,
            type = entryType,
            id = id,
            name = name,
            coordinates = coordinates.toDomain(),
            attributes = entryType?.toDomainAttributes(attributes),
            count = count,
        )
    }
}

@Serializable
internal data class MapCoordinatesDto(
    val lat: Double,
    val lng: Double,
) {
    fun toDomain(): MapCoordinates = MapCoordinates(lat = lat, lng = lng)
}

@Serializable
internal data class MapItemsMetaDto(
    val hasMoreDetail: Boolean = false,
) {
    fun toDomain(): MapItemsMeta = MapItemsMeta(hasMoreDetail = hasMoreDetail)
}

@Serializable
private data class MapSpotAttributesDto(
    val hasPhoneContacts: Boolean = false,
    val hasRadioChannels: Boolean = false,
    val phoneContactsCount: Int = 0,
    val radioChannelsCount: Int = 0,
) {
    fun toDomain(): MapEntryAttributes.Spot =
        MapEntryAttributes.Spot(
            hasPhoneContacts = hasPhoneContacts,
            hasRadioChannels = hasRadioChannels,
            phoneContactsCount = phoneContactsCount,
            radioChannelsCount = radioChannelsCount,
        )
}

@Serializable
private data class MapCheckInAttributesDto(
    val user: MapUserProjectionDto,
    val checkedInAt: String,
    val locationName: String? = null,
) {
    fun toDomain(): MapEntryAttributes.CheckIn =
        MapEntryAttributes.CheckIn(
            user = user.toDomain(),
            checkedInAt = checkedInAt,
            locationName = locationName,
        )
}

@Serializable
private data class MapAlertAttributesDto(
    val category: AlertCategory,
    val content: String,
    val language: String? = null,
    val source: String = "user",
    val sourceId: String? = null,
    val sourceAttributes: MapAlertSourceAttributesDto? = null,
) {
    fun toDomain(): MapEntryAttributes.NavigationAlert =
        MapEntryAttributes.NavigationAlert(
            category = category,
            content = content,
            source = source,
            sourceName = sourceAttributes?.externalSourceName,
            sourceNumber = sourceAttributes?.externalNumber,
            sourceUrl = sourceAttributes?.externalSourceUrl,
        )
}

@Serializable
private data class MapAlertSourceAttributesDto(
    val type: String? = null,
    val externalSourceName: String? = null,
    val externalSourceUrl: String? = null,
    val externalNumber: String? = null,
)

@Serializable
private data class MapUserProjectionDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
) {
    fun toDomain(): MapUserProjection =
        MapUserProjection(
            id = id,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )
}

private val mapAttributesJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun String.toMapEntryType(): MapEntryType? =
    when (this) {
        "post" -> MapEntryType.Post
        "spot" -> MapEntryType.Spot
        "check_in" -> MapEntryType.CheckIn
        "navigation_alert" -> MapEntryType.NavigationAlert
        else -> null
    }

private fun MapEntryType.toDomainAttributes(attributes: JsonObject?): MapEntryAttributes? {
    if (attributes == null) return null
    return try {
        when (this) {
            MapEntryType.Spot -> mapAttributesJson
                .decodeFromJsonElement<MapSpotAttributesDto>(attributes)
                .toDomain()

            MapEntryType.CheckIn -> mapAttributesJson
                .decodeFromJsonElement<MapCheckInAttributesDto>(attributes)
                .toDomain()

            MapEntryType.NavigationAlert -> mapAttributesJson
                .decodeFromJsonElement<MapAlertAttributesDto>(attributes)
                .toDomain()

            MapEntryType.Post -> null
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
