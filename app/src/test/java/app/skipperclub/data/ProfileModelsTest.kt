package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun decodesFullProfileFromOpenApiExample() {
        val payload = """
            {
              "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b00",
              "name": "Jan Kowalski",
              "email": "jan@example.com",
              "role": "admin",
              "avatarUrl": "https://cdn.example.com/avatars/jan.jpg",
              "bio": "Sailing enthusiast and Baltic Sea explorer",
              "city": "Gdańsk",
              "country": "PL",
              "sailingExperience": "advanced",
              "facebookUrl": "https://facebook.com/jan.skipper",
              "instagramUsername": "@jan_skipper",
              "tiktokUsername": "@jan_skipper",
              "whatsappNumber": "+48123456789",
              "sailingLicenses": "RYA Yachtmaster Offshore",
              "yearsOfExperience": 10,
              "languagesSpoken": ["pl", "en", "de"],
              "preferredVoyageStyles": ["racing", "coastal"],
              "cruisesCount": 15,
              "friendsCount": 42,
              "postsCount": 28,
              "currentUserFriendshipStatus": "none",
              "createdAt": "2025-01-15T10:00:00Z",
              "updatedAt": "2025-11-20T14:30:00Z"
            }
        """.trimIndent()

        val profile = json.decodeFromString<ProfileDto>(payload).toDomain()

        assertEquals("Jan Kowalski", profile.name)
        assertEquals("jan@example.com", profile.email)
        assertTrue(profile.isAdmin)
        assertEquals(SailingExperience.Advanced, profile.sailingExperience)
        assertEquals(10, profile.yearsOfExperience)
        assertEquals(listOf("pl", "en", "de"), profile.languagesSpoken)
        assertEquals(listOf("racing", "coastal"), profile.preferredVoyageStyles)
        assertEquals(15, profile.cruisesCount)
        assertEquals("@jan_skipper", profile.instagramUsername)
    }

    @Test
    fun decodesMinimalProfileWithDefaults() {
        val payload = """
            { "id": "u2", "name": "Ola", "email": "ola@example.com" }
        """.trimIndent()

        val profile = json.decodeFromString<ProfileDto>(payload).toDomain()

        assertEquals(SessionUser.ROLE_USER, profile.role)
        assertNull(profile.sailingExperience)
        assertNull(profile.bio)
        assertTrue(profile.languagesSpoken.isEmpty())
        assertEquals(0, profile.postsCount)
        assertEquals(emptyList<String>(), profile.preferredVoyageStyles)
    }

    @Test
    fun blankOptionalStringsAreNormalizedToNull() {
        val payload = """
            { "id": "u3", "name": "Test", "email": "t@example.com", "bio": "   ", "city": "" }
        """.trimIndent()

        val profile = json.decodeFromString<ProfileDto>(payload).toDomain()

        assertNull(profile.bio)
        assertNull(profile.city)
    }

    @Test
    fun decodesProfileWithExplicitNullArraysAndAbsentRole() {
        // Real payload shape from GET /v1/profile: optional arrays come back as explicit `null`,
        // `role` is absent, and `sailingLicenses`/`yearsOfExperience` are null.
        val payload = """
            {
              "id": "01985af0",
              "name": "Krzysztof",
              "email": "krzysztof@example.com",
              "avatarUrl": "https://media.example.com/a.jpeg",
              "bio": "Sailing enthusiast",
              "city": "Warszawa",
              "country": "PL",
              "cruisesCount": 22,
              "currentUserFriendshipStatus": "none",
              "sailingExperience": "professional",
              "sailingLicenses": null,
              "yearsOfExperience": null,
              "languagesSpoken": null,
              "preferredVoyageStyles": null,
              "friendsCount": 1,
              "postsCount": 17,
              "createdAt": "2025-07-30T10:46:21.842Z"
            }
        """.trimIndent()

        val profile = json.decodeFromString<ProfileDto>(payload).toDomain()

        assertEquals("Krzysztof", profile.name)
        assertEquals(SessionUser.ROLE_USER, profile.role)
        assertFalse(profile.isAdmin)
        assertEquals(SailingExperience.Professional, profile.sailingExperience)
        assertNull(profile.sailingLicenses)
        assertNull(profile.yearsOfExperience)
        assertTrue(profile.languagesSpoken.isEmpty())
        assertTrue(profile.preferredVoyageStyles.isEmpty())
        assertEquals(22, profile.cruisesCount)
        assertEquals(17, profile.postsCount)
    }

    @Test
    fun unknownSailingExperienceFallsBackToUnknown() {
        assertEquals(SailingExperience.Unknown, SailingExperience.fromWire("guru"))
        assertNull(SailingExperience.fromWire(null))
    }
}
