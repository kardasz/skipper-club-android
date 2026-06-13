package app.skipperclub.ui.main.cruises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruisePort
import app.skipperclub.data.CruiseUser
import app.skipperclub.data.CruiseUserRole
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.VesselType
import app.skipperclub.ui.theme.SkipperClubTheme

/** Summary card for the cruise list. Tapping anywhere opens the detail screen. */
@Composable
fun CruiseCard(
    cruise: Cruise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CruiseCardHeader(cruise)

            Text(
                text = cruise.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )

            cruise.type?.let { type ->
                CruiseTagChip(
                    text = stringResource(type.labelRes()),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            CruiseInfoRow(
                icon = Icons.Outlined.CalendarMonth,
                text = formatDateRange(cruise.departureDate, cruise.arrivalDate),
                modifier = Modifier.padding(top = 10.dp),
            )
            CruiseInfoRow(
                icon = Icons.Outlined.Place,
                text = "${cruise.departurePort.name} → ${cruise.arrivalPort.name}",
                modifier = Modifier.padding(top = 6.dp),
            )
            if (cruise.vessel.isNotBlank()) {
                CruiseInfoRow(
                    icon = Icons.Outlined.Sailing,
                    text = "${stringResource(cruise.vesselType.labelRes())} • ${cruise.vessel}",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPrice(cruise.costPerPerson, cruise.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " /${stringResource(R.string.cruise_per_person)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = " ${cruise.participantsCount}/${cruise.maxParticipants}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                AvailabilityBadge(cruise)
            }

            val hashtags = cruiseHashtags(cruise)
            if (hashtags.isNotEmpty()) {
                CruiseHashtagRow(
                    hashtags = hashtags,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CruiseCardHeader(cruise: Cruise) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CruiseAvatar(
            name = cruise.organizer.name,
            avatarUrl = cruise.organizer.avatarUrl,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = cruise.organizer.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralNights(cruise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cruise.isPrivate) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.cruise_private),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp),
            )
        }
        RoleBadge(cruise)
    }
}

@Composable
private fun pluralNights(cruise: Cruise): String {
    val nights = cruiseNights(cruise) ?: return ""
    return pluralStringResource(R.plurals.cruise_nights, nights, nights)
}

@Composable
private fun RoleBadge(cruise: Cruise) {
    val role = cruise.currentUserRole
    when {
        role == CruiseUserRole.Organizer -> CruiseStatusBadge(
            text = stringResource(R.string.participation_organizer),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        cruise.currentUserParticipation != null -> {
            val state = cruise.currentUserParticipation.state
            CruiseStatusBadge(
                text = stringResource(state.labelRes()),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun AvailabilityBadge(cruise: Cruise) {
    when (cruiseAvailability(cruise)) {
        CruiseAvailability.Full -> CruiseStatusBadge(
            text = stringResource(R.string.cruise_availability_full),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )

        CruiseAvailability.FillingUp -> CruiseStatusBadge(
            text = stringResource(R.string.cruise_availability_filling_up),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        CruiseAvailability.Open -> Unit
    }
}

@Composable
internal fun CruiseStatusBadge(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun CruiseTagChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
internal fun CruiseInfoRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CruiseHashtagRow(
    hashtags: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hashtags.forEach { tag ->
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

// --- Previews ---

internal fun previewCruise(
    id: String = "c1",
    title: String = "Mediterranean Summer Sailing",
    role: CruiseUserRole = CruiseUserRole.None,
    participantsCount: Int = 3,
    maxParticipants: Int = 6,
    isPrivate: Boolean = false,
): Cruise = Cruise(
    id = id,
    title = title,
    description = "Week-long sailing adventure along the Croatian coast. #adriatic #summer",
    hashtags = listOf("adriatic", "summer"),
    departureDate = "2025-07-15",
    departurePort = CruisePort("Split, Croatia", PostCoordinates(43.5081, 16.4402)),
    arrivalDate = "2025-07-22",
    arrivalPort = CruisePort("Dubrovnik, Croatia", PostCoordinates(42.6507, 18.0944)),
    costPerPerson = 850.0,
    currency = CruiseCurrency.Eur,
    maxParticipants = maxParticipants,
    participantsCount = participantsCount,
    isPrivate = isPrivate,
    vessel = "Bavaria Cruiser 46",
    vesselType = VesselType.SailingYacht,
    type = app.skipperclub.data.CruiseType.Relax,
    organizer = CruiseUser(id = "org", name = "Jan Kowalski"),
    currentUserRole = role,
    createdAt = "2025-06-01T10:00:00Z",
    updatedAt = "2025-06-01T10:00:00Z",
)

@Preview(showBackground = true, widthDp = 380, locale = "en")
@Composable
private fun CruiseCardPreview() {
    SkipperClubTheme {
        Column(modifier = Modifier.padding(12.dp)) {
            CruiseCard(cruise = previewCruise(role = CruiseUserRole.Organizer), onClick = {})
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            CruiseCard(
                cruise = previewCruise(
                    id = "c2",
                    title = "Croatia Milebuilding",
                    participantsCount = 6,
                    isPrivate = true,
                ),
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 380, locale = "pl")
@Composable
private fun CruiseCardPreviewPl() {
    SkipperClubTheme {
        CruiseCard(
            cruise = previewCruise(participantsCount = 5),
            onClick = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
