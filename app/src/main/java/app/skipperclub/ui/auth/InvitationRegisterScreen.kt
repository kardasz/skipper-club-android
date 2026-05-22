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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

private const val CODE_LENGTH = 8
private const val PASSWORD_MIN = 8
private const val PASSWORD_MAX = 128
private const val NAME_MAX = 100
private const val EMAIL_MAX = 320

@Composable
fun InvitationRegisterScreen(
    initialCode: String = "",
    onBack: () -> Unit,
    onSubmit: (code: String, name: String, email: String, password: String) -> Unit,
    codeErrorMessage: String? = null,
    nameErrorMessage: String? = null,
    emailErrorMessage: String? = null,
    passwordErrorMessage: String? = null,
    formErrorMessage: String? = null,
    onClearError: () -> Unit = {},
) {
    var code by rememberSaveable { mutableStateOf(sanitizeCode(initialCode)) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    InvitationRegisterContent(
        code = code,
        onCodeChange = {
            code = sanitizeCode(it)
            onClearError()
        },
        name = name,
        onNameChange = {
            name = it.take(NAME_MAX)
            onClearError()
        },
        email = email,
        onEmailChange = {
            email = it.trim().take(EMAIL_MAX)
            onClearError()
        },
        password = password,
        onPasswordChange = {
            password = it.take(PASSWORD_MAX)
            onClearError()
        },
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onBack = onBack,
        onSubmit = {
            onSubmit(code, name.trim(), email.trim(), password)
        },
        codeErrorMessage = codeErrorMessage,
        nameErrorMessage = nameErrorMessage,
        emailErrorMessage = emailErrorMessage,
        passwordErrorMessage = passwordErrorMessage,
        formErrorMessage = formErrorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvitationRegisterContent(
    code: String,
    onCodeChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    codeErrorMessage: String?,
    nameErrorMessage: String?,
    emailErrorMessage: String?,
    passwordErrorMessage: String?,
    formErrorMessage: String?,
) {
    val codeFilled = code.length == CODE_LENGTH
    val nameFilled = name.trim().isNotEmpty()
    val emailFilled = email.trim().isNotEmpty()
    val passwordFilled = password.length >= PASSWORD_MIN
    val canSubmit = codeFilled && nameFilled && emailFilled && passwordFilled

    val visualTransformation = remember(passwordVisible) {
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    }

    val fieldShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

    AuthScaffold(
        title = stringResource(R.string.invitation_title),
        subtitle = stringResource(R.string.invitation_subtitle),
        onBack = onBack,
    ) {
        if (formErrorMessage != null) {
            AuthErrorMessage(message = formErrorMessage)
        }

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.invitation_code_placeholder)) },
            isError = codeErrorMessage != null,
            supportingText = {
                Text(codeErrorMessage ?: stringResource(R.string.invitation_code_helper))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.invitation_code_content_description),
                )
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.invitation_name_placeholder)) },
            isError = nameErrorMessage != null,
            supportingText = nameErrorMessage?.let { message ->
                { Text(message) }
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = stringResource(R.string.invitation_name_content_description),
                )
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.invitation_email_placeholder)) },
            isError = emailErrorMessage != null,
            supportingText = emailErrorMessage?.let { message ->
                { Text(message) }
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_mail),
                    contentDescription = stringResource(R.string.invitation_email_content_description),
                )
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.invitation_password_placeholder)) },
            isError = passwordErrorMessage != null,
            supportingText = {
                Text(passwordErrorMessage ?: stringResource(R.string.invitation_password_helper))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.invitation_password_content_description),
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
                            if (passwordVisible) R.string.invitation_password_hide
                            else R.string.invitation_password_show
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
            visualTransformation = visualTransformation,
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
                text = stringResource(R.string.invitation_submit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun sanitizeCode(raw: String): String =
    raw.uppercase()
        .filter { it.isLetterOrDigit() && it.code < 128 }
        .take(CODE_LENGTH)

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun InvitationRegisterPreviewEmpty() {
    SkipperClubTheme {
        InvitationRegisterContent(
            code = "",
            onCodeChange = {},
            name = "",
            onNameChange = {},
            email = "",
            onEmailChange = {},
            password = "",
            onPasswordChange = {},
            passwordVisible = false,
            onTogglePasswordVisibility = {},
            onBack = {},
            onSubmit = {},
            codeErrorMessage = null,
            nameErrorMessage = null,
            emailErrorMessage = null,
            passwordErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun InvitationRegisterPreviewFilled() {
    SkipperClubTheme {
        InvitationRegisterContent(
            code = "ABC12345",
            onCodeChange = {},
            name = "Anna Nowak",
            onNameChange = {},
            email = "anna@example.com",
            onEmailChange = {},
            password = "SecurePass123",
            onPasswordChange = {},
            passwordVisible = true,
            onTogglePasswordVisibility = {},
            onBack = {},
            onSubmit = {},
            codeErrorMessage = stringResource(R.string.auth_error_invalid_invitation),
            nameErrorMessage = null,
            emailErrorMessage = stringResource(R.string.auth_error_invitation_email_mismatch),
            passwordErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InvitationRegisterPreviewDark() {
    SkipperClubTheme {
        InvitationRegisterContent(
            code = "XYZ98765",
            onCodeChange = {},
            name = "",
            onNameChange = {},
            email = "",
            onEmailChange = {},
            password = "",
            onPasswordChange = {},
            passwordVisible = false,
            onTogglePasswordVisibility = {},
            onBack = {},
            onSubmit = {},
            codeErrorMessage = null,
            nameErrorMessage = null,
            emailErrorMessage = null,
            passwordErrorMessage = null,
            formErrorMessage = stringResource(R.string.auth_error_captcha),
        )
    }
}
