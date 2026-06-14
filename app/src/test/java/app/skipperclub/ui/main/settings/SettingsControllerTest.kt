package app.skipperclub.ui.main.settings

import app.skipperclub.data.NotificationSettings
import app.skipperclub.data.SettingsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends, so an Unconfined scope runs every launched
 * coroutine to completion synchronously.
 */
class SettingsControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeSettingsGateway()
    private val events = mutableListOf<SettingsEvent>()

    private fun controller(token: String? = "token"): SettingsController {
        val controller = SettingsController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesNotificationSettings() {
        gateway.settings = NotificationSettings(emailNotificationsEnabled = false, pushNotificationsEnabled = true)
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals(false, state.notifications?.emailNotificationsEnabled)
        assertEquals(true, state.notifications?.pushNotificationsEnabled)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
    }

    @Test
    fun loadInitialIsIdempotent() {
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.getCalls)
    }

    @Test
    fun loadFailureSetsFailedFlagAndEmitsEvent() {
        gateway.getError = SettingsError.Network(RuntimeException("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertNull(state.notifications)
        assertTrue(state.loadFailed)
        assertTrue(state.hasLoadedOnce)
        assertTrue(events.any { it is SettingsEvent.LoadFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertEquals(0, gateway.getCalls)
        assertTrue(events.contains(SettingsEvent.SessionExpired))
    }

    @Test
    fun retryRecoversAfterFailure() {
        gateway.getError = SettingsError.Network(RuntimeException("offline"))
        val controller = controller()
        controller.loadInitialIfNeeded()
        assertTrue(controller.state.value.loadFailed)

        gateway.getError = null
        gateway.settings = NotificationSettings(emailNotificationsEnabled = true, pushNotificationsEnabled = true)
        controller.retry()

        val state = controller.state.value
        assertFalse(state.loadFailed)
        assertEquals(true, state.notifications?.emailNotificationsEnabled)
    }

    @Test
    fun togglePushSendsBothFieldsAndPersists() {
        gateway.settings = NotificationSettings(emailNotificationsEnabled = true, pushNotificationsEnabled = true)
        val controller = controller()
        controller.loadInitialIfNeeded()

        controller.setPushEnabled(false)

        assertEquals(1, gateway.updateCalls)
        // Full-replacement semantics: both fields are sent.
        assertEquals(
            NotificationSettings(emailNotificationsEnabled = true, pushNotificationsEnabled = false),
            gateway.lastUpdate,
        )
        val state = controller.state.value
        assertEquals(false, state.notifications?.pushNotificationsEnabled)
        assertFalse(state.isSaving)
    }

    @Test
    fun toggleRevertsToConfirmedValueOnFailure() {
        gateway.settings = NotificationSettings(emailNotificationsEnabled = true, pushNotificationsEnabled = true)
        val controller = controller()
        controller.loadInitialIfNeeded()
        gateway.updateError = SettingsError.Server(500, "boom")

        controller.setEmailEnabled(false)

        // Optimistic flip rolled back to the last server-confirmed value.
        assertEquals(true, controller.state.value.notifications?.emailNotificationsEnabled)
        assertFalse(controller.state.value.isSaving)
        assertTrue(events.any { it is SettingsEvent.SaveFailed })
    }

    @Test
    fun toggleBeforeLoadIsNoOp() {
        val controller = controller()

        controller.setEmailEnabled(false)

        assertEquals(0, gateway.updateCalls)
        assertNull(controller.state.value.notifications)
    }
}
