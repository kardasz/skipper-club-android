package app.skipperclub.ui.main.alert

import androidx.annotation.StringRes
import app.skipperclub.R
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertSeverity

/**
 * Localized label for an [AlertCategory]. Labels match the marker labels the
 * backend localizes for `/v1/map/items`
 * (see docs/api/reference/enums/alert-categories.md).
 *
 * Public and stable: the posts card + create wizard also resolve category labels
 * through this function, so keep the signature intact.
 */
@StringRes
fun AlertCategory.labelRes(): Int = when (this) {
    AlertCategory.NavigationWarning -> R.string.alert_category_navigation_warning
    AlertCategory.Navtex -> R.string.alert_category_navtex
    AlertCategory.NoticeToMariners -> R.string.alert_category_notice_to_mariners
    AlertCategory.Obstruction -> R.string.alert_category_obstruction
    AlertCategory.Works -> R.string.alert_category_works
    AlertCategory.Regatta -> R.string.alert_category_regatta
    AlertCategory.Diving -> R.string.alert_category_diving
    AlertCategory.MilitaryExercise -> R.string.alert_category_military_exercise
    AlertCategory.Weather -> R.string.alert_category_weather
    AlertCategory.Other -> R.string.alert_category_other
}

/**
 * Localized label for an [AlertSeverity] (`content.alert.severity`).
 *
 * Public and stable: the posts card + create wizard also resolve severity labels
 * through this function, so keep the signature intact.
 */
@StringRes
fun AlertSeverity.labelRes(): Int = when (this) {
    AlertSeverity.Info -> R.string.alert_severity_info
    AlertSeverity.Warning -> R.string.alert_severity_warning
    AlertSeverity.Critical -> R.string.alert_severity_critical
}
