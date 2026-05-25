package app.skipperclub.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.skipperclub.BuildConfig
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Minimal Places API (New) client for resolving a map target to a named place.
 * Uses the REST Nearby Search endpoint so the app does not need the Places SDK yet.
 */
object PlacesApi {
    private const val NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby"
    private const val FIELD_MASK = "places.displayName,places.formattedAddress,places.location,places.primaryType,places.types"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    suspend fun findBestNearbyPlace(context: Context, lat: Double, lng: Double): String? {
        if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) return null
        val androidApiHeaders = context.googleApiAndroidHeaders() ?: return null

        for (query in NearbyPlaceQueries) {
            val places = searchNearby(
                lat = lat,
                lng = lng,
                radiusMeters = query.radiusMeters,
                includedTypes = query.includedTypes,
                androidApiHeaders = androidApiHeaders,
            )
            places.bestCandidate(lat, lng, query.maxDistanceMeters)?.let { return it }
        }
        return null
    }

    private suspend fun searchNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        includedTypes: List<String>,
        androidApiHeaders: GoogleApiAndroidHeaders,
    ): List<PlaceCandidate> {
        val requestPayload = PlacesNearbySearchRequest(
            includedTypes = includedTypes,
            maxResultCount = 8,
            rankPreference = "DISTANCE",
            languageCode = Locale.getDefault().language,
            locationRestriction = PlacesLocationRestriction(
                circle = PlacesCircle(
                    center = PlacesLatLng(latitude = lat, longitude = lng),
                    radius = radiusMeters,
                ),
            ),
        )
        val body = json.encodeToString(requestPayload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(NEARBY_SEARCH_URL)
            .post(body)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", BuildConfig.GOOGLE_MAPS_API_KEY)
            .header("X-Goog-FieldMask", FIELD_MASK)
            .header("X-Android-Package", androidApiHeaders.packageName)
            .header("X-Android-Cert", androidApiHeaders.certificateSha1)
            .build()

        return try {
            execute(request).use { response ->
                if (!response.isSuccessful) return emptyList()
                val payload = response.body.string()
                json.decodeFromString<PlacesNearbySearchResponse>(payload).places.orEmpty()
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }
}

private data class GoogleApiAndroidHeaders(
    val packageName: String,
    val certificateSha1: String,
)

@Suppress("DEPRECATION")
private fun Context.googleApiAndroidHeaders(): GoogleApiAndroidHeaders? {
    val appContext = applicationContext
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val packageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        packageInfo.signingInfo?.apkContentsSigners
    } else {
        val packageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_SIGNATURES,
        )
        packageInfo.signatures
    }
    val signature = signatures?.firstOrNull() ?: return null
    val certificateSha1 = MessageDigest
        .getInstance("SHA-1")
        .digest(signature.toByteArray())
        .joinToString(separator = "") { byte -> "%02X".format(byte) }
    return GoogleApiAndroidHeaders(
        packageName = appContext.packageName,
        certificateSha1 = certificateSha1,
    )
}

private data class NearbyPlaceQuery(
    val includedTypes: List<String>,
    val radiusMeters: Double,
    val maxDistanceMeters: Double,
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

private fun List<PlaceCandidate>.bestCandidate(lat: Double, lng: Double, maxDistanceMeters: Double): String? =
    asSequence()
        .mapNotNull { place ->
            val name = place.displayName?.text
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.matchesStreetAddressLine() }
                ?: return@mapNotNull null
            val distanceMeters = place.location?.distanceTo(lat, lng) ?: 0.0
            if (distanceMeters <= maxDistanceMeters) PlaceMatch(name, distanceMeters) else null
        }
        .minByOrNull { it.distanceMeters }
        ?.name

private data class PlaceMatch(
    val name: String,
    val distanceMeters: Double,
)

private fun PlacesLatLng.distanceTo(lat: Double, lng: Double): Double {
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
