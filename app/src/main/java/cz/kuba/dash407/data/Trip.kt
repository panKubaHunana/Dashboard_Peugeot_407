package cz.kuba.dash407.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Denní kniha jízd podle GPS.
 *
 * Údaje jedné jízdy (ujeto, průměr, maximum, začátek, souřadnice) se vynulují, když začne nová jízda.
 * Denní údaje (čas v pohybu, počet jízd) se vynulují o půlnoci.
 */
data class TripStats(
    /** Začátek jízdy (ms); u nové instalace čas spuštění. */
    val startedAt: Long = System.currentTimeMillis(),
    /** Ujeto v této jízdě. */
    val distanceM: Double = 0.0,
    /** Čas v pohybu v této jízdě. */
    val movingMs: Long = 0L,
    val topKmh: Float = 0f,
    /** Počet jízd dnes. */
    val drives: Int = 0,
    /** Kdy se auto naposledy pohybovalo (0 = zatím ne). */
    val lastMovingAt: Long = 0L,
    /** Jízda právě probíhá (auto nestojí déle než [Trip.PAUSE_MS]). */
    val active: Boolean = false,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    /** Den, ke kterému patří denní údaje (LocalDate.toEpochDay). */
    val day: Long = LocalDate.now().toEpochDay(),
    /** Čas v pohybu za celý den. */
    val dayMovingMs: Long = 0L,
    /** Ujeto za celý den. */
    val dayDistanceM: Double = 0.0,
    /** Celkem ujeto podle GPS od instalace (nikdy se nenuluje; podklad pro odhad tachometru). */
    val totalM: Double = 0.0,
    /** Odhad tachometru na začátku jízdy. */
    val odoStart: Int? = null
) {
    /** Průměrná rychlost jízdy za dobu, kdy se auto pohybovalo. */
    val averageKmh: Double
        get() = if (movingMs > 0) distanceM / 1000.0 / (movingMs / 3_600_000.0) else 0.0
}

object Trip {
    private const val KEY = "trip_v2"
    /** Stání delší než 5 minut ukončí jízdu; další rozjezd je nová jízda. */
    const val PAUSE_MS = 5 * 60_000L
    /** Kratší přejezdy (parkování, popojetí) se do knihy jízd nezapisují. */
    const val MIN_DRIVE_M = 300.0

    private val _state = MutableStateFlow(TripStats())
    val state: StateFlow<TripStats> = _state

    private var last: Fix? = null
    private var trackLat: Double? = null
    private var trackLon: Double? = null
    private var lastSave = 0L
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        DriveLog.init(context)
        DriveTracks.init(context)
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null }
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                fun d(k: String): Double? = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null
                _state.value = TripStats(
                    startedAt = o.optLong("startedAt", System.currentTimeMillis()),
                    distanceM = o.optDouble("distanceM", 0.0),
                    movingMs = o.optLong("movingMs", 0L),
                    topKmh = o.optDouble("topKmh", 0.0).toFloat(),
                    drives = o.optInt("drives", 0),
                    lastMovingAt = o.optLong("lastMovingAt", 0L),
                    active = o.optBoolean("active", false),
                    startLat = d("startLat"), startLon = d("startLon"),
                    endLat = d("endLat"), endLon = d("endLon"),
                    day = o.optLong("day", LocalDate.now().toEpochDay()),
                    dayMovingMs = o.optLong("dayMovingMs", 0L),
                    dayDistanceM = o.optDouble("dayDistanceM", 0.0),
                    totalM = o.optDouble("totalM", 0.0),
                    odoStart = if (o.has("odoStart")) o.getInt("odoStart") else null
                )
            } catch (e: Exception) {
                // Poškozený záznam: začneme znovu.
            }
        }
        // Rádio bylo vypnuté uprostřed jízdy: jízdu uzavřeme k poslednímu pohybu.
        closeIfIdle(System.currentTimeMillis())
        rollDay(System.currentTimeMillis())
    }

    @Synchronized
    fun onFix(f: Fix) {
        closeIfIdle(f.time)
        rollDay(f.time)
        val prev = last
        last = f
        if (prev == null) return
        val dt = f.time - prev.time
        if (dt <= 0 || dt > 10_000) return
        if ((f.accuracy ?: 0f) > 40f) return
        val d = Gps.distance(prev.lat, prev.lon, f.lat, f.lon)
        val speed = f.speedKmh ?: (d / dt * 3600.0).toFloat()
        if (speed < 3f || d > 400) return
        var s = _state.value
        if (!s.active) {
            // Nová jízda: údaje jízdy začínají od nuly, start je poslední poloha před rozjezdem.
            s = s.copy(
                startedAt = prev.time, distanceM = 0.0, movingMs = 0L, topKmh = 0f,
                drives = s.drives + 1, active = true,
                startLat = prev.lat, startLon = prev.lon,
                odoStart = Odometer.km()
            )
            DriveTracks.start(prev.lat, prev.lon)
            trackLat = prev.lat
            trackLon = prev.lon
            save()
        }
        // Skutečná trasa: bod zhruba po 40 m.
        val tl = trackLat
        val tn = trackLon
        if (tl == null || tn == null || Gps.distance(tl, tn, f.lat, f.lon) >= DriveTracks.STEP_M) {
            DriveTracks.append(f.lat, f.lon)
            trackLat = f.lat
            trackLon = f.lon
        }
        _state.value = s.copy(
            distanceM = s.distanceM + d,
            movingMs = s.movingMs + dt,
            topKmh = max(s.topKmh, speed),
            lastMovingAt = f.time,
            endLat = f.lat, endLon = f.lon,
            dayMovingMs = s.dayMovingMs + dt,
            dayDistanceM = s.dayDistanceM + d,
            totalM = s.totalM + d
        )
        if (f.time - lastSave > 30_000) save()
    }

    /** Ruční ukončení jízdy (podržení widgetu): další rozjezd bude nová jízda. */
    @Synchronized
    fun endDriveNow() {
        val s = _state.value
        if (s.active) finish(s, s.lastMovingAt.takeIf { it > 0 } ?: System.currentTimeMillis())
    }

    @Synchronized
    fun save() {
        lastSave = System.currentTimeMillis()
        val s = _state.value
        try {
            val o = JSONObject()
                .put("startedAt", s.startedAt).put("distanceM", s.distanceM).put("movingMs", s.movingMs)
                .put("topKmh", s.topKmh.toDouble()).put("drives", s.drives).put("lastMovingAt", s.lastMovingAt)
                .put("active", s.active).put("day", s.day).put("dayMovingMs", s.dayMovingMs).put("dayDistanceM", s.dayDistanceM).put("totalM", s.totalM)
            s.startLat?.let { o.put("startLat", it) }
            s.startLon?.let { o.put("startLon", it) }
            s.endLat?.let { o.put("endLat", it) }
            s.endLon?.let { o.put("endLon", it) }
            s.odoStart?.let { o.put("odoStart", it) }
            Prefs.shared().edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
            // Úložiště ještě není připravené.
        }
    }

    private fun closeIfIdle(now: Long) {
        val s = _state.value
        if (s.active && s.lastMovingAt > 0 && now - s.lastMovingAt > PAUSE_MS) finish(s, s.lastMovingAt)
    }

    /** O půlnoci se vynulují denní údaje; probíhající jízda se počítá do nového dne. */
    private fun rollDay(now: Long) {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val s = _state.value
        if (s.day == today) return
        _state.value = s.copy(day = today, drives = if (s.active) 1 else 0, dayMovingMs = 0L, dayDistanceM = 0.0)
        save()
    }

    /** Uzavře jízdu a zapíše ji do knihy jízd. Údaje ve widgetu zůstanou do další jízdy. */
    private fun finish(s: TripStats, end: Long) {
        val keep = s.distanceM >= MIN_DRIVE_M && s.startLat != null && s.endLat != null
        if (keep) {
            DriveTracks.finish(s.startedAt)
            DriveLog.add(
                DriveRecord(
                    id = s.startedAt, start = s.startedAt, end = end,
                    startLat = s.startLat!!, startLon = s.startLon ?: 0.0,
                    endLat = s.endLat!!, endLon = s.endLon ?: 0.0,
                    distanceM = s.distanceM, movingMs = s.movingMs,
                    avgKmh = s.averageKmh, maxKmh = s.topKmh.toDouble(),
                    odoStart = s.odoStart,
                    odoEnd = s.odoStart?.let { (it + s.distanceM / 1000.0).toInt() } ?: Odometer.km()
                )
            )
        } else {
            DriveTracks.discard()
        }
        trackLat = null
        trackLon = null
        _state.value = if (keep) s.copy(active = false) else s.copy(
            // Krátký přejezd se nepočítá: denní počet jízd se vrátí.
            active = false, drives = (s.drives - 1).coerceAtLeast(0)
        )
        save()
    }
}
