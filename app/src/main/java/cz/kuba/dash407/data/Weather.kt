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
import java.time.LocalDate
import java.util.Locale

/** Předpověď na jeden den. */
data class DayForecast(
    val date: LocalDate,
    val code: Int?,
    val maxC: Double?,
    val minC: Double?,
    val rainPct: Int?
)

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
    val approximate: Boolean = false,
    /** Obec, pro kterou se počasí ukazuje (podle polohy, jinak výchozí Hradec Králové). */
    val place: String? = null,
    val days: List<DayForecast> = emptyList()
)

object Weather {
    /** Když ještě není poloha z GPS: Hradec Králové. */
    private const val HOME_LAT = 50.2092
    private const val HOME_LON = 15.8328
    private const val HOME_NAME = "Hradec Králové"

    private var geoLat = 0.0
    private var geoLon = 0.0
    private var geoName: String? = null

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
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&forecast_days=4&wind_speed_unit=kmh&timezone=auto",
                lat, lon
            )
            val root = JSONObject(Net.getText(url))
            val c = root.getJSONObject("current")
            val days = parseDays(root)
            _state.value = WeatherState(
                tempC = c.optDouble("temperature_2m").takeIf { !it.isNaN() },
                feelsC = c.optDouble("apparent_temperature").takeIf { !it.isNaN() },
                humidity = c.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
                windKmh = c.optDouble("wind_speed_10m").takeIf { !it.isNaN() },
                code = c.optInt("weather_code", -1).takeIf { it >= 0 },
                isDay = c.optInt("is_day", 1) == 1,
                at = System.currentTimeMillis(),
                approximate = fix == null,
                place = if (fix == null) HOME_NAME else _state.value.place,
                days = days
            )
            // Název obce se dohledá zvlášť, aby zpoždění geokódování nezdrželo počasí.
            if (fix != null) {
                val name = placeName(lat, lon)
                if (name != null && name != _state.value.place) _state.value = _state.value.copy(place = name)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = "Počasí se nepodařilo načíst", at = System.currentTimeMillis() - 13 * 60_000)
        }
    }

    private fun parseDays(root: JSONObject): List<DayForecast> {
        val d = root.optJSONObject("daily") ?: return emptyList()
        val t = d.optJSONArray("time") ?: return emptyList()
        val code = d.optJSONArray("weather_code")
        val max = d.optJSONArray("temperature_2m_max")
        val min = d.optJSONArray("temperature_2m_min")
        val rain = d.optJSONArray("precipitation_probability_max")
        fun dbl(a: org.json.JSONArray?, i: Int): Double? = if (a == null || a.isNull(i)) null else a.optDouble(i).takeIf { !it.isNaN() }
        return (0 until t.length()).mapNotNull { i ->
            val date = try { LocalDate.parse(t.getString(i)) } catch (e: Exception) { return@mapNotNull null }
            DayForecast(date, dbl(code, i)?.toInt(), dbl(max, i), dbl(min, i), dbl(rain, i)?.toInt())
        }
    }

    /** Obec podle polohy (Nominatim, obráceně). Výsledek se drží, dokud se auto nepřesune o víc než 3 km. */
    private fun placeName(lat: Double, lon: Double): String? {
        val cached = geoName
        if (cached != null && Gps.distance(geoLat, geoLon, lat, lon) < 3000) return cached
        return try {
            val url = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=12&accept-language=cs&lat=%.4f&lon=%.4f",
                lat, lon
            )
            val o = JSONObject(Net.getText(url))
            val a = o.optJSONObject("address")
            val name = a?.let { ad ->
                listOf("city", "town", "village", "municipality", "suburb", "hamlet")
                    .firstNotNullOfOrNull { k -> ad.optString(k).takeIf { it.isNotBlank() } }
            } ?: o.optString("name").takeIf { it.isNotBlank() }
            if (name != null) {
                geoName = name; geoLat = lat; geoLon = lon
            }
            name ?: cached
        } catch (e: Exception) {
            cached
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
