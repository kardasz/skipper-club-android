package app.skipperclub.ui.main.notifications

import app.skipperclub.data.NotificationStatus
import app.skipperclub.data.NotificationsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends, so an Unconfined scope runs every launched
 * coroutine to completion synchronously.
 */
class NotificationsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeNotificationsGateway()
    private val events = mutableListOf<NotificationsEvent>()

    private fun controller(token: String? = "token"): NotificationsController {
        val controller = NotificationsController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            pageSize = 2,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesNotificationsAndPagingState() {
        gateway.pages = listOf(
            notificationsPage(listOf(testNotification("n1"), testNotification("n2")), total = 5),
        )
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(listOf("n1", "n2"), state.notifications.map { it.id })
        assertTrue(state.hasMore)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertEquals(2, state.unreadCount)
    }

    @Test
    fun loadInitialIsIdempotent() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"))))
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun loadMoreAppendsNextPageDeduplicated() {
        gateway.pages = listOf(
            notificationsPage(listOf(testNotification("n1"), testNotification("n2")), total = 3),
            notificationsPage(listOf(testNotification("n2"), testNotification("n3")), total = 3, offset = 2),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf("n1", "n2", "n3"), state.notifications.map { it.id })
        assertEquals(2, gateway.listQueries.last().offset)
    }

    @Test
    fun loadMoreIsNoopWithoutMorePages() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.loadMore()

        assertEquals(1, gateway.calls.count { it == "list" })
    }

    @Test
    fun markReadUpdatesStatusOptimisticallyAndCallsGateway() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.markRead(controller.state.value.notifications.first())

        assertEquals(NotificationStatus.Read, controller.state.value.notifications.first().status)
        assertEquals(0, controller.state.value.unreadCount)
        assertTrue(gateway.calls.contains("updateStatus:n1:READ"))
    }

    @Test
    fun markReadSkipsAlreadyReadNotifications() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1", status = NotificationStatus.Read))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.markRead(controller.state.value.notifications.first())

        assertFalse(gateway.calls.any { it.startsWith("updateStatus") })
    }

    @Test
    fun markReadFailureRevertsStatusAndEmitsError() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = NotificationsError.NotFound(null)

        controller.markRead(controller.state.value.notifications.first())

        assertEquals(NotificationStatus.Unread, controller.state.value.notifications.first().status)
        assertTrue(events.any { it is NotificationsEvent.OperationFailed })
    }

    @Test
    fun markAllReadClearsEveryUnreadAndCallsGateway() {
        gateway.pages = listOf(
            notificationsPage(
                listOf(
                    testNotification("n1"),
                    testNotification("n2", status = NotificationStatus.Read),
                    testNotification("n3"),
                ),
            ),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.markAllRead()

        assertEquals(0, controller.state.value.unreadCount)
        assertTrue(gateway.calls.contains("markAllRead"))
    }

    @Test
    fun markAllReadFailureRestoresPreviousState() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"), testNotification("n2"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = NotificationsError.Network(Exception("offline"))

        controller.markAllRead()

        assertEquals(2, controller.state.value.unreadCount)
        assertTrue(events.any { it is NotificationsEvent.OperationFailed })
    }

    @Test
    fun deleteRemovesNotificationAndCallsGateway() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"), testNotification("n2"))))
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.delete(controller.state.value.notifications.first())

        assertEquals(listOf("n2"), controller.state.value.notifications.map { it.id })
        assertTrue(gateway.calls.contains("delete:n1"))
    }

    @Test
    fun deleteFailureRestoresNotificationAndEmitsError() {
        gateway.pages = listOf(notificationsPage(listOf(testNotification("n1"))))
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.mutationError = NotificationsError.Server(500, null)

        controller.delete(controller.state.value.notifications.first())

        assertEquals(listOf("n1"), controller.state.value.notifications.map { it.id })
        assertTrue(events.any { it is NotificationsEvent.OperationFailed })
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.listError = NotificationsError.Network(Exception("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(controller.state.value.hasLoadedOnce)
        assertTrue(events.any { it is NotificationsEvent.OperationFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(NotificationsEvent.SessionExpired))
    }

    @Test
    fun refreshReplacesNotifications() {
        gateway.pages = listOf(
            notificationsPage(listOf(testNotification("n1"))),
            notificationsPage(listOf(testNotification("n2"))),
        )
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.refresh()

        assertEquals(listOf("n2"), controller.state.value.notifications.map { it.id })
    }
}
