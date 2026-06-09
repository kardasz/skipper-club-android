package app.skipperclub.data

import kotlinx.serialization.Serializable
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
    val id: String,
    val name: String,
    val coordinates: MapCoordinates,
    val count: Int? = null,
)

enum class MapEntryKind {
    Item,
    Cluster,
}

data class MapCoordinates(
    val lat: Double,
    val lng: Double,
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
    val id: String,
    val name: String,
    val coordinates: MapCoordinatesDto,
    val geometry: JsonObject? = null,
    val count: Int? = null,
) {
    fun toDomain(): MapEntry? {
        val entryKind = when (kind) {
            "item" -> MapEntryKind.Item
            "cluster" -> MapEntryKind.Cluster
            else -> return null
        }
        return MapEntry(
            kind = entryKind,
            id = id,
            name = name,
            coordinates = coordinates.toDomain(),
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
