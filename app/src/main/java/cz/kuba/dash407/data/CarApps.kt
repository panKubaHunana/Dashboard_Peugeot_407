package cz.kuba.dash407.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Jedna zkratka na původní aplikaci rádia (DSP, volant, klimatizace…). */
data class CarAppSlot(
    val id: String,
    val title: String,
    val description: String,
    /** Části názvu nebo balíčku, podle kterých se aplikace v rádiu najde sama. */
    val keywords: List<String>
)

/**
 * Zkratky na původní aplikace autorádia. Vlastní obrazovky pro DSP, učení tlačítek
 * a klimatizaci přijdou, až budou známé příkazy jednotky; do té doby Dash 407 otevírá
 * původní aplikace. Aplikaci najde podle názvu, nebo si ji Kuba vybere sám.
 */
object CarApps {
    val slots = listOf(
        CarAppSlot("dsp", "Zvuk a DSP", "Ekvalizér, zvukové pole, prostorový zvuk, basy",
            listOf("dsp", "ekvaliz", "equaliz", "zvukov", "sound", "audio")),
        CarAppSlot("keys", "Tlačítka na volantu", "Učení tlačítek (Klíčová studie)",
            listOf("klíčová studie", "klicova studie", "studie", "steer", "swc", "volant", "wheel key", "key learn", "keystudy")),
        CarAppSlot("climate", "Klimatizace", "Ovládání a nastavení klimatizace",
            listOf("klimatiz", "centrální ovládání", "centralni ovladani", "climate", "hvac", "aircon", "air condition")),
        CarAppSlot("car", "Nastavení vozu", "Nastavení CAN boxu a funkcí vozu",
            listOf("nastavení vozu", "nastaveni vozu", "car setting", "carsetting", "vehicle", "canbus", "car info"))
    )

    private val _chosen = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Ručně vybrané balíčky podle id zkratky. */
    val chosen: StateFlow<Map<String, String>> = _chosen
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        val p = try { Prefs.shared() } catch (e: Exception) { return }
        _chosen.value = slots.mapNotNull { s -> p.getString("carapp_" + s.id, null)?.takeIf { it.isNotBlank() }?.let { s.id to it } }.toMap()
    }

    fun choose(slotId: String, packageName: String?) {
        _chosen.value = if (packageName == null) _chosen.value - slotId else _chosen.value + (slotId to packageName)
        try { Prefs.shared().edit().putString("carapp_$slotId", packageName ?: "").apply() } catch (e: Exception) { }
    }

    /** Ručně vybraná aplikace, jinak první nalezená podle názvu; null = v rádiu nenalezena. */
    fun resolve(slot: CarAppSlot, apps: List<AppEntry>, ownPackage: String): AppEntry? {
        _chosen.value[slot.id]?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
        return apps.firstOrNull { a ->
            a.packageName != ownPackage && slot.keywords.any { k ->
                a.label.contains(k, ignoreCase = true) || a.packageName.contains(k.replace(" ", ""), ignoreCase = true)
            }
        }
    }
}
