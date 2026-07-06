package app.skipperclub.ui.main.posts

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import app.skipperclub.R
import app.skipperclub.data.Post
import app.skipperclub.data.ReactionType

/**
 * Since API v8.0.0 posts no longer carry a `type`; what a post "is" is derived from
 * its server-computed `contentKeys` / `content`. [PostKind] captures the primary
 * facet used for the header badge and icon. Precedence — a post that both carries an
 * alert and media renders as an alert — is alert > route > media > note.
 */
enum class PostKind {
    Alert,
    Route,
    Media,
    Note,
}

/** The dominant facet of a post, used to pick its header badge/icon. */
val Post.primaryKind: PostKind
    get() = when {
        hasAlert -> PostKind.Alert
        hasRoute -> PostKind.Route
        hasMedia -> PostKind.Media
        else -> PostKind.Note
    }

@StringRes
fun PostKind.labelRes(): Int = when (this) {
    PostKind.Alert -> R.string.post_kind_alert
    PostKind.Route -> R.string.post_kind_route
    PostKind.Media -> R.string.post_kind_media
    PostKind.Note -> R.string.post_kind_note
}

fun PostKind.icon(): ImageVector = when (this) {
    PostKind.Alert -> Icons.Outlined.WarningAmber
    PostKind.Route -> Icons.Outlined.Route
    PostKind.Media -> Icons.Outlined.PhotoCamera
    PostKind.Note -> Icons.AutoMirrored.Outlined.Notes
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
