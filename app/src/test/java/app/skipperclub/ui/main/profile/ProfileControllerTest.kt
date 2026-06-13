package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileError
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
class ProfileControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeProfileGateway()
    private val events = mutableListOf<ProfileEvent>()

    private fun controller(token: String? = "token"): ProfileController {
        val controller = ProfileController(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun initialLoadPopulatesProfile() {
        gateway.profile = testProfile(name = "Jan Kowalski")
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertEquals("Jan Kowalski", state.profile?.name)
        assertTrue(state.hasLoadedOnce)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
    }

    @Test
    fun loadInitialIsIdempotent() {
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.calls)
    }

    @Test
    fun loadFailureSetsFailedFlagAndEmitsEvent() {
        gateway.error = ProfileError.Network(RuntimeException("offline"))
        val controller = controller()

        controller.loadInitialIfNeeded()

        val state = controller.state.value
        assertNull(state.profile)
        assertTrue(state.loadFailed)
        assertTrue(state.hasLoadedOnce)
        assertTrue(events.any { it is ProfileEvent.LoadFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertEquals(0, gateway.calls)
        assertTrue(events.contains(ProfileEvent.SessionExpired))
    }

    @Test
    fun refreshRetriesAfterFailure() {
        gateway.error = ProfileError.Network(RuntimeException("offline"))
        val controller = controller()
        controller.loadInitialIfNeeded()
        assertTrue(controller.state.value.loadFailed)

        gateway.error = null
        gateway.profile = testProfile(name = "Recovered")
        controller.refresh()

        val state = controller.state.value
        assertEquals("Recovered", state.profile?.name)
        assertFalse(state.loadFailed)
        assertFalse(state.isRefreshing)
    }
}
