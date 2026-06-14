package app.skipperclub.ui.main.spots

import app.skipperclub.data.Coordinates
import app.skipperclub.data.CreateSpotRequest
import app.skipperclub.data.PhoneContact
import app.skipperclub.data.RadioChannel
import app.skipperclub.data.Spot
import app.skipperclub.data.SpotListQuery
import app.skipperclub.data.SpotsError
import app.skipperclub.data.SpotsPage
import app.skipperclub.data.UpdateSpotAggregateRequest

internal fun testSpot(
    id: String,
    name: String = "Spot $id",
    lat: Double = 54.0,
    lng: Double = 18.0,
    phoneContacts: List<PhoneContact> = emptyList(),
    radioChannels: List<RadioChannel> = emptyList(),
) = Spot(
    id = id,
    name = name,
    coordinates = Coordinates(lat, lng),
    phoneContacts = phoneContacts,
    radioChannels = radioChannels,
    createdAt = "2026-06-10T09:00:00Z",
    updatedAt = "2026-06-10T09:00:00Z",
)

internal fun spotsPage(
    spots: List<Spot>,
    total: Int = spots.size,
    offset: Int = 0,
) = SpotsPage(spots = spots, total = total, limit = 20, offset = offset)

/** Configurable in-memory [SpotsGateway]; records calls for assertions. */
internal class FakeSpotsGateway : SpotsGateway {
    var pages: List<SpotsPage> = listOf(spotsPage(emptyList()))
    var listError: SpotsError? = null
    var mutationError: SpotsError? = null
    val listQueries = mutableListOf<SpotListQuery>()
    val calls = mutableListOf<String>()
    var lastCreate: CreateSpotRequest? = null
    var lastUpdate: Pair<String, UpdateSpotAggregateRequest>? = null

    private var listCallCount = 0

    override suspend fun list(accessToken: String, query: SpotListQuery): SpotsPage {
        calls += "list"
        listQueries += query
        listError?.let { throw it }
        val page = pages[minOf(listCallCount, pages.lastIndex)]
        listCallCount++
        return page
    }

    override suspend fun create(accessToken: String, body: CreateSpotRequest): Spot {
        calls += "create:${body.name}"
        lastCreate = body
        mutationError?.let { throw it }
        return testSpot("new", name = body.name, lat = body.coordinates.lat, lng = body.coordinates.lng)
    }

    override suspend fun update(accessToken: String, spotId: String, body: UpdateSpotAggregateRequest): Spot {
        calls += "update:$spotId"
        lastUpdate = spotId to body
        mutationError?.let { throw it }
        return testSpot(spotId, name = body.name ?: "Spot $spotId")
    }

    override suspend fun delete(accessToken: String, spotId: String) {
        calls += "delete:$spotId"
        mutationError?.let { throw it }
    }
}
