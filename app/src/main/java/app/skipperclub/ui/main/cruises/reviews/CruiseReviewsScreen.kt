package app.skipperclub.ui.main.cruises.reviews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.data.Review
import app.skipperclub.data.ReviewUser
import app.skipperclub.data.ReviewsError
import app.skipperclub.data.SessionStore
import app.skipperclub.ui.main.cruises.CruiseAvatar
import app.skipperclub.ui.main.cruises.formatCruiseDate
import app.skipperclub.ui.main.cruises.previewCruise
import app.skipperclub.ui.notification.InAppNotificationHost
import app.skipperclub.ui.notification.InAppNotificationType
import app.skipperclub.ui.notification.rememberInAppNotificationHostState
import app.skipperclub.ui.theme.SkipperClubTheme

/** Full-screen blind-review center for a single cruise. Hosts the submit form as a nested dialog. */
@Composable
fun CruiseReviewsScreen(
    cruiseId: String,
    currentUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope, cruiseId) {
        CruiseReviewsController(
            scope = scope,
            accessToken = { SessionStore.validSession()?.accessToken },
            currentUserId = { currentUserId },
            cruiseId = cruiseId,
        )
    }
    val state by controller.state.collectAsState()
    val notificationHostState = rememberInAppNotificationHostState()

    val errorNetwork = stringResource(R.string.review_error_network)
    val errorAuth = stringResource(R.string.review_error_auth)
    val errorForbidden = stringResource(R.string.review_error_not_participant)
    val errorNotCompleted = stringResource(R.string.review_error_not_completed)
    val errorAlready = stringResource(R.string.review_error_already_reviewed)
    val errorSelf = stringResource(R.string.review_error_cannot_self)
    val errorGeneric = stringResource(R.string.review_error_generic)
    val toastPublished = stringResource(R.string.review_toast_submitted_published)
    val toastPendingTemplate = stringResource(R.string.review_toast_submitted_pending_template)

    fun submitError(error: Exception): String = when (error) {
        is ReviewsError.Network -> errorNetwork
        is ReviewsError.AuthenticationRequired -> errorAuth
        is ReviewsError.Forbidden -> errorForbidden
        is ReviewsError.CruiseNotCompleted -> errorNotCompleted
        is ReviewsError.AlreadyReviewed -> errorAlready
        is ReviewsError.CannotReviewSelf -> errorSelf
        else -> errorGeneric
    }

    LaunchedEffect(controller) { controller.load() }
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is CruiseReviewsEvent.SubmitFailed ->
                    notificationHostState.show(submitError(event.error), InAppNotificationType.Error)
                is CruiseReviewsEvent.Submitted -> {
                    val message = if (event.published) {
                        toastPublished
                    } else {
                        String.format(toastPendingTemplate, event.reviewedUserName)
                    }
                    notificationHostState.show(message, InAppNotificationType.Success)
                }
                CruiseReviewsEvent.SessionExpired ->
                    notificationHostState.show(errorAuth, InAppNotificationType.Error)
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize().testTag("cruise_reviews"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CruiseReviewsContent(
                state = state,
                currentUserId = currentUserId,
                onClose = onClose,
                onWriteReview = controller::openSubmit,
                onRetry = controller::load,
            )
            InAppNotificationHost(hostState = notificationHostState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    state.submitTarget?.let { target ->
        SubmitReviewDialog(
            reviewedUser = target,
            isSubmitting = state.isSubmitting,
            onDismiss = controller::closeSubmit,
            onSubmit = { communication, behavior, skills, duties, comment ->
                controller.submit(target, communication, behavior, skills, duties, comment)
            },
        )
    }
}

@Composable
internal fun CruiseReviewsContent(
    state: CruiseReviewsUiState,
    currentUserId: String?,
    onClose: () -> Unit,
    onWriteReview: (ReviewUser) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        ReviewsTopBar(onClose = onClose)
        when (state.accessState) {
            ReviewsAccessState.Loading ->
                Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }

            ReviewsAccessState.LoadFailed -> CenteredMessage(
                icon = Icons.Outlined.RateReview,
                title = stringResource(R.string.review_load_failed_title),
                description = stringResource(R.string.review_load_failed_description),
                actionLabel = stringResource(R.string.cruises_retry),
                onAction = onRetry,
            )

            ReviewsAccessState.AccessDenied -> CenteredMessage(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.review_access_denied_title),
                description = stringResource(R.string.review_access_denied_description),
            )

            ReviewsAccessState.NotCompleted -> CenteredMessage(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.review_not_completed_title),
                description = stringResource(
                    R.string.review_not_completed_description,
                    formatCruiseDate(state.cruise?.arrivalDate.orEmpty()),
                ),
            )

            ReviewsAccessState.Ready -> ReadyContent(
                state = state,
                currentUserId = currentUserId,
                onWriteReview = onWriteReview,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: CruiseReviewsUiState,
    currentUserId: String?,
    onWriteReview: (ReviewUser) -> Unit,
) {
    val reviewable = state.reviewableUsers(currentUserId)
    val given = state.givenReviews(currentUserId)
    val received = state.receivedReviews(currentUserId)
    var bannerVisible by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cruise_reviews_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.cruise?.let { cruise ->
            item {
                Text(
                    text = cruise.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (bannerVisible) {
            item { BlindBanner(onDismiss = { bannerVisible = false }) }
        }

        item { SectionHeader(stringResource(R.string.review_section_people_title)) }
        if (reviewable.isEmpty()) {
            item {
                CardMessage(
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.review_people_all_done_title),
                    description = stringResource(R.string.review_people_all_done_description),
                )
            }
        } else {
            items(reviewable.size, key = { reviewable[it].id }) { index ->
                ReviewableRow(user = reviewable[index], onWriteReview = onWriteReview)
            }
        }

        item { SectionHeader(stringResource(R.string.review_section_given_title)) }
        if (given.isEmpty()) {
            item {
                CardMessage(
                    icon = Icons.Outlined.RateReview,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.review_given_empty_title),
                    description = stringResource(R.string.review_given_empty_description),
                )
            }
        } else {
            items(given.size, key = { "given_${given[it].id}" }) { index ->
                ReviewCard(review = given[index], currentUserId = currentUserId)
            }
        }

        item { SectionHeader(stringResource(R.string.review_section_received_title)) }
        if (received.isEmpty()) {
            item {
                CardMessage(
                    icon = Icons.Outlined.RateReview,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.review_received_empty_title),
                    description = stringResource(R.string.review_received_empty_description),
                )
            }
        } else {
            items(received.size, key = { "received_${received[it].id}" }) { index ->
                ReviewCard(review = received[index], currentUserId = currentUserId)
            }
        }
    }
}

@Composable
private fun ReviewsTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("cruise_reviews_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
        }
        Text(
            text = stringResource(R.string.review_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BlindBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = stringResource(R.string.review_blind_banner_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.review_blind_banner_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).testTag("review_banner_dismiss")) {
            Text("×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ReviewableRow(user: ReviewUser, onWriteReview: (ReviewUser) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("reviewable_${user.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CruiseAvatar(name = user.name, avatarUrl = user.avatarUrl, modifier = Modifier.size(40.dp))
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
        )
        Button(onClick = { onWriteReview(user) }, modifier = Modifier.testTag("write_review_${user.id}")) {
            Text(stringResource(R.string.review_people_action))
        }
    }
}

@Composable
private fun CardMessage(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
            }
        }
    }
}

// --- Previews ---

private const val PREVIEW_ME = "me"

private fun previewReviewUser(id: String, name: String) = ReviewUser(id = id, name = name)

private fun previewReview(
    id: String,
    reviewer: ReviewUser,
    reviewedUser: ReviewUser,
    comment: String,
): Review = Review(
    id = id,
    cruiseId = "c1",
    reviewer = reviewer,
    reviewedUser = reviewedUser,
    cruise = null,
    ratings = app.skipperclub.data.ReviewRatings(5, 4, 5, 4, 4.5),
    comment = comment,
    status = app.skipperclub.data.ReviewStatus.Published,
    createdAt = "2026-03-14T10:00:00Z",
    updatedAt = "2026-03-14T12:00:00Z",
)

private fun previewReadyState(): CruiseReviewsUiState {
    val me = previewReviewUser(PREVIEW_ME, "Anna Nowak")
    val jan = previewReviewUser("org", "Jan Kowalski")
    val piotr = previewReviewUser("p2", "Piotr Wiśniewski")
    val cruise = previewCruise(title = "Tygodniowy rejs w Chorwacji").copy(
        arrivalDate = "2026-03-12",
        organizer = app.skipperclub.data.CruiseUser(id = "org", name = "Jan Kowalski"),
        participants = listOf(
            app.skipperclub.data.CruiseUser(id = PREVIEW_ME, name = "Anna Nowak"),
            app.skipperclub.data.CruiseUser(id = "p2", name = "Piotr Wiśniewski"),
        ),
    )
    return CruiseReviewsUiState(
        isLoading = false,
        cruise = cruise,
        canAccess = true,
        isCompleted = true,
        reviews = listOf(
            previewReview("r1", reviewer = me, reviewedUser = jan, comment = "Świetny skipper, dużo nauczyłam się o nawigacji. Bardzo cierpliwy i pomocny przez cały tydzień na pokładzie."),
            previewReview("r2", reviewer = piotr, reviewedUser = me, comment = "Great crew member, always helpful and positive. Would gladly sail together again on the next trip!"),
        ),
        // Piotr still needs a review from me → he stays in "people to review".
        submittedUserIds = emptySet(),
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900, locale = "en")
@Composable
private fun CruiseReviewsReadyPreview() {
    SkipperClubTheme {
        CruiseReviewsContent(
            state = previewReadyState(),
            currentUserId = PREVIEW_ME,
            onClose = {},
            onWriteReview = {},
            onRetry = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 400,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CruiseReviewsReadyPreviewDark() {
    SkipperClubTheme {
        CruiseReviewsContent(
            state = previewReadyState(),
            currentUserId = PREVIEW_ME,
            onClose = {},
            onWriteReview = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900, locale = "pl")
@Composable
private fun CruiseReviewsReadyPreviewPl() {
    SkipperClubTheme {
        CruiseReviewsContent(
            state = previewReadyState(),
            currentUserId = PREVIEW_ME,
            onClose = {},
            onWriteReview = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 700, locale = "pl")
@Composable
private fun CruiseReviewsNotCompletedPreviewPl() {
    SkipperClubTheme {
        CruiseReviewsContent(
            state = previewReadyState().copy(isCompleted = false),
            currentUserId = PREVIEW_ME,
            onClose = {},
            onWriteReview = {},
            onRetry = {},
        )
    }
}
