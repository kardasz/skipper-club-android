package app.skipperclub.ui.main.notifications

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.skipperclub.R
import app.skipperclub.data.AppNotification
import app.skipperclub.data.NotificationEventType
import app.skipperclub.data.NotificationSourceType
import java.time.Instant
import java.time.format.DateTimeParseException

/** Destination opened when a notification row is tapped. */
sealed interface NotificationTarget {
    data class Cruise(val cruiseId: String) : NotificationTarget
    data class CruiseReviews(val cruiseId: String) : NotificationTarget
    data class Post(val postId: String, val focusComments: Boolean) : NotificationTarget
}

/**
 * Resolves the in-app destination for a notification, or `null` when the linked
 * surface is not yet available on Android (friend/message have no screen).
 *
 * Review notifications carry `sourceId = reviewId` (not a cruise), so they rely on
 * `metadata.cruiseId` to deep-link into the cruise reviews center; the cruise-scoped
 * review reminder uses its `sourceId` directly. Pure function — covered by unit tests.
 */
fun AppNotification.target(): NotificationTarget? = when (sourceType) {
    NotificationSourceType.Cruise ->
        if (eventType == NotificationEventType.CruiseReviewReminder) {
            NotificationTarget.CruiseReviews(sourceId)
        } else {
            NotificationTarget.Cruise(sourceId)
        }
    NotificationSourceType.Post -> NotificationTarget.Post(
        postId = sourceId,
        focusComments = eventType == NotificationEventType.PostCommented,
    )
    NotificationSourceType.Review -> cruiseId?.let { NotificationTarget.CruiseReviews(it) }
    NotificationSourceType.Message,
    NotificationSourceType.Media,
    NotificationSourceType.Friend,
    -> null
}

/** Human-readable, localized message for a notification (mirrors the iOS client). */
@Composable
fun AppNotification.displayText(): String {
    val actor = actorName ?: stringResource(R.string.notification_actor_someone)
    val cruise = cruiseTitle ?: stringResource(R.string.notification_a_cruise)
    return when (eventType) {
        NotificationEventType.CruiseInvitationSent ->
            stringResource(R.string.notification_cruise_invitation_sent, actor, cruise)
        NotificationEventType.CruiseRequestPending ->
            stringResource(R.string.notification_cruise_request_pending, actor, cruise)
        NotificationEventType.CruiseRequestAccepted ->
            stringResource(R.string.notification_cruise_request_accepted, cruise)
        NotificationEventType.CruiseInvitationAccepted ->
            stringResource(R.string.notification_cruise_invitation_accepted, actor, cruise)
        NotificationEventType.CruiseParticipantJoined ->
            stringResource(R.string.notification_cruise_participant_joined, actor, cruise)
        NotificationEventType.CruiseRequestRejected ->
            stringResource(R.string.notification_cruise_request_rejected, cruise)
        NotificationEventType.CruiseParticipantLeft ->
            stringResource(R.string.notification_cruise_participant_left, actor, cruise)
        NotificationEventType.CruiseParticipantRemoved ->
            stringResource(R.string.notification_cruise_participant_removed, cruise)
        NotificationEventType.CruiseDetailsChanged ->
            stringResource(R.string.notification_cruise_details_changed, cruise, actor)
        NotificationEventType.CruiseReviewReminder ->
            stringResource(R.string.notification_cruise_review_reminder, cruise)
        NotificationEventType.PostReacted ->
            stringResource(R.string.notification_post_reacted, actor)
        NotificationEventType.PostCommented -> {
            val excerpt = commentText?.take(50)?.trim().orEmpty()
            if (excerpt.isEmpty()) {
                stringResource(R.string.notification_post_commented_no_text, actor)
            } else {
                stringResource(R.string.notification_post_commented, actor, excerpt)
            }
        }
        NotificationEventType.FriendRequestSent ->
            stringResource(R.string.notification_friend_request_sent, actor)
        NotificationEventType.FriendRequestAccepted ->
            stringResource(R.string.notification_friend_request_accepted, actor)
        NotificationEventType.FriendRequestRejected ->
            stringResource(R.string.notification_friend_request_rejected, actor)
        NotificationEventType.ReviewPendingReceived ->
            stringResource(R.string.notification_review_pending_received, actor, cruise)
        NotificationEventType.ReviewPublished ->
            stringResource(R.string.notification_review_published, cruise)
        NotificationEventType.Unknown ->
            stringResource(R.string.notification_unknown)
    }
}

/** Leading icon for a notification, chosen by event type. */
val NotificationEventType.icon: ImageVector
    get() = when (this) {
        NotificationEventType.CruiseInvitationSent -> Icons.Filled.Sailing
        NotificationEventType.CruiseRequestPending -> Icons.Filled.PersonAdd
        NotificationEventType.CruiseRequestAccepted -> Icons.Filled.CheckCircle
        NotificationEventType.CruiseInvitationAccepted -> Icons.Filled.CheckCircle
        NotificationEventType.CruiseParticipantJoined -> Icons.Filled.Group
        NotificationEventType.CruiseRequestRejected -> Icons.Filled.Cancel
        NotificationEventType.CruiseParticipantLeft -> Icons.Filled.PersonRemove
        NotificationEventType.CruiseParticipantRemoved -> Icons.Filled.PersonRemove
        NotificationEventType.CruiseDetailsChanged -> Icons.Filled.Edit
        NotificationEventType.CruiseReviewReminder -> Icons.Filled.Sailing
        NotificationEventType.PostReacted -> Icons.Filled.Favorite
        NotificationEventType.PostCommented -> Icons.Outlined.ChatBubble
        NotificationEventType.FriendRequestSent -> Icons.Filled.PersonAdd
        NotificationEventType.FriendRequestAccepted -> Icons.Filled.Group
        NotificationEventType.FriendRequestRejected -> Icons.Filled.PersonRemove
        NotificationEventType.ReviewPendingReceived -> Icons.Filled.RateReview
        NotificationEventType.ReviewPublished -> Icons.Filled.Star
        NotificationEventType.Unknown -> Icons.Filled.Notifications
    }

internal fun notificationRelativeTime(isoTimestamp: String, nowMillis: Long): String =
    try {
        DateUtils.getRelativeTimeSpanString(
            Instant.parse(isoTimestamp).toEpochMilli(),
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } catch (_: DateTimeParseException) {
        ""
    }
