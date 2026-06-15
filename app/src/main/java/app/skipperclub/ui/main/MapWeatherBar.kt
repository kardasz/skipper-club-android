package app.skipperclub.ui.main

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipperclub.R
import app.skipperclub.data.SeaWeather
import app.skipperclub.data.WeatherCondition
import app.skipperclub.ui.theme.SkipperClubTheme
import kotlin.math.roundToInt

/**
 * Floating bar pinned to the top of the map showing the current weather for the
 * centre of the visible area. Stateless: the hosting screen owns the
 * [MapWeatherController] and passes its [MapWeatherUiState] down.
 *
 * Renders nothing until there is something useful to show: once a reading lands
 * it stays put (even while a pan triggers a refresh), and a silent error with no
 * prior data keeps the map clean rather than nagging.
 */
@Composable
fun MapWeatherBar(
    state: MapWeatherUiState,
    modifier: Modifier = Modifier,
) {
    val weather = state.weather
    when {
        weather != null -> WeatherBarSurface(modifier) { WeatherBarContent(weather) }
        state.isLoading -> WeatherBarSurface(modifier) { WeatherLoadingContent() }
        else -> Unit
    }
}

@Composable
private fun WeatherBarSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        content()
    }
}

@Composable
private fun WeatherLoadingContent() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.weather_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WeatherBarContent(weather: SeaWeather) {
    // Read the locale through the composition local so the bar recomposes on a
    // locale change (lint: NonObservableLocale).
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = stringResource(R.string.weather_bar_title).uppercase(locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = weather.condition.icon(),
                contentDescription = stringResource(R.string.weather_icon_content_description),
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "${weather.temperatureC.roundToInt()}°",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(weather.condition.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            WeatherMetric(
                icon = Icons.Filled.Air,
                contentDescription = stringResource(R.string.weather_wind_content_description),
                value = stringResource(
                    R.string.weather_wind_value,
                    weather.windSpeedKn.roundToInt(),
                    weather.windCardinal,
                ),
            )
            weather.waveHeightM?.let { wave ->
                Spacer(Modifier.width(16.dp))
                WeatherMetric(
                    icon = Icons.Filled.Waves,
                    contentDescription = stringResource(R.string.weather_wave_content_description),
                    value = stringResource(
                        R.string.weather_wave_value,
                        String.format(locale, "%.1f", wave),
                    ),
                )
            }
        }
        if (weather.todayMaxC != null || weather.tomorrowMaxC != null) {
            Spacer(Modifier.height(6.dp))
            Row {
                weather.todayMaxC?.let {
                    Text(
                        text = stringResource(R.string.weather_today, it.roundToInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (weather.todayMaxC != null && weather.tomorrowMaxC != null) {
                    Text(
                        text = "  ·  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                weather.tomorrowMaxC?.let {
                    Text(
                        text = stringResource(R.string.weather_tomorrow, it.roundToInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(icon: ImageVector, contentDescription: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun WeatherCondition.icon(): ImageVector = when (this) {
    WeatherCondition.Clear, WeatherCondition.MainlyClear -> Icons.Filled.WbSunny
    WeatherCondition.PartlyCloudy, WeatherCondition.Overcast, WeatherCondition.Fog -> Icons.Filled.Cloud
    WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.Showers -> Icons.Filled.Grain
    WeatherCondition.Snow -> Icons.Filled.AcUnit
    WeatherCondition.Thunderstorm -> Icons.Filled.Bolt
    WeatherCondition.Unknown -> Icons.Filled.Cloud
}

@StringRes
private fun WeatherCondition.labelRes(): Int = when (this) {
    WeatherCondition.Clear -> R.string.weather_condition_clear
    WeatherCondition.MainlyClear -> R.string.weather_condition_mainly_clear
    WeatherCondition.PartlyCloudy -> R.string.weather_condition_partly_cloudy
    WeatherCondition.Overcast -> R.string.weather_condition_overcast
    WeatherCondition.Fog -> R.string.weather_condition_fog
    WeatherCondition.Drizzle -> R.string.weather_condition_drizzle
    WeatherCondition.Rain -> R.string.weather_condition_rain
    WeatherCondition.Snow -> R.string.weather_condition_snow
    WeatherCondition.Showers -> R.string.weather_condition_showers
    WeatherCondition.Thunderstorm -> R.string.weather_condition_thunderstorm
    WeatherCondition.Unknown -> R.string.weather_condition_unknown
}

private val previewWeather = SeaWeather(
    temperatureC = 23.0,
    condition = WeatherCondition.Clear,
    windSpeedKn = 18.0,
    windDirectionDeg = 225.0,
    waveHeightM = 1.2,
    todayMaxC = 23.0,
    tomorrowMaxC = 21.0,
)

@Composable
private fun WeatherBarPreviewBox(state: MapWeatherUiState) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            .padding(12.dp),
    ) {
        MapWeatherBar(state = state, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun MapWeatherBarPreviewEn() {
    SkipperClubTheme {
        WeatherBarPreviewBox(MapWeatherUiState(weather = previewWeather))
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun MapWeatherBarPreviewPl() {
    SkipperClubTheme {
        WeatherBarPreviewBox(
            MapWeatherUiState(
                weather = previewWeather.copy(
                    condition = WeatherCondition.PartlyCloudy,
                    windSpeedKn = 24.0,
                    windDirectionDeg = 315.0,
                ),
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapWeatherBarPreviewDark() {
    SkipperClubTheme {
        WeatherBarPreviewBox(MapWeatherUiState(weather = previewWeather))
    }
}

@Preview(showBackground = true, widthDp = 360, locale = "pl")
@Composable
private fun MapWeatherBarPreviewLoading() {
    SkipperClubTheme {
        WeatherBarPreviewBox(MapWeatherUiState(isLoading = true))
    }
}
