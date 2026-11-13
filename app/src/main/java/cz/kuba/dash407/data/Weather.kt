package cz.kuba.dash407.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

/** Aktuální počasí z Open-Meteo (zdarma, bez klíče). */
data class WeatherState(
    val tempC: Double? = null,
    val feelsC: Double? = null,
    val humidity: Int? = null,
    val windKmh: Double? = null,
    val code: Int? = null,
    val isDay: Boolean = true,
    val at: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    val approximate: Boolean = false
)

object Weather {
    /** Když ještě není poloha z GPS: Hradec Králové. */
    private const val HOME_LAT = 50.2092
    private const val HOME_LON = 15.8328

    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var users = 0

    @Synchronized
    fun acquire() {
        users++
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                val s = _state.value
                val old = System.currentTimeMillis() - s.at > 15 * 60_000
                val betterPosition = s.approximate && Gps.fix.value != null
                if (old || betterPosition) load()
                delay(30_000)
            }
        }
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            job?.cancel()
            job = null
        }
    }

    fun refresh() {
        scope.launch { load() }
    }

    private fun load() {
        val fix = Gps.fix.value
        val lat = fix?.lat ?: HOME_LAT
        val lon = fix?.lon ?: HOME_LON
        _state.value = _state.value.copy(loading = true)
        try {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                    "&wind_speed_unit=kmh&timezone=auto",
                lat, lon
            )
            val c = JSONObject(Net.getText(url)).getJSONObject("current")
            _state.value = WeatherState(
                tempC = c.optDouble("temperature_2m").takeIf { !it.isNaN() },
                feelsC = c.optDouble("apparent_temperature").takeIf { !it.isNaN() },
                humidity = c.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
                windKmh = c.optDouble("wind_speed_10m").takeIf { !it.isNaN() },
                code = c.optInt("weather_code", -1).takeIf { it >= 0 },
                isDay = c.optInt("is_day", 1) == 1,
                at = System.currentTimeMillis(),
                approximate = fix == null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = "Počasí se nepodařilo načíst", at = System.currentTimeMillis() - 13 * 60_000)
        }
    }

    /** Český popis podle kódu WMO. */
    fun describe(code: Int?): String = when (code) {
        null -> "Bez údajů"
        0 -> "Jasno"
        1 -> "Skoro jasno"
        2 -> "Polojasno"
        3 -> "Zataženo"
        45, 48 -> "Mlha"
        51, 53, 55 -> "Mrholení"
        56, 57 -> "Mrznoucí mrholení"
        61 -> "Slabý déšť"
        63 -> "Déšť"
        65 -> "Silný déšť"
        66, 67 -> "Mrznoucí déšť"
        71 -> "Slabé sněžení"
        73 -> "Sněžení"
        75 -> "Silné sněžení"
        77 -> "Sněhová zrna"
        80, 81 -> "Přeháňky"
        82 -> "Silné přeháňky"
        85, 86 -> "Sněhové přeháňky"
        95 -> "Bouřka"
        96, 99 -> "Bouřka s kroupami"
        else -> "Počasí"
    }
}
