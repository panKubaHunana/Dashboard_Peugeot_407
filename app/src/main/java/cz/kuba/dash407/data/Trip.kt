package cz.kuba.dash407.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/** Jízda podle GPS: od spuštění nebo od posledního vynulování. */
data class TripStats(
    val startedAt: Long = System.currentTimeMillis(),
    val distanceM: Double = 0.0,
    val movingMs: Long = 0L,
    val topKmh: Float = 0f
) {
    /** Průměrná rychlost za dobu, kdy se auto pohybovalo. */
    val averageKmh: Double
        get() = if (movingMs > 0) distanceM / 1000.0 / (movingMs / 3_600_000.0) else 0.0
}

/** Palubní počítač z GPS. Ukládá se, takže přežije vypnutí rádia. */
object Trip {
    private const val KEY = "trip_v1"
    private val _state = MutableStateFlow(TripStats())
    val state: StateFlow<TripStats> = _state

    private var last: Fix? = null
    private var lastSave = 0L
    private var loaded = false

    @Synchronized
    fun init() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        val p = raw.split(';')
        if (p.size == 4) {
            _state.value = TripStats(
                startedAt = p[0].toLongOrNull() ?: System.currentTimeMillis(),
                distanceM = p[1].toDoubleOrNull() ?: 0.0,
                movingMs = p[2].toLongOrNull() ?: 0L,
                topKmh = p[3].toFloatOrNull() ?: 0f
            )
        }
    }

    @Synchronized
    fun onFix(f: Fix) {
        val prev = last
        last = f
        if (prev == null) return
        val dt = f.time - prev.time
        if (dt <= 0 || dt > 10_000) return
        if ((f.accuracy ?: 0f) > 40f) return
        val d = Gps.distance(prev.lat, prev.lon, f.lat, f.lon)
        val speed = f.speedKmh ?: (d / dt * 3600.0).toFloat()
        if (speed < 3f || d > 400) return
        val s = _state.value
        _state.value = s.copy(
            distanceM = s.distanceM + d,
            movingMs = s.movingMs + dt,
            topKmh = max(s.topKmh, speed)
        )
        if (f.time - lastSave > 30_000) save()
    }

    @Synchronized
    fun reset() {
        _state.value = TripStats()
        save()
    }

    @Synchronized
    fun save() {
        lastSave = System.currentTimeMillis()
        val s = _state.value
        try {
            Prefs.shared().edit().putString(KEY, "${s.startedAt};${s.distanceM};${s.movingMs};${s.topKmh}").apply()
        } catch (e: Exception) {
            // Úložiště ještě není připravené.
        }
    }
}
