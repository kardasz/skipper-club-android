package app.skipperclub.ui.main

import app.skipperclub.data.SeaWeather
import app.skipperclub.data.WeatherCondition
import app.skipperclub.data.WeatherError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fake gateway never suspends and the controller is built with a zero
 * debounce, so an Unconfined scope runs each launched coroutine to completion
 * synchronously (`delay(0)` returns immediately).
 */
class MapWeatherControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val gateway = FakeWeatherGateway()

    private fun controller() = MapWeatherController(
        scope = scope,
        gateway = gateway,
        debounceMillis = 0,
    )

    @Test
    fun viewportCenterLoadsWeather() {
        gateway.result = sampleWeather
        val controller = controller()

        controller.onViewportCenter(lat = 54.49, lng = 18.55)

        val state = controller.state.value
        assertEquals(sampleWeather, state.weather)
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(1, gateway.calls)
    }

    @Test
    fun fetchUsesCoordinatesRoundedToTwoDecimals() {
        gateway.result = sampleWeather
        val controller = controller()

        controller.onViewportCenter(lat = 54.487654, lng = 18.665432)

        assertEquals(54.49, gateway.lastLat!!, 0.0)
        assertEquals(18.67, gateway.lastLng!!, 0.0)
    }

    @Test
    fun repeatedNearbyCenterDoesNotRefetch() {
        gateway.result = sampleWeather
        val controller = controller()

        controller.onViewportCenter(lat = 54.490, lng = 18.550)
        // Within the same ~1 km rounding bucket: no second network call.
        controller.onViewportCenter(lat = 54.491, lng = 18.551)

        assertEquals(1, gateway.calls)
    }

    @Test
    fun distinctCenterTriggersRefetch() {
        gateway.result = sampleWeather
        val controller = controller()

        controller.onViewportCenter(lat = 54.49, lng = 18.55)
        controller.onViewportCenter(lat = 43.94, lng = 15.44)

        assertEquals(2, gateway.calls)
    }

    @Test
    fun errorWithNoPriorDataFlagsErrorAndKeepsBarEmpty() {
        gateway.error = WeatherError.Network(RuntimeException("offline"))
        val controller = controller()

        controller.onViewportCenter(lat = 54.49, lng = 18.55)

        val state = controller.state.value
        assertNull(state.weather)
        assertTrue(state.hasError)
        assertFalse(state.isLoading)
    }

    @Test
    fun errorAfterSuccessKeepsStaleReadingWithoutErrorFlag() {
        gateway.result = sampleWeather
        val controller = controller()
        controller.onViewportCenter(lat = 54.49, lng = 18.55)

        gateway.result = null
        gateway.error = WeatherError.Network(RuntimeException("offline"))
        controller.onViewportCenter(lat = 43.94, lng = 15.44)

        val state = controller.state.value
        // The previous reading stays on screen; we don't blank the bar on a blip.
        assertEquals(sampleWeather, state.weather)
        assertFalse(state.hasError)
        assertFalse(state.isLoading)
    }

    @Test
    fun retryAfterErrorRecovers() {
        gateway.error = WeatherError.Network(RuntimeException("offline"))
        val controller = controller()
        controller.onViewportCenter(lat = 54.49, lng = 18.55)
        assertTrue(controller.state.value.hasError)

        gateway.error = null
        gateway.result = sampleWeather
        // Same bucket retried because the previous attempt yielded no data.
        controller.onViewportCenter(lat = 54.49, lng = 18.55)

        assertEquals(sampleWeather, controller.state.value.weather)
        assertFalse(controller.state.value.hasError)
    }

    private val sampleWeather = SeaWeather(
        temperatureC = 23.0,
        condition = WeatherCondition.Clear,
        windSpeedKn = 18.0,
        windDirectionDeg = 225.0,
        waveHeightM = 1.2,
        todayMaxC = 23.0,
        tomorrowMaxC = 21.0,
    )

    private class FakeWeatherGateway : WeatherGateway {
        var result: SeaWeather? = null
        var error: WeatherError? = null
        var calls = 0
        var lastLat: Double? = null
        var lastLng: Double? = null

        override suspend fun fetch(lat: Double, lng: Double): SeaWeather {
            calls++
            lastLat = lat
            lastLng = lng
            error?.let { throw it }
            return result ?: error("FakeWeatherGateway: no result or error configured")
        }
    }
}
