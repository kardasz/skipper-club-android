package app.skipperclub.ui.main.profile

import app.skipperclub.data.ProfileError
import app.skipperclub.data.ProfileUpdate
import app.skipperclub.data.SailingExperience
import app.skipperclub.data.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A locally picked avatar awaiting upload on save. */
data class PendingAvatar(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int?,
    val height: Int?,
) {
    // Identity equality is enough; the bytes are never compared in UI state diffs.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Editable form state for the "Edit profile" screen, seeded from a [UserProfile]. */
data class EditProfileUiState(
    val name: String = "",
    val bio: String = "",
    val city: String = "",
    val country: String = "",
    val sailingExperience: SailingExperience? = null,
    val yearsOfExperience: String = "",
    val sailingLicenses: String = "",
    val languagesSpoken: String = "",
    val preferredVoyageStyles: String = "",
    val facebookUrl: String = "",
    val instagramUsername: String = "",
    val tiktokUsername: String = "",
    val whatsappNumber: String = "",
    val currentAvatarUrl: String? = null,
    val pendingAvatar: PendingAvatar? = null,
    val isSaving: Boolean = false,
    val nameInvalid: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving

    companion object {
        fun from(profile: UserProfile): EditProfileUiState = EditProfileUiState(
            name = profile.name,
            bio = profile.bio.orEmpty(),
            city = profile.city.orEmpty(),
            country = profile.country.orEmpty(),
            sailingExperience = profile.sailingExperience?.takeIf { it != SailingExperience.Unknown },
            yearsOfExperience = profile.yearsOfExperience?.toString().orEmpty(),
            sailingLicenses = profile.sailingLicenses.orEmpty(),
            languagesSpoken = profile.languagesSpoken.joinToString(", "),
            preferredVoyageStyles = profile.preferredVoyageStyles.joinToString(", "),
            facebookUrl = profile.facebookUrl.orEmpty(),
            instagramUsername = profile.instagramUsername.orEmpty(),
            tiktokUsername = profile.tiktokUsername.orEmpty(),
            whatsappNumber = profile.whatsappNumber.orEmpty(),
            currentAvatarUrl = profile.avatarUrl,
        )
    }
}

sealed interface EditProfileEvent {
    data class Saved(val profile: UserProfile) : EditProfileEvent
    data class SaveFailed(val error: Exception) : EditProfileEvent
    data object SessionExpired : EditProfileEvent
    /** The picked image could not be read or exceeds the 10 MB limit. */
    data object AvatarRejected : EditProfileEvent
}

/**
 * State holder for the "Edit profile" screen. Owns the form fields, uploads a
 * newly picked avatar through the presigned flow, then `PUT`s the profile. Plain
 * class (no ViewModel/DI — see CLAUDE.md §State); unit-tested with a fake
 * [ProfileGateway].
 */
class EditProfileController(
    private val source: UserProfile,
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: ProfileGateway = RealProfileGateway,
) {
    private val _state = MutableStateFlow(EditProfileUiState.from(source))
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditProfileEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditProfileEvent> = _events.asSharedFlow()

    private var saveJob: Job? = null

    fun onName(value: String) = _state.update { it.copy(name = value, nameInvalid = false) }
    fun onBio(value: String) = _state.update { it.copy(bio = value) }
    fun onCity(value: String) = _state.update { it.copy(city = value) }
    fun onCountry(value: String) = _state.update { it.copy(country = value.uppercase()) }
    fun onSailingExperience(value: SailingExperience?) = _state.update { it.copy(sailingExperience = value) }
    fun onYearsOfExperience(value: String) =
        _state.update { it.copy(yearsOfExperience = value.filter(Char::isDigit).take(3)) }

    fun onSailingLicenses(value: String) = _state.update { it.copy(sailingLicenses = value) }
    fun onLanguagesSpoken(value: String) = _state.update { it.copy(languagesSpoken = value) }
    fun onPreferredVoyageStyles(value: String) = _state.update { it.copy(preferredVoyageStyles = value) }
    fun onFacebookUrl(value: String) = _state.update { it.copy(facebookUrl = value) }
    fun onInstagramUsername(value: String) = _state.update { it.copy(instagramUsername = value) }
    fun onTiktokUsername(value: String) = _state.update { it.copy(tiktokUsername = value) }
    fun onWhatsappNumber(value: String) = _state.update { it.copy(whatsappNumber = value) }

    fun onAvatarPicked(fileName: String, mimeType: String, bytes: ByteArray, width: Int?, height: Int?) =
        _state.update {
            it.copy(pendingAvatar = PendingAvatar(fileName, mimeType, bytes, width, height))
        }

    fun onAvatarRejected() {
        _events.tryEmit(EditProfileEvent.AvatarRejected)
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        if (current.name.isBlank()) {
            _state.update { it.copy(nameInvalid = true) }
            return
        }
        saveJob?.cancel()
        _state.update { it.copy(isSaving = true) }
        saveJob = scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSaving = false) }
                return@launch
            }
            try {
                val newAvatarUrl = current.pendingAvatar?.let { avatar ->
                    gateway.uploadAvatar(
                        accessToken = token,
                        fileName = avatar.fileName,
                        mimeType = avatar.mimeType,
                        bytes = avatar.bytes,
                        width = avatar.width,
                        height = avatar.height,
                    )
                }
                val updated = gateway.updateProfile(token, current.toUpdate())
                // PUT returns a UserDetail without email; preserve what we already hold,
                // and prefer the freshly uploaded avatar URL.
                val merged = updated.copy(
                    email = source.email,
                    avatarUrl = newAvatarUrl ?: updated.avatarUrl,
                )
                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(EditProfileEvent.Saved(merged))
            } catch (error: ProfileError) {
                _state.update { it.copy(isSaving = false) }
                _events.tryEmit(EditProfileEvent.SaveFailed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(EditProfileEvent.SessionExpired)
        return token
    }
}

/** Maps the trimmed form fields to a [ProfileUpdate], using `null` to clear optional fields. */
internal fun EditProfileUiState.toUpdate(): ProfileUpdate = ProfileUpdate(
    name = name.trim(),
    bio = bio.trim().ifBlank { null },
    city = city.trim().ifBlank { null },
    country = country.trim().uppercase().ifBlank { null },
    sailingExperience = sailingExperience,
    yearsOfExperience = yearsOfExperience.trim().toIntOrNull(),
    sailingLicenses = sailingLicenses.trim().ifBlank { null },
    languagesSpoken = languagesSpoken.toTagList(),
    preferredVoyageStyles = preferredVoyageStyles.toTagList(),
    facebookUrl = facebookUrl.trim().ifBlank { null },
    instagramUsername = instagramUsername.trim().ifBlank { null },
    tiktokUsername = tiktokUsername.trim().ifBlank { null },
    whatsappNumber = whatsappNumber.trim().ifBlank { null },
)

/** Splits a comma/whitespace-separated input into a trimmed, de-duplicated tag list. */
internal fun String.toTagList(): List<String> =
    split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
