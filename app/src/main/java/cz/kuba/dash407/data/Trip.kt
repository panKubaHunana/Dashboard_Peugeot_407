package cz.kuba.dash407.data

import android.content.Context
import cz.kuba.dash407.car.CanOdometer
import cz.kuba.dash407.car.Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Denní kniha jízd.
 *
 * Údaje jedné jízdy (ujeto, průměr, maximum, začátek, souřadnice) se vynulují, když začne nová jízda.
 * Denní údaje (čas v pohybu, počet jízd) se vynulují o půlnoci.
 */
data class TripStats(
    /** Začátek jízdy (ms) = start motoru; u nové instalace čas spuštění. */
    val startedAt: Long = System.currentTimeMillis(),
    /** Ujeto v této jízdě (GPS). */
    val distanceM: Double = 0.0,
    /** Čas v pohybu v této jízdě. */
    val movingMs: Long = 0L,
    val topKmh: Float = 0f,
    /** Počet jízd dnes. */
    val drives: Int = 0,
    /** Kdy se auto naposledy pohybovalo (0 = zatím ne). */
    val lastMovingAt: Long = 0L,
    /** Kdy přišla poslední poloha z GPS během jízdy. */
    val lastFixAt: Long = 0L,
    /** Jízda právě probíhá (motor běží). */
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
    /** Stav tachometru na začátku jízdy (z CAN boxu, nebo odhad). */
    val odoStart: Int? = null,
    /** Stav na začátku přišel přímo z CAN boxu. */
    val odoFromCan: Boolean = false
) {
    /** Průměrná rychlost jízdy za dobu, kdy se auto pohybovalo. */
    val averageKmh: Double
        get() = if (movingMs > 0) distanceM / 1000.0 / (movingMs / 3_600_000.0) else 0.0
}

/**
 * Jízda = od startu motoru do stopu motoru (viz [Engine]). Mezi jízdami nemusí být žádná pauza:
 * nový start motoru hned zakládá novou jízdu. Kratší jízdy než [MIN_DRIVE_M] se nezapisují.
 */
object Trip {
    private const val KEY = "trip_v2"
    /** Kratší přejezdy (popojetí na parkovišti, nastartování na místě) se do knihy jízd nezapisují. */
    const val MIN_DRIVE_M = 100.0
    /** Když aplikace spadne a hned naběhne, rozjetá jízda pokračuje, pokud poslední poloha není starší. */
    private const val RESUME_MS = 3 * 60_000L
    /** Poloha před startem motoru se použije jako start, když není starší než 2 minuty. */
    private const val START_FIX_MS = 2 * 60_000L

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
                    lastFixAt = o.optLong("lastFixAt", o.optLong("lastMovingAt", 0L)),
                    active = o.optBoolean("active", false),
                    startLat = d("startLat"), startLon = d("startLon"),
                    endLat = d("endLat"), endLon = d("endLon"),
                    day = o.optLong("day", LocalDate.now().toEpochDay()),
                    dayMovingMs = o.optLong("dayMovingMs", 0L),
                    dayDistanceM = o.optDouble("dayDistanceM", 0.0),
                    totalM = o.optDouble("totalM", 0.0),
                    odoStart = if (o.has("odoStart")) o.getInt("odoStart") else null,
                    odoFromCan = o.optBoolean("odoFromCan", false)
                )
            } catch (e: Exception) {
                // Poškozený záznam: začneme znovu.
            }
        }
        // Rádio se vypnulo bez zprávy (nebo aplikace spadla): starou jízdu uzavřeme k poslední poloze.
        // Když aplikace jen rychle naběhla znovu, jízda pokračuje.
        val s = _state.value
        val now = System.currentTimeMillis()
        if (s.active) {
            val lastSeen = max(s.lastFixAt, s.startedAt)
            if (now - lastSeen > RESUME_MS) finish(s, lastSeen)
        }
        rollDay(now)
        // Motor už běží (Engine se vyhodnotil dřív): jízda začne hned.
        if (Engine.state.value.running && !_state.value.active) begin(_state.value, now)
    }

    /** Start nebo stop motoru (z [Engine]). */
    @Synchronized
    fun onEngine(running: Boolean, at: Long) {
        if (!loaded) return
        rollDay(at)
        val s = _state.value
        if (running) {
            if (!s.active) begin(s, at)
        } else if (s.active) {
            finish(s, at)
        }
    }

    private fun begin(s: TripStats, at: Long) {
        val p = last?.takeIf { at - it.time <= START_FIX_MS && (it.accuracy ?: 0f) <= 60f }
        val can = CanOdometer.fresh()
        _state.value = s.copy(
            startedAt = at, distanceM = 0.0, movingMs = 0L, topKmh = 0f,
            drives = s.drives + 1, active = true, lastMovingAt = 0L, lastFixAt = p?.time ?: 0L,
            startLat = p?.lat, startLon = p?.lon, endLat = p?.lat, endLon = p?.lon,
            odoStart = can ?: Odometer.km(), odoFromCan = can != null
        )
        if (p != null) {
            DriveTracks.start(p.lat, p.lon)
            trackLat = p.lat
            trackLon = p.lon
        } else {
            trackLat = null
            trackLon = null
        }
        save()
    }

    @Synchronized
    fun onFix(f: Fix) {
        rollDay(f.time)
        val prev = last
        last = f
        var s = _state.value
        if (!s.active) return
        if ((f.accuracy ?: 0f) > 40f) return
        if (s.startLat == null) {
            // Motor běží, ale poloha přišla až teď: tady jízda začíná.
            s = s.copy(startLat = f.lat, startLon = f.lon, endLat = f.lat, endLon = f.lon, lastFixAt = f.time)
            _state.value = s
            DriveTracks.start(f.lat, f.lon)
            trackLat = f.lat
            trackLon = f.lon
            save()
            return
        }
        s = s.copy(lastFixAt = f.time)
        _state.value = s
        if (prev == null) return
        val dt = f.time - prev.time
        if (dt <= 0 || dt > 10_000) return
        val d = Gps.distance(prev.lat, prev.lon, f.lat, f.lon)
        val speed = f.speedKmh ?: (d / dt * 3600.0).toFloat()
        // Stání na místě (GPS se chvěje) se do vzdálenosti nepočítá.
        if (speed < 3f || d > 400) return
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

    /** Ruční ukončení jízdy (podržení widgetu). Když motor dál běží, hned začne nová jízda. */
    @Synchronized
    fun endDriveNow() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (s.active) finish(s, now)
        if (Engine.state.value.running) begin(_state.value, now)
    }

    @Synchronized
    fun save() {
        lastSave = System.currentTimeMillis()
        val s = _state.value
        try {
            val o = JSONObject()
                .put("startedAt", s.startedAt).put("distanceM", s.distanceM).put("movingMs", s.movingMs)
                .put("topKmh", s.topKmh.toDouble()).put("drives", s.drives).put("lastMovingAt", s.lastMovingAt)
                .put("lastFixAt", s.lastFixAt).put("active", s.active).put("day", s.day)
                .put("dayMovingMs", s.dayMovingMs).put("dayDistanceM", s.dayDistanceM).put("totalM", s.totalM)
                .put("odoFromCan", s.odoFromCan)
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
        val canEnd = CanOdometer.fresh()
        val odoEnd = when {
            canEnd != null -> canEnd
            s.odoStart != null -> (s.odoStart + s.distanceM / 1000.0).toInt()
            else -> Odometer.km()
        }
        // S tachometrem z CAN boxu na obou koncích se ujeto ověří i podle něj (třeba při výpadku GPS v tunelu).
        val canKm = if (s.odoFromCan && canEnd != null && s.odoStart != null) (canEnd - s.odoStart).coerceAtLeast(0) else null
        val distance = max(s.distanceM, (canKm ?: 0) * 1000.0)
        val keep = distance >= MIN_DRIVE_M && s.startLat != null && s.endLat != null
        if (keep) {
            DriveTracks.finish(s.startedAt)
            DriveLog.add(
                DriveRecord(
                    id = s.startedAt, start = s.startedAt, end = end,
                    startLat = s.startLat!!, startLon = s.startLon ?: 0.0,
                    endLat = s.endLat!!, endLon = s.endLon ?: 0.0,
                    distanceM = distance, movingMs = s.movingMs,
                    avgKmh = s.averageKmh, maxKmh = s.topKmh.toDouble(),
                    odoStart = s.odoStart,
                    odoEnd = odoEnd
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
