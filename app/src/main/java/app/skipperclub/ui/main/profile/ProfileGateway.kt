package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileApi
import app.skipperclub.data.UserProfile

/**
 * Seam between the profile UI controller and [ProfileApi] so the state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface ProfileGateway {
    suspend fun getProfile(accessToken: String): UserProfile
}

object RealProfileGateway : ProfileGateway {
    override suspend fun getProfile(accessToken: String): UserProfile =
        ProfileApi.getProfile(accessToken)
}
