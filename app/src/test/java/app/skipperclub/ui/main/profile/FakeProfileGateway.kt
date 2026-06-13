package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileError
import app.skipperclub.data.ProfileUpdate
import app.skipperclub.data.SailingExperience
import app.skipperclub.data.UserProfile

internal fun testProfile(
    id: String = "u1",
    name: String = "Anna Nowak",
    email: String = "anna@example.com",
    role: String = "user",
) = UserProfile(
    id = id,
    name = name,
    email = email,
    role = role,
    bio = "Sailing enthusiast",
    city = "Gdańsk",
    country = "PL",
    sailingExperience = SailingExperience.Advanced,
    yearsOfExperience = 10,
    languagesSpoken = listOf("pl", "en"),
    preferredVoyageStyles = listOf("coastal"),
    cruisesCount = 15,
    friendsCount = 42,
    postsCount = 28,
    createdAt = "2025-01-15T10:00:00Z",
)

/** Configurable in-memory [ProfileGateway]; records calls for assertions. */
internal class FakeProfileGateway : ProfileGateway {
    var profile: UserProfile = testProfile()
    var error: ProfileError? = null
    var calls = 0

    // Update/avatar recording for the edit-screen tests.
    var updateError: ProfileError? = null
    var avatarError: ProfileError? = null
    var lastUpdate: ProfileUpdate? = null
    var updateCalls = 0
    var avatarCalls = 0
    var lastAvatarBytes: ByteArray? = null
    var uploadedAvatarUrl: String = "https://cdn.example.com/avatars/new.jpg"
    /** Field values reflected back in the update response (UserDetail has no email). */
    var updateResponse: (ProfileUpdate) -> UserProfile = { update ->
        profile.copy(
            name = update.name,
            email = "",
            bio = update.bio,
            city = update.city,
            country = update.country,
            sailingExperience = update.sailingExperience,
            yearsOfExperience = update.yearsOfExperience,
            sailingLicenses = update.sailingLicenses,
            languagesSpoken = update.languagesSpoken,
            preferredVoyageStyles = update.preferredVoyageStyles,
            facebookUrl = update.facebookUrl,
            instagramUsername = update.instagramUsername,
            tiktokUsername = update.tiktokUsername,
            whatsappNumber = update.whatsappNumber,
        )
    }

    override suspend fun getProfile(accessToken: String): UserProfile {
        calls++
        error?.let { throw it }
        return profile
    }

    override suspend fun updateProfile(accessToken: String, update: ProfileUpdate): UserProfile {
        updateCalls++
        lastUpdate = update
        updateError?.let { throw it }
        return updateResponse(update)
    }

    override suspend fun uploadAvatar(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        width: Int?,
        height: Int?,
    ): String {
        avatarCalls++
        lastAvatarBytes = bytes
        avatarError?.let { throw it }
        return uploadedAvatarUrl
    }
}
