package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileError
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

    override suspend fun getProfile(accessToken: String): UserProfile {
        calls++
        error?.let { throw it }
        return profile
    }
}
