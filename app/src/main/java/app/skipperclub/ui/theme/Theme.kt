package app.skipperclub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Currently active [SkipperPalette]. Defaults to [SkipperPalettes.Default]. */
val LocalSkipperPalette = staticCompositionLocalOf { SkipperPalettes.Default }

@Composable
fun SkipperClubTheme(
    palette: SkipperPalette = LocalSkipperPalette.current,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * When true on Android 12+, Material You wallpaper colors replace the palette's
     * [androidx.compose.material3.ColorScheme]. [ExtendedColors] always come from the
     * palette so the brand's success/etc. semantics survive dynamic colour.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> palette.dark
        else -> palette.light
    }
    val extended = if (darkTheme) palette.extendedDark else palette.extendedLight

    CompositionLocalProvider(
        LocalSkipperPalette provides palette,
        LocalExtendedColors provides extended,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** Access brand-specific semantic colors: `MaterialTheme.extended.success`. */
val MaterialTheme.extended: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
