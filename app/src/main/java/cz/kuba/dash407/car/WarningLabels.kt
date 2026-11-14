package cz.kuba.dash407.car

import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Vlastní pojmenování kódů výstrah. Význam kódů z CAN boxu pro Peugeot 407 není nikde
 * spolehlivě popsaný, proto si ho Kuba doplní sám podle toho, co v tu chvíli ukázal displej auta.
 */
object WarningLabels {
    private const val KEY = "warning_labels_v1"
    private val _labels = MutableStateFlow<Map<Int, String>>(emptyMap())
    val labels: StateFlow<Map<Int, String>> = _labels
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(raw)
            _labels.value = o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.getString(k) } }.toMap()
        } catch (e: Exception) {
            // Poškozené: bez popisků.
        }
    }

    fun set(code: Int, text: String) {
        init()
        val m = _labels.value.toMutableMap()
        if (text.isBlank()) m.remove(code) else m[code] = text.trim()
        _labels.value = m
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), v)
        try { Prefs.shared().edit().putString(KEY, o.toString()).apply() } catch (e: Exception) { }
    }
}
