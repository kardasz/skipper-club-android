package app.skipperclub.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

private const val EMAIL_MAX = 320

@Composable
fun PasswordResetRequestScreen(
    initialEmail: String = "",
    linkSent: Boolean = false,
    onBack: () -> Unit,
    onSubmit: (email: String) -> Unit,
    onSignIn: (email: String) -> Unit,
    emailErrorMessage: String? = null,
    formErrorMessage: String? = null,
    onClearError: () -> Unit = {},
) {
    val emailState = rememberSaveable { mutableStateOf(initialEmail) }

    PasswordResetRequestScreenContent(
        email = emailState.value,
        onEmailChange = {
            emailState.value = it.trim().take(EMAIL_MAX)
            onClearError()
        },
        linkSent = linkSent,
        onBack = onBack,
        onSubmit = { onSubmit(emailState.value.trim()) },
        onSignIn = { onSignIn(emailState.value.trim()) },
        emailErrorMessage = emailErrorMessage,
        formErrorMessage = formErrorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordResetRequestScreenContent(
    email: String,
    onEmailChange: (String) -> Unit,
    linkSent: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    emailErrorMessage: String?,
    formErrorMessage: String?,
) {
    AuthScaffold(
        title = stringResource(
            if (linkSent) R.string.password_reset_request_sent_title
            else R.string.password_reset_request_title
        ),
        subtitle = if (linkSent) {
            stringResource(R.string.password_reset_request_sent_subtitle, email)
        } else {
            stringResource(R.string.password_reset_request_subtitle)
        },
        onBack = onBack,
    ) {
        if (formErrorMessage != null) {
            AuthErrorMessage(message = formErrorMessage)
        }

        if (linkSent) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    text = stringResource(R.string.password_reset_request_sent_body),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.password_reset_back_to_sign_in),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            return@AuthScaffold
        }

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.login_email_placeholder)) },
            isError = emailErrorMessage != null,
            supportingText = emailErrorMessage?.let { message ->
                { Text(message) }
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_mail),
                    contentDescription = stringResource(R.string.login_email_content_description),
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        )

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = email.isNotBlank(),
        ) {
            Text(
                text = stringResource(R.string.password_reset_request_submit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun PasswordResetRequestPreviewEn() {
    SkipperClubTheme {
        PasswordResetRequestScreenContent(
            email = "jan.kowalski@email.com",
            onEmailChange = {},
            linkSent = false,
            onBack = {},
            onSubmit = {},
            onSignIn = {},
            emailErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PasswordResetRequestPreviewPl() {
    SkipperClubTheme {
        PasswordResetRequestScreenContent(
            email = "jan.kowalski@email.com",
            onEmailChange = {},
            linkSent = true,
            onBack = {},
            onSubmit = {},
            onSignIn = {},
            emailErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PasswordResetRequestPreviewDark() {
    SkipperClubTheme {
        PasswordResetRequestScreenContent(
            email = "not-an-email",
            onEmailChange = {},
            linkSent = false,
            onBack = {},
            onSubmit = {},
            onSignIn = {},
            emailErrorMessage = stringResource(R.string.auth_error_invalid_email),
            formErrorMessage = stringResource(R.string.auth_error_captcha),
        )
    }
}
