package app.skipperclub.ui.main.posts.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skipperclub.data.AlertCategory
import app.skipperclub.data.AlertInputDto
import app.skipperclub.data.AlertSeverity
import app.skipperclub.data.CoordinatesDto
import app.skipperclub.data.CreatePostRequest
import app.skipperclub.data.FriendUser
import app.skipperclub.data.GeocodedLocation
import app.skipperclub.data.MediaUploadMeta
import app.skipperclub.data.Post
import app.skipperclub.data.PostContentInputDto
import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostLocationInputDto
import app.skipperclub.data.PostRouteStop
import app.skipperclub.data.RouteInputDto
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

const val POST_TEXT_MAX_LENGTH = 2200
const val POST_MEDIA_MAX_COUNT = 10
const val POST_ROUTE_STOPS_MAX_COUNT = 30
const val POST_TAGGED_USERS_MAX_COUNT = 20

/** Validation problems surfaced under the relevant field / section. */
enum class PostWizardError {
    TextRequired,
    StopsRequired,
    AlertCategoryRequired,
    AlertLocationRequired,
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
 * State holder for the post creation / edit form. Since API v8.0.0 there is a
 * single form (no post-type chooser): required [text], an optional [locationName]
 * anchor, and two mutually exclusive optional sections — a route ([routeEnabled])
 * or an alert ([alertEnabled]) — plus media and tagged users.
 *
 * Pure Kotlin + Compose snapshot state (no Android types) so validation and
 * request building are unit-testable on the JVM with a fake [PostsGateway].
 */
class PostWizardState(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val gateway: PostsGateway = RealPostsGateway,
    private val locationSearchDebounceMillis: Long = 350,
    editingPost: Post? = null,
) {
    val editingPostId: String? = editingPost?.id
    val isEditing: Boolean get() = editingPostId != null

    // --- Text (required) ---
    var text by mutableStateOf(editingPost?.content?.text.orEmpty())
        private set

    // --- Location ---
    var locationName by mutableStateOf(editingPost?.location?.name)
        private set
    var coordinates by mutableStateOf(editingPost?.location?.point)
        private set
    var locationQuery by mutableStateOf(editingPost?.location?.name.orEmpty())
        private set
    var locationResults by mutableStateOf<List<GeocodedLocation>>(emptyList())
        private set
    var isSearchingLocation by mutableStateOf(false)
        private set

    // --- Route (optional, mutually exclusive with alert) ---
    var routeEnabled by mutableStateOf(editingPost?.content?.route != null)
        private set
    val stops = mutableStateListOf<PostRouteStop>()
    var durationDaysText by mutableStateOf(editingPost?.content?.route?.durationDays?.toString().orEmpty())
        private set
    var lengthNmText by mutableStateOf(
        editingPost?.content?.route?.lengthNm
            ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            .orEmpty(),
    )
        private set

    // --- Alert (optional, mutually exclusive with route) ---
    var alertEnabled by mutableStateOf(editingPost?.content?.alert != null)
        private set
    var alertCategory by mutableStateOf(editingPost?.content?.alert?.category)
        private set
    var alertSeverity by mutableStateOf(editingPost?.content?.alert?.severity)
        private set

    // --- Media ---
    val media = mutableStateListOf<WizardMedia>()

    /** People tagged in this post (max [POST_TAGGED_USERS_MAX_COUNT]). */
    val taggedUsers = mutableStateListOf<FriendUser>()

    // People search (tag section)
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
        editingPost?.content?.route?.stops?.let { stops.addAll(it) }
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

    /** Set after a failed publish so fields can highlight what is missing. */
    var visibleErrors by mutableStateOf<Set<PostWizardError>>(emptySet())
        private set

    private val _events = MutableSharedFlow<PostWizardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PostWizardEvent> = _events.asSharedFlow()

    private var locationSearchJob: Job? = null
    private var tagSearchJob: Job? = null

    fun updateText(value: String) {
        text = value.take(POST_TEXT_MAX_LENGTH)
        if (text.isNotBlank()) visibleErrors = visibleErrors - PostWizardError.TextRequired
    }

    // --- Location ---

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
        visibleErrors = visibleErrors - PostWizardError.AlertLocationRequired
    }

    fun clearLocation() {
        locationName = null
        coordinates = null
        locationQuery = ""
        locationResults = emptyList()
    }

    // --- Route / Alert exclusivity ---

    fun updateRouteEnabled(enabled: Boolean) {
        routeEnabled = enabled
        if (enabled) {
            alertEnabled = false
            visibleErrors = visibleErrors - PostWizardError.AlertCategoryRequired - PostWizardError.AlertLocationRequired
        } else {
            visibleErrors = visibleErrors - PostWizardError.StopsRequired
        }
    }

    fun updateAlertEnabled(enabled: Boolean) {
        alertEnabled = enabled
        if (enabled) {
            routeEnabled = false
            visibleErrors = visibleErrors - PostWizardError.StopsRequired
        } else {
            visibleErrors = visibleErrors - PostWizardError.AlertCategoryRequired - PostWizardError.AlertLocationRequired
        }
    }

    fun selectAlertCategory(category: AlertCategory) {
        alertCategory = category
        visibleErrors = visibleErrors - PostWizardError.AlertCategoryRequired
    }

    fun selectAlertSeverity(severity: AlertSeverity?) {
        alertSeverity = severity
    }

    // --- Route stops ---

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

    // --- Media ---

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

    // --- Tags ---

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

    private val taggedUserIds: List<String>
        get() = taggedUsers.map { it.id }

    /** All validation problems blocking publish. Empty means the form is valid. */
    fun validate(): Set<PostWizardError> = buildSet {
        if (text.isBlank() || text.length > POST_TEXT_MAX_LENGTH) add(PostWizardError.TextRequired)
        if (routeEnabled && stops.isEmpty()) add(PostWizardError.StopsRequired)
        if (alertEnabled) {
            if (alertCategory == null) add(PostWizardError.AlertCategoryRequired)
            if (coordinates == null) add(PostWizardError.AlertLocationRequired)
        }
    }

    val canPublish: Boolean
        get() = !isPublishing && !isUploadingMedia && validate().isEmpty()

    val hasUserInput: Boolean
        get() = text.isNotBlank() || locationName != null || routeEnabled || alertEnabled ||
            stops.isNotEmpty() || media.isNotEmpty() || taggedUsers.isNotEmpty()

    private fun buildContent(): PostContentInputDto {
        val route = if (routeEnabled && !alertEnabled) {
            RouteInputDto(
                stops = stops.map { RouteStopDto.from(it) },
                durationDays = durationDaysText.toIntOrNull(),
                lengthNm = lengthNmText.toDoubleOrNull(),
            )
        } else {
            null
        }
        val category = alertCategory
        val alert = if (alertEnabled && !routeEnabled && category != null) {
            AlertInputDto(category = category, severity = alertSeverity)
        } else {
            null
        }
        return PostContentInputDto(text = text.trim(), route = route, alert = alert)
    }

    private fun buildLocation(): PostLocationInputDto? {
        val name = locationName
        val point = coordinates
        if (name == null && point == null) return null
        return PostLocationInputDto(
            name = name,
            point = point?.let { CoordinatesDto.from(it) },
            area = null,
        )
    }

    internal fun buildRequest(): CreatePostRequest? {
        if (routeEnabled && alertEnabled) return null
        return CreatePostRequest(
            content = buildContent(),
            location = buildLocation(),
            mediaIds = uploadedMediaIds.takeIf { it.isNotEmpty() },
            taggedUserIds = taggedUserIds.takeIf { it.isNotEmpty() },
        )
    }

    internal fun buildUpdateRequest(): UpdatePostRequest? {
        if (routeEnabled && alertEnabled) return null
        return UpdatePostRequest(
            content = buildContent(),
            location = buildLocation(),
            mediaIds = uploadedMediaIds.takeIf { it.isNotEmpty() },
            taggedUserIds = taggedUserIds.takeIf { it.isNotEmpty() },
        )
    }

    fun publish() {
        if (isPublishing) return
        val errors = validate()
        if (errors.isNotEmpty()) {
            visibleErrors = errors
            return
        }
        visibleErrors = emptySet()
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
