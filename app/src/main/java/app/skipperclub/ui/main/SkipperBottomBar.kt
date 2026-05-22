package app.skipperclub.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun SkipperBottomBar(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            SideNavItem(MainDestination.POSTS, selected, onSelect, Modifier.weight(1f))
            SideNavItem(MainDestination.CRUISES, selected, onSelect, Modifier.weight(1f))
            MapNavItem(
                selected = selected == MainDestination.MAP,
                onClick = { onSelect(MainDestination.MAP) },
                modifier = Modifier.weight(1f),
            )
            SideNavItem(MainDestination.MESSAGES, selected, onSelect, Modifier.weight(1f))
            SideNavItem(MainDestination.MENU, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SideNavItem(
    destination: MainDestination,
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = destination == selected
    val tint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(destination.labelRes)
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect(destination) },
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MapNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(MainDestination.MAP.labelRes)
    val circleColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, clip = false),
            shape = CircleShape,
            color = circleColor,
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(MainDestination.MAP.iconRes),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun SkipperBottomBarPreviewMapSelected() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.MAP, onSelect = {})
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun SkipperBottomBarPreviewPostsSelected() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.POSTS, onSelect = {})
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun SkipperBottomBarPreviewPl() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.MAP, onSelect = {})
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SkipperBottomBarPreviewDark() {
    SkipperClubTheme {
        SkipperBottomBar(selected = MainDestination.MAP, onSelect = {})
    }
}
