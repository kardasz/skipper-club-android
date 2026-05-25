package app.skipperclub.ui.main.checkin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Returns `true` when at least coarse-grained location access is granted. */
fun Context.hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/**
 * Fetches a fresh GPS fix via FusedLocationProvider. Requires either
 * `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`; callers must check
 * [hasLocationPermission] first.
 *
 * Returns `null` when no fix is available (e.g. GPS disabled or timed out).
 */
@SuppressLint("MissingPermission")
suspend fun Context.fetchCurrentLocation(): Location? {
    val client = LocationServices.getFusedLocationProviderClient(this)
    val request = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMaxUpdateAgeMillis(60_000L)
        .build()
    val cancellation = CancellationTokenSource()
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancellation.cancel() }
        client.getCurrentLocation(request, cancellation.token)
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { continuation.resume(null) }
    }
}

/**
 * Reverse-geocodes [lat]/[lng] into a human-readable label. Prefers sailing-relevant
 * place names (locality / feature name) over a full address. Returns `null` if the
 * device has no geocoder backend or the lookup fails.
 */
suspend fun Context.reverseGeocode(lat: Double, lng: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(this, Locale.getDefault())
    val addresses = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeAsync(geocoder, lat, lng)
        } else {
            geocodeSync(geocoder, lat, lng)
        }
    }
    return addresses?.bestLabel()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun geocodeAsync(geocoder: Geocoder, lat: Double, lng: Double): List<Address>? =
    suspendCancellableCoroutine { continuation ->
        try {
            geocoder.getFromLocation(lat, lng, ReverseGeocodeMaxResults) { results ->
                continuation.resume(results)
            }
        } catch (_: IOException) {
            continuation.resume(null)
        }
    }

@Suppress("DEPRECATION")
private fun geocodeSync(geocoder: Geocoder, lat: Double, lng: Double): List<Address>? =
    try {
        geocoder.getFromLocation(lat, lng, ReverseGeocodeMaxResults)
    } catch (_: IOException) {
        null
    }

private fun List<Address>.bestLabel(): String? {
    firstNotNullOfOrNull { it.namedPlaceLabel() }?.let { return it }
    firstNotNullOfOrNull { it.streetAddressLabel() }?.let { return it }
    firstNotNullOfOrNull { it.areaLabel() }?.let { return it }
    return firstNotNullOfOrNull { it.getAddressLine(0)?.takeIf(String::isUsefulLabel) }
}

private fun Address.namedPlaceLabel(): String? {
    // Named place (POI / business / building). Many residential geocoder hits
    //    put the house number into featureName, which we skip — we want a place
    //    name like "Marina Gdańsk", not "3".
    premises?.takeIf { it.isUsefulLabel() }?.let { return it }
    featureName?.takeIf { name ->
        name.isUsefulLabel() &&
            !name.looksLikeStreetNumber() &&
            !name.equals(thoroughfare, ignoreCase = true) &&
            !name.equals(subThoroughfare, ignoreCase = true) &&
            !name.matchesStreetAddressLine()
    }?.let { return it }

    getAddressLine(0)
        ?.substringBefore(",")
        ?.takeIf { candidate ->
            candidate.isUsefulLabel() &&
                !candidate.equals(thoroughfare, ignoreCase = true) &&
                !candidate.matchesStreetAddressLine()
        }
        ?.let { return it }

    return null
}

private fun Address.streetAddressLabel(): String? {
    val street = thoroughfare?.takeIf { it.isUsefulLabel() }
    if (street != null) {
        val number = subThoroughfare?.takeIf { it.isUsefulLabel() }
        return if (number != null) "$street $number" else street
    }
    return null
}

private fun Address.areaLabel(): String? {
    subLocality?.takeIf { it.isUsefulLabel() }?.let { return it }
    locality?.takeIf { it.isUsefulLabel() }?.let { return it }
    adminArea?.takeIf { it.isUsefulLabel() }?.let { return it }
    return null
}

/** Matches values like "3", "12A", "3/5", "12 B" that should not be used as a place name. */
private fun String.looksLikeStreetNumber(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return true
    return trimmed.all { it.isDigit() || it.isWhitespace() || it == '/' || it == '-' || it.isLetter() } &&
        trimmed.any { it.isDigit() } &&
        trimmed.count { it.isLetter() } <= 2
}

private fun String.isUsefulLabel(): Boolean = trim().isNotEmpty()

private fun String.matchesStreetAddressLine(): Boolean {
    val trimmed = trim()
    return trimmed.any { it.isDigit() } &&
        Regex("""\b\d+[A-Za-z]?\b""").containsMatchIn(trimmed)
}

private const val ReverseGeocodeMaxResults = 5
