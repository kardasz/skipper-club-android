package app.skipperclub.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

private const val PASSWORD_MIN = 8
private const val PASSWORD_MAX = 128

@Composable
fun PasswordResetScreen(
    email: String,
    code: String,
    onBack: () -> Unit,
    onSubmit: (email: String, code: String, password: String) -> Unit,
    passwordErrorMessage: String? = null,
    codeErrorMessage: String? = null,
    formErrorMessage: String? = null,
    onClearError: () -> Unit = {},
) {
    var password by rememberSaveable { mutableStateOf("") }
    var repeatedPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var repeatedPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var localRepeatedPasswordError by rememberSaveable { mutableStateOf(false) }
    val mismatchMessage = stringResource(R.string.password_reset_error_password_mismatch)

    PasswordResetScreenContent(
        email = email,
        password = password,
        onPasswordChange = {
            password = it.take(PASSWORD_MAX)
            localRepeatedPasswordError = false
            onClearError()
        },
        repeatedPassword = repeatedPassword,
        onRepeatedPasswordChange = {
            repeatedPassword = it.take(PASSWORD_MAX)
            localRepeatedPasswordError = false
            onClearError()
        },
        passwordVisible = passwordVisible,
        repeatedPasswordVisible = repeatedPasswordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onToggleRepeatedPasswordVisibility = { repeatedPasswordVisible = !repeatedPasswordVisible },
        onBack = onBack,
        onSubmit = {
            if (password != repeatedPassword) {
                localRepeatedPasswordError = true
            } else {
                onSubmit(email, code, password)
            }
        },
        passwordErrorMessage = passwordErrorMessage,
        repeatedPasswordErrorMessage = if (localRepeatedPasswordError) mismatchMessage else null,
        codeErrorMessage = codeErrorMessage,
        formErrorMessage = formErrorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordResetScreenContent(
    email: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    repeatedPassword: String,
    onRepeatedPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    repeatedPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    onToggleRepeatedPasswordVisibility: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    passwordErrorMessage: String?,
    repeatedPasswordErrorMessage: String?,
    codeErrorMessage: String?,
    formErrorMessage: String?,
) {
    val passwordFilled = password.length >= PASSWORD_MIN
    val repeatedPasswordFilled = repeatedPassword.length >= PASSWORD_MIN
    val canSubmit = passwordFilled && repeatedPasswordFilled
    val passwordTransformation = remember(passwordVisible) {
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    }
    val repeatedPasswordTransformation = remember(repeatedPasswordVisible) {
        if (repeatedPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
    }
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

    AuthScaffold(
        title = stringResource(R.string.password_reset_title),
        subtitle = stringResource(R.string.password_reset_subtitle, email),
        onBack = onBack,
    ) {
        if (formErrorMessage != null) {
            AuthErrorMessage(message = formErrorMessage)
        }

        if (codeErrorMessage != null) {
            AuthErrorMessage(message = codeErrorMessage)
        }

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password-reset-new-password"),
            placeholder = { Text(stringResource(R.string.password_reset_new_password_placeholder)) },
            isError = passwordErrorMessage != null,
            supportingText = {
                Text(passwordErrorMessage ?: stringResource(R.string.password_reset_password_helper))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.password_content_description),
                )
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        painter = painterResource(
                            if (passwordVisible) R.drawable.ic_visibility_off
                            else R.drawable.ic_visibility
                        ),
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.password_hide
                            else R.string.password_show
                        ),
                    )
                }
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = passwordTransformation,
        )

        OutlinedTextField(
            value = repeatedPassword,
            onValueChange = onRepeatedPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password-reset-repeat-password"),
            placeholder = { Text(stringResource(R.string.password_reset_repeat_password_placeholder)) },
            isError = repeatedPasswordErrorMessage != null,
            supportingText = repeatedPasswordErrorMessage?.let { message ->
                { Text(message) }
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.password_content_description),
                )
            },
            trailingIcon = {
                IconButton(onClick = onToggleRepeatedPasswordVisibility) {
                    Icon(
                        painter = painterResource(
                            if (repeatedPasswordVisible) R.drawable.ic_visibility_off
                            else R.drawable.ic_visibility
                        ),
                        contentDescription = stringResource(
                            if (repeatedPasswordVisible) R.string.password_hide
                            else R.string.password_show
                        ),
                    )
                }
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = repeatedPasswordTransformation,
        )

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = canSubmit,
        ) {
            Text(
                text = stringResource(R.string.password_reset_submit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun PasswordResetPreviewEn() {
    SkipperClubTheme {
        PasswordResetScreenContent(
            email = "jan.kowalski@email.com",
            password = "",
            onPasswordChange = {},
            repeatedPassword = "",
            onRepeatedPasswordChange = {},
            passwordVisible = false,
            repeatedPasswordVisible = false,
            onTogglePasswordVisibility = {},
            onToggleRepeatedPasswordVisibility = {},
            onBack = {},
            onSubmit = {},
            passwordErrorMessage = null,
            repeatedPasswordErrorMessage = null,
            codeErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PasswordResetPreviewPl() {
    SkipperClubTheme {
        PasswordResetScreenContent(
            email = "jan.kowalski@email.com",
            password = "Secret123!",
            onPasswordChange = {},
            repeatedPassword = "Secret123?",
            onRepeatedPasswordChange = {},
            passwordVisible = true,
            repeatedPasswordVisible = true,
            onTogglePasswordVisibility = {},
            onToggleRepeatedPasswordVisibility = {},
            onBack = {},
            onSubmit = {},
            passwordErrorMessage = null,
            repeatedPasswordErrorMessage = stringResource(R.string.password_reset_error_password_mismatch),
            codeErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PasswordResetPreviewDark() {
    SkipperClubTheme {
        PasswordResetScreenContent(
            email = "jan.kowalski@email.com",
            password = "short",
            onPasswordChange = {},
            repeatedPassword = "short",
            onRepeatedPasswordChange = {},
            passwordVisible = false,
            repeatedPasswordVisible = false,
            onTogglePasswordVisibility = {},
            onToggleRepeatedPasswordVisibility = {},
            onBack = {},
            onSubmit = {},
            passwordErrorMessage = stringResource(R.string.auth_error_invalid_password),
            repeatedPasswordErrorMessage = null,
            codeErrorMessage = stringResource(R.string.auth_error_invalid_password_reset_code),
            formErrorMessage = stringResource(R.string.auth_error_rate_limit),
        )
    }
}
