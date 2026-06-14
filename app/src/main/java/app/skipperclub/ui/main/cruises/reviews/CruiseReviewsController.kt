package app.skipperclub.ui.main.cruises.reviews

import app.skipperclub.data.CreateReviewPayload
import app.skipperclub.data.Cruise
import app.skipperclub.data.CruiseParticipantState
import app.skipperclub.data.CruiseUserRole
import app.skipperclub.data.CruisesError
import app.skipperclub.data.Review
import app.skipperclub.data.ReviewStatus
import app.skipperclub.data.ReviewUser
import app.skipperclub.data.ReviewsError
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which variant of the reviews center the current user should see. */
enum class ReviewsAccessState { Loading, LoadFailed, AccessDenied, NotCompleted, Ready }

data class CruiseReviewsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val cruise: Cruise? = null,
    val reviews: List<Review> = emptyList(),
    /** Users reviewed this session (covers the just-submitted pending review the list won't return). */
    val submittedUserIds: Set<String> = emptySet(),
    val isCompleted: Boolean = false,
    val canAccess: Boolean = false,
    val submitTarget: ReviewUser? = null,
    val isSubmitting: Boolean = false,
) {
    val accessState: ReviewsAccessState
        get() = when {
            isLoading && cruise == null -> ReviewsAccessState.Loading
            cruise == null -> ReviewsAccessState.LoadFailed
            !canAccess -> ReviewsAccessState.AccessDenied
            !isCompleted -> ReviewsAccessState.NotCompleted
            else -> ReviewsAccessState.Ready
        }
}

sealed interface CruiseReviewsEvent {
    data class SubmitFailed(val error: Exception) : CruiseReviewsEvent
    data class Submitted(val reviewedUserName: String, val published: Boolean) : CruiseReviewsEvent
    data object SessionExpired : CruiseReviewsEvent
}

/**
 * State holder for the blind-review center of a single cruise. Computes eligibility
 * (organizer or accepted participant + cruise completed), the list of crew still to
 * review, and owns the submission flow. The screen only renders [CruiseReviewsUiState].
 */
class CruiseReviewsController(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val currentUserId: () -> String?,
    private val cruiseId: String,
    private val gateway: ReviewsGateway = RealReviewsGateway,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val _state = MutableStateFlow(CruiseReviewsUiState())
    val state: StateFlow<CruiseReviewsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CruiseReviewsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CruiseReviewsEvent> = _events.asSharedFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            try {
                val cruise = gateway.getCruise(token, cruiseId)
                val canAccess = cruise.canAccessReviews()
                val completed = cruise.isCompleted()
                val reviews = if (canAccess && completed) {
                    gateway.listReviews(token, cruiseId).reviews
                } else {
                    emptyList()
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        cruise = cruise,
                        canAccess = canAccess,
                        isCompleted = completed,
                        reviews = reviews,
                    )
                }
            } catch (error: Exception) {
                if (error is CruisesError.AuthenticationRequired || error is ReviewsError.AuthenticationRequired) {
                    _events.tryEmit(CruiseReviewsEvent.SessionExpired)
                }
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun openSubmit(user: ReviewUser) {
        _state.update { it.copy(submitTarget = user) }
    }

    fun closeSubmit() {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(submitTarget = null) }
    }

    fun submit(
        reviewedUser: ReviewUser,
        communication: Int,
        behavior: Int,
        skills: Int,
        duties: Int,
        comment: String,
    ) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isSubmitting = false) }
                return@launch
            }
            try {
                val review = gateway.createReview(
                    accessToken = token,
                    cruiseId = cruiseId,
                    payload = CreateReviewPayload(
                        reviewedUserId = reviewedUser.id,
                        communication = communication,
                        behavior = behavior,
                        skills = skills,
                        duties = duties,
                        comment = comment.trim(),
                    ),
                )
                // Refresh published reviews; the pending one won't appear until reciprocated.
                val reviews = runCatching { gateway.listReviews(token, cruiseId).reviews }
                    .getOrDefault(_state.value.reviews)
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitTarget = null,
                        reviews = reviews,
                        submittedUserIds = it.submittedUserIds + reviewedUser.id,
                    )
                }
                _events.tryEmit(
                    CruiseReviewsEvent.Submitted(
                        reviewedUserName = reviewedUser.name,
                        published = review.status == ReviewStatus.Published,
                    ),
                )
            } catch (error: Exception) {
                if (error is ReviewsError.AuthenticationRequired) {
                    _events.tryEmit(CruiseReviewsEvent.SessionExpired)
                }
                _state.update { it.copy(isSubmitting = false) }
                _events.tryEmit(CruiseReviewsEvent.SubmitFailed(error))
            }
        }
    }

    private fun Cruise.canAccessReviews(): Boolean =
        currentUserRole == CruiseUserRole.Organizer ||
            currentUserParticipation?.state == CruiseParticipantState.Accepted

    private fun Cruise.isCompleted(): Boolean {
        val arrival = runCatching { LocalDate.parse(arrivalDate.take(10)) }.getOrNull() ?: return true
        return arrival.isBefore(today())
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()
        if (token == null) _events.tryEmit(CruiseReviewsEvent.SessionExpired)
        return token
    }
}

/** Crew members the current user can still review: all accepted members minus self and already-reviewed. */
fun CruiseReviewsUiState.reviewableUsers(currentUserId: String?): List<ReviewUser> {
    val cruise = cruise ?: return emptyList()
    val me = currentUserId
    val members = buildList {
        add(ReviewUser(cruise.organizer.id, cruise.organizer.name, cruise.organizer.avatarUrl))
        cruise.participants.forEach { add(ReviewUser(it.id, it.name, it.avatarUrl)) }
    }.distinctBy { it.id }
    val alreadyReviewed = reviews
        .filter { it.reviewer.id == me }
        .map { it.reviewedUser.id }
        .toSet() + submittedUserIds
    return members.filter { it.id != me && it.id !in alreadyReviewed }
}

/** Published reviews the current user wrote about others. */
fun CruiseReviewsUiState.givenReviews(currentUserId: String?): List<Review> =
    reviews.filter { it.reviewer.id == currentUserId }

/** Published reviews other crew members wrote about the current user. */
fun CruiseReviewsUiState.receivedReviews(currentUserId: String?): List<Review> =
    reviews.filter { it.reviewedUser.id == currentUserId }
