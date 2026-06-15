package app.skipperclub.ui.main

import app.skipperclub.data.SeaWeather
import app.skipperclub.data.WeatherApi
import app.skipperclub.data.WeatherError
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Seam over [WeatherApi] so the controller can be unit-tested with a fake. */
interface WeatherGateway {
    suspend fun fetch(lat: Double, lng: Double): SeaWeather
}

object RealWeatherGateway : WeatherGateway {
    override suspend fun fetch(lat: Double, lng: Double): SeaWeather = WeatherApi.fetch(lat, lng)
}

data class MapWeatherUiState(
    val weather: SeaWeather? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

/**
 * Drives the weather bar at the top of the map. The map feeds it the centre of
 * the currently visible viewport whenever the camera settles ([onViewportCenter]);
 * the controller debounces, skips near-identical points (rounded to ~1 km), and
 * keeps the last good reading on screen while a refresh is in flight — so panning
 * never blanks the bar.
 *
 * Plain class (no ViewModel/DI yet — see CLAUDE.md §State); owned by the map
 * composable via `remember` and unit-tested with a fake [WeatherGateway].
 */
class MapWeatherController(
    private val scope: CoroutineScope,
    private val gateway: WeatherGateway = RealWeatherGateway,
    private val debounceMillis: Long = 400,
) {
    private val _state = MutableStateFlow(MapWeatherUiState())
    val state: StateFlow<MapWeatherUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastKey: String? = null

    fun onViewportCenter(lat: Double, lng: Double) {
        val roundedLat = round2(lat)
        val roundedLng = round2(lng)
        val key = "$roundedLat,$roundedLng"
        // Same patch of sea already shown — skip, unless the last attempt has no data.
        if (key == lastKey && _state.value.weather != null) return
        lastKey = key
        job?.cancel()
        _state.update { it.copy(isLoading = true, hasError = false) }
        job = scope.launch {
            delay(debounceMillis)
            try {
                val weather = gateway.fetch(roundedLat, roundedLng)
                _state.update { it.copy(weather = weather, isLoading = false, hasError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: WeatherError) {
                // Keep the stale reading if we have one; only flag an empty bar.
                _state.update { it.copy(isLoading = false, hasError = it.weather == null) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, hasError = it.weather == null) }
            }
        }
    }
}

private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
