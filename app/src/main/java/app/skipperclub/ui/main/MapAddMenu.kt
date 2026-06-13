package app.skipperclub.ui.main

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.main.checkin.hasLocationPermission
import app.skipperclub.ui.theme.SkipperClubTheme

/**
 * Bottom-right "+" speed-dial that opens the two map creation actions:
 * "Meldunek" (check-in) and "Ostrzeżenie" (navigation alert). It owns the
 * location-permission gate for check-in, since that flow needs a GPS fix to
 * recentre the map.
 */
@Composable
fun MapAddMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectCheckIn: () -> Unit,
    onPermissionDenied: () -> Unit,
    onSelectAlert: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onSelectCheckIn() else onPermissionDenied()
    }

    val fabRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "Add menu FAB rotation",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim that dismisses the menu when tapped outside the actions.
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onExpandedChange(false) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp)
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MapAddMenuOption(
                        icon = Icons.Filled.LocationOn,
                        label = stringResource(R.string.map_action_check_in),
                        testTag = "map_add_check_in",
                        onClick = {
                            onExpandedChange(false)
                            if (context.hasLocationPermission()) {
                                onSelectCheckIn()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                    )
                    MapAddMenuOption(
                        icon = Icons.Filled.Warning,
                        label = stringResource(R.string.map_action_alert),
                        testTag = "map_add_alert",
                        onClick = {
                            onExpandedChange(false)
                            onSelectAlert()
                        },
                    )
                }
            }

            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.testTag("map_add_fab"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (expanded) R.string.map_action_collapse else R.string.map_action_expand,
                    ),
                    modifier = Modifier.rotate(fabRotation),
                )
            }
        }
    }
}

@Composable
private fun MapAddMenuOption(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 10.dp)
                .size(22.dp),
        )
        Text(text = label)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 420, locale = "pl")
@Composable
private fun MapAddMenuExpandedPreview() {
    SkipperClubTheme {
        MapAddMenu(
            expanded = true,
            onExpandedChange = {},
            onSelectCheckIn = {},
            onPermissionDenied = {},
            onSelectAlert = {},
            bottomInset = 24.dp,
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 420,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MapAddMenuCollapsedPreview() {
    SkipperClubTheme {
        MapAddMenu(
            expanded = false,
            onExpandedChange = {},
            onSelectCheckIn = {},
            onPermissionDenied = {},
            onSelectAlert = {},
            bottomInset = 24.dp,
        )
    }
}
