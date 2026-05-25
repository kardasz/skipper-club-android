package app.skipperclub.ui.main.checkin

data class LocationLabel(
    val placeName: String? = null,
    val addressLine: String? = null,
) {
    val title: String?
        get() = placeName.cleanLabel() ?: addressLine.cleanLabel()

    val subtitle: String?
        get() {
            val cleanPlaceName = placeName.cleanLabel()
            val cleanAddress = addressLine.cleanLabel()
            return cleanAddress?.takeIf { cleanPlaceName != null && !it.equals(cleanPlaceName, ignoreCase = true) }
        }

    val submissionLabel: String?
        get() {
            val cleanTitle = title ?: return null
            val cleanSubtitle = subtitle
            return if (cleanSubtitle == null) cleanTitle else "$cleanTitle, $cleanSubtitle"
        }
}

private fun String?.cleanLabel(): String? = this
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
