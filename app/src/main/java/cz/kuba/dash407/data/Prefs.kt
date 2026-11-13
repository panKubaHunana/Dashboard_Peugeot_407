package cz.kuba.dash407.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Vzhled prostředí. Indexy odkazují do nabídek v ui/Theme.kt. */
data class Look(
    val accent: Int = 0,
    val background: Int = 0,
    val tileAlpha: Float = 0.92f,
    val corner: Int = 1,
    val textScale: Int = 1,
    val animations: Boolean = true,
    val tileBorder: Boolean = true,
    val showSeconds: Boolean = false,
    val barLabels: Boolean = true,
    val swipePages: Boolean = true,
    val hideSystemBars: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("accent", accent)
        .put("background", background)
        .put("tileAlpha", tileAlpha.toDouble())
        .put("corner", corner)
        .put("textScale", textScale)
        .put("animations", animations)
        .put("tileBorder", tileBorder)
        .put("showSeconds", showSeconds)
        .put("barLabels", barLabels)
        .put("swipePages", swipePages)
        .put("hideSystemBars", hideSystemBars)
        .toString()

    companion object {
        fun fromJson(text: String?): Look {
            if (text.isNullOrBlank()) return Look()
            return try {
                val o = JSONObject(text)
                val d = Look()
                Look(
                    accent = o.optInt("accent", d.accent),
                    background = o.optInt("background", d.background),
                    tileAlpha = o.optDouble("tileAlpha", d.tileAlpha.toDouble()).toFloat().coerceIn(0.4f, 1f),
                    corner = o.optInt("corner", d.corner),
                    textScale = o.optInt("textScale", d.textScale),
                    animations = o.optBoolean("animations", d.animations),
                    tileBorder = o.optBoolean("tileBorder", d.tileBorder),
                    showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                    barLabels = o.optBoolean("barLabels", d.barLabels),
                    swipePages = o.optBoolean("swipePages", d.swipePages),
                    hideSystemBars = o.optBoolean("hideSystemBars", d.hideSystemBars)
                )
            } catch (e: Exception) {
                Look()
            }
        }
    }
}

/** Nastavení mapy a navigace Mapy.com. */
data class MapSettings(
    val apiKey: String = "",
    val mapset: String = "basic",
    val dark: Boolean = true,
    val zoom: Double = 16.0,
    val follow: Boolean = true,
    val homeAddress: String = "",
    val workAddress: String = "",
    val windowMode: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("apiKey", apiKey)
        .put("mapset", mapset)
        .put("dark", dark)
        .put("zoom", zoom)
        .put("follow", follow)
        .put("home", homeAddress)
        .put("work", workAddress)
        .put("window", windowMode)
        .toString()

    companion object {
        val MAPSETS = listOf("basic", "outdoor", "winter", "aerial")

        fun fromJson(text: String?): MapSettings {
            if (text.isNullOrBlank()) return MapSettings()
            return try {
                val o = JSONObject(text)
                val d = MapSettings()
                MapSettings(
                    apiKey = o.optString("apiKey", d.apiKey).trim(),
                    mapset = o.optString("mapset", d.mapset).takeIf { it in MAPSETS } ?: d.mapset,
                    dark = o.optBoolean("dark", d.dark),
                    zoom = o.optDouble("zoom", d.zoom).coerceIn(3.0, 19.0),
                    follow = o.optBoolean("follow", d.follow),
                    homeAddress = o.optString("home", d.homeAddress),
                    workAddress = o.optString("work", d.workAddress),
                    windowMode = o.optBoolean("window", d.windowMode)
                )
            } catch (e: Exception) {
                MapSettings()
            }
        }
    }
}

/** Uložená nastavení: dok, ukázkový režim, vzhled, mapa a rozložení ploch. */
object Prefs {
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_DEMO = "demo"
    private const val KEY_LOOK = "look"
    private const val KEY_MAP = "map"
    private const val KEY_LAYOUT = "layout"
    const val MAX_FAVORITES = 6

    private lateinit var store: SharedPreferences

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites

    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo

    private val _look = MutableStateFlow(Look())
    val look: StateFlow<Look> = _look

    private val _map = MutableStateFlow(MapSettings())
    val map: StateFlow<MapSettings> = _map

    private val _layout = MutableStateFlow(DashLayout.default())
    val layout: StateFlow<DashLayout> = _layout

    /** Jeden krok zpět pro úpravy ploch. */
    private var previousLayout: DashLayout? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = context.applicationContext.getSharedPreferences("dash407", Context.MODE_PRIVATE)
        _favorites.value = (store.getString(KEY_FAVORITES, "") ?: "").split('|').filter { it.isNotBlank() }
        _demo.value = store.getBoolean(KEY_DEMO, false)
        _look.value = Look.fromJson(store.getString(KEY_LOOK, null))
        _map.value = MapSettings.fromJson(store.getString(KEY_MAP, null))
        _layout.value = DashLayout.fromJson(store.getString(KEY_LAYOUT, null)) ?: DashLayout.default()
    }

    /** Přidá nebo odebere aplikaci z doku; vrací false, když je dok plný. */
    fun toggleFavorite(packageName: String): Boolean {
        val now = _favorites.value
        val next = when {
            packageName in now -> now - packageName
            now.size >= MAX_FAVORITES -> return false
            else -> now + packageName
        }
        _favorites.value = next
        store.edit().putString(KEY_FAVORITES, next.joinToString("|")).apply()
        return true
    }

    fun setDemo(value: Boolean) {
        _demo.value = value
        store.edit().putBoolean(KEY_DEMO, value).apply()
    }

    fun updateLook(change: (Look) -> Look) {
        val next = change(_look.value)
        _look.value = next
        store.edit().putString(KEY_LOOK, next.toJson()).apply()
    }

    fun updateMap(change: (MapSettings) -> MapSettings) {
        val next = change(_map.value)
        _map.value = next
        store.edit().putString(KEY_MAP, next.toJson()).apply()
    }

    /** Změní dlaždice jedné plochy a uloží stav pro „Zpět“. */
    fun updatePage(pos: PagePos, change: (List<Slot>) -> List<Slot>) {
        val old = _layout.value
        val next = old.with(pos, change(old.page(pos)))
        if (next == old) return
        previousLayout = old
        _canUndo.value = true
        saveLayout(next)
    }

    fun undo() {
        val prev = previousLayout ?: return
        previousLayout = null
        _canUndo.value = false
        saveLayout(prev)
    }

    fun resetLayout() {
        previousLayout = _layout.value
        _canUndo.value = true
        saveLayout(DashLayout.default())
    }

    private fun saveLayout(layout: DashLayout) {
        _layout.value = layout
        store.edit().putString(KEY_LAYOUT, layout.toJson()).apply()
    }
}

/** Úpravy seznamu dlaždic jedné plochy. */
object SlotEdit {
    fun move(slots: List<Slot>, id: Long, delta: Int): List<Slot> {
        val i = slots.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= slots.size) return slots
        val list = slots.toMutableList()
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        return list
    }

    fun toggleWide(slots: List<Slot>, id: Long): List<Slot> =
        slots.map { if (it.id == id) it.copy(wide = !it.wide) else it }

    fun resize(slots: List<Slot>, id: Long, delta: Int): List<Slot> =
        slots.map { if (it.id == id) it.copy(height = (it.height + delta).coerceIn(1, 4)) else it }

    fun remove(slots: List<Slot>, id: Long): List<Slot> = slots.filterNot { it.id == id }

    fun add(slots: List<Slot>, type: WidgetType): List<Slot> = slots + DashLayout.slot(type)
}
