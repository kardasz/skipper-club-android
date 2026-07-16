package app.skipperclub.ui.main.messages

import android.text.format.DateUtils
import app.skipperclub.data.Chat
import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatUser
import app.skipperclub.data.UserPresence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Participants other than the signed-in user, used for titles and avatars. */
internal fun otherParticipants(chat: Chat, currentUserId: String?): List<ChatUser> =
    chat.participants.filterNot { it.id == currentUserId }

/**
 * Display title: explicit name when present, otherwise the other participants'
 * names (group chats list up to [maxNames]). Returns null when nothing usable
 * is available so the UI can fall back to a localized placeholder.
 */
internal fun chatTitle(chat: Chat, currentUserId: String?, maxNames: Int = 3): String? {
    chat.name?.takeIf { it.isNotBlank() }?.let { return it }
    val others = otherParticipants(chat, currentUserId)
    if (others.isEmpty()) return null
    if (chat.type == ChatType.OneToOne) return others.first().name
    val visible = others.take(maxNames).joinToString(", ") { it.name }
    val overflow = others.size - maxNames
    return if (overflow > 0) "$visible +$overflow" else visible
}

internal fun chatRelativeTime(isoTimestamp: String, nowMillis: Long): String =
    try {
        DateUtils.getRelativeTimeSpanString(
            Instant.parse(isoTimestamp).toEpochMilli(),
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } catch (_: DateTimeParseException) {
        ""
    }

private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun messageTime(isoTimestamp: String): String =
    try {
        messageTimeFormatter.format(Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()))
    } catch (_: DateTimeParseException) {
        ""
    }

internal fun messageDay(isoTimestamp: String): LocalDate? =
    try {
        Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (_: DateTimeParseException) {
        null
    }

internal fun messageDayLabel(day: LocalDate, nowMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        nowMillis,
        DateUtils.DAY_IN_MILLIS,
    ).toString()

/** What to show for a 1:1 chat participant's presence; localized text is resolved by the caller. */
internal sealed interface PresenceStatus {
    data object Online : PresenceStatus
    data class LastSeen(val relativeTime: String) : PresenceStatus
}

/**
 * Resolves [presence] into something displayable, or null when nothing useful is known yet
 * (no presence event has arrived, or the last-seen timestamp failed to parse).
 */
internal fun presenceStatus(presence: UserPresence?, nowMillis: Long): PresenceStatus? {
    if (presence == null) return null
    if (presence.isOnline) return PresenceStatus.Online
    val lastSeen = presence.lastSeen ?: return null
    val relative = chatRelativeTime(lastSeen, nowMillis)
    if (relative.isBlank()) return null
    return PresenceStatus.LastSeen(relative)
}

internal fun ChatUser.initials(): String =
    name
        .trim()
        .split(Regex("\\s+"))
        .asSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "?" }
