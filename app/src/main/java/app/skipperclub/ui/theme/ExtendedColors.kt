package app.skipperclub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that don't map cleanly to Material 3's ColorScheme roles.
 * Accessed via `MaterialTheme.extended` (see [androidx.compose.material3.MaterialTheme]
 * extension in `Theme.kt`) so they participate in the palette swap.
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

internal val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    error("ExtendedColors not provided. Wrap your composable in SkipperClubTheme.")
}
