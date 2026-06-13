package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileApi
import app.skipperclub.data.ProfileUpdate
import app.skipperclub.data.UserProfile

/**
 * Seam between the profile UI controllers and [ProfileApi] so the state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface ProfileGateway {
    suspend fun getProfile(accessToken: String): UserProfile

    suspend fun updateProfile(accessToken: String, update: ProfileUpdate): UserProfile

    suspend fun uploadAvatar(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int?,
        height: Int?,
    ): String
}

object RealProfileGateway : ProfileGateway {
    override suspend fun getProfile(accessToken: String): UserProfile =
        ProfileApi.getProfile(accessToken)

    override suspend fun updateProfile(accessToken: String, update: ProfileUpdate): UserProfile =
        ProfileApi.updateProfile(accessToken, update)

    override suspend fun uploadAvatar(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int?,
        height: Int?,
    ): String = ProfileApi.uploadAvatar(accessToken, fileName, mimeType, bytes, width, height)
}
