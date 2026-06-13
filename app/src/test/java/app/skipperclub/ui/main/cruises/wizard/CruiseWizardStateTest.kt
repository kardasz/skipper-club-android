package app.skipperclub.ui.main.cruises.wizard

import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.VesselType
import app.skipperclub.ui.main.cruises.FakeCruisesGateway
import app.skipperclub.ui.main.cruises.testCruise
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CruiseWizardStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeCruisesGateway()
    private val events = mutableListOf<CruiseWizardEvent>()

    private fun wizard(existing: app.skipperclub.data.Cruise? = null, token: String? = "token"): CruiseWizardState {
        val state = CruiseWizardState(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            portSearchDebounceMillis = 0,
            existing = existing,
        )
        scope.launch { state.events.collect { events += it } }
        return state
    }

    private val split = GeocodedLocation("Split", "Split, Croatia", PostCoordinates(43.5, 16.4))
    private val dubrovnik = GeocodedLocation("Dubrovnik", "Dubrovnik, Croatia", PostCoordinates(42.6, 18.0))

    private fun fillValid(state: CruiseWizardState) {
        state.updateTitle("Adriatic Summer")
        state.updateDescription("A relaxed week along the Croatian coast.")
        state.selectPort(CruisePortTarget.Departure, split)
        state.selectPort(CruisePortTarget.Arrival, dubrovnik)
        state.selectDepartureDate(LocalDate.of(2025, 7, 15))
        state.selectArrivalDate(LocalDate.of(2025, 7, 22))
        state.updateVessel("Bavaria Cruiser 46")
        state.selectVesselType(VesselType.SailingYacht)
        state.updateCost("850")
        state.selectCurrency(CruiseCurrency.Eur)
        state.updateMaxParticipants("6")
    }

    @Test
    fun basicsStepRequiresTitleAndDescription() {
        val state = wizard()

        state.next()

        assertEquals(CruiseWizardStep.Basics, state.step)
        assertTrue(CruiseWizardError.TitleTooShort in state.visibleErrors)
        assertTrue(CruiseWizardError.DescriptionTooShort in state.visibleErrors)
    }

    @Test
    fun validBasicsAdvancesToRoute() {
        val state = wizard()
        state.updateTitle("Adriatic Summer")
        state.updateDescription("A relaxed week along the coast.")

        state.next()

        assertEquals(CruiseWizardStep.Route, state.step)
    }

    @Test
    fun routeStepRequiresPortsAndValidDates() {
        val state = wizard()
        state.updateTitle("Adriatic Summer")
        state.updateDescription("A relaxed week along the coast.")
        state.next()

        state.next()

        assertEquals(CruiseWizardStep.Route, state.step)
        assertTrue(CruiseWizardError.DeparturePortRequired in state.visibleErrors)
        assertTrue(CruiseWizardError.ArrivalPortRequired in state.visibleErrors)
        assertTrue(CruiseWizardError.DatesInvalid in state.visibleErrors)
    }

    @Test
    fun arrivalBeforeDepartureIsInvalid() {
        val state = wizard()
        state.selectPort(CruisePortTarget.Departure, split)
        state.selectPort(CruisePortTarget.Arrival, dubrovnik)
        state.selectDepartureDate(LocalDate.of(2025, 7, 22))
        state.selectArrivalDate(LocalDate.of(2025, 7, 15))

        assertTrue(CruiseWizardError.DatesInvalid in state.errorsFor(CruiseWizardStep.Route))
    }

    @Test
    fun stopsAreAddedAndRemoved() {
        val state = wizard()
        state.selectPort(CruisePortTarget.Stop, split)
        state.selectPort(CruisePortTarget.Stop, dubrovnik)
        assertEquals(2, state.stops.size)

        state.removeStop(0)
        assertEquals(listOf("Dubrovnik"), state.stops.map { it.name })
    }

    @Test
    fun buildPayloadMapsAllFields() {
        val state = wizard()
        fillValid(state)
        state.selectPort(CruisePortTarget.Stop, split)
        state.updatePrivate(true)
        state.updateSmokingAllowed(false)

        val payload = state.buildPayload()!!

        assertEquals("Adriatic Summer", payload.title)
        assertEquals("2025-07-15", payload.departureDate)
        assertEquals("2025-07-22", payload.arrivalDate)
        assertEquals("Split", payload.departurePort.name)
        assertEquals("Dubrovnik", payload.arrivalPort.name)
        assertEquals(1, payload.stops?.size)
        assertEquals(850.0, payload.costPerPerson, 0.0)
        assertEquals("EUR", payload.currency)
        assertEquals(6, payload.maxParticipants)
        assertEquals("SAILING_YACHT", payload.vesselType)
        assertTrue(payload.isPrivate)
        assertFalse(payload.smokingAllowed!!)
    }

    @Test
    fun publishCreatesWhenNoExistingCruise() {
        gateway.createdCruise = testCruise("created")
        val state = wizard()
        fillValid(state)

        state.publish()

        assertTrue(gateway.calls.contains("create"))
        assertTrue(events.any { it is CruiseWizardEvent.Published })
    }

    @Test
    fun publishUpdatesWhenEditingExistingCruise() {
        val existing = testCruise("c1")
        gateway.updatedCruise = testCruise("c1", title = "Updated")
        val state = wizard(existing = existing)
        fillValid(state)

        state.publish()

        assertTrue(gateway.calls.contains("update:c1"))
        assertTrue(events.any { it is CruiseWizardEvent.Published })
    }

    @Test
    fun publishWithInvalidDataSurfacesErrorsAndDoesNotCallGateway() {
        val state = wizard()

        state.publish()

        assertFalse(gateway.calls.contains("create"))
        assertTrue(state.visibleErrors.isNotEmpty())
    }

    @Test
    fun editingPrefillsFieldsFromExistingCruise() {
        val existing = testCruise("c1", title = "Original")
        val state = wizard(existing = existing)

        assertEquals("Original", state.title)
        assertTrue(state.isEditing)
        assertEquals("Split", state.departurePort?.name)
    }

    @Test
    fun missingTokenOnPublishEmitsSessionExpired() {
        val state = wizard(token = null)
        fillValid(state)

        state.publish()

        assertNull(events.firstOrNull { it is CruiseWizardEvent.Published })
        assertTrue(events.contains(CruiseWizardEvent.SessionExpired))
    }
}
