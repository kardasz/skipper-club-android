package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicProfileControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeProfileGateway()
    private val events = mutableListOf<ProfileEvent>()

    private fun controller(userId: String = "other", token: String? = "token"): PublicProfileController {
        val controller = PublicProfileController(
            scope = scope,
            userId = userId,
            accessToken = { token },
            gateway = gateway,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun loadFetchesTargetUserById() {
        gateway.userProfile = testProfile(id = "other", name = "Jan Kowalski", email = "")
        val controller = controller(userId = "other")

        controller.loadInitialIfNeeded()

        assertEquals("Jan Kowalski", controller.state.value.profile?.name)
        assertEquals("other", gateway.lastUserId)
        assertTrue(controller.state.value.hasLoadedOnce)
        assertFalse(controller.state.value.loadFailed)
    }

    @Test
    fun loadInitialIsIdempotent() {
        val controller = controller()

        controller.loadInitialIfNeeded()
        controller.loadInitialIfNeeded()

        assertEquals(1, gateway.userCalls)
    }

    @Test
    fun loadFailureSetsFlagAndEmitsEvent() {
        gateway.userError = ProfileError.NotFound(null)
        val controller = controller()

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.any { it is ProfileEvent.LoadFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.loadInitialIfNeeded()

        assertTrue(controller.state.value.loadFailed)
        assertTrue(events.contains(ProfileEvent.SessionExpired))
    }
}
