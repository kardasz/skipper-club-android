package app.skipperclub.ui.main.profile

import androidx.annotation.StringRes
import app.skipperclub.R
import app.skipperclub.data.SailingExperience
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** String resource for a sailing-experience level, or null for [SailingExperience.Unknown]. */
@StringRes
internal fun SailingExperience.labelRes(): Int? = when (this) {
    SailingExperience.Beginner -> R.string.profile_experience_beginner
    SailingExperience.Intermediate -> R.string.profile_experience_intermediate
    SailingExperience.Advanced -> R.string.profile_experience_advanced
    SailingExperience.Professional -> R.string.profile_experience_professional
    SailingExperience.Unknown -> null
}

/**
 * Human-readable location from optional city + ISO country code, e.g. "Gdańsk, Poland".
 * Returns null when neither is present.
 */
internal fun formatLocation(city: String?, country: String?, locale: Locale = Locale.getDefault()): String? {
    val countryName = country?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val code = raw.uppercase(Locale.ROOT)
        // Translate only recognized ISO 3166-1 alpha-2 codes; pass anything else through verbatim.
        if (code in isoCountryCodes) Locale("", code).getDisplayCountry(locale) else raw
    }
    return listOfNotNull(city?.trim()?.takeIf { it.isNotEmpty() }, countryName)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
}

private val isoCountryCodes: Set<String> by lazy { Locale.getISOCountries().toHashSet() }

/** Formats an ISO-8601 timestamp as a localized date, or null when it cannot be parsed. */
internal fun formatMemberSince(isoTimestamp: String?, locale: Locale = Locale.getDefault()): String? {
    if (isoTimestamp.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(isoTimestamp)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }.getOrNull()
}

/** Title-cases a free-form voyage-style tag (e.g. "coastal" → "Coastal"). */
internal fun formatVoyageStyle(style: String, locale: Locale = Locale.getDefault()): String =
    style.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** Uppercases an ISO 639-1 language code for display (e.g. "pl" → "PL"); leaves names untouched. */
internal fun formatLanguage(code: String, locale: Locale = Locale.getDefault()): String {
    val trimmed = code.trim()
    return if (trimmed.length == 2) trimmed.uppercase(locale) else trimmed
}
