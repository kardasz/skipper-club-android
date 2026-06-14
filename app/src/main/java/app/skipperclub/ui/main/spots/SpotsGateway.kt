package app.skipperclub.ui.main.spots

import app.skipperclub.data.CreateSpotRequest
import app.skipperclub.data.Spot
import app.skipperclub.data.SpotListQuery
import app.skipperclub.data.SpotsApi
import app.skipperclub.data.SpotsPage
import app.skipperclub.data.UpdateSpotAggregateRequest

/**
 * Seam between the spots UI controller and [SpotsApi] so the state-machine logic
 * stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface SpotsGateway {
    suspend fun list(accessToken: String, query: SpotListQuery): SpotsPage
    suspend fun create(accessToken: String, body: CreateSpotRequest): Spot
    suspend fun update(accessToken: String, spotId: String, body: UpdateSpotAggregateRequest): Spot
    suspend fun delete(accessToken: String, spotId: String)
}

object RealSpotsGateway : SpotsGateway {
    override suspend fun list(accessToken: String, query: SpotListQuery): SpotsPage =
        SpotsApi.list(accessToken, query)

    override suspend fun create(accessToken: String, body: CreateSpotRequest): Spot =
        SpotsApi.create(accessToken, body)

    override suspend fun update(accessToken: String, spotId: String, body: UpdateSpotAggregateRequest): Spot =
        SpotsApi.update(accessToken, spotId, body)

    override suspend fun delete(accessToken: String, spotId: String) =
        SpotsApi.delete(accessToken, spotId)
}
