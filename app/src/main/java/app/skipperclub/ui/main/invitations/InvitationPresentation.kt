package app.skipperclub.ui.main.invitations

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.skipperclub.R
import app.skipperclub.data.InvitationStatus
import app.skipperclub.ui.theme.extended
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateTime: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private val dateOnly: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** `"18 Jan 2025, 10:30"` in the device locale, or the raw value when unparseable. */
fun formatInvitationDateTime(isoTimestamp: String): String =
    runCatching {
        Instant.parse(isoTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(dateTime.withLocale(Locale.getDefault()))
    }.getOrDefault(isoTimestamp)

/** `"18 Jan 2025"` in the device locale, or the raw value when unparseable. */
fun formatInvitationDate(isoTimestamp: String): String =
    runCatching {
        Instant.parse(isoTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(dateOnly.withLocale(Locale.getDefault()))
    }.getOrDefault(isoTimestamp)

/** Localized status label for chips and detail rows. */
@Composable
@ReadOnlyComposable
fun InvitationStatus.label(): String = stringResource(
    when (this) {
        InvitationStatus.Pending -> R.string.invitation_status_pending
        InvitationStatus.Accepted -> R.string.invitation_status_accepted
        InvitationStatus.Expired -> R.string.invitation_status_expired
        InvitationStatus.Unknown -> R.string.invitation_status_unknown
    },
)

/** Accent color used for the status chip background tint. */
@Composable
@ReadOnlyComposable
fun InvitationStatus.accentColor(): Color = when (this) {
    InvitationStatus.Pending -> MaterialTheme.colorScheme.primary
    InvitationStatus.Accepted -> MaterialTheme.extended.success
    InvitationStatus.Expired -> MaterialTheme.colorScheme.onSurfaceVariant
    InvitationStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}
