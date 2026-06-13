package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileError
import app.skipperclub.data.SailingExperience
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
class EditProfileControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeProfileGateway()
    private val events = mutableListOf<EditProfileEvent>()

    private fun controller(token: String? = "token") = EditProfileController(
        source = testProfile(name = "Jan Kowalski"),
        scope = scope,
        accessToken = { token },
        gateway = gateway,
    ).also { c -> scope.launch { c.events.collect { events += it } } }

    @Test
    fun seedsFormFromSourceProfile() {
        val state = controller().state.value
        assertEquals("Jan Kowalski", state.name)
        assertEquals("Sailing enthusiast", state.bio)
        assertEquals("Gdańsk", state.city)
        assertEquals("PL", state.country)
        assertEquals(SailingExperience.Advanced, state.sailingExperience)
        assertEquals("10", state.yearsOfExperience)
        assertEquals("pl, en", state.languagesSpoken)
    }

    @Test
    fun saveSendsTrimmedUpdateAndEmitsSavedPreservingEmail() {
        val controller = controller()
        controller.onName("  Anna Nowak  ")
        controller.onBio("  ")
        controller.onLanguagesSpoken("pl, en, en, de")

        controller.save()

        val update = gateway.lastUpdate!!
        assertEquals("Anna Nowak", update.name)
        assertNull(update.bio) // blank cleared
        assertEquals(listOf("pl", "en", "de"), update.languagesSpoken) // de-duplicated
        val saved = events.filterIsInstance<EditProfileEvent.Saved>().single()
        // PUT/UserDetail omits email; the controller preserves what it already held.
        assertEquals("anna@example.com", saved.profile.email)
        assertFalse(controller.state.value.isSaving)
    }

    @Test
    fun blankNameBlocksSaveAndFlagsField() {
        val controller = controller()
        controller.onName("   ")

        controller.save()

        assertTrue(controller.state.value.nameInvalid)
        assertEquals(0, gateway.updateCalls)
        assertTrue(events.isEmpty())
    }

    @Test
    fun pickedAvatarIsUploadedBeforeUpdateAndUrlWins() {
        val controller = controller()
        gateway.uploadedAvatarUrl = "https://cdn.example.com/avatars/fresh.jpg"
        controller.onAvatarPicked("a.jpg", "image/jpeg", byteArrayOf(1, 2, 3), 100, 100)

        controller.save()

        assertEquals(1, gateway.avatarCalls)
        assertEquals(1, gateway.updateCalls)
        val saved = events.filterIsInstance<EditProfileEvent.Saved>().single()
        assertEquals("https://cdn.example.com/avatars/fresh.jpg", saved.profile.avatarUrl)
    }

    @Test
    fun updateFailureEmitsSaveFailedAndStopsSaving() {
        gateway.updateError = ProfileError.Validation("bad")
        val controller = controller()

        controller.save()

        assertTrue(events.any { it is EditProfileEvent.SaveFailed })
        assertFalse(controller.state.value.isSaving)
    }

    @Test
    fun avatarFailureSkipsProfileUpdate() {
        gateway.avatarError = ProfileError.Network(RuntimeException("offline"))
        val controller = controller()
        controller.onAvatarPicked("a.jpg", "image/jpeg", byteArrayOf(1), null, null)

        controller.save()

        assertEquals(1, gateway.avatarCalls)
        assertEquals(0, gateway.updateCalls)
        assertTrue(events.any { it is EditProfileEvent.SaveFailed })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.save()

        assertTrue(events.contains(EditProfileEvent.SessionExpired))
        assertEquals(0, gateway.updateCalls)
        assertFalse(controller.state.value.isSaving)
    }

    @Test
    fun countryIsUppercasedAndDigitsOnlyForYears() {
        val controller = controller()
        controller.onCountry("de")
        controller.onYearsOfExperience("1a2b")

        assertEquals("DE", controller.state.value.country)
        assertEquals("12", controller.state.value.yearsOfExperience)
    }
}
