package cz.kuba.dash407.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Vzhled prostředí, který si uživatel nastaví v panelu Vzhled. */
data class Appearance(
    val accent: Long = 0xFF4FC3F7,
    val background: Int = 0,
    val cardAlpha: Float = 0.72f,
    val corner: Int = 22,
    val gap: Int = 8,
    val border: Boolean = true,
    val textScale: Float = 1f,
    val animations: Boolean = true,
    val titles: Boolean = true,
    val darkMap: Boolean = true,
    val mapStyle: String = "basic",
    val seconds: Boolean = false,
    val cardTint: Boolean = false,
    /** dark, light, auto (podle světlometů a času – zatím podle systému). */
    val theme: String = "dark",
    val font: String = "default",
    val intro: Boolean = true,
    /** modern, retro, custom */
    val introStyle: String = "modern",
    val introSeconds: Float = 4f
)

object AppearanceStore {
    private const val KEY = "appearance_v1"
    private val _state = MutableStateFlow(Appearance())
    val state: StateFlow<Appearance> = _state
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(raw)
            val d = Appearance()
            _state.value = Appearance(
                accent = o.optLong("accent", d.accent),
                background = o.optInt("background", d.background),
                cardAlpha = o.optDouble("cardAlpha", d.cardAlpha.toDouble()).toFloat(),
                corner = o.optInt("corner", d.corner),
                gap = o.optInt("gap", d.gap),
                border = o.optBoolean("border", d.border),
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat(),
                animations = o.optBoolean("animations", d.animations),
                titles = o.optBoolean("titles", d.titles),
                darkMap = o.optBoolean("darkMap", d.darkMap),
                mapStyle = o.optString("mapStyle", d.mapStyle),
                seconds = o.optBoolean("seconds", d.seconds),
                cardTint = o.optBoolean("cardTint", d.cardTint),
                theme = o.optString("theme", d.theme),
                font = o.optString("font", d.font),
                intro = o.optBoolean("intro", d.intro),
                introStyle = o.optString("introStyle", d.introStyle),
                introSeconds = o.optDouble("introSeconds", d.introSeconds.toDouble()).toFloat()
            )
        } catch (e: Exception) {
            // Poškozené nastavení: zůstane výchozí vzhled.
        }
    }

    fun update(change: (Appearance) -> Appearance) {
        val a = change(_state.value)
        _state.value = a
        val o = JSONObject()
            .put("accent", a.accent)
            .put("background", a.background)
            .put("cardAlpha", a.cardAlpha.toDouble())
            .put("corner", a.corner)
            .put("gap", a.gap)
            .put("border", a.border)
            .put("textScale", a.textScale.toDouble())
            .put("animations", a.animations)
            .put("titles", a.titles)
            .put("darkMap", a.darkMap)
            .put("mapStyle", a.mapStyle)
            .put("seconds", a.seconds)
            .put("cardTint", a.cardTint)
            .put("theme", a.theme)
            .put("font", a.font)
            .put("intro", a.intro)
            .put("introStyle", a.introStyle)
            .put("introSeconds", a.introSeconds.toDouble())
        try {
            Prefs.shared().edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
            // Neuloženo.
        }
    }

    fun reset() = update { Appearance() }
}
