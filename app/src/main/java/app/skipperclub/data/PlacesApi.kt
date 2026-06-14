package app.skipperclub.data

import android.content.Context
import app.skipperclub.BuildConfig
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.net.SearchNearbyResponse
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Places SDK (New) client for resolving a map target to a named place.
 */
object PlacesApi {
    @Volatile
    private var initialized = false

    suspend fun findBestNearbyPlace(context: Context, lat: Double, lng: Double): NearbyPlace? {
        val client = placesClient(context) ?: return null
        for (query in NearbyPlaceQueries) {
            val places = client.searchNearby(
                lat = lat,
                lng = lng,
                radiusMeters = query.radiusMeters,
                includedTypes = query.includedTypes,
            )
            places.bestCandidate(lat, lng, query.maxDistanceMeters)?.let { return it }
        }
        return null
    }

    internal fun placesClient(context: Context): PlacesClient? {
        if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) return null
        val appContext = context.applicationContext
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    Places.initializeWithNewPlacesApiEnabled(appContext, BuildConfig.GOOGLE_MAPS_API_KEY)
                    initialized = true
                }
            }
        }
        return Places.createClient(appContext)
    }

    private suspend fun PlacesClient.searchNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        includedTypes: List<String>,
    ): List<Place> {
        val request = SearchNearbyRequest
            .builder(
                CircularBounds.newInstance(LatLng(lat, lng), radiusMeters),
                PlaceFields,
            )
            .setIncludedTypes(includedTypes)
            .setMaxResultCount(8)
            .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
            .build()

        return suspendCancellableCoroutine { continuation ->
            searchNearby(request)
                .addOnSuccessListener { response: SearchNearbyResponse ->
                    continuation.resume(response.places)
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }
    }
}

data class NearbyPlace(
    val name: String,
    val addressLine: String?,
)

/** A single Places autocomplete suggestion shown in the spot form. */
data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String?,
)

/** A place the admin picked, with the coordinates the spot will be created at. */
data class ResolvedPlace(
    val placeId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String?,
)

/**
 * Place lookup used by the spot form. Abstracted behind an interface so the
 * Composable form stays previewable / testable without the Places SDK or a live
 * API key.
 */
interface PlaceSearch {
    suspend fun autocomplete(query: String): List<PlacePrediction>
    suspend fun resolve(placeId: String): ResolvedPlace?
}

/**
 * [PlaceSearch] backed by the Places SDK (New). A single
 * [AutocompleteSessionToken] groups the keystrokes and the final detail fetch
 * into one billable session; it is rotated after each successful [resolve].
 */
class RealPlaceSearch(context: Context) : PlaceSearch {
    private val appContext = context.applicationContext
    private val client: PlacesClient? by lazy { PlacesApi.placesClient(appContext) }

    @Volatile
    private var sessionToken: AutocompleteSessionToken = AutocompleteSessionToken.newInstance()

    override suspend fun autocomplete(query: String): List<PlacePrediction> {
        val client = client ?: return emptyList()
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    continuation.resume(
                        response.autocompletePredictions.map { prediction ->
                            PlacePrediction(
                                placeId = prediction.placeId,
                                primaryText = prediction.getPrimaryText(null).toString(),
                                secondaryText = prediction.getSecondaryText(null).toString().ifBlank { null },
                            )
                        },
                    )
                }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
    }

    override suspend fun resolve(placeId: String): ResolvedPlace? {
        val client = client ?: return null
        val request = FetchPlaceRequest.builder(placeId, ResolvePlaceFields)
            .setSessionToken(sessionToken)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.fetchPlace(request)
                .addOnSuccessListener { response ->
                    // End of the billing session — a new token starts the next search.
                    sessionToken = AutocompleteSessionToken.newInstance()
                    val place = response.place
                    val location = place.location
                    if (location == null) {
                        continuation.resume(null)
                    } else {
                        continuation.resume(
                            ResolvedPlace(
                                placeId = placeId,
                                name = place.displayName?.trim().orEmpty(),
                                lat = location.latitude,
                                lng = location.longitude,
                                address = place.formattedAddress?.trim()?.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}

private val ResolvePlaceFields = listOf(
    Place.Field.ID,
    Place.Field.DISPLAY_NAME,
    Place.Field.FORMATTED_ADDRESS,
    Place.Field.LOCATION,
)

private data class NearbyPlaceQuery(
    val includedTypes: List<String>,
    val radiusMeters: Double,
    val maxDistanceMeters: Double,
)

private val PlaceFields = listOf(
    Place.Field.DISPLAY_NAME,
    Place.Field.FORMATTED_ADDRESS,
    Place.Field.LOCATION,
)

private val NearbyPlaceQueries = listOf(
    NearbyPlaceQuery(
        includedTypes = listOf("marina"),
        radiusMeters = 350.0,
        maxDistanceMeters = 350.0,
    ),
    NearbyPlaceQuery(
        includedTypes = listOf(
            "restaurant",
            "cafe",
            "bar",
            "coffee_shop",
            "bakery",
            "fast_food_restaurant",
        ),
        radiusMeters = 120.0,
        maxDistanceMeters = 120.0,
    ),
    NearbyPlaceQuery(
        includedTypes = listOf(
            "tourist_attraction",
            "hotel",
            "park",
            "water_park",
            "shopping_mall",
            "store",
        ),
        radiusMeters = 100.0,
        maxDistanceMeters = 100.0,
    ),
)

private fun List<Place>.bestCandidate(lat: Double, lng: Double, maxDistanceMeters: Double): NearbyPlace? =
    asSequence()
        .mapNotNull { place ->
            val name = place.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.matchesStreetAddressLine() }
                ?: return@mapNotNull null
            val distanceMeters = place.location?.distanceTo(lat, lng) ?: 0.0
            if (distanceMeters <= maxDistanceMeters) {
                PlaceMatch(
                    place = NearbyPlace(
                        name = name,
                        addressLine = place.formattedAddress?.trim()?.takeIf { it.isNotBlank() },
                    ),
                    distanceMeters = distanceMeters,
                )
            } else {
                null
            }
        }
        .minByOrNull { it.distanceMeters }
        ?.place

private data class PlaceMatch(
    val place: NearbyPlace,
    val distanceMeters: Double,
)

private fun LatLng.distanceTo(lat: Double, lng: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(latitude - lat)
    val dLng = Math.toRadians(longitude - lng)
    val originLat = Math.toRadians(lat)
    val targetLat = Math.toRadians(latitude)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(originLat) * kotlin.math.cos(targetLat) *
        kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun String.matchesStreetAddressLine(): Boolean {
    val trimmed = trim()
    return trimmed.any { it.isDigit() } &&
        Regex("""\b\d+[A-Za-z]?\b""").containsMatchIn(trimmed)
}
