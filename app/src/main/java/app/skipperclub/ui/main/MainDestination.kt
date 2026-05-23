package app.skipperclub.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.ui.graphics.vector.ImageVector
import app.skipperclub.R

enum class MainDestination(
    @param:StringRes val labelRes: Int,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
) {
    POSTS(R.string.nav_posts, Icons.Outlined.DynamicFeed, Icons.Filled.DynamicFeed),
    CRUISES(R.string.nav_cruises, Icons.Outlined.Sailing, Icons.Filled.Sailing),
    MAP(R.string.nav_map, Icons.Outlined.Map, Icons.Filled.Map),
    MESSAGES(R.string.nav_messages, Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    MENU(R.string.nav_menu, Icons.Outlined.Menu, Icons.Filled.Menu),
}
