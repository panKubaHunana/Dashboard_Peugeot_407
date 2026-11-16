package cz.kuba.dash407.car

import cz.kuba.dash407.data.Odometer
import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Kde v rámcích CAN boxu leží stav tachometru: typ rámce, pozice prvního bajtu, počet bajtů,
 * pořadí bajtů a jestli je hodnota v desetinách km.
 */
data class OdoField(val type: Int, val offset: Int, val width: Int, val little: Boolean, val tenths: Boolean) {
    fun read(d: ByteArray): Int? {
        if (offset + width > d.size) return null
        var v = 0L
        for (i in 0 until width) {
            val b = d[offset + if (little) width - 1 - i else i].toLong() and 0xFF
            v = (v shl 8) or b
        }
        return (if (tenths) v / 10 else v).toInt()
    }

    fun describe(): String = String.format(
        "rámec %02X, bajty %d–%d (%s%s)", type, offset + 1, offset + width,
        if (little) "od nejnižšího" else "od nejvyššího", if (tenths) ", desetiny km" else ""
    )
}

/**
 * Stav tachometru přímo z CAN boxu.
 *
 * Protokol CAN boxu pro Peugeot 407 není nikde popsaný, proto se místo tachometru jednou „naučí“:
 * Kuba opíše kilometry z přístrojového štítu a aplikace v posledních rámcích z CAN boxu najde
 * číslo, které jim odpovídá. Pak už tachometr čte sama a podle něj opravuje i odhad z GPS.
 */
object CanOdometer {
    private const val KEY = "can_odo_v1"
    /** Odchylka při hledání: štít ukazuje celé km, rámec může být o kus napřed. */
    private const val TOLERANCE_KM = 5
    private const val FRESH_MS = 120_000L

    /** Poslední podoba každého typu rámce z CAN boxu (pro hledání tachometru). */
    private val frames = ConcurrentHashMap<Int, ByteArray>()

    private val _field = MutableStateFlow<OdoField?>(null)
    val field: StateFlow<OdoField?> = _field
    private val _km = MutableStateFlow<Int?>(null)
    /** Poslední stav tachometru z CAN boxu. */
    val km: StateFlow<Int?> = _km
    @Volatile private var kmAt = 0L
    private var loaded = false
    /** Hodnota mimo rozsah, která se ale opakuje: po [RELEARN_FRAMES] stejných rámcích se přijme. */
    private var oddValue = -1
    private var oddCount = 0
    private const val RELEARN_FRAMES = 10

    private fun load() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(raw)
            _field.value = OdoField(o.getInt("type"), o.getInt("offset"), o.getInt("width"), o.getBoolean("little"), o.getBoolean("tenths"))
        } catch (e: Exception) {
            // Poškozené: tachometr se naučí znovu.
        }
    }

    /** Každý rámec z CAN boxu (volá [Rzc.decode]). */
    fun onFrame(type: Int, d: ByteArray) {
        frames[type] = d.copyOf()
        load()
        val f = _field.value ?: return
        if (type != f.type) return
        val v = f.read(d) ?: return
        if (v !in 1..2_000_000) return
        val old = _km.value
        // Tachometr nejde pozpátku; skok o víc než 500 km je chyba v rámci.
        // Když se ale stejná „chybná“ hodnota drží (první přečtená byla nesmysl), přijme se,
        // aby tachometr nezůstal navždy zaseknutý.
        if (old != null && (v < old - 1 || v > old + 500)) {
            if (abs(v - oddValue) <= 2) oddCount++ else { oddValue = v; oddCount = 1 }
            if (oddCount < RELEARN_FRAMES) return
        }
        oddValue = -1
        oddCount = 0
        kmAt = System.currentTimeMillis()
        if (old != v) {
            _km.value = v
            Odometer.calibrate(v, Odometer.SOURCE_CAN)
        }
    }

    /** Stav z CAN boxu, pokud přišel v posledních dvou minutách. */
    fun fresh(): Int? {
        load()
        val v = _km.value ?: return null
        return if (System.currentTimeMillis() - kmAt <= FRESH_MS) v else null
    }

    /** Kolik různých rámců z CAN boxu zatím přišlo. */
    fun frameCount(): Int = frames.size

    /** Všechna místa v rámcích, kde leží číslo blízké [actualKm]. */
    fun candidates(actualKm: Int): List<OdoField> {
        val out = ArrayList<OdoField>()
        for ((type, d) in frames.entries.sortedBy { it.key }) {
            for (width in intArrayOf(3, 4)) {
                for (offset in 0..(d.size - width)) {
                    for (little in booleanArrayOf(false, true)) {
                        for (tenths in booleanArrayOf(false, true)) {
                            val f = OdoField(type, offset, width, little, tenths)
                            val v = f.read(d) ?: continue
                            if (abs(v - actualKm) <= TOLERANCE_KM) out.add(f)
                        }
                    }
                }
            }
        }
        // Stejné číslo může vyjít jako 3 i 4 bajty (nulový horní bajt) – stačí kratší varianta.
        return out.filter { a ->
            out.none { b -> b !== a && b.type == a.type && b.width < a.width && b.little == a.little && b.tenths == a.tenths &&
                (if (a.little) b.offset == a.offset else b.offset == a.offset + 1) }
        }
    }

    /**
     * Najde tachometr v rámcích CAN boxu a uloží jeho místo.
     * Vrací srozumitelnou větu o výsledku.
     */
    fun learn(actualKm: Int): String {
        if (frames.isEmpty()) return "Z CAN boxu zatím nepřišla žádná data. Zapni zapalování a zkus to znovu za pár sekund."
        val found = candidates(actualKm)
        if (found.isEmpty()) {
            return "V datech z CAN boxu (${frames.size} druhů rámců) jsem stav ${actualKm} km nenašel. " +
                "CAN box tachometr zřejmě neposílá – dál se počítá odhad z GPS. Zkus to ještě jednou za jízdy."
        }
        // Nejbližší číslo ke stavu ze štítu; při shodě kratší zápis (3 bajty) a celé km.
        val f = found.minWithOrNull(
            compareBy<OdoField>({ abs((it.read(frames[it.type] ?: ByteArray(0)) ?: Int.MAX_VALUE) - actualKm) }, { it.width }, { it.tenths })
        ) ?: found.first()
        save(f)
        val extra = if (found.size > 1) " Pozor, číslo sedělo na ${found.size} místech – vybral jsem nejbližší. Zkontroluj po jízdě, že tachometr přibývá." else ""
        return "Tachometr nalezen: ${f.describe()}. Dash 407 ho teď čte přímo z CAN boxu.$extra"
    }

    fun forget() {
        _field.value = null
        _km.value = null
        try { Prefs.shared().edit().remove(KEY).apply() } catch (e: Exception) { }
    }

    private fun save(f: OdoField) {
        _field.value = f
        _km.value = null
        try {
            Prefs.shared().edit().putString(
                KEY, JSONObject().put("type", f.type).put("offset", f.offset).put("width", f.width)
                    .put("little", f.little).put("tenths", f.tenths).toString()
            ).apply()
        } catch (e: Exception) { }
        frames[f.type]?.let { onFrame(f.type, it) }
    }
}
