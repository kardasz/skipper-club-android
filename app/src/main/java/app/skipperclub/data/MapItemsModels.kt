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
    data class CheckIn(
        val user: MapUserProjection,
        val checkedInAt: String,
        val locationName: String?,
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
            MapEntryType.CheckIn -> mapAttributesJson
                .decodeFromJsonElement<MapCheckInAttributesDto>(attributes)
                .toDomain()

            MapEntryType.Post,
            MapEntryType.Spot,
            MapEntryType.NavigationAlert,
            -> null
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
