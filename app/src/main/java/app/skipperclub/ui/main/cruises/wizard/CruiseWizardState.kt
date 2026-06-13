package app.skipperclub.ui.main.cruises.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruisePayload
import app.skipperclub.data.CruisePort
import app.skipperclub.data.CruisePortDto
import app.skipperclub.data.CruiseType
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.VesselType
import app.skipperclub.ui.main.cruises.CruisesGateway
import app.skipperclub.ui.main.cruises.RealCruisesGateway
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

const val CRUISE_TITLE_MIN_LENGTH = 3
const val CRUISE_TITLE_MAX_LENGTH = 255
const val CRUISE_DESCRIPTION_MIN_LENGTH = 10
const val CRUISE_DESCRIPTION_MAX_LENGTH = 2000
const val CRUISE_VESSEL_MIN_LENGTH = 5
const val CRUISE_STOPS_MAX_COUNT = 20
const val CRUISE_MAX_PARTICIPANTS_LIMIT = 50
const val CRUISE_COST_LIMIT = 100_000.0

enum class CruiseWizardStep { Basics, Route, Vessel, Crew, Summary }

/** Validation problems surfaced under the relevant field / step. */
enum class CruiseWizardError {
    TitleTooShort,
    DescriptionTooShort,
    DeparturePortRequired,
    ArrivalPortRequired,
    DatesInvalid,
    VesselNameTooShort,
    VesselTypeRequired,
    CostInvalid,
    MaxParticipantsInvalid,
}

sealed interface CruiseWizardEvent {
    data class Published(val cruise: Cruise) : CruiseWizardEvent
    data class PublishFailed(val error: Exception) : CruiseWizardEvent
    data object SessionExpired : CruiseWizardEvent
}

/** Which field a geocoder search result should land in. */
enum class CruisePortTarget { Departure, Arrival, Stop }

/**
 * State machine for the cruise create/edit wizard. Pure Kotlin + Compose snapshot
 * state (no Android types) so step flow, validation and request building are
 * unit-testable on the JVM with a fake [CruisesGateway]. Pass [existing] to edit
 * a cruise instead of creating one.
 */
class CruiseWizardState(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: CruisesGateway = RealCruisesGateway,
    private val portSearchDebounceMillis: Long = 350,
    val existing: Cruise? = null,
) {
    val isEditing: Boolean
        get() = existing != null

    var step by mutableStateOf(CruiseWizardStep.Basics)
        private set

    var title by mutableStateOf(existing?.title.orEmpty())
        private set
    var description by mutableStateOf(existing?.description.orEmpty())
        private set
    var type by mutableStateOf(existing?.type)
        private set

    var departurePort by mutableStateOf(existing?.departurePort)
        private set
    var arrivalPort by mutableStateOf(existing?.arrivalPort)
        private set
    var departureDate by mutableStateOf(existing?.departureDate?.toLocalDateOrNull())
        private set
    var arrivalDate by mutableStateOf(existing?.arrivalDate?.toLocalDateOrNull())
        private set
    val stops = mutableStateListOf<CruisePort>().apply { existing?.stops?.let { addAll(it) } }

    var portQuery by mutableStateOf("")
        private set
    var portResults by mutableStateOf<List<GeocodedLocation>>(emptyList())
        private set
    var isSearchingPorts by mutableStateOf(false)
        private set

    var vessel by mutableStateOf(existing?.vessel.orEmpty())
        private set
    var vesselType by mutableStateOf(existing?.vesselType)
        private set
    var vesselBrand by mutableStateOf(existing?.vesselBrand.orEmpty())
        private set
    var vesselModel by mutableStateOf(existing?.vesselModel.orEmpty())
        private set
    var vesselYearText by mutableStateOf(existing?.vesselYear?.toString().orEmpty())
        private set
    var vesselLengthText by mutableStateOf(existing?.vesselLength?.toString().orEmpty())
        private set
    var vesselCabinsText by mutableStateOf(existing?.vesselCabins?.toString().orEmpty())
        private set
    var requiredSkills by mutableStateOf(existing?.requiredSkills.orEmpty())
        private set

    var costText by mutableStateOf(
        existing?.costPerPerson?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty(),
    )
        private set
    var currency by mutableStateOf(existing?.currency ?: CruiseCurrency.Eur)
        private set
    var maxParticipantsText by mutableStateOf(existing?.maxParticipants?.toString() ?: "8")
        private set
    var isPrivate by mutableStateOf(existing?.isPrivate ?: false)
        private set
    var smokingAllowed by mutableStateOf(existing?.smokingAllowed)
        private set
    var alcoholAllowed by mutableStateOf(existing?.alcoholAllowed)
        private set
    var petsAllowed by mutableStateOf(existing?.petsAllowed)
        private set
    var childrenAllowed by mutableStateOf(existing?.childrenAllowed)
        private set

    var isPublishing by mutableStateOf(false)
        private set

    /** Set after a failed Next tap so fields can highlight what is missing. */
    var visibleErrors by mutableStateOf<Set<CruiseWizardError>>(emptySet())
        private set

    private val _events = MutableSharedFlow<CruiseWizardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CruiseWizardEvent> = _events.asSharedFlow()

    private var portSearchJob: Job? = null

    val steps: List<CruiseWizardStep> = CruiseWizardStep.entries

    val stepIndex: Int
        get() = steps.indexOf(step).coerceAtLeast(0)

    fun updateTitle(value: String) {
        title = value.take(CRUISE_TITLE_MAX_LENGTH)
        if (value.trim().length >= CRUISE_TITLE_MIN_LENGTH) {
            visibleErrors = visibleErrors - CruiseWizardError.TitleTooShort
        }
    }

    fun updateDescription(value: String) {
        description = value.take(CRUISE_DESCRIPTION_MAX_LENGTH)
        if (value.trim().length >= CRUISE_DESCRIPTION_MIN_LENGTH) {
            visibleErrors = visibleErrors - CruiseWizardError.DescriptionTooShort
        }
    }

    fun selectType(value: CruiseType?) {
        type = value
    }

    fun updatePortQuery(value: String) {
        portQuery = value
        portSearchJob?.cancel()
        if (value.trim().length < 3) {
            portResults = emptyList()
            isSearchingPorts = false
            return
        }
        isSearchingPorts = true
        portSearchJob = scope.launch {
            delay(portSearchDebounceMillis)
            val token = runCatching { accessToken() }.getOrNull() ?: run {
                isSearchingPorts = false
                _events.tryEmit(CruiseWizardEvent.SessionExpired)
                return@launch
            }
            try {
                portResults = gateway.searchLocations(token, value.trim())
            } catch (_: Exception) {
                portResults = emptyList()
            }
            isSearchingPorts = false
        }
    }

    fun clearPortSearch() {
        portSearchJob?.cancel()
        portQuery = ""
        portResults = emptyList()
        isSearchingPorts = false
    }

    fun selectPort(target: CruisePortTarget, location: GeocodedLocation) {
        val port = CruisePort(name = location.displayName, coordinates = location.coordinates)
        when (target) {
            CruisePortTarget.Departure -> {
                departurePort = port
                visibleErrors = visibleErrors - CruiseWizardError.DeparturePortRequired
            }

            CruisePortTarget.Arrival -> {
                arrivalPort = port
                visibleErrors = visibleErrors - CruiseWizardError.ArrivalPortRequired
            }

            CruisePortTarget.Stop -> {
                if (stops.size < CRUISE_STOPS_MAX_COUNT) stops.add(port)
            }
        }
        clearPortSearch()
    }

    fun removeStop(index: Int) {
        if (index in stops.indices) stops.removeAt(index)
    }

    fun selectDepartureDate(date: LocalDate) {
        departureDate = date
        if (arrivalDate?.isBefore(date) != true) {
            visibleErrors = visibleErrors - CruiseWizardError.DatesInvalid
        }
    }

    fun selectArrivalDate(date: LocalDate) {
        arrivalDate = date
        if (departureDate?.isAfter(date) != true) {
            visibleErrors = visibleErrors - CruiseWizardError.DatesInvalid
        }
    }

    fun updateVessel(value: String) {
        vessel = value.take(CRUISE_TITLE_MAX_LENGTH)
        if (value.trim().length >= CRUISE_VESSEL_MIN_LENGTH) {
            visibleErrors = visibleErrors - CruiseWizardError.VesselNameTooShort
        }
    }

    fun selectVesselType(value: VesselType) {
        vesselType = value
        visibleErrors = visibleErrors - CruiseWizardError.VesselTypeRequired
    }

    fun updateVesselBrand(value: String) {
        vesselBrand = value.take(100)
    }

    fun updateVesselModel(value: String) {
        vesselModel = value.take(100)
    }

    fun updateVesselYear(value: String) {
        vesselYearText = value.filter { it.isDigit() }.take(4)
    }

    fun updateVesselLength(value: String) {
        vesselLengthText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
    }

    fun updateVesselCabins(value: String) {
        vesselCabinsText = value.filter { it.isDigit() }.take(2)
    }

    fun updateRequiredSkills(value: String) {
        requiredSkills = value.take(1000)
    }

    fun updateCost(value: String) {
        costText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(9)
        if (parsedCost() != null) visibleErrors = visibleErrors - CruiseWizardError.CostInvalid
    }

    fun selectCurrency(value: CruiseCurrency) {
        currency = value
    }

    fun updateMaxParticipants(value: String) {
        maxParticipantsText = value.filter { it.isDigit() }.take(2)
        if (parsedMaxParticipants() != null) {
            visibleErrors = visibleErrors - CruiseWizardError.MaxParticipantsInvalid
        }
    }

    fun updatePrivate(value: Boolean) {
        isPrivate = value
    }

    fun updateSmokingAllowed(value: Boolean?) {
        smokingAllowed = value
    }

    fun updateAlcoholAllowed(value: Boolean?) {
        alcoholAllowed = value
    }

    fun updatePetsAllowed(value: Boolean?) {
        petsAllowed = value
    }

    fun updateChildrenAllowed(value: Boolean?) {
        childrenAllowed = value
    }

    private fun parsedCost(): Double? =
        costText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..CRUISE_COST_LIMIT }

    private fun parsedMaxParticipants(): Int? =
        maxParticipantsText.toIntOrNull()?.takeIf { it in 1..CRUISE_MAX_PARTICIPANTS_LIMIT }

    /** Validation errors blocking the given step's Next action. */
    fun errorsFor(step: CruiseWizardStep): Set<CruiseWizardError> =
        when (step) {
            CruiseWizardStep.Basics -> buildSet {
                if (title.trim().length < CRUISE_TITLE_MIN_LENGTH) add(CruiseWizardError.TitleTooShort)
                if (description.trim().length < CRUISE_DESCRIPTION_MIN_LENGTH) {
                    add(CruiseWizardError.DescriptionTooShort)
                }
            }

            CruiseWizardStep.Route -> buildSet {
                if (departurePort == null) add(CruiseWizardError.DeparturePortRequired)
                if (arrivalPort == null) add(CruiseWizardError.ArrivalPortRequired)
                val departure = departureDate
                val arrival = arrivalDate
                if (departure == null || arrival == null || arrival.isBefore(departure)) {
                    add(CruiseWizardError.DatesInvalid)
                }
            }

            CruiseWizardStep.Vessel -> buildSet {
                if (vessel.trim().length < CRUISE_VESSEL_MIN_LENGTH) {
                    add(CruiseWizardError.VesselNameTooShort)
                }
                if (vesselType == null) add(CruiseWizardError.VesselTypeRequired)
            }

            CruiseWizardStep.Crew -> buildSet {
                if (parsedCost() == null) add(CruiseWizardError.CostInvalid)
                if (parsedMaxParticipants() == null) add(CruiseWizardError.MaxParticipantsInvalid)
            }

            CruiseWizardStep.Summary -> emptySet()
        }

    val canGoNext: Boolean
        get() = if (step == CruiseWizardStep.Summary) !isPublishing else true

    /** Advances if the current step validates; otherwise surfaces the errors. */
    fun next() {
        val errors = errorsFor(step)
        if (errors.isNotEmpty()) {
            visibleErrors = errors
            return
        }
        visibleErrors = emptySet()
        val index = steps.indexOf(step)
        if (index < steps.lastIndex) {
            step = steps[index + 1]
        }
    }

    /** Returns false when already at the first step (caller should close). */
    fun back(): Boolean {
        val index = steps.indexOf(step)
        if (index <= 0) return false
        visibleErrors = emptySet()
        step = steps[index - 1]
        return true
    }

    val hasUserInput: Boolean
        get() = !isEditing &&
            (
                title.isNotBlank() || description.isNotBlank() || departurePort != null ||
                    arrivalPort != null || vessel.isNotBlank()
                )

    internal fun buildPayload(): CruisePayload? {
        val departure = departurePort ?: return null
        val arrival = arrivalPort ?: return null
        val departureDay = departureDate ?: return null
        val arrivalDay = arrivalDate ?: return null
        val cost = parsedCost() ?: return null
        val crewLimit = parsedMaxParticipants() ?: return null
        val vesselTypeValue = vesselType ?: return null
        return CruisePayload(
            title = title.trim(),
            description = description.trim(),
            departureDate = departureDay.toString(),
            departurePort = CruisePortDto.from(departure),
            arrivalDate = arrivalDay.toString(),
            arrivalPort = CruisePortDto.from(arrival),
            stops = stops.map { CruisePortDto.from(it) }.takeIf { it.isNotEmpty() },
            requiredSkills = requiredSkills.trim().takeIf { it.length >= 5 },
            costPerPerson = cost,
            currency = currency.wireValue,
            maxParticipants = crewLimit,
            isPrivate = isPrivate,
            vessel = vessel.trim(),
            vesselBrand = vesselBrand.trim().takeIf { it.length >= 2 },
            vesselModel = vesselModel.trim().takeIf { it.length >= 2 },
            vesselYear = vesselYearText.toIntOrNull()?.takeIf { it in 1950..2030 },
            vesselLength = vesselLengthText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 15.0..200.0 },
            vesselCabins = vesselCabinsText.toIntOrNull()?.takeIf { it in 1..20 },
            vesselType = vesselTypeValue.wireValue,
            type = type?.wireValue,
            smokingAllowed = smokingAllowed,
            alcoholAllowed = alcoholAllowed,
            petsAllowed = petsAllowed,
            childrenAllowed = childrenAllowed,
        )
    }

    fun publish() {
        if (isPublishing) return
        val allErrors = steps.flatMap { errorsFor(it) }.toSet()
        if (allErrors.isNotEmpty()) {
            visibleErrors = allErrors
            return
        }
        val payload = buildPayload() ?: return
        isPublishing = true
        scope.launch {
            val token = runCatching { accessToken() }.getOrNull() ?: run {
                isPublishing = false
                _events.tryEmit(CruiseWizardEvent.SessionExpired)
                return@launch
            }
            try {
                val cruise = existing?.let { gateway.update(token, it.id, payload) }
                    ?: gateway.create(token, payload)
                isPublishing = false
                _events.tryEmit(CruiseWizardEvent.Published(cruise))
            } catch (error: Exception) {
                isPublishing = false
                _events.tryEmit(CruiseWizardEvent.PublishFailed(error))
            }
        }
    }
}

/** Backend sends dates as `YYYY-MM-DD` (sometimes with a time suffix). */
private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(take(10)) }.getOrNull()
