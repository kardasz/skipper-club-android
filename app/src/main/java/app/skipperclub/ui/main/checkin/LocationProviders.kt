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
    return addresses?.firstOrNull()?.bestLabel()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun geocodeAsync(geocoder: Geocoder, lat: Double, lng: Double): List<Address>? =
    suspendCancellableCoroutine { continuation ->
        try {
            geocoder.getFromLocation(lat, lng, 1) { results ->
                continuation.resume(results)
            }
        } catch (_: IOException) {
            continuation.resume(null)
        }
    }

@Suppress("DEPRECATION")
private fun geocodeSync(geocoder: Geocoder, lat: Double, lng: Double): List<Address>? =
    try {
        geocoder.getFromLocation(lat, lng, 1)
    } catch (_: IOException) {
        null
    }

private fun Address.bestLabel(): String? {
    // 1) Named place (POI / business / building). Many residential geocoder hits
    //    put the house number into featureName, which we skip — we want a place
    //    name like "Marina Gdańsk", not "3".
    premises?.takeIf { it.isNotBlank() }?.let { return it }
    featureName?.takeIf { name ->
        name.isNotBlank() &&
            !name.looksLikeStreetNumber() &&
            !name.equals(thoroughfare, ignoreCase = true) &&
            !name.equals(subThoroughfare, ignoreCase = true)
    }?.let { return it }

    // 2) Street (+ house number).
    val street = thoroughfare?.takeIf { it.isNotBlank() }
    if (street != null) {
        val number = subThoroughfare?.takeIf { it.isNotBlank() }
        return if (number != null) "$street $number" else street
    }

    // 3) Neighbourhood / city / region fallbacks, then the formatted address line.
    subLocality?.takeIf { it.isNotBlank() }?.let { return it }
    locality?.takeIf { it.isNotBlank() }?.let { return it }
    adminArea?.takeIf { it.isNotBlank() }?.let { return it }
    return getAddressLine(0)?.takeIf { it.isNotBlank() }
}

/** Matches values like "3", "12A", "3/5", "12 B" that should not be used as a place name. */
private fun String.looksLikeStreetNumber(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty()) return true
    return trimmed.all { it.isDigit() || it.isWhitespace() || it == '/' || it == '-' || it.isLetter() } &&
        trimmed.any { it.isDigit() } &&
        trimmed.count { it.isLetter() } <= 2
}
