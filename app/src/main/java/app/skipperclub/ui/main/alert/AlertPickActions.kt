package app.skipperclub.ui.main.alert

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipperclub.R
import app.skipperclub.ui.theme.SkipperClubTheme

/**
 * Bottom-aligned actions shown while the user is aiming the map at the alert
 * location: "Next" advances to the form, "Cancel" leaves the flow.
 */
@Composable
fun AlertPickActions(
    onNext: () -> Unit,
    onCancel: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .widthIn(min = 224.dp, max = 320.dp)
                    .defaultMinSize(minHeight = 60.dp)
                    .testTag("alert_pick_next"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.alert_next),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier
                    .widthIn(min = 224.dp, max = 320.dp)
                    .defaultMinSize(minHeight = 56.dp)
                    .testTag("alert_pick_cancel"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.alert_cancel),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 320, locale = "pl")
@Composable
private fun AlertPickActionsPreviewPl() {
    SkipperClubTheme {
        AlertPickActions(onNext = {}, onCancel = {}, bottomInset = 24.dp)
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 320,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AlertPickActionsPreviewDark() {
    SkipperClubTheme {
        AlertPickActions(onNext = {}, onCancel = {}, bottomInset = 24.dp)
    }
}
