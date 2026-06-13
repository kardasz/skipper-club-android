package app.skipperclub.data

import kotlinx.serialization.Serializable

/**
 * Sailing experience level (`docs/api/openapi.yaml` → `sailingExperience`). [Unknown]
 * is a forward-compatible fallback so an unexpected server value never drops the
 * field; the UI hides unknown values rather than crashing.
 */
enum class SailingExperience(val wireValue: String) {
    Beginner("beginner"),
    Intermediate("intermediate"),
    Advanced("advanced"),
    Professional("professional"),
    Unknown(""),
    ;

    companion object {
        fun fromWire(value: String?): SailingExperience? =
            value?.let { wire -> entries.firstOrNull { it.wireValue == wire } ?: Unknown }
    }
}

/** The current user's own profile as rendered by the "My profile" screen. */
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val role: String = SessionUser.ROLE_USER,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val country: String? = null,
    val sailingExperience: SailingExperience? = null,
    val yearsOfExperience: Int? = null,
    val sailingLicenses: String? = null,
    val languagesSpoken: List<String> = emptyList(),
    val preferredVoyageStyles: List<String> = emptyList(),
    val facebookUrl: String? = null,
    val instagramUsername: String? = null,
    val tiktokUsername: String? = null,
    val whatsappNumber: String? = null,
    val cruisesCount: Int = 0,
    val friendsCount: Int = 0,
    val postsCount: Int = 0,
    val createdAt: String? = null,
) {
    val isAdmin: Boolean get() = role.equals(SessionUser.ROLE_ADMIN, ignoreCase = true)
}

@Serializable
internal data class ProfileDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val country: String? = null,
    val sailingExperience: String? = null,
    val yearsOfExperience: Int? = null,
    val sailingLicenses: String? = null,
    // Server sends explicit `null` (not an absent key) when unset, so these must be nullable.
    val languagesSpoken: List<String>? = null,
    val preferredVoyageStyles: List<String>? = null,
    val facebookUrl: String? = null,
    val instagramUsername: String? = null,
    val tiktokUsername: String? = null,
    val whatsappNumber: String? = null,
    val cruisesCount: Int = 0,
    val friendsCount: Int = 0,
    val postsCount: Int = 0,
    val createdAt: String? = null,
) {
    fun toDomain(): UserProfile = UserProfile(
        id = id,
        name = name,
        email = email,
        role = role ?: SessionUser.ROLE_USER,
        avatarUrl = avatarUrl,
        bio = bio?.takeIf { it.isNotBlank() },
        city = city?.takeIf { it.isNotBlank() },
        country = country?.takeIf { it.isNotBlank() },
        sailingExperience = SailingExperience.fromWire(sailingExperience),
        yearsOfExperience = yearsOfExperience,
        sailingLicenses = sailingLicenses?.takeIf { it.isNotBlank() },
        languagesSpoken = languagesSpoken.orEmpty().filter { it.isNotBlank() },
        preferredVoyageStyles = preferredVoyageStyles.orEmpty().filter { it.isNotBlank() },
        facebookUrl = facebookUrl?.takeIf { it.isNotBlank() },
        instagramUsername = instagramUsername?.takeIf { it.isNotBlank() },
        tiktokUsername = tiktokUsername?.takeIf { it.isNotBlank() },
        whatsappNumber = whatsappNumber?.takeIf { it.isNotBlank() },
        cruisesCount = cruisesCount,
        friendsCount = friendsCount,
        postsCount = postsCount,
        createdAt = createdAt,
    )
}
