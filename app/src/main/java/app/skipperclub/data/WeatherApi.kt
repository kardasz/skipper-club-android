package app.skipperclub.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Client for Open-Meteo (https://open-meteo.com) — a free, key-less weather API.
 *
 * [fetch] pulls current conditions plus a 2-day max-temperature outlook from the
 * forecast endpoint, and (best-effort) wave height from the separate marine
 * endpoint. The marine grid only covers sea points, so a marine miss is
 * non-fatal: we still return [SeaWeather] with `waveHeightM = null`.
 *
 * Unlike the SkipperClub REST endpoints this hits external hosts directly and
 * needs no auth token; otherwise it follows the same raw-OkHttp pattern as
 * [MapItemsApi].
 */
object WeatherApi {
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val MARINE_URL = "https://marine-api.open-meteo.com/v1/marine"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .let { HttpLoggingProvider.apply(it) }
        .build()

    internal fun forecastRequest(lat: Double, lng: Double): Request {
        val url = FORECAST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lng.toString())
            .addQueryParameter("current", "temperature_2m,weather_code,wind_speed_10m,wind_direction_10m")
            .addQueryParameter("daily", "temperature_2m_max")
            .addQueryParameter("wind_speed_unit", "kn")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "2")
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
    }

    internal fun marineRequest(lat: Double, lng: Double): Request {
        val url = MARINE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lng.toString())
            .addQueryParameter("current", "wave_height")
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
    }

    suspend fun fetch(lat: Double, lng: Double): SeaWeather {
        val forecast = execute(forecastRequest(lat, lng)).use { response ->
            if (!response.isSuccessful) throw response.toWeatherError()
            decodeForecast(response.body.string())
        }
        // Wave height is a bonus; never let the marine endpoint sink the bar.
        val wave = runCatching {
            execute(marineRequest(lat, lng)).use { response ->
                if (response.isSuccessful) decodeWaveHeight(response.body.string()) else null
            }
        }.getOrNull()
        return forecast.copy(waveHeightM = wave)
    }

    internal fun decodeForecast(payload: String): SeaWeather {
        val dto = try {
            json.decodeFromString<ForecastDto>(payload)
        } catch (_: SerializationException) {
            throw WeatherError.Server(200, "Malformed response")
        }
        val current = dto.current ?: throw WeatherError.Server(200, "Missing current weather")
        val temperature = current.temperature ?: throw WeatherError.Server(200, "Missing temperature")
        val daily = dto.daily?.temperatureMax.orEmpty()
        return SeaWeather(
            temperatureC = temperature,
            condition = WeatherCondition.fromWmoCode(current.weatherCode ?: -1),
            windSpeedKn = current.windSpeed ?: 0.0,
            windDirectionDeg = current.windDirection ?: 0.0,
            todayMaxC = daily.getOrNull(0),
            tomorrowMaxC = daily.getOrNull(1),
        )
    }

    internal fun decodeWaveHeight(payload: String): Double? =
        runCatching { json.decodeFromString<MarineDto>(payload).current?.waveHeight }.getOrNull()

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(WeatherError.Network(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }
                },
            )
        }

    internal fun Response.toWeatherError(): WeatherError = when (code) {
        429 -> WeatherError.RateLimited
        else -> WeatherError.Server(code, "Server error ($code)")
    }
}

sealed class WeatherError(message: String) : Exception(message) {
    class Network(cause: Throwable) : WeatherError(cause.message ?: "Network error")
    object RateLimited : WeatherError("Too many requests")
    class Server(val statusCode: Int, detail: String) : WeatherError(detail)
}
