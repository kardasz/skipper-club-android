package app.skipperclub.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

private const val OTP_LENGTH = 6

@Composable
fun OtpVerifyScreen(
    email: String,
    onBack: () -> Unit,
    onVerify: (email: String, code: String) -> Unit,
    onResend: (email: String) -> Unit,
    codeErrorMessage: String? = null,
    formErrorMessage: String? = null,
    onClearError: () -> Unit = {},
) {
    var code by rememberSaveable { mutableStateOf("") }
    OtpVerifyScreenContent(
        email = email,
        code = code,
        onCodeChange = {
            code = it
            onClearError()
        },
        onBack = onBack,
        onVerify = { onVerify(email, code) },
        onResend = { onResend(email) },
        codeErrorMessage = codeErrorMessage,
        formErrorMessage = formErrorMessage,
    )
}

@Composable
private fun OtpVerifyScreenContent(
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    codeErrorMessage: String?,
    formErrorMessage: String?,
) {
    val codeComplete = code.length == OTP_LENGTH

    AuthScaffold(
        title = stringResource(R.string.otp_title),
        subtitle = stringResource(R.string.otp_subtitle, email),
        onBack = onBack,
    ) {
        if (formErrorMessage != null) {
            AuthErrorMessage(message = formErrorMessage)
        }

        OtpCodeInput(
            value = code,
            onValueChange = onCodeChange,
            onCodeComplete = { onVerify() },
            contentDescription = stringResource(R.string.otp_input_content_description),
            isError = codeErrorMessage != null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (codeErrorMessage != null) {
            Text(
                text = codeErrorMessage,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onVerify,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = codeComplete,
        ) {
            Text(
                text = stringResource(R.string.otp_verify),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.otp_didnt_receive),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onResend,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.otp_resend),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun OtpVerifyScreenPreviewEmpty() {
    SkipperClubTheme {
        OtpVerifyScreenContent(
            email = "jan.kowalski@email.com",
            code = "",
            onCodeChange = {},
            onBack = {},
            onVerify = {},
            onResend = {},
            codeErrorMessage = null,
            formErrorMessage = null,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun OtpVerifyScreenPreviewPartial() {
    SkipperClubTheme {
        OtpVerifyScreenContent(
            email = "jan.kowalski@email.com",
            code = "123",
            onCodeChange = {},
            onBack = {},
            onVerify = {},
            onResend = {},
            codeErrorMessage = stringResource(R.string.auth_error_invalid_otp),
            formErrorMessage = null,
        )
    }
}
