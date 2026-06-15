package app.skipperclub.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApiTest {

    @Test
    fun forecastRequestTargetsOpenMeteoWithKnotsAndCurrentFields() {
        val request = WeatherApi.forecastRequest(lat = 54.49, lng = 18.55)

        assertEquals("GET", request.method)
        assertEquals(
            "https://api.open-meteo.com/v1/forecast",
            "${request.url.scheme}://${request.url.host}${request.url.encodedPath}",
        )
        assertEquals("54.49", request.url.queryParameter("latitude"))
        assertEquals("18.55", request.url.queryParameter("longitude"))
        assertEquals("kn", request.url.queryParameter("wind_speed_unit"))
        assertEquals("2", request.url.queryParameter("forecast_days"))
        assertEquals(
            "temperature_2m,weather_code,wind_speed_10m,wind_direction_10m",
            request.url.queryParameter("current"),
        )
        assertEquals("temperature_2m_max", request.url.queryParameter("daily"))
        // Open-Meteo is key-less: no auth header is attached.
        assertNull(request.header("Authorization"))
    }

    @Test
    fun marineRequestTargetsMarineHostWithWaveHeight() {
        val request = WeatherApi.marineRequest(lat = 54.49, lng = 18.55)

        assertEquals(
            "https://marine-api.open-meteo.com/v1/marine",
            "${request.url.scheme}://${request.url.host}${request.url.encodedPath}",
        )
        assertEquals("wave_height", request.url.queryParameter("current"))
    }

    @Test
    fun decodeForecastMapsCurrentConditionsAndDailyOutlook() {
        val weather = WeatherApi.decodeForecast(
            """
                {
                  "current": {
                    "temperature_2m": 22.7,
                    "weather_code": 0,
                    "wind_speed_10m": 18.3,
                    "wind_direction_10m": 225.0
                  },
                  "daily": {
                    "temperature_2m_max": [23.4, 21.1]
                  }
                }
            """.trimIndent(),
        )

        assertEquals(22.7, weather.temperatureC, 0.0)
        assertEquals(WeatherCondition.Clear, weather.condition)
        assertEquals(18.3, weather.windSpeedKn, 0.0)
        assertEquals("SW", weather.windCardinal)
        assertEquals(23.4, weather.todayMaxC!!, 0.0)
        assertEquals(21.1, weather.tomorrowMaxC!!, 0.0)
        // Wave height is filled in separately from the marine endpoint.
        assertNull(weather.waveHeightM)
    }

    @Test
    fun decodeForecastToleratesMissingDailyBlock() {
        val weather = WeatherApi.decodeForecast(
            """
                {
                  "current": {
                    "temperature_2m": 15.0,
                    "weather_code": 61,
                    "wind_speed_10m": 9.0,
                    "wind_direction_10m": 90.0
                  }
                }
            """.trimIndent(),
        )

        assertEquals(WeatherCondition.Rain, weather.condition)
        assertEquals("E", weather.windCardinal)
        assertNull(weather.todayMaxC)
        assertNull(weather.tomorrowMaxC)
    }

    @Test
    fun decodeForecastThrowsServerErrorWhenCurrentMissing() {
        val error = runCatching {
            WeatherApi.decodeForecast("""{"daily":{"temperature_2m_max":[20.0]}}""")
        }.exceptionOrNull()

        assertTrue(error is WeatherError.Server)
    }

    @Test
    fun decodeWaveHeightReadsCurrentWaveHeight() {
        val wave = WeatherApi.decodeWaveHeight(
            """{ "current": { "wave_height": 1.2 } }""",
        )

        assertEquals(1.2, wave!!, 0.0)
    }

    @Test
    fun decodeWaveHeightReturnsNullForLandPointWithoutMarineData() {
        assertNull(WeatherApi.decodeWaveHeight("""{ "error": true, "reason": "No data" }"""))
    }

    @Test
    fun weatherCodeMappingCoversWmoGroups() {
        assertEquals(WeatherCondition.Clear, WeatherCondition.fromWmoCode(0))
        assertEquals(WeatherCondition.PartlyCloudy, WeatherCondition.fromWmoCode(2))
        assertEquals(WeatherCondition.Fog, WeatherCondition.fromWmoCode(45))
        assertEquals(WeatherCondition.Snow, WeatherCondition.fromWmoCode(73))
        assertEquals(WeatherCondition.Showers, WeatherCondition.fromWmoCode(81))
        assertEquals(WeatherCondition.Thunderstorm, WeatherCondition.fromWmoCode(95))
        assertEquals(WeatherCondition.Unknown, WeatherCondition.fromWmoCode(404))
    }

    @Test
    fun cardinalForCoversCompassRose() {
        assertEquals("N", cardinalFor(0.0))
        assertEquals("N", cardinalFor(360.0))
        assertEquals("NE", cardinalFor(45.0))
        assertEquals("E", cardinalFor(90.0))
        assertEquals("S", cardinalFor(180.0))
        assertEquals("SW", cardinalFor(225.0))
        assertEquals("NW", cardinalFor(315.0))
        // Wraps negatives correctly.
        assertEquals("NW", cardinalFor(-45.0))
    }

    @Test
    fun rateLimitedResponseMapsToRateLimitedError() {
        val error = response(429, """{"reason":"Minutely API request limit exceeded"}""")
            .toWeatherErrorForTest()

        assertTrue(error is WeatherError.RateLimited)
    }

    @Test
    fun serverErrorResponseMapsToServerError() {
        val error = response(500, "boom").toWeatherErrorForTest()

        assertTrue(error is WeatherError.Server)
        assertEquals(500, (error as WeatherError.Server).statusCode)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.open-meteo.com/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun Response.toWeatherErrorForTest(): WeatherError =
        WeatherApi.run { toWeatherError() }
}
