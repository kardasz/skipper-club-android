package app.skipperclub.ui.main.spotdetail

import app.skipperclub.data.Coordinates
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.Spot
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotDetailUiStateTest {

    private val spot = Spot(
        id = "spot-1",
        name = "Marina Sopot",
        coordinates = Coordinates(lat = 54.4416, lng = 18.5674),
        phoneContacts = emptyList(),
        radioChannels = emptyList(),
        createdAt = "2026-01-01T10:00:00Z",
        updatedAt = "2026-01-01T10:00:00Z",
    )

    @Test
    fun readyStateExposesSpotIdAndName() {
        val state: SpotDetailUiState = SpotDetailUiState.Ready(spot)

        assertEquals("spot-1", state.spotId)
        assertEquals("Marina Sopot", state.name)
    }

    @Test
    fun loadingStateCarriesKnownIdAndName() {
        val state: SpotDetailUiState = SpotDetailUiState.Loading("spot-2", "Górki Zachodnie")

        assertEquals("spot-2", state.spotId)
        assertEquals("Górki Zachodnie", state.name)
    }

    @Test
    fun displayPhoneAppendsExtensionWhenPresent() {
        val contact = PhoneContact(id = "p1", label = "Kapitanat", phone = "+48 58 555 99 00", extension = "12")

        assertEquals("+48 58 555 99 00 · ext. 12", displayPhone(contact))
    }

    @Test
    fun displayPhoneOmitsBlankExtension() {
        val contact = PhoneContact(id = "p2", label = null, phone = "+48 58 555 12 34", extension = "  ")

        assertEquals("+48 58 555 12 34", displayPhone(contact))
    }
}
