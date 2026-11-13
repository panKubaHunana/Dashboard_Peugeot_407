package cz.kuba.dash407.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Aktuální počasí. Hodnota null znamená „zatím nevíme“. */
data class WeatherState(
    val tempC: Double? = null,
    val feelsC: Double? = null,
    val windKmh: Double? = null,
    val code: Int? = null,
    val isDay: Boolean = true,
    val updatedAt: Long = 0L,
    val loading: Boolean = false,
    val failed: Boolean = false
)

/**
 * Počasí z Open-Meteo (zdarma pro nekomerční použití, bez klíče).
 * Obnovuje se nejvýš jednou za 30 minut, po chybě za 3 minuty.
 */
object Weather {
    private const val REFRESH_MS = 30 * 60_000L
    private const val RETRY_MS = 3 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state

    @Volatile private var lastAttempt = 0L

    fun maybeRefresh(lat: Double, lon: Double, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (s.loading) return
        val wait = if (s.failed || s.updatedAt == 0L) RETRY_MS else REFRESH_MS
        if (!force && now - lastAttempt < wait) return
        lastAttempt = now
        _state.value = s.copy(loading = true)
        scope.launch { fetch(lat, lon) }
    }

    private fun fetch(lat: Double, lon: Double) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day&timezone=auto",
            lat, lon
        )
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(text).getJSONObject("current")
            _state.value = WeatherState(
                tempC = current.optDouble("temperature_2m").takeUnless { it.isNaN() },
                feelsC = current.optDouble("apparent_temperature").takeUnless { it.isNaN() },
                windKmh = current.optDouble("wind_speed_10m").takeUnless { it.isNaN() },
                code = if (current.has("weather_code")) current.optInt("weather_code") else null,
                isDay = current.optInt("is_day", 1) == 1,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, failed = true)
        } finally {
            connection?.disconnect()
        }
    }

    /** Druh počasí podle kódu WMO, jak ho vrací Open-Meteo. */
    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, UNKNOWN }

    fun kindOf(code: Int?): Kind = when (code) {
        null -> Kind.UNKNOWN
        0 -> Kind.CLEAR
        1, 2 -> Kind.PARTLY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        51, 53, 55, 56, 57 -> Kind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        95, 96, 99 -> Kind.STORM
        else -> Kind.UNKNOWN
    }
}
