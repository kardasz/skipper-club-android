package app.skipperclub.ui.main.posts.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skipperclub.data.CoordinatesDto
import app.skipperclub.data.CreatePostRequest
import app.skipperclub.data.FriendUser
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.MediaUploadMeta
import app.skipperclub.data.Post
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostRouteStop
import app.skipperclub.data.PostType
import app.skipperclub.data.RouteStopDto
import app.skipperclub.data.UpdatePostRequest
import app.skipperclub.ui.main.posts.PostsGateway
import app.skipperclub.ui.main.posts.RealPostsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

const val POST_DESCRIPTION_MAX_LENGTH = 2200
const val POST_MEDIA_MAX_COUNT = 10
const val POST_ROUTE_STOPS_MAX_COUNT = 30
const val POST_TAGGED_USERS_MAX_COUNT = 20

enum class PostWizardStep { Type, Details, RouteStops, Media, Tags, Summary }

/** Validation problems surfaced under the relevant field / step. */
enum class PostWizardError {
    DescriptionRequired,
    LocationRequired,
    StopsRequired,
    MediaRequired,
}

sealed interface PostWizardEvent {
    data class Published(val post: Post) : PostWizardEvent

    /** Emitted in edit mode; the host applies the update via the feed controller. */
    data class Updated(val postId: String, val request: UpdatePostRequest) : PostWizardEvent
    data class PublishFailed(val error: Exception) : PostWizardEvent
    data class MediaUploadFailed(val error: Exception) : PostWizardEvent
    data object SessionExpired : PostWizardEvent
}

data class WizardMedia(
    val localId: Long,
    val fileName: String,
    val isVideo: Boolean = false,
    val isUploading: Boolean = false,
    val failed: Boolean = false,
    val mediaId: String? = null,
    val publicUrl: String? = null,
)

/**
 * State machine for the post creation wizard. Pure Kotlin + Compose snapshot
 * state (no Android types) so step flow, validation and request building are
 * unit-testable on the JVM with a fake [PostsGateway].
 */
class PostWizardState(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: PostsGateway = RealPostsGateway,
    private val locationSearchDebounceMillis: Long = 350,
    editingPost: Post? = null,
) {
    /** Non-null in edit mode; type is immutable so the Type step is skipped. */
    val editingPostId: String? = editingPost?.id
    val isEditing: Boolean get() = editingPostId != null

    var step by mutableStateOf(if (editingPost != null) PostWizardStep.Details else PostWizardStep.Type)
        private set
    var selectedType by mutableStateOf(editingPost?.type)
        private set

    var description by mutableStateOf(editingPost?.description.orEmpty())
        private set
    var locationName by mutableStateOf(editingPost?.locationName)
        private set
    var coordinates by mutableStateOf(editingPost?.coordinates)
        private set
    /** Preserved from the edited post; the create form no longer exposes a region picker. */
    var regionCode by mutableStateOf(editingPost?.regionCode)
        private set

    var locationQuery by mutableStateOf(editingPost?.locationName.orEmpty())
        private set
    var locationResults by mutableStateOf<List<GeocodedLocation>>(emptyList())
        private set
    var isSearchingLocation by mutableStateOf(false)
        private set

    val stops = mutableStateListOf<PostRouteStop>()
    var durationDaysText by mutableStateOf(editingPost?.durationDays?.toString().orEmpty())
        private set
    var lengthNmText by mutableStateOf(
        editingPost?.lengthNm?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            .orEmpty(),
    )
        private set

    val media = mutableStateListOf<WizardMedia>()

    /** People tagged in this post (max [POST_TAGGED_USERS_MAX_COUNT]). */
    val taggedUsers = mutableStateListOf<FriendUser>()

    // People search (tag step)
    var tagQuery by mutableStateOf("")
        private set
    var tagResults by mutableStateOf<List<FriendUser>>(emptyList())
        private set
    var isSearchingTags by mutableStateOf(false)
        private set

    var isPublishing by mutableStateOf(false)
        private set

    private var nextMediaLocalId = 0L

    init {
        editingPost?.stops?.let { stops.addAll(it) }
        editingPost?.media?.forEach { item ->
            media.add(
                WizardMedia(
                    localId = nextMediaLocalId++,
                    fileName = item.url.substringAfterLast('/').ifBlank { "media" },
                    isVideo = item.isVideo,
                    isUploading = false,
                    mediaId = item.id,
                    publicUrl = item.url,
                ),
            )
        }
        editingPost?.taggedUsers?.forEach { user ->
            taggedUsers.add(FriendUser(id = user.id, name = user.name, avatarUrl = user.avatarUrl))
        }
    }

    /** Set after a failed Next tap so fields can highlight what is missing. */
    var visibleErrors by mutableStateOf<Set<PostWizardError>>(emptySet())
        private set

    private val _events = MutableSharedFlow<PostWizardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PostWizardEvent> = _events.asSharedFlow()

    private var locationSearchJob: Job? = null
    private var tagSearchJob: Job? = null

    /** Ordered steps for the selected type (route inserts the stops step). */
    val steps: List<PostWizardStep>
        get() {
            val type = selectedType
            return buildList {
                if (!isEditing) add(PostWizardStep.Type)
                add(PostWizardStep.Details)
                if (type?.requiresStops == true) add(PostWizardStep.RouteStops)
                add(PostWizardStep.Media)
                add(PostWizardStep.Tags)
                add(PostWizardStep.Summary)
            }
        }

    val stepIndex: Int
        get() = steps.indexOf(step).coerceAtLeast(0)

    fun selectType(type: PostType) {
        selectedType = type
        visibleErrors = emptySet()
    }

    fun updateDescription(value: String) {
        description = value.take(POST_DESCRIPTION_MAX_LENGTH)
        if (value.isNotBlank()) visibleErrors = visibleErrors - PostWizardError.DescriptionRequired
    }

    fun updateLocationQuery(value: String) {
        locationQuery = value
        locationSearchJob?.cancel()
        if (value.trim().length < 3) {
            locationResults = emptyList()
            isSearchingLocation = false
            return
        }
        isSearchingLocation = true
        locationSearchJob = scope.launch {
            delay(locationSearchDebounceMillis)
            val token = runCatching { accessToken() }.getOrNull() ?: run {
                isSearchingLocation = false
                _events.tryEmit(PostWizardEvent.SessionExpired)
                return@launch
            }
            try {
                locationResults = gateway.searchLocations(token, value.trim())
            } catch (_: Exception) {
                locationResults = emptyList()
            }
            isSearchingLocation = false
        }
    }

    fun selectLocation(location: GeocodedLocation) {
        locationName = location.displayName
        coordinates = location.coordinates
        locationQuery = location.displayName
        locationResults = emptyList()
        visibleErrors = visibleErrors - PostWizardError.LocationRequired
    }

    fun clearLocation() {
        locationName = null
        coordinates = null
        locationQuery = ""
        locationResults = emptyList()
    }

    fun addStop(location: GeocodedLocation) {
        if (stops.size >= POST_ROUTE_STOPS_MAX_COUNT) return
        stops.add(PostRouteStop(name = location.displayName, coordinates = location.coordinates))
        visibleErrors = visibleErrors - PostWizardError.StopsRequired
    }

    fun removeStop(index: Int) {
        if (index in stops.indices) stops.removeAt(index)
    }

    fun moveStop(index: Int, delta: Int) {
        val target = index + delta
        if (index !in stops.indices || target !in stops.indices) return
        val stop = stops.removeAt(index)
        stops.add(target, stop)
    }

    fun updateDurationDays(value: String) {
        durationDaysText = value.filter { it.isDigit() }.take(3)
    }

    fun updateLengthNm(value: String) {
        lengthNmText = value.filter { it.isDigit() || it == '.' }.take(7)
    }

    fun uploadMedia(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        meta: MediaUploadMeta = MediaUploadMeta(),
    ) {
        if (media.size >= POST_MEDIA_MAX_COUNT) return
        val item = WizardMedia(
            localId = nextMediaLocalId++,
            fileName = fileName,
            isVideo = mimeType.startsWith("video/"),
            isUploading = true,
        )
        media.add(item)
        scope.launch {
            val token = runCatching { accessToken() }.getOrNull() ?: run {
                media.remove(media.first { it.localId == item.localId })
                _events.tryEmit(PostWizardEvent.SessionExpired)
                return@launch
            }
            try {
                val uploaded = gateway.uploadMedia(token, fileName, mimeType, bytes, meta)
                replaceMedia(item.localId) {
                    it.copy(isUploading = false, mediaId = uploaded.mediaId, publicUrl = uploaded.publicUrl)
                }
                visibleErrors = visibleErrors - PostWizardError.MediaRequired
            } catch (error: Exception) {
                replaceMedia(item.localId) { it.copy(isUploading = false, failed = true) }
                _events.tryEmit(PostWizardEvent.MediaUploadFailed(error))
            }
        }
    }

    fun removeMedia(localId: Long) {
        media.removeAll { it.localId == localId }
    }

    val isUploadingMedia: Boolean
        get() = media.any { it.isUploading }

    fun updateTagQuery(value: String) {
        tagQuery = value
        tagSearchJob?.cancel()
        if (value.trim().length < 2) {
            tagResults = emptyList()
            isSearchingTags = false
            return
        }
        isSearchingTags = true
        tagSearchJob = scope.launch {
            delay(locationSearchDebounceMillis)
            val token = runCatching { accessToken() }.getOrNull() ?: run {
                isSearchingTags = false
                _events.tryEmit(PostWizardEvent.SessionExpired)
                return@launch
            }
            try {
                val taggedIds = taggedUsers.mapTo(mutableSetOf()) { it.id }
                tagResults = gateway.searchFriends(token, value.trim()).filterNot { it.id in taggedIds }
            } catch (_: Exception) {
                tagResults = emptyList()
            }
            isSearchingTags = false
        }
    }

    fun addTag(user: FriendUser) {
        if (taggedUsers.size >= POST_TAGGED_USERS_MAX_COUNT) return
        if (taggedUsers.any { it.id == user.id }) return
        taggedUsers.add(user)
        tagQuery = ""
        tagResults = emptyList()
    }

    fun removeTag(userId: String) {
        taggedUsers.removeAll { it.id == userId }
    }

    private val uploadedMediaIds: List<String>
        get() = media.mapNotNull { it.mediaId }

    /** Validation errors blocking the given step's Next action. */
    fun errorsFor(step: PostWizardStep): Set<PostWizardError> {
        val type = selectedType ?: return emptySet()
        return when (step) {
            PostWizardStep.Type -> emptySet()
            PostWizardStep.Details -> buildSet {
                if (type.requiresDescription && description.isBlank()) {
                    add(PostWizardError.DescriptionRequired)
                }
                if (type.requiresLocation && (locationName == null || coordinates == null)) {
                    add(PostWizardError.LocationRequired)
                }
            }

            PostWizardStep.RouteStops ->
                if (type.requiresStops && stops.isEmpty()) {
                    setOf(PostWizardError.StopsRequired)
                } else {
                    emptySet()
                }

            PostWizardStep.Media ->
                if (type.requiresMedia && uploadedMediaIds.isEmpty()) {
                    setOf(PostWizardError.MediaRequired)
                } else {
                    emptySet()
                }

            PostWizardStep.Tags -> emptySet()

            PostWizardStep.Summary -> emptySet()
        }
    }

    val canGoNext: Boolean
        get() = when (step) {
            PostWizardStep.Type -> selectedType != null
            PostWizardStep.Media -> !isUploadingMedia
            PostWizardStep.Summary -> !isPublishing
            else -> true
        }

    /** Advances if the current step validates; otherwise surfaces the errors. */
    fun next() {
        val errors = errorsFor(step)
        if (errors.isNotEmpty()) {
            visibleErrors = errors
            return
        }
        visibleErrors = emptySet()
        val ordered = steps
        val index = ordered.indexOf(step)
        if (index < ordered.lastIndex) {
            step = ordered[index + 1]
        }
    }

    /** Returns false when already at the first step (caller should close). */
    fun back(): Boolean {
        val ordered = steps
        val index = ordered.indexOf(step)
        if (index <= 0) return false
        visibleErrors = emptySet()
        step = ordered[index - 1]
        return true
    }

    val hasUserInput: Boolean
        get() = selectedType != null &&
            (
                description.isNotBlank() || locationName != null || regionCode != null ||
                    stops.isNotEmpty() || media.isNotEmpty()
                )

    private val taggedUserIds: List<String>
        get() = taggedUsers.map { it.id }

    internal fun buildRequest(): CreatePostRequest? {
        val type = selectedType ?: return null
        return CreatePostRequest(
            type = type.wireValue,
            regionCode = regionCode,
            description = description.trim().takeIf { it.isNotEmpty() },
            locationName = locationName,
            coordinates = coordinates?.let { CoordinatesDto.from(it) },
            mediaIds = uploadedMediaIds.takeIf { it.isNotEmpty() },
            taggedUserIds = taggedUserIds.takeIf { it.isNotEmpty() },
            stops = if (type.requiresStops) stops.map { RouteStopDto.from(it) } else null,
            durationDays = if (type.requiresStops) durationDaysText.toIntOrNull() else null,
            lengthNm = if (type.requiresStops) lengthNmText.toDoubleOrNull() else null,
        )
    }

    internal fun buildUpdateRequest(): UpdatePostRequest? {
        val type = selectedType ?: return null
        return UpdatePostRequest(
            regionCode = regionCode,
            description = description.trim().takeIf { it.isNotEmpty() },
            locationName = locationName,
            coordinates = coordinates?.let { CoordinatesDto.from(it) },
            mediaIds = uploadedMediaIds.takeIf { it.isNotEmpty() },
            taggedUserIds = taggedUserIds.takeIf { it.isNotEmpty() },
            stops = if (type.requiresStops) stops.map { RouteStopDto.from(it) } else null,
            durationDays = if (type.requiresStops) durationDaysText.toIntOrNull() else null,
            lengthNm = if (type.requiresStops) lengthNmText.toDoubleOrNull() else null,
        )
    }

    fun publish() {
        if (isPublishing) return
        val allErrors = steps.flatMap { errorsFor(it) }.toSet()
        if (allErrors.isNotEmpty()) {
            visibleErrors = allErrors
            return
        }
        val editId = editingPostId
        if (editId != null) {
            val updateRequest = buildUpdateRequest() ?: return
            isPublishing = true
            _events.tryEmit(PostWizardEvent.Updated(editId, updateRequest))
            return
        }
        val request = buildRequest() ?: return
        isPublishing = true
        scope.launch {
            val token = runCatching { accessToken() }.getOrNull() ?: run {
                isPublishing = false
                _events.tryEmit(PostWizardEvent.SessionExpired)
                return@launch
            }
            try {
                val post = gateway.create(token, request)
                isPublishing = false
                _events.tryEmit(PostWizardEvent.Published(post))
            } catch (error: Exception) {
                isPublishing = false
                _events.tryEmit(PostWizardEvent.PublishFailed(error))
            }
        }
    }

    private fun replaceMedia(localId: Long, transform: (WizardMedia) -> WizardMedia) {
        val index = media.indexOfFirst { it.localId == localId }
        if (index >= 0) {
            media[index] = transform(media[index])
        }
    }
}
