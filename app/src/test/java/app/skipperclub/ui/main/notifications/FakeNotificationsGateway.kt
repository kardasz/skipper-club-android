package app.skipperclub.ui.main.notifications

import app.skipperclub.data.AppNotification
import app.skipperclub.data.NotificationEventType
import app.skipperclub.data.NotificationListQuery
import app.skipperclub.data.NotificationSourceType
import app.skipperclub.data.NotificationStatus
import app.skipperclub.data.NotificationsError
import app.skipperclub.data.NotificationsPage

internal fun testNotification(
    id: String,
    eventType: NotificationEventType = NotificationEventType.CruiseInvitationSent,
    sourceType: NotificationSourceType = NotificationSourceType.Cruise,
    sourceId: String = "src-$id",
    relationId: String? = "rel-$id",
    status: NotificationStatus = NotificationStatus.Unread,
    metadata: Map<String, String> = mapOf("actorName" to "Anna", "cruiseTitle" to "Mazury"),
) = AppNotification(
    id = id,
    eventType = eventType,
    sourceType = sourceType,
    sourceId = sourceId,
    relationId = relationId,
    status = status,
    metadata = metadata,
    createdAt = "2026-06-13T09:00:00Z",
    readAt = if (status == NotificationStatus.Read) "2026-06-13T09:30:00Z" else null,
)

internal fun notificationsPage(
    notifications: List<AppNotification>,
    total: Int = notifications.size,
    offset: Int = 0,
) = NotificationsPage(notifications = notifications, total = total, limit = 20, offset = offset)

/** Configurable in-memory [NotificationsGateway]; records calls for assertions. */
internal class FakeNotificationsGateway : NotificationsGateway {
    var pages: List<NotificationsPage> = listOf(notificationsPage(emptyList()))
    var listError: NotificationsError? = null
    var mutationError: NotificationsError? = null
    val listQueries = mutableListOf<NotificationListQuery>()
    val calls = mutableListOf<String>()

    private var listCallCount = 0

    override suspend fun list(accessToken: String, query: NotificationListQuery): NotificationsPage {
        calls += "list"
        listQueries += query
        listError?.let { throw it }
        val page = pages[minOf(listCallCount, pages.lastIndex)]
        listCallCount++
        return page
    }

    override suspend fun updateStatus(accessToken: String, notificationId: String, status: NotificationStatus) {
        calls += "updateStatus:$notificationId:${status.wireValue}"
        mutationError?.let { throw it }
    }

    override suspend fun delete(accessToken: String, notificationId: String) {
        calls += "delete:$notificationId"
        mutationError?.let { throw it }
    }

    override suspend fun markAllRead(accessToken: String) {
        calls += "markAllRead"
        mutationError?.let { throw it }
    }
}
