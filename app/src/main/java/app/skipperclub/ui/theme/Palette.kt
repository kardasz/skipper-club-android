package app.skipperclub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A SkipperClub theme palette bundles both Material 3 [ColorScheme]s (light + dark)
 * and the [ExtendedColors] set, so a single value fully describes a colour identity.
 *
 * Add new palettes by creating another `val` (e.g. `SunsetPalette`) and registering
 * it in [SkipperPalettes.All]. Theme switching is then a matter of swapping the
 * palette passed to `SkipperClubTheme(...)`.
 */
@Immutable
data class SkipperPalette(
    val id: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val extendedLight: ExtendedColors,
    val extendedDark: ExtendedColors,
)

/** Default, brand palette: nautical blue + amber. */
val OceanPalette: SkipperPalette = SkipperPalette(
    id = "ocean",
    light = lightColorScheme(
        primary = BrandBlue,
        onPrimary = Color.White,
        primaryContainer = BrandBlueContainer,
        onPrimaryContainer = BrandBlueOnContainer,
        secondary = BrandAmber,
        onSecondary = Color.White,
        secondaryContainer = BrandAmberContainer,
        onSecondaryContainer = BrandAmberOnContainer,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
    ),
    dark = darkColorScheme(
        primary = BrandBlueOnDark,
        onPrimary = Color.White,
        primaryContainer = BrandBlueContainerDark,
        onPrimaryContainer = BrandBlueContainer,
        secondary = BrandAmberOnDark,
        onSecondary = Color.Black,
        secondaryContainer = BrandAmberContainerDark,
        onSecondaryContainer = BrandAmberContainer,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
    ),
    extendedLight = ExtendedColors(
        success = SuccessGreen,
        onSuccess = Color.White,
        successContainer = SuccessGreenContainer,
        onSuccessContainer = SuccessGreenOnContainer,
    ),
    extendedDark = ExtendedColors(
        success = SuccessGreenOnDark,
        onSuccess = Color.Black,
        successContainer = SuccessGreenContainerDark,
        onSuccessContainer = SuccessGreenContainer,
    ),
)

/** Registry of selectable palettes. Future settings UI will iterate this list. */
object SkipperPalettes {
    val Default: SkipperPalette = OceanPalette
    val All: List<SkipperPalette> = listOf(OceanPalette)

    fun byId(id: String?): SkipperPalette =
        All.firstOrNull { it.id == id } ?: Default
}
