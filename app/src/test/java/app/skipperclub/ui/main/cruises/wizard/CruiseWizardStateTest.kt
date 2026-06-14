package app.skipperclub.ui.main.cruises.wizard

import app.skipperclub.data.CruiseAiDraft
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruisePort
import app.skipperclub.data.CruiseType
import app.skipperclub.data.CruisesError
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

    /** Pinned "today" so the future-departure rule is deterministic; sample dates are in 2025-07. */
    private val today = LocalDate.of(2025, 1, 1)

    private fun wizard(existing: app.skipperclub.data.Cruise? = null, token: String? = "token"): CruiseWizardState {
        val state = CruiseWizardState(
            scope = scope,
            accessToken = { token },
            gateway = gateway,
            portSearchDebounceMillis = 0,
            existing = existing,
            today = { today },
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

    /** Create-mode wizards open on the AI-draft step; skip it to reach Basics. */
    private fun wizardAtBasics(): CruiseWizardState = wizard().also { it.next() }

    @Test
    fun createWizardStartsOnAiDraftStep() {
        val state = wizard()

        assertEquals(CruiseWizardStep.AiDraft, state.step)
        assertEquals(CruiseWizardStep.AiDraft, state.steps.first())
    }

    @Test
    fun editWizardSkipsAiDraftStep() {
        val state = wizard(existing = testCruise("c1"))

        assertEquals(CruiseWizardStep.Basics, state.step)
        assertFalse(CruiseWizardStep.AiDraft in state.steps)
    }

    @Test
    fun skippingAiDraftAdvancesToBasics() {
        val state = wizard()

        state.next()

        assertEquals(CruiseWizardStep.Basics, state.step)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun basicsStepRequiresTitleAndDescription() {
        val state = wizardAtBasics()

        state.next()

        assertEquals(CruiseWizardStep.Basics, state.step)
        assertTrue(CruiseWizardError.TitleTooShort in state.visibleErrors)
        assertTrue(CruiseWizardError.DescriptionTooShort in state.visibleErrors)
    }

    @Test
    fun validBasicsAdvancesToRoute() {
        val state = wizardAtBasics()
        state.updateTitle("Adriatic Summer")
        state.updateDescription("A relaxed week along the coast.")

        state.next()

        assertEquals(CruiseWizardStep.Route, state.step)
    }

    @Test
    fun routeStepRequiresPortsAndValidDates() {
        val state = wizardAtBasics()
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
    fun generateDraftAppliesFieldsAndAdvances() {
        gateway.aiDraftResult = CruiseAiDraft(
            title = "Croatian Adventure",
            description = "A relaxed week in Croatia.",
            departureDate = "2025-07-15",
            departurePort = CruisePort("Split", PostCoordinates(43.5, 16.4)),
            arrivalDate = "2025-07-22",
            arrivalPort = CruisePort("Dubrovnik", PostCoordinates(42.6, 18.0)),
            stops = listOf(CruisePort("Hvar", PostCoordinates(43.17, 16.44))),
            costPerPerson = 1500.0,
            currency = CruiseCurrency.Eur,
            vessel = "Bavaria 46",
            vesselType = VesselType.SailingYacht,
            vesselCabins = 4,
            maxParticipants = 8,
            type = CruiseType.Relax,
        )
        val state = wizard()
        state.updateAiDescription("Week-long relaxed sailing in Croatia from Split, Bavaria 46.")

        state.generateDraft()

        assertTrue(gateway.calls.contains("aiDraft"))
        assertEquals(CruiseWizardStep.Basics, state.step)
        assertEquals("Croatian Adventure", state.title)
        assertEquals("Split", state.departurePort?.name)
        assertEquals("Dubrovnik", state.arrivalPort?.name)
        assertEquals(LocalDate.of(2025, 7, 15), state.departureDate)
        assertEquals(listOf("Hvar"), state.stops.map { it.name })
        assertEquals("1500", state.costText)
        assertEquals("Bavaria 46", state.vessel)
        assertEquals(VesselType.SailingYacht, state.vesselType)
        assertEquals("4", state.vesselCabinsText)
        assertEquals("8", state.maxParticipantsText)
        assertEquals(CruiseType.Relax, state.type)
        assertTrue(events.contains(CruiseWizardEvent.DraftGenerated))
    }

    @Test
    fun generateDraftIsBlockedUntilDescriptionLongEnough() {
        val state = wizard()
        state.updateAiDescription("short")

        assertFalse(state.canGenerateDraft)
        state.generateDraft()

        assertFalse(gateway.calls.contains("aiDraft"))
        assertEquals(CruiseWizardStep.AiDraft, state.step)
    }

    @Test
    fun generateDraftFailureKeepsStepAndEmitsEvent() {
        gateway.aiDraftError = CruisesError.Server(500, "boom")
        val state = wizard()
        state.updateAiDescription("A relaxed week along the Croatian coast from Split.")

        state.generateDraft()

        assertEquals(CruiseWizardStep.AiDraft, state.step)
        assertTrue(events.any { it is CruiseWizardEvent.DraftFailed })
    }

    @Test
    fun missingTokenOnGenerateEmitsSessionExpired() {
        val state = wizard(token = null)
        state.updateAiDescription("A relaxed week along the Croatian coast from Split.")

        state.generateDraft()

        assertFalse(gateway.calls.contains("aiDraft"))
        assertTrue(events.contains(CruiseWizardEvent.SessionExpired))
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
    fun departureOnTodayIsRejectedAsNotInFuture() {
        val state = wizard()
        state.selectPort(CruisePortTarget.Departure, split)
        state.selectPort(CruisePortTarget.Arrival, dubrovnik)
        state.selectDepartureDate(today)
        state.selectArrivalDate(today.plusDays(7))

        val errors = state.errorsFor(CruiseWizardStep.Route)
        assertTrue(CruiseWizardError.DepartureNotInFuture in errors)
        assertFalse(CruiseWizardError.DatesInvalid in errors)
    }

    @Test
    fun routeStepDoesNotAdvanceWhenDepartureNotInFuture() {
        val state = wizardAtBasics()
        state.updateTitle("Adriatic Summer")
        state.updateDescription("A relaxed week along the coast.")
        state.next()
        state.selectPort(CruisePortTarget.Departure, split)
        state.selectPort(CruisePortTarget.Arrival, dubrovnik)
        state.selectDepartureDate(today)
        state.selectArrivalDate(today.plusDays(7))

        state.next()

        assertEquals(CruiseWizardStep.Route, state.step)
        assertTrue(CruiseWizardError.DepartureNotInFuture in state.visibleErrors)
    }

    @Test
    fun editingDepartureToFutureClearsTheVisibleError() {
        val state = wizardAtBasics()
        state.updateTitle("Adriatic Summer")
        state.updateDescription("A relaxed week along the coast.")
        state.next()
        state.selectPort(CruisePortTarget.Departure, split)
        state.selectPort(CruisePortTarget.Arrival, dubrovnik)
        state.selectDepartureDate(today)
        state.selectArrivalDate(today.plusDays(7))
        state.next() // stays on Route, surfaces DepartureNotInFuture
        assertTrue(CruiseWizardError.DepartureNotInFuture in state.visibleErrors)

        // Re-pick a future departure; the date error clears without another Next tap.
        state.selectDepartureDate(LocalDate.of(2025, 7, 15))

        assertFalse(CruiseWizardError.DepartureNotInFuture in state.visibleErrors)
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
    fun publishJumpsToFirstStepWithAnError() {
        // Everything valid except a past departure date, which the Route step owns.
        val state = wizard()
        fillValid(state)
        state.selectDepartureDate(today.minusDays(1))
        state.selectArrivalDate(today.plusDays(7))

        state.publish()

        assertFalse(gateway.calls.contains("create"))
        assertEquals(CruiseWizardStep.Route, state.step)
        assertTrue(CruiseWizardError.DepartureNotInFuture in state.visibleErrors)
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
