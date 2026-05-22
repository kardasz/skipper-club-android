package app.skipperclub.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun PasswordScreen(
    email: String,
    onBack: () -> Unit,
    onContinue: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    passwordErrorMessage: String? = null,
    formErrorMessage: String? = null,
    onClearError: () -> Unit = {},
) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    PasswordScreenContent(
        email = email,
        password = password,
        onPasswordChange = {
            password = it
            onClearError()
        },
        passwordVisible = visible,
        onToggleVisibility = { visible = !visible },
        onBack = onBack,
        onContinue = { onContinue(email, password) },
        onForgotPassword = { onForgotPassword(email) },
        passwordErrorMessage = passwordErrorMessage,
        formErrorMessage = formErrorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordScreenContent(
    email: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onForgotPassword: () -> Unit,
    passwordErrorMessage: String?,
    formErrorMessage: String?,
) {
    val passwordFilled = password.length >= 8
    val visualTransformation = remember(passwordVisible) {
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    }

    AuthScaffold(
        title = stringResource(R.string.password_title),
        subtitle = stringResource(R.string.password_subtitle, email),
        onBack = onBack,
    ) {
        if (formErrorMessage != null) {
            AuthErrorMessage(message = formErrorMessage)
        }

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.password_placeholder)) },
            isError = passwordErrorMessage != null,
            supportingText = passwordErrorMessage?.let { message ->
                { Text(message) }
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_key),
                    contentDescription = stringResource(R.string.password_content_description),
                )
            },
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
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
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = visualTransformation,
        )

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = passwordFilled,
        ) {
            Text(
                text = stringResource(R.string.password_continue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onForgotPassword,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.password_forgot),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun PasswordScreenPreview() {
    SkipperClubTheme {
        PasswordScreenContent(
            email = "jan.kowalski@email.com",
            password = "",
            onPasswordChange = {},
            passwordVisible = false,
            onToggleVisibility = {},
            onBack = {},
            onContinue = {},
            onForgotPassword = {},
            passwordErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PasswordScreenPreviewPl() {
    SkipperClubTheme {
        PasswordScreenContent(
            email = "jan.kowalski@email.com",
            password = "secret123",
            onPasswordChange = {},
            passwordVisible = true,
            onToggleVisibility = {},
            onBack = {},
            onContinue = {},
            onForgotPassword = {},
            passwordErrorMessage = stringResource(R.string.auth_error_invalid_credentials),
            formErrorMessage = null,
        )
    }
}
