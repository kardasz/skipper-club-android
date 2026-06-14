package app.skipperclub.ui.main.cruises.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Review
import app.skipperclub.ui.main.cruises.CruiseAvatar
import app.skipperclub.ui.main.cruises.formatCruiseDate

/** A single published review card: counterpart user, average badge, category breakdown, comment. */
@Composable
fun ReviewCard(
    review: Review,
    currentUserId: String?,
    modifier: Modifier = Modifier,
) {
    val isGiven = review.reviewer.id == currentUserId
    val other = if (isGiven) review.reviewedUser else review.reviewer
    val relationLabel = if (isGiven) {
        stringResource(R.string.review_card_review_of)
    } else {
        stringResource(R.string.review_card_reviewed_by)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CruiseAvatar(name = other.name, avatarUrl = other.avatarUrl, modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = relationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = other.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AverageBadge(average = review.ratings.average)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingCategory.entries.forEach { category ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(category.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StarRatingDisplay(
                        value = category.valueOf(review.ratings),
                        starSize = 11.dp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        if (review.comment.isNotBlank()) {
            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Text(
            text = stringResource(R.string.review_card_date, formatCruiseDate(review.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AverageBadge(average: Double) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StarBadgeContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = StarBadgeContent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = formatAverage(average),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = StarBadgeContent,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private val StarBadgeContainer = Color(0x1AF5A623)
private val StarBadgeContent = Color(0xFFB7791F)
