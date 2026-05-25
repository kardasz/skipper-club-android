package app.skipperclub.data

import kotlinx.serialization.Serializable

@Serializable
data class PlacesNearbySearchRequest(
    val includedTypes: List<String>,
    val maxResultCount: Int,
    val rankPreference: String,
    val languageCode: String,
    val locationRestriction: PlacesLocationRestriction,
)

@Serializable
data class PlacesLocationRestriction(
    val circle: PlacesCircle,
)

@Serializable
data class PlacesCircle(
    val center: PlacesLatLng,
    val radius: Double,
)

@Serializable
data class PlacesLatLng(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class PlacesNearbySearchResponse(
    val places: List<PlaceCandidate>? = null,
)

@Serializable
data class PlaceCandidate(
    val displayName: PlaceDisplayName? = null,
    val formattedAddress: String? = null,
    val location: PlacesLatLng? = null,
    val primaryType: String? = null,
    val types: List<String>? = null,
)

@Serializable
data class PlaceDisplayName(
    val text: String,
    val languageCode: String? = null,
)
