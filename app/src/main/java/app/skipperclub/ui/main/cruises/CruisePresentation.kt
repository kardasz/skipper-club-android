package app.skipperclub.ui.main.cruises

import androidx.annotation.StringRes
import app.skipperclub.R
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseType
import app.skipperclub.data.VesselType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * UI-layer presentation helpers for cruises: enum → string-resource mappings,
 * date / price formatting and availability badges. Kept out of `data/` so the
 * domain model stays free of Android/localized text (CLAUDE.md §Strings).
 */

@StringRes
fun VesselType.labelRes(): Int = when (this) {
    VesselType.SailingYacht -> R.string.vessel_type_sailing_yacht
    VesselType.Catamaran -> R.string.vessel_type_catamaran
    VesselType.Motorboat -> R.string.vessel_type_motorboat
    VesselType.Trimaran -> R.string.vessel_type_trimaran
    VesselType.Gulet -> R.string.vessel_type_gulet
    VesselType.Schooner -> R.string.vessel_type_schooner
}

@StringRes
fun CruiseType.labelRes(): Int = when (this) {
    CruiseType.BeginnerIntro -> R.string.cruise_type_beginner_intro
    CruiseType.Training -> R.string.cruise_type_training
    CruiseType.Milebuilding -> R.string.cruise_type_milebuilding
    CruiseType.Advanced -> R.string.cruise_type_advanced
    CruiseType.SportRegatta -> R.string.cruise_type_sport_regatta
    CruiseType.Family -> R.string.cruise_type_family
    CruiseType.Singles -> R.string.cruise_type_singles
    CruiseType.Couples -> R.string.cruise_type_couples
    CruiseType.Seniors -> R.string.cruise_type_seniors
    CruiseType.WomenOnly -> R.string.cruise_type_women_only
    CruiseType.MenOnly -> R.string.cruise_type_men_only
    CruiseType.Party -> R.string.cruise_type_party
    CruiseType.Relax -> R.string.cruise_type_relax
    CruiseType.Survival -> R.string.cruise_type_survival
    CruiseType.Photography -> R.string.cruise_type_photography
    CruiseType.Culinary -> R.string.cruise_type_culinary
    CruiseType.CulturalHistorical -> R.string.cruise_type_cultural_historical
    CruiseType.Exploration -> R.string.cruise_type_exploration
}

/** Contextual label for a participant state (organizer-facing, used in chips/badges). */
@StringRes
fun CruiseParticipantState.labelRes(): Int = when (this) {
    CruiseParticipantState.Pending -> R.string.participant_state_pending
    CruiseParticipantState.Invited -> R.string.participant_state_invited
    CruiseParticipantState.Accepted -> R.string.participant_state_accepted
    CruiseParticipantState.RejectedByParticipant -> R.string.participant_state_rejected_by_participant
    CruiseParticipantState.RejectedByOrganizer -> R.string.participant_state_rejected_by_organizer
    CruiseParticipantState.WithdrawnByParticipant -> R.string.participant_state_withdrawn_by_participant
    CruiseParticipantState.WithdrawnByOrganizer -> R.string.participant_state_withdrawn_by_organizer
    CruiseParticipantState.CanceledByParticipant -> R.string.participant_state_canceled_by_participant
    CruiseParticipantState.CanceledByOrganizer -> R.string.participant_state_canceled_by_organizer
}

val CruiseCurrency.symbol: String
    get() = when (this) {
        CruiseCurrency.Pln -> "zł"
        CruiseCurrency.Eur -> "€"
        CruiseCurrency.Usd -> "$"
    }

/** `"1 200 €"` — whole-number price plus currency symbol, grouped for the locale. */
fun formatPrice(amount: Double, currency: CruiseCurrency): String {
    val rounded = amount.toLong()
    val grouped = String.format(Locale.getDefault(), "%,d", rounded).replace(',', ' ')
    return "$grouped ${currency.symbol}"
}

private val isoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.take(10), isoDate) }.getOrNull()

fun formatCruiseDate(value: String): String =
    parseDate(value)?.format(dayMonthYear.withLocale(Locale.getDefault())) ?: value

/** Non-composable so the locale read stays off the Compose snapshot (lint: NonObservableLocale). */
fun formatLocalDate(date: LocalDate): String =
    date.format(dayMonthYear.withLocale(Locale.getDefault()))

/** `"15 Jul – 22 Jul 2025"` — collapses the year onto the arrival side. */
fun formatDateRange(departure: String, arrival: String): String {
    val from = parseDate(departure)
    val to = parseDate(arrival)
    if (from == null || to == null) return "$departure – $arrival"
    val locale = Locale.getDefault()
    val fromText = from.format(dayMonth.withLocale(locale))
    val toText = to.format(dayMonthYear.withLocale(locale))
    return "$fromText – $toText"
}

/** Inclusive number of nights between departure and arrival, or null when unknown. */
fun cruiseNights(cruise: Cruise): Int? {
    val from = parseDate(cruise.departureDate) ?: return null
    val to = parseDate(cruise.arrivalDate) ?: return null
    val days = (to.toEpochDay() - from.toEpochDay()).toInt()
    return days.takeIf { it >= 0 }
}

enum class CruiseAvailability { Open, FillingUp, Full }

/** Mirrors the iOS availability badge: full at capacity, "filling up" above 80%. */
fun cruiseAvailability(cruise: Cruise): CruiseAvailability {
    if (cruise.maxParticipants <= 0) return CruiseAvailability.Open
    return when {
        cruise.participantsCount >= cruise.maxParticipants -> CruiseAvailability.Full
        cruise.participantsCount.toDouble() / cruise.maxParticipants > 0.8 -> CruiseAvailability.FillingUp
        else -> CruiseAvailability.Open
    }
}

/** Hashtags surfaced on a card come from the explicit field, falling back to `#tags` in the text. */
fun cruiseHashtags(cruise: Cruise): List<String> {
    if (cruise.hashtags.isNotEmpty()) return cruise.hashtags
    return Regex("#(\\w+)").findAll(cruise.description)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}
