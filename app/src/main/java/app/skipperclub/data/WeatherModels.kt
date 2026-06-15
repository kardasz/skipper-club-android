package app.skipperclub.data

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Current sea weather for a single point (the centre of the visible map
 * viewport), sourced from Open-Meteo's free, key-less forecast + marine APIs.
 *
 * [waveHeightM], [todayMaxC] and [tomorrowMaxC] are optional: the marine grid
 * only covers sea points (a land centre yields no wave height) and the daily
 * outlook may be absent for some responses. The bar simply hides whatever is
 * missing instead of failing.
 */
data class SeaWeather(
    val temperatureC: Double,
    val condition: WeatherCondition,
    val windSpeedKn: Double,
    val windDirectionDeg: Double,
    val waveHeightM: Double? = null,
    val todayMaxC: Double? = null,
    val tomorrowMaxC: Double? = null,
) {
    /** 8-point compass abbreviation for [windDirectionDeg] (language-neutral, like "kn"). */
    val windCardinal: String get() = cardinalFor(windDirectionDeg)
}

/**
 * The WMO weather-code groups we render. Mapping codes to a small enum keeps
 * localized labels and icon choices in the UI layer (per CLAUDE.md the data
 * layer never carries user-facing text).
 */
enum class WeatherCondition {
    Clear,
    MainlyClear,
    PartlyCloudy,
    Overcast,
    Fog,
    Drizzle,
    Rain,
    Snow,
    Showers,
    Thunderstorm,
    Unknown,
    ;

    companion object {
        /** WMO weather interpretation codes — see https://open-meteo.com/en/docs */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> Clear
            1 -> MainlyClear
            2 -> PartlyCloudy
            3 -> Overcast
            45, 48 -> Fog
            51, 53, 55, 56, 57 -> Drizzle
            61, 63, 65, 66, 67 -> Rain
            71, 73, 75, 77 -> Snow
            80, 81, 82, 85, 86 -> Showers
            95, 96, 99 -> Thunderstorm
            else -> Unknown
        }
    }
}

private val CARDINALS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

internal fun cardinalFor(deg: Double): String {
    val normalized = ((deg % 360) + 360) % 360
    val index = (normalized / 45.0).roundToInt() % 8
    return CARDINALS[index]
}

@Serializable
internal data class ForecastDto(
    val current: ForecastCurrentDto? = null,
    val daily: ForecastDailyDto? = null,
)

@Serializable
internal data class ForecastCurrentDto(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
)

@Serializable
internal data class ForecastDailyDto(
    @SerialName("temperature_2m_max") val temperatureMax: List<Double> = emptyList(),
)

@Serializable
internal data class MarineDto(
    val current: MarineCurrentDto? = null,
)

@Serializable
internal data class MarineCurrentDto(
    @SerialName("wave_height") val waveHeight: Double? = null,
)
