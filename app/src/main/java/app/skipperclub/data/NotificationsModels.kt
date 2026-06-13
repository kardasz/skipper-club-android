package app.skipperclub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Read state of a notification. Wire values follow `docs/api/openapi.yaml` (`NotificationStatus`). */
enum class NotificationStatus(val wireValue: String) {
    Unread("UNREAD"),
    Read("READ"),
    ;

    companion object {
        fun fromWire(value: String): NotificationStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Source domain object that produced a notification (`NotificationSourceType`). */
enum class NotificationSourceType(val wireValue: String) {
    Cruise("CRUISE"),
    Post("POST"),
    Message("MESSAGE"),
    Review("REVIEW"),
    Media("MEDIA"),
    Friend("FRIEND"),
    ;

    companion object {
        fun fromWire(value: String): NotificationSourceType? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Domain event that triggered a notification (`NotificationEventType`). [Unknown]
 * is a forward-compatible fallback so a future server event never drops the row.
 */
enum class NotificationEventType(val wireValue: String) {
    CruiseInvitationSent("CRUISE_INVITATION_SENT"),
    CruiseParticipantJoined("CRUISE_PARTICIPANT_JOINED"),
    CruiseRequestPending("CRUISE_REQUEST_PENDING"),
    CruiseRequestAccepted("CRUISE_REQUEST_ACCEPTED"),
    CruiseRequestRejected("CRUISE_REQUEST_REJECTED"),
    CruiseInvitationAccepted("CRUISE_INVITATION_ACCEPTED"),
    CruiseParticipantLeft("CRUISE_PARTICIPANT_LEFT"),
    CruiseParticipantRemoved("CRUISE_PARTICIPANT_REMOVED"),
    CruiseDetailsChanged("CRUISE_DETAILS_CHANGED"),
    CruiseReviewReminder("CRUISE_REVIEW_REMINDER"),
    PostReacted("POST_REACTED"),
    PostCommented("POST_COMMENTED"),
    FriendRequestSent("FRIEND_REQUEST_SENT"),
    FriendRequestAccepted("FRIEND_REQUEST_ACCEPTED"),
    FriendRequestRejected("FRIEND_REQUEST_REJECTED"),
    ReviewPendingReceived("REVIEW_PENDING_RECEIVED"),
    ReviewPublished("REVIEW_PUBLISHED"),
    Unknown(""),
    ;

    companion object {
        fun fromWire(value: String): NotificationEventType =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/** Bulk action accepted by `POST /v1/notifications/actions` (`NotificationAction`). */
enum class NotificationBulkAction(val wireValue: String) {
    MarkRead("mark-read"),
    Delete("delete"),
}

/** A single notification as rendered by the UI. */
data class AppNotification(
    val id: String,
    val eventType: NotificationEventType,
    val sourceType: NotificationSourceType,
    val sourceId: String,
    val relationId: String? = null,
    val status: NotificationStatus,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: String,
    val readAt: String? = null,
) {
    val isUnread: Boolean get() = status == NotificationStatus.Unread

    val actorName: String? get() = metadata["actorName"]
    val cruiseTitle: String? get() = metadata["cruiseTitle"]
    val commentText: String? get() = metadata["commentText"]
    val reactionType: String? get() = metadata["reactionType"]
}

/** Query parameters for `GET /v1/notifications`. */
data class NotificationListQuery(
    val status: NotificationStatus? = null,
    val sourceType: NotificationSourceType? = null,
    val order: SortOrder = SortOrder.Desc,
    val limit: Int = 20,
    val offset: Int = 0,
)

data class NotificationsPage(
    val notifications: List<AppNotification>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + notifications.size < total
}

@Serializable
internal data class UpdateNotificationRequest(
    val status: String,
)

@Serializable
internal data class NotificationActionsRequest(
    val action: String,
    val notificationIds: List<String>? = null,
    val all: Boolean? = null,
)

@Serializable
internal data class NotificationDto(
    val id: String,
    val eventType: String,
    val sourceType: String,
    val sourceId: String,
    val relationId: String? = null,
    val status: String,
    val metadata: JsonObject? = null,
    val createdAt: String,
    val readAt: String? = null,
) {
    /** Rows with an unknown source/status are dropped rather than crash the list. */
    fun toDomain(): AppNotification? {
        val source = NotificationSourceType.fromWire(sourceType) ?: return null
        val state = NotificationStatus.fromWire(status) ?: return null
        return AppNotification(
            id = id,
            eventType = NotificationEventType.fromWire(eventType),
            sourceType = source,
            sourceId = sourceId,
            relationId = relationId,
            status = state,
            metadata = metadata.toStringMap(),
            createdAt = createdAt,
            readAt = readAt,
        )
    }
}

/** Keeps only string-valued metadata entries (all documented keys are strings). */
private fun JsonObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return buildMap {
        for ((key, element) in this@toStringMap) {
            (element as? JsonPrimitive)?.contentOrNull?.let { put(key, it) }
        }
    }
}

@Serializable
internal data class NotificationsListDto(
    val notifications: List<NotificationDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): NotificationsPage =
        NotificationsPage(
            notifications = notifications.mapNotNull { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}

@Serializable
internal data class UnreadNotificationCountDto(
    val count: Int = 0,
)
