package app.skipperclub.ui.main.posts

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Pure countdown logic for time-sensitive posts (`expiresAt`). Kept free of
 * Android/Compose types so it is unit-testable on the JVM; the card maps the
 * phase to localized strings and colors.
 */
object PostExpiry {

    sealed interface Phase {
        data object Expired : Phase
        data class Minutes(val minutes: Int) : Phase
        data class Hours(val hours: Int, val minutes: Int) : Phase
        data class Days(val days: Int, val hours: Int) : Phase
    }

    enum class Urgency { Critical, Warning, Normal }

    fun remainingMillis(expiresAtIso: String?, nowMillis: Long): Long? {
        if (expiresAtIso.isNullOrBlank()) return null
        return try {
            Instant.parse(expiresAtIso).toEpochMilli() - nowMillis
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun phase(remainingMillis: Long): Phase {
        if (remainingMillis <= 0) return Phase.Expired
        val totalMinutes = remainingMillis / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> Phase.Days(days.toInt(), hours.toInt())
            hours > 0 -> Phase.Hours(hours.toInt(), minutes.toInt())
            else -> Phase.Minutes(minutes.toInt().coerceAtLeast(1))
        }
    }

    fun urgency(remainingMillis: Long): Urgency = when {
        remainingMillis <= 60 * 60_000L -> Urgency.Critical
        remainingMillis <= 6 * 60 * 60_000L -> Urgency.Warning
        else -> Urgency.Normal
    }
}
