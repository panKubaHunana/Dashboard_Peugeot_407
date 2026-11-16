package cz.kuba.dash407.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Čerpací stanice z OpenStreetMap. */
data class Station(val id: String, val name: String, val lat: Double, val lon: Double)

/** Nabídka zápisu tankování: u které stanice a kdy auto zastavilo. */
data class FuelPrompt(val station: Station, val at: Long)

/**
 * Automatické tankování: když auto stojí aspoň minutu u čerpací stanice (do 70 m),
 * nabídne se zápis tankování. Okno zmizí, když se auto rozjede rychleji než 15 km/h,
 * a ještě jednou se ukáže při další zastávce (do hodiny), pokud tankování zapsané není.
 * Seznam stanic se stahuje z OpenStreetMap (Overpass) pro okruh 3 km a drží se v paměti.
 */
object FuelStops {
    private const val KEY_ENABLED = "auto_fuel_enabled"
    private const val STOP_MS = 60_000L
    private const val NEAR_M = 70.0
    private const val AREA_M = 3000
    private const val SERVER = "https://overpass-api.de/api/interpreter?data="

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _prompt = MutableStateFlow<FuelPrompt?>(null)
    /** Právě zobrazená nabídka (null = nic). */
    val prompt: StateFlow<FuelPrompt?> = _prompt

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled
    private var loadedPrefs = false

    private var stations: List<Station> = emptyList()
    private var areaLat = 0.0
    private var areaLon = 0.0
    private var areaLoaded = false
    private var loading = false
    private var lastFailAt = 0L

    private var firstFix = true
    private var stillSince = 0L
    /** Nabídka skrytá při rozjezdu – ukáže se ještě jednou při další zastávce. */
    private var pending: FuelPrompt? = null
    /** Po „Později“ se nabídka vrátí až po odjezdu od pumpy (ne hned na stejném místě). */
    private var waitForMove = false
    /** Stanice, u kterých Kuba řekl „Netankoval jsem“ (id → čas). */
    private val declined = HashMap<String, Long>()

    private fun loadPrefs() {
        if (loadedPrefs) return
        loadedPrefs = true
        _enabled.value = try { Prefs.shared().getBoolean(KEY_ENABLED, true) } catch (e: Exception) { true }
    }

    fun setEnabled(on: Boolean) {
        loadPrefs()
        _enabled.value = on
        if (!on) { _prompt.value = null; pending = null }
        try { Prefs.shared().edit().putBoolean(KEY_ENABLED, on).apply() } catch (e: Exception) { }
    }

    @Synchronized
    fun onFix(f: Fix) {
        loadPrefs()
        if (!_enabled.value) return
        val speed = f.speedKmh ?: 0f
        if (speed < 3f) {
            // Po zapnutí rádia u pumpy (motor byl vypnutý) se rovnou počítá jako zastávka.
            if (stillSince == 0L) stillSince = if (firstFix) f.time - STOP_MS else f.time
        } else {
            stillSince = 0L
            if (speed > 15f) waitForMove = false
            val shown = _prompt.value
            if (shown != null && speed > 15f) {
                pending = shown
                _prompt.value = null
            }
        }
        firstFix = false
        if (stillSince == 0L || _prompt.value != null) return
        val still = f.time - stillSince
        if (still >= 20_000) ensureArea(f.lat, f.lon)

        // Skrytá nabídka z pumpy: ukázat ještě jednou při další zastávce (nejdéle hodinu).
        val p = pending
        if (p != null) {
            if (System.currentTimeMillis() - p.at > 3_600_000L || recentlyRefueled(p.at)) pending = null
            else if (still >= 15_000 && !waitForMove) {
                pending = null
                _prompt.value = p
                return
            }
        }
        if (still < STOP_MS || waitForMove) return
        val st = nearest(f.lat, f.lon) ?: return
        val now = System.currentTimeMillis()
        if ((declined[st.id] ?: 0L) > now - 3 * 3_600_000L) return
        if (recentlyRefueled(now - 2 * 3_600_000L)) return
        _prompt.value = FuelPrompt(st, now)
    }

    /** Tankování zapsané po daném čase – pak se už nic nenabízí. */
    private fun recentlyRefueled(since: Long) = FuelLog.entries.value.any { it.at >= since }

    /** Kuba tankování zapsal (z okna nabídky). */
    @Synchronized
    fun saved() {
        _prompt.value = null
        pending = null
    }

    /** „Netankoval jsem“ – u této stanice se 3 hodiny nic nenabídne. */
    @Synchronized
    fun decline() {
        _prompt.value?.let { declined[it.station.id] = System.currentTimeMillis() }
        _prompt.value = null
        pending = null
    }

    /** „Později“ – okno zmizí a ukáže se znovu při další zastávce. */
    @Synchronized
    fun later() {
        pending = _prompt.value?.copy(at = System.currentTimeMillis())
        _prompt.value = null
        waitForMove = true
    }

    // --- Známé stanice: pamatují se, aby automatické tankování fungovalo i bez internetu -----

    private const val KEY_KNOWN = "fuel_stations_v1"
    private const val KNOWN_MAX = 800
    private var known: Map<String, Station>? = null

    private fun known(): Map<String, Station> {
        known?.let { return it }
        val m = LinkedHashMap<String, Station>()
        try {
            val raw = Prefs.shared().getString(KEY_KNOWN, null)
            if (raw != null) {
                val a = org.json.JSONArray(raw)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    m[o.getString("id")] = Station(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))
                }
            }
        } catch (e: Exception) { }
        known = m
        return m
    }

    private fun remember(list: List<Station>) {
        val m = LinkedHashMap(known())
        for (st in list) { m.remove(st.id); m[st.id] = st }
        while (m.size > KNOWN_MAX) m.remove(m.keys.first())
        known = m
        try {
            val a = org.json.JSONArray()
            for (st in m.values) a.put(JSONObject().put("id", st.id).put("name", st.name).put("lat", st.lat).put("lon", st.lon))
            Prefs.shared().edit().putString(KEY_KNOWN, a.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun nearest(lat: Double, lon: Double): Station? =
        (stations + known().values.filter { k -> stations.none { it.id == k.id } })
            .map { it to Gps.distance(lat, lon, it.lat, it.lon) }
            .filter { it.second <= NEAR_M }
            .minByOrNull { it.second }?.first

    private fun ensureArea(lat: Double, lon: Double) {
        if (loading) return
        if (areaLoaded && Gps.distance(lat, lon, areaLat, areaLon) < AREA_M - 1000) return
        if (System.currentTimeMillis() - lastFailAt < 120_000L) return
        // Bez internetu se použijí stanice známé z dřívějška.
        if (!NetQueue.online.value) return
        loading = true
        scope.launch {
            try {
                val q = String.format(
                    Locale.US,
                    "[out:json][timeout:15];nwr(around:%d,%.5f,%.5f)[amenity=fuel];out center tags;",
                    AREA_M, lat, lon
                )
                val root = JSONObject(Net.getText(SERVER + URLEncoder.encode(q, "UTF-8"), 20_000))
                val els = root.optJSONArray("elements")
                val list = ArrayList<Station>()
                if (els != null) for (i in 0 until els.length()) {
                    val e = els.getJSONObject(i)
                    val c = e.optJSONObject("center")
                    val la = if (e.has("lat")) e.getDouble("lat") else c?.optDouble("lat") ?: continue
                    val lo = if (e.has("lon")) e.getDouble("lon") else c?.optDouble("lon") ?: continue
                    val t = e.optJSONObject("tags")
                    val brand = t?.optString("brand")?.takeIf { it.isNotBlank() } ?: t?.optString("name")?.takeIf { it.isNotBlank() } ?: "Čerpací stanice"
                    val place = t?.optString("addr:street")?.takeIf { it.isNotBlank() } ?: t?.optString("addr:city")?.takeIf { it.isNotBlank() }
                    list.add(Station(e.optString("type") + e.optLong("id"), if (place != null) "$brand, $place" else brand, la, lo))
                }
                synchronized(this@FuelStops) {
                    remember(list)
                    stations = list
                    areaLat = lat
                    areaLon = lon
                    areaLoaded = true
                }
            } catch (e: Exception) {
                lastFailAt = System.currentTimeMillis()
            } finally {
                loading = false
            }
        }
    }
}
