package app.skipperclub.ui.main.cruises

import app.skipperclub.data.ChatUser
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseAiDraft
import app.skipperclub.data.CruiseCurrency
import app.skipperclub.data.CruiseListQuery
import app.skipperclub.data.CruiseParticipant
import app.skipperclub.data.CruiseParticipantRole
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseParticipantsPage
import app.skipperclub.data.CruisePayload
import app.skipperclub.data.CruisePort
import app.skipperclub.data.CruiseUser
import app.skipperclub.data.CruiseUserRole
import app.skipperclub.data.CruisesError
import app.skipperclub.data.CruisesPage
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.UserSearchQuery
import app.skipperclub.data.UsersPage
import app.skipperclub.data.VesselType

internal fun testCruise(
    id: String = "c1",
    title: String = "Cruise $id",
    currentUserRole: CruiseUserRole = CruiseUserRole.None,
    currentUserParticipation: CruiseParticipant? = null,
    participantsCount: Int = 0,
    maxParticipants: Int = 6,
): Cruise = Cruise(
    id = id,
    title = title,
    description = "Description for $id",
    departureDate = "2025-07-15",
    departurePort = CruisePort("Split", PostCoordinates(43.5, 16.4)),
    arrivalDate = "2025-07-22",
    arrivalPort = CruisePort("Dubrovnik", PostCoordinates(42.6, 18.0)),
    costPerPerson = 850.0,
    currency = CruiseCurrency.Eur,
    maxParticipants = maxParticipants,
    participantsCount = participantsCount,
    vessel = "Bavaria 46",
    vesselType = VesselType.SailingYacht,
    organizer = CruiseUser(id = "org", name = "Organizer"),
    currentUserRole = currentUserRole,
    currentUserParticipation = currentUserParticipation,
    createdAt = "2025-06-01T10:00:00Z",
    updatedAt = "2025-06-01T10:00:00Z",
)

internal fun testParticipant(
    id: String,
    userId: String = "u-$id",
    role: CruiseParticipantRole = CruiseParticipantRole.Participant,
    state: CruiseParticipantState = CruiseParticipantState.Pending,
): CruiseParticipant = CruiseParticipant(
    id = id,
    cruiseId = "c1",
    userId = userId,
    user = CruiseUser(id = userId, name = "User $userId"),
    role = role,
    state = state,
    createdAt = "2025-06-01T10:00:00Z",
    updatedAt = "2025-06-01T10:00:00Z",
)

internal fun cruisesPage(cruises: List<Cruise>, total: Int = cruises.size, offset: Int = 0) =
    CruisesPage(cruises = cruises, total = total, limit = 20, offset = offset)

/** Configurable in-memory [CruisesGateway] that records calls for assertions. */
internal class FakeCruisesGateway : CruisesGateway {
    var cruisePages: List<CruisesPage> = listOf(cruisesPage(emptyList()))
    var listError: CruisesError? = null
    val listQueries = mutableListOf<CruiseListQuery>()
    private var listCallCount = 0

    var cruise: Cruise = testCruise()
    var getError: CruisesError? = null

    var createdCruise: Cruise = testCruise(id = "created")
    val createPayloads = mutableListOf<CruisePayload>()

    var aiDraftResult: CruiseAiDraft = CruiseAiDraft()
    var aiDraftError: CruisesError? = null
    val aiDraftDescriptions = mutableListOf<String>()
    var updatedCruise: Cruise = testCruise(id = "c1", title = "Updated")
    val updatePayloads = mutableListOf<CruisePayload>()

    var participantsResult: CruiseParticipantsPage =
        CruiseParticipantsPage(emptyList(), total = 0, limit = 100, offset = 0)

    var mutationError: CruisesError? = null

    var usersPage: UsersPage = UsersPage(emptyList(), total = 0, limit = 20, offset = 0)
    var locations: List<GeocodedLocation> = emptyList()

    val calls = mutableListOf<String>()

    override suspend fun list(accessToken: String, query: CruiseListQuery): CruisesPage {
        calls += "list"
        listQueries += query
        listError?.let { throw it }
        val page = cruisePages[minOf(listCallCount, cruisePages.lastIndex)]
        listCallCount++
        return page
    }

    override suspend fun get(accessToken: String, cruiseId: String): Cruise {
        calls += "get:$cruiseId"
        getError?.let { throw it }
        return cruise
    }

    override suspend fun create(accessToken: String, payload: CruisePayload): Cruise {
        calls += "create"
        createPayloads += payload
        mutationError?.let { throw it }
        return createdCruise
    }

    override suspend fun aiDraft(accessToken: String, description: String): CruiseAiDraft {
        calls += "aiDraft"
        aiDraftDescriptions += description
        aiDraftError?.let { throw it }
        return aiDraftResult
    }

    override suspend fun update(accessToken: String, cruiseId: String, payload: CruisePayload): Cruise {
        calls += "update:$cruiseId"
        updatePayloads += payload
        mutationError?.let { throw it }
        return updatedCruise
    }

    override suspend fun delete(accessToken: String, cruiseId: String) {
        calls += "delete:$cruiseId"
        mutationError?.let { throw it }
    }

    override suspend fun participants(accessToken: String, cruiseId: String): CruiseParticipantsPage {
        calls += "participants:$cruiseId"
        return participantsResult
    }

    override suspend fun addParticipant(
        accessToken: String,
        cruiseId: String,
        userId: String,
    ): CruiseParticipant {
        calls += "addParticipant:$cruiseId:$userId"
        mutationError?.let { throw it }
        return testParticipant("new", userId = userId, state = CruiseParticipantState.Pending)
    }

    override suspend fun updateParticipantState(
        accessToken: String,
        cruiseId: String,
        participantId: String,
        state: CruiseParticipantState,
    ): CruiseParticipant {
        calls += "updateParticipantState:$participantId:${state.wireValue}"
        mutationError?.let { throw it }
        return testParticipant(participantId, state = state)
    }

    override suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage {
        calls += "searchUsers:${query.search.orEmpty()}"
        return usersPage
    }

    override suspend fun searchLocations(accessToken: String, query: String): List<GeocodedLocation> {
        calls += "searchLocations:$query"
        return locations
    }
}
