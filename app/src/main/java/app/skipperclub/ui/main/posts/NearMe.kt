package app.skipperclub.ui.main.posts

import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostSortField
import app.skipperclub.data.SortOrder
import kotlin.math.roundToInt

/**
 * Pure logic behind the "Near me" distance filter. Kept free of Android/Compose
 * types so the conversions and [PostFilters] transitions are unit-testable on the JVM;
 * [NearMeSheet] renders it and supplies the device fix.
 *
 * The API filters in kilometres (`distance`), but sailors think in nautical miles, so
 * the slider is in NM and we convert on the way into the query. A radius search implies
 * nearest-first ordering (`sort=distance`, ascending).
 */
const val NearMeMinNm = 1
const val NearMeMaxNm = 50
const val NearMeDefaultNm = 12

private const val KM_PER_NM = 1.852

/** Nautical miles → kilometres for the `distance` query param (never below 1 km). */
fun nauticalMilesToKm(nm: Int): Int = (nm * KM_PER_NM).roundToInt().coerceAtLeast(1)

/** Kilometres → nautical miles, clamped to the slider range (for re-opening the sheet). */
fun kmToNauticalMiles(km: Int): Int =
    (km / KM_PER_NM).roundToInt().coerceIn(NearMeMinNm, NearMeMaxNm)

/**
 * Applies a "Near me" search centered on [center] with a [radiusNm] nautical-mile
 * radius, switching the feed to nearest-first. Composes with other filters (e.g. a
 * search query) since it only touches the distance-related fields.
 */
fun PostFilters.withNearMe(center: PostCoordinates, radiusNm: Int, label: String): PostFilters =
    copy(
        center = center,
        centerLabel = label,
        radiusKm = nauticalMilesToKm(radiusNm.coerceIn(NearMeMinNm, NearMeMaxNm)),
        sort = PostSortField.Distance,
        order = SortOrder.Asc,
    )

/** Removes the distance search and restores the default chronological ordering. */
fun PostFilters.clearNearMe(): PostFilters =
    copy(
        center = null,
        centerLabel = null,
        radiusKm = null,
        sort = PostSortField.PublishedAt,
        order = SortOrder.Desc,
    )

/** True when a "Near me" distance search is currently applied. */
val PostFilters.isNearMeActive: Boolean
    get() = center != null && radiusKm != null && sort == PostSortField.Distance

/** The active radius in nautical miles, or `null` when "Near me" is off. */
val PostFilters.nearMeRadiusNm: Int?
    get() = if (isNearMeActive) radiusKm?.let { kmToNauticalMiles(it) } else null
