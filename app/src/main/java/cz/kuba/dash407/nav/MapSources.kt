package cz.kuba.dash407.nav

import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Net
import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Jaký klíč mapový podklad potřebuje. */
enum class KeyNeed { NONE, MAPY, GOOGLE }

/** Jeden mapový podklad na výběr v panelu Vzhled. */
data class MapSource(
    val id: String,
    val name: String,
    val group: String,
    val key: KeyNeed,
    val attribution: String,
    val link: String,
    val maxZoom: Int,
    /** Otevřené servery chtějí, aby si aplikace dlaždice pamatovala (OSM: aspoň 7 dní). */
    val diskCache: Boolean,
    /** Podklad, který se použije při zapnuté tmavé mapě místo převrácení barev (null = převrátit barvy). */
    val night: String? = null,
    /** Sám je tmavý: barvy se nepřevracejí. */
    val dark: Boolean = false
)

object MapSources {
    val all = listOf(
        MapSource("basic", "Základní", "Mapy.com", KeyNeed.MAPY, "© Seznam.cz a.s. a další", "https://api.mapy.com/copyright", 19, false),
        MapSource("outdoor", "Turistická", "Mapy.com", KeyNeed.MAPY, "© Seznam.cz a.s. a další", "https://api.mapy.com/copyright", 19, false),
        MapSource("winter", "Zimní", "Mapy.com", KeyNeed.MAPY, "© Seznam.cz a.s. a další", "https://api.mapy.com/copyright", 19, false),
        MapSource("aerial", "Letecká", "Mapy.com", KeyNeed.MAPY, "© Seznam.cz a.s. a další", "https://api.mapy.com/copyright", 19, false),
        MapSource("carto_voyager", "Voyager", "CARTO", KeyNeed.NONE, "© OpenStreetMap © CARTO", "https://carto.com/attributions", 20, true, night = "carto_dark"),
        MapSource("carto_dark", "Tmavá", "CARTO", KeyNeed.NONE, "© OpenStreetMap © CARTO", "https://carto.com/attributions", 20, true, dark = true),
        MapSource("carto_light", "Světlá", "CARTO", KeyNeed.NONE, "© OpenStreetMap © CARTO", "https://carto.com/attributions", 20, true, night = "carto_dark"),
        MapSource("esri_street", "Silniční", "Esri", KeyNeed.NONE, "Esri, HERE, Garmin, © OpenStreetMap", "https://www.esri.com/en-us/legal/terms/full-master-agreement", 19, true),
        MapSource("esri_sat", "Satelit", "Esri", KeyNeed.NONE, "Esri, Maxar, Earthstar Geographics", "https://www.esri.com/en-us/legal/terms/full-master-agreement", 19, true, dark = true),
        MapSource("osm", "OpenStreetMap", "Open source", KeyNeed.NONE, "© přispěvatelé OpenStreetMap", "https://www.openstreetmap.org/copyright", 19, true),
        MapSource("otm", "OpenTopoMap", "Open source", KeyNeed.NONE, "© OpenStreetMap, SRTM | © OpenTopoMap (CC-BY-SA)", "https://opentopomap.org/about", 17, true),
        MapSource("g_roadmap", "Google mapa", "Google", KeyNeed.GOOGLE, "Google", "https://www.google.com/intl/cs/help/terms_maps/", 20, false),
        MapSource("g_satellite", "Google satelit", "Google", KeyNeed.GOOGLE, "Google", "https://www.google.com/intl/cs/help/terms_maps/", 20, false),
        MapSource("g_terrain", "Google terén", "Google", KeyNeed.GOOGLE, "Google", "https://www.google.com/intl/cs/help/terms_maps/", 15, false)
    )

    /** Co se opravdu kreslí: u tmavé mapy případně tmavá varianta téhož poskytovatele. */
    fun effective(id: String, darkMap: Boolean): MapSource {
        val s = of(id)
        return if (darkMap && s.night != null) of(s.night) else s
    }

    fun of(id: String): MapSource = all.firstOrNull { it.id == id } ?: all.first()

    fun hasKey(s: MapSource): Boolean = when (s.key) {
        KeyNeed.NONE -> true
        KeyNeed.MAPY -> Prefs.mapyKey.value.isNotBlank()
        KeyNeed.GOOGLE -> Prefs.googleKey.value.isNotBlank()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Adresa dlaždice. Volat mimo hlavní vlákno (Google si napoprvé zakládá relaci). */
    fun url(id: String, z: Int, x: Int, y: Int): String = when (id) {
        "osm" -> "https://tile.openstreetmap.org/$z/$x/$y.png"
        "carto_voyager" -> "https://${"abcd"[(x + y) % 4]}.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y@2x.png"
        "carto_dark" -> "https://${"abcd"[(x + y) % 4]}.basemaps.cartocdn.com/dark_all/$z/$x/$y@2x.png"
        "carto_light" -> "https://${"abcd"[(x + y) % 4]}.basemaps.cartocdn.com/light_all/$z/$x/$y@2x.png"
        "esri_street" -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$z/$y/$x"
        "esri_sat" -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        "otm" -> "https://${"abc"[(x + y) % 3]}.tile.opentopomap.org/$z/$x/$y.png"
        "g_roadmap", "g_satellite", "g_terrain" -> {
            val key = Prefs.googleKey.value
            val session = GoogleTiles.session(id.removePrefix("g_"), key)
            "https://tile.googleapis.com/v1/2dtiles/$z/$x/$y?session=${enc(session)}&key=${enc(key)}"
        }
        else -> Mapy.tileUrl(id, z, x, y, Prefs.mapyKey.value)
    }
}

/**
 * Oficiální Google Map Tiles API: dlaždice se stahují v rámci relace (session),
 * která platí zhruba dva týdny. Potřebuje API klíč z Google Cloud s povoleným Map Tiles API.
 */
object GoogleTiles {
    private val sessions = HashMap<String, Pair<String, Long>>()
    private val _copyright = MutableStateFlow("Google")
    val copyright: StateFlow<String> = _copyright
    private var lastCopyrightAt = 0L

    @Synchronized
    fun session(mapType: String, key: String): String {
        val cached = sessions[mapType]
        val now = System.currentTimeMillis() / 1000
        if (cached != null && cached.second - 3600 > now) return cached.first
        val body = JSONObject()
            .put("mapType", mapType)
            .put("language", "cs-CZ")
            .put("region", "CZ")
        if (mapType == "terrain") body.put("layerTypes", JSONArray().put("layerRoadmap"))
        val o = JSONObject(Net.postJson("https://tile.googleapis.com/v1/createSession?key=" + URLEncoder.encode(key, "UTF-8"), body.toString()))
        val token = o.getString("session")
        val expiry = o.optString("expiry").toLongOrNull() ?: (now + 7 * 24 * 3600)
        sessions[mapType] = token to expiry
        return token
    }

    @Synchronized
    fun reset() {
        sessions.clear()
    }

    /** Text autorských práv pro výřez mapy, povinný u Google dlaždic. Volat mimo hlavní vlákno. */
    fun refreshCopyright(mapType: String, zoom: Int, f: Fix?) {
        val now = System.currentTimeMillis()
        if (now - lastCopyrightAt < 60_000) return
        lastCopyrightAt = now
        val key = Prefs.googleKey.value
        if (key.isBlank()) return
        try {
            val lat = f?.lat ?: 50.2092
            val lon = f?.lon ?: 15.8328
            val d = 0.05
            val s = session(mapType, key)
            val url = String.format(
                Locale.US,
                "https://tile.googleapis.com/tile/v1/viewport?session=%s&key=%s&zoom=%d&north=%.5f&south=%.5f&east=%.5f&west=%.5f",
                URLEncoder.encode(s, "UTF-8"), URLEncoder.encode(key, "UTF-8"), zoom, lat + d, lat - d, lon + d, lon - d
            )
            val c = JSONObject(Net.getText(url)).optString("copyright")
            if (c.isNotBlank()) _copyright.value = "Google · $c"
        } catch (e: Exception) {
            // Zůstane obecné „Google“.
        }
    }

    /** Ověření klíče z Nastavení. Volat mimo hlavní vlákno. */
    fun test(key: String): String = try {
        reset()
        session("roadmap", key)
        "Klíč funguje, Google mapa se načte."
    } catch (e: cz.kuba.dash407.data.HttpError) {
        "Google klíč nebyl přijat (HTTP ${e.code}). Zkontroluj, že je v projektu zapnuté Map Tiles API a fakturační účet."
    } catch (e: Exception) {
        "Nepodařilo se spojit s Googlem: ${e.message}"
    }
}

/** Hledání adres v otevřených datech OpenStreetMap (Nominatim), když není klíč Mapy.com. */
object Nominatim {
    fun search(query: String, near: Fix?): List<Place> {
        val sb = StringBuilder("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&accept-language=cs&q=")
            .append(URLEncoder.encode(query, "UTF-8"))
        if (near != null) {
            // Upřednostní okolí, ale nehledá jen v něm.
            sb.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f", near.lon - 1.5, near.lat + 1.0, near.lon + 1.5, near.lat - 1.0))
        }
        val a = JSONArray(Net.getText(sb.toString()))
        val out = ArrayList<Place>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val display = o.optString("display_name")
            val name = o.optString("name").ifBlank { display.substringBefore(",") }
            out.add(
                Place(
                    name = name,
                    label = o.optString("type").replace('_', ' '),
                    location = display.substringAfter(", ", "").take(80),
                    lat = o.getString("lat").toDouble(),
                    lon = o.getString("lon").toDouble()
                )
            )
        }
        return out
    }
}
