package cz.kuba.dash407.nav

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.widget.Toast
import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.HttpError
import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Stav navigace v Dash 407. */
data class NavState(
    val active: Boolean = false,
    val loading: Boolean = false,
    val route: NavRoute? = null,
    val next: Maneuver? = null,
    val after: Maneuver? = null,
    val distToNextM: Double = 0.0,
    val alongM: Double = 0.0,
    val shapeIndex: Int = 0,
    val remainingM: Double = 0.0,
    val remainingS: Int = 0,
    val arrived: Boolean = false,
    val rerouting: Boolean = false,
    val error: String? = null
)

/**
 * Navigace krok za krokem přímo v launcheru: trasa a pokyny z Valhally (OSM),
 * mapové podklady z Mapy.com, hlasové pokyny přes převod textu na řeč v rádiu.
 */
object NavEngine {
    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var app: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    private var lastIndex = 0
    private var offCount = 0
    private var lastReroute = 0L
    private var spokenAlert = -1
    private var spokenPre = -1
    private var spokenFar = -1
    private var arrivedAt = 0L

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        ensureTts()
    }

    private fun ensureTts() {
        val ctx = app ?: return
        if (tts != null) return
        tts = TextToSpeech(ctx) { status -> onTtsInit(status) }
    }

    private fun onTtsInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) return
        val r = t.setLanguage(Locale("cs", "CZ"))
        ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        // Pokyn, který přišel dřív, než byla řeč připravená.
        pending?.let { say(it); pending = null }
    }

    /** Je k dispozici česká řeč? (null = ještě se zjišťuje) */
    fun voiceAvailable(): Boolean = ttsReady

    fun say(text: String?) {
        if (text.isNullOrBlank() || !Prefs.navVoice.value) return
        val t = tts ?: return
        if (!ttsReady) { pending = text; return }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav" + System.currentTimeMillis())
    }

    /** Spustí navigaci z aktuální polohy do cíle. */
    fun start(context: Context, place: Place) {
        init(context)
        ensureTts()
        val from = Gps.fix.value
        if (from == null) {
            Toast.makeText(context, "Čekám na polohu z GPS – zkus to za chvíli", Toast.LENGTH_LONG).show()
            return
        }
        _state.value = NavState(active = true, loading = true)
        Recent.add(place)
        // Trasa Mapy.com (s dopravou) pro přesnější čas příjezdu, pokud je klíč.
        if (Prefs.mapyKey.value.isNotBlank()) RouteState.plan(place)
        load(from, place, first = true)
    }

    private fun load(from: Fix, place: Place, first: Boolean) {
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Valhalla.route(from, place) }
                lastIndex = 0
                offCount = 0
                spokenAlert = -1
                spokenPre = -1
                spokenFar = -1
                arrivedAt = 0L
                _state.value = NavState(active = true, route = r, remainingM = r.lengthM, remainingS = r.timeS.toInt())
                if (first) say(r.maneuvers.firstOrNull()?.pre ?: r.maneuvers.firstOrNull()?.instruction)
                else say("Trasa přepočítána.")
                Gps.fix.value?.let { onFix(it) }
            } catch (e: Exception) {
                val msg = when {
                    e is HttpError && e.code == 400 -> "Trasu se nepodařilo najít (cíl mimo silnice?)"
                    e is HttpError -> "Server navigace vrátil chybu ${e.code}"
                    else -> "Navigace bez připojení k internetu"
                }
                if (first) {
                    _state.value = NavState(active = false, error = msg)
                    app?.let { Toast.makeText(it, msg, Toast.LENGTH_LONG).show() }
                } else {
                    _state.value = _state.value.copy(rerouting = false, error = msg)
                }
            }
        }
    }

    fun stop() {
        _state.value = NavState()
        tts?.stop()
        RouteState.clear()
    }

    /** Bod a směr na trase ve vzdálenosti d od startu (pro ukázkový režim). */
    fun pointAt(d: Double): Pair<LatLon, Float>? {
        val r = _state.value.route ?: return null
        val c = r.cumulative
        if (r.points.size < 2) return null
        var i = 1
        while (i < c.size - 1 && c[i] < d) i++
        val a = r.points[i - 1]
        val b = r.points[i]
        val seg = (c[i] - c[i - 1]).coerceAtLeast(0.01)
        val t = ((d - c[i - 1]) / seg).coerceIn(0.0, 1.0)
        val p = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        val y = (b.lon - a.lon) * cos(Math.toRadians(a.lat))
        val x = b.lat - a.lat
        val bearing = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        return p to bearing
    }

    /** Průmět polohy na úsek a–b v metrech (rovinné přiblížení, pro desítky metrů stačí). */
    private fun project(f: Fix, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = cos(Math.toRadians(f.lat)) * 111_320.0
        val ax = a.lon * k; val ay = a.lat * 110_540.0
        val bx = b.lon * k; val by = b.lat * 110_540.0
        val px = f.lon * k; val py = f.lat * 110_540.0
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 < 1e-6) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val qx = ax + t * dx; val qy = ay + t * dy
        val dist = sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy))
        return t to dist
    }

    @Synchronized
    fun onFix(f: Fix) {
        val s = _state.value
        val r = s.route ?: return
        if (!s.active) return
        val pts = r.points
        if (pts.size < 2) return

        // Nejbližší úsek trasy: hledáme kousek zpět a dál dopředu od minula.
        var bestI = lastIndex
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        val from = (lastIndex - 5).coerceAtLeast(0)
        val to = (lastIndex + 300).coerceAtMost(pts.size - 1)
        for (i in from until to) {
            val (t, d) = project(f, pts[i], pts[i + 1])
            if (d < bestD) { bestD = d; bestI = i; bestT = t }
        }
        if (bestD > 150) {
            for (i in 0 until pts.size - 1) {
                val (t, d) = project(f, pts[i], pts[i + 1])
                if (d < bestD) { bestD = d; bestI = i; bestT = t }
            }
        }
        lastIndex = bestI
        val c = r.cumulative
        val along = c[bestI] + (c[bestI + 1] - c[bestI]) * bestT
        val remaining = (r.total - along).coerceAtLeast(0.0)

        // Mimo trasu: po třech polohách za sebou přepočítat (nejvýš jednou za 12 s).
        val tolerance = 45.0 + (f.accuracy ?: 10f).toDouble()
        offCount = if (bestD > tolerance) offCount + 1 else 0
        val now = System.currentTimeMillis()
        if (offCount >= 3 && now - lastReroute > 12_000 && !s.rerouting) {
            lastReroute = now
            offCount = 0
            _state.value = s.copy(rerouting = true)
            say("Přepočítávám trasu.")
            load(f, r.dest, first = false)
            return
        }

        // Další manévr = první, který začíná až za námi.
        val idx = r.maneuvers.indexOfFirst { it.beginIndex > 0 && c[it.beginIndex] > along + 3 }
        val next = if (idx >= 0) r.maneuvers[idx] else r.maneuvers.lastOrNull()
        val after = if (idx >= 0) r.maneuvers.getOrNull(idx + 1) else null
        val dist = if (next != null) (c[next.beginIndex] - along).coerceAtLeast(0.0) else remaining

        // Čas do cíle: podíl zbývající vzdálenosti, zpřesněný trasou Mapy.com s dopravou.
        val mapy = RouteState.route.value
        val factor = if (mapy != null && r.timeS > 0 && mapy.dest.lat == r.dest.lat && mapy.dest.lon == r.dest.lon)
            (mapy.durationS / r.timeS).coerceIn(0.8, 1.6) else 1.0
        val remainingS = (r.timeS * remaining / r.total * factor).toInt()

        val arrived = remaining < 30 || Gps.distance(f.lat, f.lon, r.dest.lat, r.dest.lon) < 35
        _state.value = s.copy(
            next = next, after = after, distToNextM = dist, alongM = along, shapeIndex = bestI,
            remainingM = remaining, remainingS = remainingS, arrived = arrived, rerouting = false, error = null
        )

        // Hlasové pokyny.
        if (arrived) {
            if (arrivedAt == 0L) {
                arrivedAt = now
                say(r.maneuvers.lastOrNull()?.pre ?: "Jste v cíli.")
                scope.launch {
                    delay(20_000)
                    if (_state.value.arrived) stop()
                }
            }
            return
        }
        if (next == null || idx < 0) return
        val v = ((f.speedKmh ?: 50f) / 3.6f).toDouble()
        val farAt = if (v > 22) 2000.0 else if (v > 14) 800.0 else 0.0
        val alertAt = if (v > 22) 800.0 else if (v > 14) 400.0 else 200.0
        val preAt = maxOf(40.0, v * 7)
        if (farAt > 0 && dist in (alertAt + 300)..farAt && spokenFar != idx && next.lengthM >= 0) {
            spokenFar = idx
            say("Za ${spokenDistance(dist)} ${lowerFirst(next.alert ?: next.instruction)}")
        } else if (dist <= alertAt && dist > preAt + 30 && spokenAlert != idx) {
            spokenAlert = idx
            say("Za ${spokenDistance(dist)} ${lowerFirst(next.alert ?: next.instruction)}")
        } else if (dist <= preAt && spokenPre != idx) {
            spokenPre = idx
            spokenAlert = idx
            say(next.pre ?: next.instruction)
        }
    }

    private fun lowerFirst(s: String): String = if (s.isEmpty()) s else s[0].lowercaseChar() + s.substring(1)

    /** Vzdálenost pro hlas: „300 metrů“, „1,5 kilometru“, „2 kilometry“. */
    fun spokenDistance(m: Double): String = when {
        m < 1000 -> "${((m / 50).roundToInt() * 50).coerceAtLeast(50)} metrů"
        m < 1950 -> {
            val km = (m / 100).roundToInt() / 10.0
            if (abs(km - km.toInt()) < 0.05) "1 kilometr" else String.format(Locale("cs", "CZ"), "%.1f kilometru", km)
        }
        m < 4500 -> "${(m / 1000).roundToInt()} kilometry"
        else -> "${(m / 1000).roundToInt()} kilometrů"
    }

    /** Vzdálenost na displeji: „350 m“, „1,2 km“, „15 km“. */
    fun shownDistance(m: Double): String = when {
        m < 1000 -> "${((m / 10).roundToInt() * 10)} m"
        m < 10_000 -> String.format(Locale("cs", "CZ"), "%.1f km", m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }
}

/**
 * Jedno místo pro „Navigovat“: podle Nastavení buď navigace v Dash 407, nebo předání aplikaci Mapy.com.
 */
object Navigator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun go(context: Context, place: Place) {
        if (Prefs.navInApp.value) {
            NavEngine.start(context, place)
            cz.kuba.dash407.home.UiBus.close()
        } else Mapy.navigate(context, place)
    }

    /** Cíl zadaný textem (Domů, Práce): dohledá se přes Mapy.com, pak se navigace spustí. */
    fun go(context: Context, address: String) {
        val key = Prefs.mapyKey.value
        if (!Prefs.navInApp.value || key.isBlank()) {
            if (Prefs.navInApp.value) Toast.makeText(context, "Bez API klíče Mapy.com předávám navigaci aplikaci Mapy.com", Toast.LENGTH_SHORT).show()
            Mapy.navigate(context, address)
            return
        }
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { Mapy.search(address, Gps.fix.value, key).firstOrNull() }
                if (p != null) {
                    NavEngine.start(context, p.copy(name = address))
                    cz.kuba.dash407.home.UiBus.close()
                }
                else Toast.makeText(context, "Adresu „$address“ jsem nenašel", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Hledání adresy selhalo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
