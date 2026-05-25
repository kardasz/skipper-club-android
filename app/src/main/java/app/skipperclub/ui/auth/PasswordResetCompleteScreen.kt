package app.skipperclub.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

@Composable
fun PasswordResetCompleteScreen(
    email: String,
    onSignIn: (email: String) -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.password_reset_complete_title),
        subtitle = stringResource(R.string.password_reset_complete_subtitle),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ) {
            Text(
                text = stringResource(R.string.password_reset_complete_body),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = { onSignIn(email) },
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
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "en")
@Composable
private fun PasswordResetCompletePreviewEn() {
    SkipperClubTheme {
        PasswordResetCompleteScreen(
            email = "jan.kowalski@email.com",
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, locale = "pl")
@Composable
private fun PasswordResetCompletePreviewPl() {
    SkipperClubTheme {
        PasswordResetCompleteScreen(
            email = "jan.kowalski@email.com",
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 740, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PasswordResetCompletePreviewDark() {
    SkipperClubTheme {
        PasswordResetCompleteScreen(
            email = "jan.kowalski@email.com",
            onSignIn = {},
        )
    }
}
