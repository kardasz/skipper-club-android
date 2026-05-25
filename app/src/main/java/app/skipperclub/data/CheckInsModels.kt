package app.skipperclub.data

import kotlinx.serialization.Serializable

@Serializable
internal data class CheckInRequest(
    val lat: Double,
    val lng: Double,
    val locationName: String? = null,
)

@Serializable
data class CheckInCoordinates(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class CheckInUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

/**
 * Persisted check-in as returned by `PUT /v1/check-ins`. Most fields are nullable
 * because the OpenAPI schema is still evolving; only [coordinates] and
 * [locationName] are stable at the time of writing.
 */
@Serializable
data class CheckIn(
    val id: String? = null,
    val user: CheckInUser? = null,
    val coordinates: CheckInCoordinates? = null,
    val locationName: String? = null,
    val checkedInAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val distanceMeters: Double? = null,
)
