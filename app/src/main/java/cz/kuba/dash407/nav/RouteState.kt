package cz.kuba.dash407.nav

import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Naplánovaná trasa z Mapy.com. */
data class Route(
    val dest: Place,
    val points: List<LatLon>,
    val lengthM: Int,
    val durationS: Int,
    val createdAt: Long
) {
    /** Kumulativní vzdálenost od startu ke každému bodu, v metrech. */
    val cumulative: DoubleArray by lazy {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) {
            out[i] = out[i - 1] + Gps.distance(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        }
        out
    }
}

/** Kolik zbývá do cíle. */
data class RouteProgress(val remainingM: Double, val remainingS: Int, val arrived: Boolean, val offRouteM: Double)

/** Stav plánování: trasa, postup po ní a případná chyba. */
object RouteState {
    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route

    private val _progress = MutableStateFlow<RouteProgress?>(null)
    val progress: StateFlow<RouteProgress?> = _progress

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastIndex = 0

    /** Naplánuje trasu z aktuální polohy. Bez polohy nebo klíče vrátí chybu. */
    fun plan(place: Place, onDone: (Boolean) -> Unit = {}) {
        val key = Prefs.mapyKey.value
        val from = Gps.fix.value
        if (from == null) {
            _error.value = "Čekám na polohu z GPS"
            onDone(false); return
        }
        _busy.value = true
        _error.value = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    if (key.isNotBlank()) Mapy.route(from, place, key)
                    else {
                        // Bez klíče Mapy.com: trasa z otevřeného routeru Valhalla (OpenStreetMap).
                        val v = Valhalla.route(from, place)
                        Route(place, v.points, v.lengthM.toInt(), v.timeS.toInt(), System.currentTimeMillis())
                    }
                }
                set(r)
                Recent.add(place)
                onDone(true)
            } catch (e: Exception) {
                _error.value = "Trasu se nepodařilo naplánovat (${e.message})"
                onDone(false)
            } finally {
                _busy.value = false
            }
        }
    }

    fun set(r: Route) {
        lastIndex = 0
        _route.value = r
        _progress.value = RouteProgress(r.lengthM.toDouble(), r.durationS, false, 0.0)
        Gps.fix.value?.let { onFix(it) }
    }

    fun clear() {
        _route.value = null
        _progress.value = null
        _error.value = null
    }

    /** Najde nejbližší bod trasy (hledá jen dopředu od minula) a spočítá zbytek. */
    fun onFix(f: Fix) {
        val r = _route.value ?: return
        val pts = r.points
        if (pts.size < 2) return
        val end = minOf(pts.size, lastIndex + 400)
        var best = lastIndex
        var bestD = Double.MAX_VALUE
        for (i in lastIndex until end) {
            val d = Gps.distance(f.lat, f.lon, pts[i].lat, pts[i].lon)
            if (d < bestD) { bestD = d; best = i }
        }
        // Daleko od trasy: zkusíme celou trasu (třeba po objížďce).
        if (bestD > 300) {
            for (i in pts.indices) {
                val d = Gps.distance(f.lat, f.lon, pts[i].lat, pts[i].lon)
                if (d < bestD) { bestD = d; best = i }
            }
        }
        lastIndex = best
        val total = r.cumulative.last().coerceAtLeast(1.0)
        val remaining = (total - r.cumulative[best]).coerceAtLeast(0.0)
        val arrived = Gps.distance(f.lat, f.lon, r.dest.lat, r.dest.lon) < 60
        _progress.value = RouteProgress(
            remainingM = if (arrived) 0.0 else remaining,
            remainingS = if (arrived) 0 else (r.durationS * remaining / total).toInt(),
            arrived = arrived,
            offRouteM = bestD
        )
    }
}

/** Naposledy hledané cíle (nejvýš 6), uložené v nastavení. */
object Recent {
    private const val KEY = "recent_places"
    private val _list = MutableStateFlow<List<Place>>(emptyList())
    val list: StateFlow<List<Place>> = _list
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        try {
            val a = JSONArray(Prefs.shared().getString(KEY, "[]"))
            _list.value = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Place(o.getString("n"), o.optString("l"), o.optString("loc"), o.getDouble("lat"), o.getDouble("lon"))
            }
        } catch (e: Exception) {
            _list.value = emptyList()
        }
    }

    fun add(p: Place) {
        val next = (listOf(p) + _list.value.filterNot { it.name == p.name && it.location == p.location }).take(6)
        _list.value = next
        val a = JSONArray()
        next.forEach { a.put(JSONObject().put("n", it.name).put("l", it.label).put("loc", it.location).put("lat", it.lat).put("lon", it.lon)) }
        try {
            Prefs.shared().edit().putString(KEY, a.toString()).apply()
        } catch (e: Exception) {
            // Neuloženo, zůstane jen v paměti.
        }
    }
}
