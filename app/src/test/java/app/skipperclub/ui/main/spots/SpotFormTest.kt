package app.skipperclub.ui.main.spots

import app.skipperclub.data.Coordinates
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.RadioChannelKind
import app.skipperclub.data.ResolvedPlace
import app.skipperclub.data.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotFormTest {

    private fun spot(
        phoneContacts: List<PhoneContact> = emptyList(),
        radioChannels: List<RadioChannel> = emptyList(),
        lat: Double = 54.352,
        lng: Double = 18.653,
        name: String = "Neptun",
    ) = Spot(
        id = "s1",
        name = name,
        coordinates = Coordinates(lat, lng),
        phoneContacts = phoneContacts,
        radioChannels = radioChannels,
        createdAt = "2026-06-10T09:00:00Z",
        updatedAt = "2026-06-10T09:00:00Z",
    )

    // --- Validation ---

    @Test
    fun blankNameIsInvalid() {
        assertFalse(SpotForm(name = "  ", lat = "54.0", lng = "18.0").isValid)
    }

    @Test
    fun outOfRangeCoordinatesAreInvalid() {
        assertFalse(SpotForm(name = "X", lat = "120", lng = "18").isLatValid)
        assertFalse(SpotForm(name = "X", lat = "54", lng = "200").isLngValid)
    }

    @Test
    fun validNameAndCoordinatesAreValid() {
        assertTrue(SpotForm(name = "Neptun", lat = "54.35", lng = "18.65").isValid)
    }

    // --- Place picking ---

    @Test
    fun emptyFormHasNoLocation() {
        assertFalse(SpotForm().hasLocation)
        assertFalse(SpotForm(name = "Neptun").hasLocation)
    }

    @Test
    fun resolvedPlaceFillsNameCoordinatesAndLabel() {
        val form = SpotForm().withResolvedPlace(
            ResolvedPlace("p1", "Neptun Marina", 54.352, 18.653, "Szafarnia 11, Gdańsk"),
        )

        assertEquals("Neptun Marina", form.name)
        assertEquals(54.352, form.parsedLat)
        assertEquals(18.653, form.parsedLng)
        assertEquals("Szafarnia 11, Gdańsk", form.locationLabel)
        assertTrue(form.hasLocation)
        assertTrue(form.isValid)
    }

    @Test
    fun resolvedPlaceWithoutNameKeepsTypedName() {
        val form = SpotForm(name = "My marina").withResolvedPlace(
            ResolvedPlace("p1", "", 54.0, 18.0, null),
        )

        assertEquals("My marina", form.name)
        assertEquals("54.0", form.lat)
    }

    @Test
    fun clearLocationDropsCoordinatesButKeepsName() {
        val form = SpotForm(name = "Neptun", lat = "54.35", lng = "18.65", locationLabel = "Gdańsk").clearLocation()

        assertEquals("Neptun", form.name)
        assertFalse(form.hasLocation)
        assertNull(form.locationLabel)
    }

    @Test
    fun nonBlankContactWithoutPhoneIsInvalid() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            phoneContacts = listOf(EditablePhoneContact(label = "Office", phone = "")),
        )
        assertFalse(form.isValid)
    }

    @Test
    fun nonInternationalPhoneIsInvalid() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            phoneContacts = listOf(EditablePhoneContact(label = "Office", phone = "65689")),
        )
        assertFalse(form.isValid)
    }

    @Test
    fun internationalPhoneWithSeparatorsIsValid() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            phoneContacts = listOf(EditablePhoneContact(phone = "+48 581 234 567")),
        )
        assertTrue(form.isValid)
        assertTrue(form.phoneContacts.first().isPhoneValid)
    }

    @Test
    fun fullyBlankContactIsIgnoredForValidity() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            phoneContacts = listOf(EditablePhoneContact()),
        )
        assertTrue(form.isValid)
    }

    @Test
    fun vhfChannelOutOfRangeIsInvalid() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            radioChannels = listOf(EditableRadioChannel(name = "Port", kind = RadioChannelKind.Vhf, vhfChannel = "99")),
        )
        assertFalse(form.isValid)
    }

    @Test
    fun mhzChannelRequiresPositiveFrequency() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            radioChannels = listOf(EditableRadioChannel(name = "Port", kind = RadioChannelKind.Mhz, frequencyMhz = "0")),
        )
        assertFalse(form.isValid)
    }

    // --- Create request ---

    @Test
    fun toCreateRequestTrimsAndDropsBlankRows() {
        val form = SpotForm(
            name = "  Neptun  ",
            lat = "54.35",
            lng = "18.65",
            phoneContacts = listOf(
                EditablePhoneContact(label = " Office ", phone = " +48581234567 ", extension = " 12 "),
                EditablePhoneContact(),
            ),
            radioChannels = listOf(
                EditableRadioChannel(name = "Port", kind = RadioChannelKind.Vhf, vhfChannel = "12", isPrimary = true),
                EditableRadioChannel(),
            ),
        )

        val request = form.toCreateRequest()

        assertEquals("Neptun", request.name)
        assertEquals(54.35, request.coordinates.lat, 0.0)
        assertEquals(1, request.phoneContacts.size)
        assertEquals("Office", request.phoneContacts.first().label)
        assertEquals("+48581234567", request.phoneContacts.first().phone)
        assertEquals("12", request.phoneContacts.first().extension)
        assertEquals(1, request.radioChannels.size)
        assertEquals(12, request.radioChannels.first().vhfChannel)
        assertNull(request.radioChannels.first().frequencyMhz)
        assertTrue(request.radioChannels.first().isPrimary)
    }

    @Test
    fun toCreateRequestMapsMhzChannel() {
        val form = SpotForm(
            name = "Neptun",
            lat = "54.35",
            lng = "18.65",
            radioChannels = listOf(EditableRadioChannel(name = "WX", kind = RadioChannelKind.Mhz, frequencyMhz = "156.8")),
        )

        val channel = form.toCreateRequest().radioChannels.first()

        assertEquals(156.8, channel.frequencyMhz)
        assertNull(channel.vhfChannel)
    }

    // --- Update diff ---

    @Test
    fun unchangedFormProducesEmptyUpdate() {
        val original = spot(
            phoneContacts = listOf(PhoneContact("c1", "Office", "+48581234567", "12")),
            radioChannels = listOf(RadioChannel("r1", "Port", RadioChannelKind.Vhf, 12, null, true)),
        )

        val request = buildUpdateRequest(original, SpotForm.fromSpot(original))

        assertNull(request.name)
        assertNull(request.coordinates)
        assertNull(request.phoneContacts)
        assertNull(request.radioChannels)
        assertFalse(hasChanges(original, SpotForm.fromSpot(original)))
    }

    @Test
    fun changedNameAndCoordinatesArePopulated() {
        val original = spot(name = "Old", lat = 54.0, lng = 18.0)
        val form = SpotForm.fromSpot(original).copy(name = "New", lat = "55.0", lng = "19.0")

        val request = buildUpdateRequest(original, form)

        assertEquals("New", request.name)
        assertEquals(55.0, request.coordinates?.lat)
        assertEquals(19.0, request.coordinates?.lng)
    }

    @Test
    fun addedContactGoesIntoCreateAndRemovedIntoDelete() {
        val original = spot(phoneContacts = listOf(PhoneContact("c1", null, "+48111", null)))
        val form = SpotForm.fromSpot(original).copy(
            phoneContacts = listOf(EditablePhoneContact(phone = "+48999")), // c1 removed, new added
        )

        val diff = buildUpdateRequest(original, form).phoneContacts!!

        assertEquals(listOf("+48999"), diff.create?.map { it.phone })
        assertEquals(listOf("c1"), diff.delete)
        assertNull(diff.update)
    }

    @Test
    fun editedContactGoesIntoUpdate() {
        val original = spot(phoneContacts = listOf(PhoneContact("c1", "Office", "+48111", null)))
        val form = SpotForm.fromSpot(original).let { f ->
            f.copy(phoneContacts = f.phoneContacts.map { it.copy(phone = "+48222") })
        }

        val diff = buildUpdateRequest(original, form).phoneContacts!!

        assertEquals(1, diff.update?.size)
        assertEquals("c1", diff.update?.first()?.contactId)
        assertEquals("+48222", diff.update?.first()?.phone)
        assertNull(diff.create)
        assertNull(diff.delete)
    }

    @Test
    fun equivalentFrequencyFormattingDoesNotCountAsChange() {
        // Server returns "156.800"; user-visible string parses to the same double.
        val original = spot(radioChannels = listOf(RadioChannel("r1", "WX", RadioChannelKind.Mhz, null, "156.800", false)))
        val form = SpotForm.fromSpot(original).let { f ->
            f.copy(radioChannels = f.radioChannels.map { it.copy(frequencyMhz = "156.8") })
        }

        assertNull(buildUpdateRequest(original, form).radioChannels)
    }

    @Test
    fun togglingPrimaryChannelCountsAsUpdate() {
        val original = spot(radioChannels = listOf(RadioChannel("r1", "Port", RadioChannelKind.Vhf, 12, null, false)))
        val form = SpotForm.fromSpot(original).let { f ->
            f.copy(radioChannels = f.radioChannels.map { it.copy(isPrimary = true) })
        }

        val diff = buildUpdateRequest(original, form).radioChannels!!

        assertEquals("r1", diff.update?.first()?.channelId)
        assertEquals(true, diff.update?.first()?.isPrimary)
    }
}
