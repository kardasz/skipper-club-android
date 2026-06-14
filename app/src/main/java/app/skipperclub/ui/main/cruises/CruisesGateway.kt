package app.skipperclub.ui.main.cruises

import app.skipperclub.data.ChatsApi
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseAiDraft
import app.skipperclub.data.CruiseListQuery
import app.skipperclub.data.CruiseParticipant
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseParticipantsPage
import app.skipperclub.data.CruisePayload
import app.skipperclub.data.CruisesApi
import app.skipperclub.data.CruisesPage
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.GeocoderApi
import app.skipperclub.data.MediaUploadApi
import app.skipperclub.data.MediaUploadMeta
import app.skipperclub.data.UserSearchQuery
import app.skipperclub.data.UsersPage
import app.skipperclub.data.UploadedMedia

/**
 * Seam between the cruises UI controllers and the API singletons so state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface CruisesGateway {
    suspend fun list(accessToken: String, query: CruiseListQuery): CruisesPage
    suspend fun get(accessToken: String, cruiseId: String): Cruise
    suspend fun create(accessToken: String, payload: CruisePayload): Cruise
    suspend fun aiDraft(accessToken: String, description: String): CruiseAiDraft
    suspend fun update(accessToken: String, cruiseId: String, payload: CruisePayload): Cruise
    suspend fun delete(accessToken: String, cruiseId: String)
    suspend fun participants(accessToken: String, cruiseId: String): CruiseParticipantsPage
    suspend fun addParticipant(accessToken: String, cruiseId: String, userId: String): CruiseParticipant
    suspend fun updateParticipantState(
        accessToken: String,
        cruiseId: String,
        participantId: String,
        state: CruiseParticipantState,
    ): CruiseParticipant

    suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage
    suspend fun searchLocations(accessToken: String, query: String): List<GeocodedLocation>
    suspend fun uploadMedia(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        meta: MediaUploadMeta,
    ): UploadedMedia
}

object RealCruisesGateway : CruisesGateway {
    override suspend fun list(accessToken: String, query: CruiseListQuery): CruisesPage =
        CruisesApi.list(accessToken, query)

    override suspend fun get(accessToken: String, cruiseId: String): Cruise =
        CruisesApi.get(accessToken, cruiseId)

    override suspend fun create(accessToken: String, payload: CruisePayload): Cruise =
        CruisesApi.create(accessToken, payload)

    override suspend fun aiDraft(accessToken: String, description: String): CruiseAiDraft =
        CruisesApi.aiDraft(accessToken, description)

    override suspend fun update(accessToken: String, cruiseId: String, payload: CruisePayload): Cruise =
        CruisesApi.update(accessToken, cruiseId, payload)

    override suspend fun delete(accessToken: String, cruiseId: String) =
        CruisesApi.delete(accessToken, cruiseId)

    override suspend fun participants(accessToken: String, cruiseId: String): CruiseParticipantsPage =
        CruisesApi.participants(accessToken, cruiseId)

    override suspend fun addParticipant(
        accessToken: String,
        cruiseId: String,
        userId: String,
    ): CruiseParticipant = CruisesApi.addParticipant(accessToken, cruiseId, userId)

    override suspend fun updateParticipantState(
        accessToken: String,
        cruiseId: String,
        participantId: String,
        state: CruiseParticipantState,
    ): CruiseParticipant =
        CruisesApi.updateParticipantState(accessToken, cruiseId, participantId, state)

    override suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage =
        ChatsApi.searchUsers(accessToken, query)

    override suspend fun searchLocations(accessToken: String, query: String): List<GeocodedLocation> =
        GeocoderApi.search(accessToken, query)

    override suspend fun uploadMedia(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        meta: MediaUploadMeta,
    ): UploadedMedia =
        MediaUploadApi.upload(
            accessToken = accessToken,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            meta = meta,
        )
}
