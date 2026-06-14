package app.skipperclub.ui.main.cruises.reviews

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.skipperclub.R

/** Interactive 1–5 star picker used in the submit-review form. */
@Composable
fun StarRatingInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTagPrefix: String = "star",
) {
    Row(modifier = modifier.selectableGroup()) {
        for (star in ReviewValidation.RATING_MIN..ReviewValidation.RATING_MAX) {
            val selected = star <= value
            Icon(
                imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(R.string.review_star_content_description, star, ReviewValidation.RATING_MAX),
                tint = if (selected) StarColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(40.dp)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onValueChange(star) },
                    )
                    .testTag("${testTagPrefix}_$star"),
            )
        }
    }
}

/** Compact read-only star row used on review cards (filled vs outline up to 5). */
@Composable
fun StarRatingDisplay(
    value: Int,
    modifier: Modifier = Modifier,
    starSize: androidx.compose.ui.unit.Dp = 14.dp,
) {
    Row(modifier = modifier) {
        for (star in ReviewValidation.RATING_MIN..ReviewValidation.RATING_MAX) {
            Icon(
                imageVector = if (star <= value) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (star <= value) StarColor else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

private val StarColor = androidx.compose.ui.graphics.Color(0xFFF5A623)
