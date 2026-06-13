package app.skipperclub.ui.main.notifications

import app.skipperclub.data.NotificationListQuery
import app.skipperclub.data.NotificationStatus
import app.skipperclub.data.NotificationsApi
import app.skipperclub.data.NotificationsPage

/**
 * Seam between the notifications UI controller and [NotificationsApi] so the
 * state-machine logic stays unit-testable with fakes (no MockWebServer needed
 * at this layer).
 */
interface NotificationsGateway {
    suspend fun list(accessToken: String, query: NotificationListQuery): NotificationsPage
    suspend fun updateStatus(accessToken: String, notificationId: String, status: NotificationStatus)
    suspend fun delete(accessToken: String, notificationId: String)
    suspend fun markAllRead(accessToken: String)
}

object RealNotificationsGateway : NotificationsGateway {
    override suspend fun list(accessToken: String, query: NotificationListQuery): NotificationsPage =
        NotificationsApi.list(accessToken, query)

    override suspend fun updateStatus(accessToken: String, notificationId: String, status: NotificationStatus) =
        NotificationsApi.updateStatus(accessToken, notificationId, status)

    override suspend fun delete(accessToken: String, notificationId: String) =
        NotificationsApi.delete(accessToken, notificationId)

    override suspend fun markAllRead(accessToken: String) =
        NotificationsApi.markRead(accessToken, all = true)
}
