package app.skipperclub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull

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
    /**
     * Drawable geometry for the item. Point resources duplicate [coordinates];
     * alert posts (`contentKeys` contains `alert`) may carry `Polygon` /
     * `MultiPolygon` areas that the map renders as overlays. Null for clusters.
     */
    val geometry: MapGeometry? = null,
    /** Bounding box of a cluster's member items; used to zoom in on tap. */
    val bounds: MapViewportBounds? = null,
)

/** GeoJSON-compatible geometry decoded into WGS84 [MapCoordinates] (lat/lng). */
sealed interface MapGeometry {
    data class Point(val point: MapCoordinates) : MapGeometry
    data class MultiPoint(val points: List<MapCoordinates>) : MapGeometry
    data class Polygon(val rings: List<List<MapCoordinates>>) : MapGeometry
    data class MultiPolygon(val polygons: List<List<List<MapCoordinates>>>) : MapGeometry
}

enum class MapEntryKind {
    Item,
    Cluster,
}

enum class MapEntryType {
    Post,
    Spot,
    CheckIn,
}

data class MapCoordinates(
    val lat: Double,
    val lng: Double,
)

sealed interface MapEntryAttributes {
    /**
     * Lightweight metadata for a `post` map item (see openapi `MapPostAttributes`).
     * Drives the type-specific marker icon and the compact preview; the full post
     * is loaded via `GET /v1/posts/{id}` when the user opens the detail screen.
     */
    data class Post(
        val contentKeys: Set<PostContentKey>,
        val alertCategory: AlertCategory?,
        val status: String,
        /** Post author. `null` on system-generated posts (imported alerts). */
        val author: MapUserProjection?,
        val publishedAt: String,
        val expiresAt: String?,
        val commentsCount: Int,
        val bookmarked: Boolean,
        val mediaPreview: MapMediaPreview?,
    ) : MapEntryAttributes {
        val hasAlert: Boolean get() = contentKeys.contains(PostContentKey.Alert)
        val hasMedia: Boolean get() = contentKeys.contains(PostContentKey.Media)
        val hasRoute: Boolean get() = contentKeys.contains(PostContentKey.Route)
    }

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
}

data class MapUserProjection(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
)

/** Single media preview attached to a post map item. */
data class MapMediaPreview(
    val url: String,
    val thumbnailUrl: String?,
    val isVideo: Boolean,
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
    val bounds: MapBoundsDto? = null,
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
            geometry = geometry?.toMapGeometry(),
            bounds = bounds?.toDomain(),
        )
    }
}

@Serializable
internal data class MapBoundsDto(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    fun toDomain(): MapViewportBounds =
        MapViewportBounds(north = north, south = south, east = east, west = west)
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
private data class MapPostAttributesDto(
    val contentKeys: List<String> = emptyList(),
    val alertCategory: AlertCategory? = null,
    val status: String = "published",
    val author: MapUserProjectionDto? = null,
    val publishedAt: String,
    val expiresAt: String? = null,
    val mediaPreview: MapMediaPreviewDto? = null,
    val commentsCount: Int = 0,
    val bookmarked: Boolean = false,
) {
    fun toDomain(): MapEntryAttributes.Post =
        MapEntryAttributes.Post(
            contentKeys = contentKeys.mapNotNull { PostContentKey.fromWire(it) }.toSet(),
            alertCategory = alertCategory,
            status = status,
            author = author?.toDomain(),
            publishedAt = publishedAt,
            expiresAt = expiresAt,
            commentsCount = commentsCount,
            bookmarked = bookmarked,
            mediaPreview = mediaPreview?.toDomain(),
        )
}

@Serializable
private data class MapMediaPreviewDto(
    val id: String? = null,
    val kind: String = "image",
    val url: String,
    val thumbnailUrl: String? = null,
) {
    fun toDomain(): MapMediaPreview =
        MapMediaPreview(
            url = url,
            thumbnailUrl = thumbnailUrl,
            isVideo = kind.equals("video", ignoreCase = true),
        )
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

/** Decodes a GeoJSON geometry object (`[lng, lat]` order) into a [MapGeometry]. */
private fun JsonObject.toMapGeometry(): MapGeometry? {
    val type = (this["type"] as? JsonPrimitive)?.content ?: return null
    val coordinates = this["coordinates"] as? JsonArray ?: return null
    return when (type) {
        "Point" -> coordinates.toLngLat()?.let { MapGeometry.Point(it) }
        "MultiPoint" -> MapGeometry.MultiPoint(coordinates.toPointList())
        "Polygon" -> MapGeometry.Polygon(coordinates.toRingList())
        "MultiPolygon" -> MapGeometry.MultiPolygon(
            coordinates.mapNotNull { (it as? JsonArray)?.toRingList() },
        )
        else -> null
    }
}

private fun JsonArray.toRingList(): List<List<MapCoordinates>> =
    mapNotNull { (it as? JsonArray)?.toPointList() }

private fun JsonArray.toPointList(): List<MapCoordinates> =
    mapNotNull { (it as? JsonArray)?.toLngLat() }

/** GeoJSON positions are `[lng, lat]`; convert to lat/lng [MapCoordinates]. */
private fun JsonArray.toLngLat(): MapCoordinates? {
    if (size < 2) return null
    val lng = (this[0] as? JsonPrimitive)?.doubleOrNull ?: return null
    val lat = (this[1] as? JsonPrimitive)?.doubleOrNull ?: return null
    return MapCoordinates(lat = lat, lng = lng)
}

private fun String.toMapEntryType(): MapEntryType? =
    when (this) {
        "post" -> MapEntryType.Post
        "spot" -> MapEntryType.Spot
        "check_in" -> MapEntryType.CheckIn
        else -> null
    }

private fun MapEntryType.toDomainAttributes(attributes: JsonObject?): MapEntryAttributes? {
    if (attributes == null) return null
    return try {
        when (this) {
            MapEntryType.Post -> mapAttributesJson
                .decodeFromJsonElement<MapPostAttributesDto>(attributes)
                .toDomain()

            MapEntryType.Spot -> mapAttributesJson
                .decodeFromJsonElement<MapSpotAttributesDto>(attributes)
                .toDomain()

            MapEntryType.CheckIn -> mapAttributesJson
                .decodeFromJsonElement<MapCheckInAttributesDto>(attributes)
                .toDomain()
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
