package app.skipperclub.ui.main.cruises.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.skipperclub.R
import app.skipperclub.data.ReviewUser
import app.skipperclub.ui.main.cruises.CruiseAvatar

/**
 * Modal form for submitting a blind review of one crew member. Validates all four
 * ratings and the comment length locally before enabling submit.
 */
@Composable
fun SubmitReviewDialog(
    reviewedUser: ReviewUser,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (communication: Int, behavior: Int, skills: Int, duties: Int, comment: String) -> Unit,
) {
    var communication by rememberSaveable { mutableIntStateOf(0) }
    var behavior by rememberSaveable { mutableIntStateOf(0) }
    var skills by rememberSaveable { mutableIntStateOf(0) }
    var duties by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val ratings = mapOf(
        RatingCategory.Communication to communication,
        RatingCategory.Behavior to behavior,
        RatingCategory.Skills to skills,
        RatingCategory.Duties to duties,
    )
    val allRated = ratings.values.all { it in ReviewValidation.RATING_MIN..ReviewValidation.RATING_MAX }
    val trimmedLength = comment.trim().length
    val commentValid = trimmedLength in ReviewValidation.COMMENT_MIN..ReviewValidation.COMMENT_MAX
    val canSubmit = allRated && commentValid && !isSubmitting

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("submit_review_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CruiseAvatar(name = reviewedUser.name, avatarUrl = reviewedUser.avatarUrl, modifier = Modifier.size(44.dp))
                    Text(
                        text = stringResource(R.string.review_submit_title, reviewedUser.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                RatingRow(RatingCategory.Communication, communication, isSubmitting) { communication = it }
                RatingRow(RatingCategory.Behavior, behavior, isSubmitting) { behavior = it }
                RatingRow(RatingCategory.Skills, skills, isSubmitting) { skills = it }
                RatingRow(RatingCategory.Duties, duties, isSubmitting) { duties = it }

                if (showErrors && !allRated) {
                    Text(
                        text = stringResource(R.string.review_error_rating_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(ReviewValidation.COMMENT_MAX) },
                    label = { Text(stringResource(R.string.review_comment_label)) },
                    placeholder = { Text(stringResource(R.string.review_comment_placeholder)) },
                    isError = showErrors && !commentValid,
                    minLines = 4,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag("review_comment_field"),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val commentError = when {
                        !(showErrors && !commentValid) -> null
                        trimmedLength < ReviewValidation.COMMENT_MIN ->
                            stringResource(R.string.review_error_comment_min, ReviewValidation.COMMENT_MIN)
                        else -> stringResource(R.string.review_error_comment_max, ReviewValidation.COMMENT_MAX)
                    }
                    Text(
                        text = commentError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.review_comment_counter, trimmedLength, ReviewValidation.COMMENT_MAX),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text(stringResource(R.string.review_submit_cancel))
                    }
                    Button(
                        onClick = {
                            if (canSubmit) {
                                onSubmit(communication, behavior, skills, duties, comment.trim())
                            } else {
                                showErrors = true
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.padding(start = 8.dp).testTag("review_submit_button"),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.review_submit_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingRow(
    category: RatingCategory,
    value: Int,
    isSubmitting: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = stringResource(category.labelRes()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(category.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StarRatingInput(
            value = value,
            onValueChange = onValueChange,
            enabled = !isSubmitting,
            testTagPrefix = "star_${category.name.lowercase()}",
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
