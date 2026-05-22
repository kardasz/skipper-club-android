package app.skipperclub.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val OTP_LENGTH = 6

@Composable
fun OtpCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCodeComplete: (String) -> Unit = {},
    autoFocus: Boolean = true,
    contentDescription: String? = null,
    isError: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    BasicTextField(
        value = value,
        onValueChange = { incoming ->
            val sanitized = incoming.filter(Char::isDigit).take(OTP_LENGTH)
            if (sanitized != value) {
                onValueChange(sanitized)
                if (sanitized.length == OTP_LENGTH) onCodeComplete(sanitized)
            }
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            ),
        textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(OTP_LENGTH) { index ->
                    val char = value.getOrNull(index)
                    val isActive = index == value.length
                    val isFilled = char != null
                    val borderColor = when {
                        isError -> MaterialTheme.colorScheme.error
                        isActive -> MaterialTheme.colorScheme.primary
                        isFilled -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                    val borderWidth = if (isActive || isError) 2.dp else 1.dp
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .border(
                                width = borderWidth,
                                color = borderColor,
                                shape = RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalContentColor.current,
                        )
                    }
                }
            }
        },
    )
}
