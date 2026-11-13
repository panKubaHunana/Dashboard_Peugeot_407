package cz.kuba.dash407.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

/** Nejvyšší povolená rychlost na silnici, kde auto jede (OpenStreetMap, služba Overpass). */
data class LimitState(val kmh: Int? = null, val road: String? = null, val known: Boolean = false, val at: Long = 0L)

object SpeedLimit {
    private val _state = MutableStateFlow(LimitState())
    val state: StateFlow<LimitState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var users = 0
    @Volatile private var busy = false
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAt = 0L

    @Synchronized fun acquire() { users++ }
    @Synchronized fun release() { users = (users - 1).coerceAtLeast(0) }

    fun onFix(f: Fix) {
        if (users == 0 || busy) return
        val now = System.currentTimeMillis()
        if (now - lastAt < 8_000) return
        if (lastAt != 0L && Gps.distance(lastLat, lastLon, f.lat, f.lon) < 60) return
        busy = true
        lastAt = now
        lastLat = f.lat
        lastLon = f.lon
        scope.launch {
            try {
                val q = "[out:json][timeout:10];way(around:20,${f.lat},${f.lon})[\"highway\"];out tags 5;"
                val text = Net.postForm("https://overpass-api.de/api/interpreter", "data=" + URLEncoder.encode(q, "UTF-8"))
                val elements = JSONObject(text).optJSONArray("elements")
                var best: LimitState? = null
                if (elements != null) {
                    for (i in 0 until elements.length()) {
                        val tags = elements.getJSONObject(i).optJSONObject("tags") ?: continue
                        val road = tags.optString("ref").ifBlank { tags.optString("name") }.ifBlank { null }
                        val kmh = parse(tags.optString("maxspeed"))
                        if (kmh != null) { best = LimitState(kmh, road, true, now); break }
                        if (best == null) best = LimitState(null, road, false, now)
                    }
                }
                _state.value = best ?: LimitState(at = now)
            } catch (e: Exception) {
                // Bez signálu necháme poslední známou hodnotu.
            } finally {
                busy = false
            }
        }
    }

    /** Hodnota maxspeed z OSM: číslo, nebo zkratka typu CZ:urban. */
    private fun parse(raw: String): Int? {
        if (raw.isBlank()) return null
        raw.trim().takeWhile { it.isDigit() }.toIntOrNull()?.let {
            return if (raw.contains("mph")) (it * 1.609).toInt() else it
        }
        return when (raw.lowercase()) {
            "cz:urban", "sk:urban", "de:urban", "at:urban", "pl:urban" -> 50
            "cz:rural", "sk:rural", "pl:rural" -> 90
            "de:rural", "at:rural" -> 100
            "cz:trunk" -> 110
            "cz:motorway", "sk:motorway", "at:motorway" -> 130
            "cz:living_street", "sk:living_street", "de:living_street" -> 20
            "walk" -> 10
            else -> null
        }
    }
}
