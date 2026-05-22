package app.skipperclub.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.skipperclub.R

enum class MainDestination(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    POSTS(R.string.nav_posts, R.drawable.ic_dynamic_feed),
    CRUISES(R.string.nav_cruises, R.drawable.ic_sailing),
    MAP(R.string.nav_map, R.drawable.ic_map),
    MESSAGES(R.string.nav_messages, R.drawable.ic_chat),
    MENU(R.string.nav_menu, R.drawable.ic_menu),
}
