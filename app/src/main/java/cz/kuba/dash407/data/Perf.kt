package cz.kuba.dash407.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Časomíra 0–50, 0–100 km/h a 400 m s pevným startem, podle GPS. */
data class PerfState(
    val phase: Phase = Phase.WAITING,
    val t50: Double? = null,
    val t100: Double? = null,
    val t400: Double? = null,
    val speed400: Float? = null
) {
    enum class Phase { WAITING, READY, RUNNING, DONE }
}

object Perf {
    private val _state = MutableStateFlow(PerfState())
    val state: StateFlow<PerfState> = _state

    private var startAt = 0L
    private var distance = 0.0
    private var last: Fix? = null

    @Synchronized
    fun reset() {
        _state.value = PerfState()
        last = null
    }

    @Synchronized
    fun onFix(f: Fix) {
        val prev = last
        last = f
        val v = f.speedKmh ?: return
        val s = _state.value
        when (s.phase) {
            PerfState.Phase.WAITING, PerfState.Phase.DONE -> {
                if (s.phase == PerfState.Phase.WAITING && v < 2f) _state.value = s.copy(phase = PerfState.Phase.READY)
            }
            PerfState.Phase.READY -> {
                if (v >= 3f) {
                    // Start: odhad okamžiku rozjezdu z předchozího vzorku.
                    startAt = prev?.time ?: f.time
                    distance = 0.0
                    _state.value = PerfState(phase = PerfState.Phase.RUNNING)
                    step(prev, f)
                }
            }
            PerfState.Phase.RUNNING -> {
                step(prev, f)
                if (v < 2f) {
                    // Zastavil dřív: měření končí s tím, co stihlo.
                    _state.value = _state.value.copy(phase = PerfState.Phase.DONE)
                }
            }
        }
    }

    /** Přičte úsek a zapíše časy, pokud v něm auto překročilo hranici (lineárně mezi vzorky). */
    private fun step(prev: Fix?, f: Fix) {
        if (prev == null) return
        val v0 = prev.speedKmh ?: return
        val v1 = f.speedKmh ?: return
        val d = Gps.distance(prev.lat, prev.lon, f.lat, f.lon)
        val d0 = distance
        distance += d
        var s = _state.value
        fun cross(a: Double, b: Double, limit: Double): Double? =
            if (a < limit && b >= limit && b != a) (limit - a) / (b - a) else null
        fun at(part: Double): Double = (prev.time + (f.time - prev.time) * part - startAt) / 1000.0
        if (s.t50 == null) cross(v0.toDouble(), v1.toDouble(), 50.0)?.let { s = s.copy(t50 = at(it)) }
        if (s.t100 == null) cross(v0.toDouble(), v1.toDouble(), 100.0)?.let { s = s.copy(t100 = at(it)) }
        if (s.t400 == null) cross(d0, distance, 400.0)?.let { s = s.copy(t400 = at(it), speed400 = v1) }
        if (s.t100 != null && s.t400 != null) s = s.copy(phase = PerfState.Phase.DONE)
        _state.value = s
    }
}
