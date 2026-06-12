package app.skipperclub.ui.main.posts

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import app.skipperclub.R
import app.skipperclub.data.PostType
import app.skipperclub.data.ReactionType

@StringRes
fun PostType.labelRes(): Int = when (this) {
    PostType.Photo -> R.string.post_type_photo
    PostType.Place -> R.string.post_type_place
    PostType.Food -> R.string.post_type_food
    PostType.Marina -> R.string.post_type_marina
    PostType.Tips -> R.string.post_type_tips
    PostType.Route -> R.string.post_type_route
    PostType.Berth -> R.string.post_type_berth
    PostType.Weather -> R.string.post_type_weather
    PostType.NavigationWarning -> R.string.post_type_navigation_warning
    PostType.Help -> R.string.post_type_help
}

@StringRes
fun PostType.descriptionRes(): Int = when (this) {
    PostType.Photo -> R.string.post_type_photo_desc
    PostType.Place -> R.string.post_type_place_desc
    PostType.Food -> R.string.post_type_food_desc
    PostType.Marina -> R.string.post_type_marina_desc
    PostType.Tips -> R.string.post_type_tips_desc
    PostType.Route -> R.string.post_type_route_desc
    PostType.Berth -> R.string.post_type_berth_desc
    PostType.Weather -> R.string.post_type_weather_desc
    PostType.NavigationWarning -> R.string.post_type_navigation_warning_desc
    PostType.Help -> R.string.post_type_help_desc
}

fun PostType.icon(): ImageVector = when (this) {
    PostType.Photo -> Icons.Outlined.PhotoCamera
    PostType.Place -> Icons.Outlined.Place
    PostType.Food -> Icons.Outlined.Restaurant
    PostType.Marina -> Icons.Outlined.Anchor
    PostType.Tips -> Icons.Outlined.Lightbulb
    PostType.Route -> Icons.Outlined.Route
    PostType.Berth -> Icons.Outlined.DirectionsBoat
    PostType.Weather -> Icons.Outlined.Cloud
    PostType.NavigationWarning -> Icons.Outlined.WarningAmber
    PostType.Help -> Icons.AutoMirrored.Outlined.HelpOutline
}

val ReactionType.emoji: String
    get() = when (this) {
        ReactionType.Heart -> "❤️"
        ReactionType.ThumbsUp -> "👍"
        ReactionType.ThumbsDown -> "👎"
        ReactionType.Laugh -> "😂"
        ReactionType.Wow -> "😮"
        ReactionType.Sad -> "😢"
        ReactionType.Fire -> "🔥"
        ReactionType.Clap -> "👏"
        ReactionType.Party -> "🎉"
        ReactionType.Thinking -> "🤔"
        ReactionType.Anchor -> "⚓"
        ReactionType.Sailboat -> "⛵"
        ReactionType.Wave -> "🌊"
        ReactionType.Sun -> "☀️"
        ReactionType.Compass -> "🧭"
        ReactionType.Fish -> "🐟"
        ReactionType.Whale -> "🐋"
        ReactionType.Dolphin -> "🐬"
        ReactionType.Wind -> "💨"
        ReactionType.Lifesaver -> "🛟"
    }
