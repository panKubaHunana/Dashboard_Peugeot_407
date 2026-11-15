package cz.kuba.dash407.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Druhy záznamů v servisní knížce. */
enum class ServiceKind(val label: String, val defKm: Int, val defMonths: Int) {
    SERVICE("Pravidelná prohlídka", 20_000, 12),
    OIL("Výměna oleje", 20_000, 12),
    AIR("Vzduchový filtr", 40_000, 48),
    FUEL("Palivový filtr", 60_000, 48),
    BRAKES("Servis brzd", 0, 24)
}

/** Interval připomínky; 0 = nesledovat. */
data class Interval(val km: Int, val months: Int)

data class ServiceEntry(
    val id: Long,
    val kind: ServiceKind,
    val dateMs: Long,
    val km: Int?,
    val price: Double?,
    val note: String
)

data class CarInfo(
    val plate: String = "",
    val vin: String = "",
    val engine: String = "",
    /** Ručně zadaný stav tachometru a kdy byl zadán. */
    val odoKm: Int? = null,
    val odoAt: Long = 0L
)

enum class Level { UNKNOWN, OK, SOON, OVERDUE }

/** Vyhodnocení jedné položky: kdy a při kolika km má přijít další servis. */
data class DueInfo(
    val kind: ServiceKind,
    val last: ServiceEntry?,
    val nextKm: Int?,
    val nextDate: LocalDate?,
    val remainingKm: Int?,
    val remainingDays: Long?,
    val level: Level
)

/** Servisní knížka uložená jako JSON v soukromém úložišti aplikace. */
object ServiceBook {
    private lateinit var file: File
    private val zone: ZoneId get() = ZoneId.systemDefault()
    val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d. M. yyyy")

    private val _car = MutableStateFlow(CarInfo())
    val car: StateFlow<CarInfo> = _car
    private val _entries = MutableStateFlow<List<ServiceEntry>>(emptyList())
    /** Od nejnovějšího. */
    val entries: StateFlow<List<ServiceEntry>> = _entries
    private val _intervals = MutableStateFlow(ServiceKind.values().associateWith { Interval(it.defKm, it.defMonths) })
    val intervals: StateFlow<Map<ServiceKind, Interval>> = _intervals

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "servisni_knizka.json")
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            root.optJSONObject("car")?.let {
                _car.value = CarInfo(
                    plate = it.optString("plate"), vin = it.optString("vin"), engine = it.optString("engine"),
                    odoKm = if (it.has("odoKm")) it.optInt("odoKm") else null, odoAt = it.optLong("odoAt")
                )
            }
            root.optJSONObject("intervals")?.let { o ->
                _intervals.value = ServiceKind.values().associateWith { k ->
                    o.optJSONObject(k.name)?.let { Interval(it.optInt("km", k.defKm), it.optInt("months", k.defMonths)) }
                        ?: Interval(k.defKm, k.defMonths)
                }
            }
            val arr = root.optJSONArray("entries") ?: JSONArray()
            _entries.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val kind = ServiceKind.values().firstOrNull { it.name == o.optString("kind") } ?: return@mapNotNull null
                ServiceEntry(
                    o.getLong("id"), kind, o.getLong("date"),
                    if (o.has("km")) o.optInt("km") else null,
                    if (o.has("price")) o.optDouble("price") else null,
                    o.optString("note")
                )
            }.sortedByDescending { it.dateMs }
        } catch (e: Exception) {
            // Poškozený soubor: začne se prázdnou knížkou, původní soubor zůstane na disku.
        }
    }

    private fun save() {
        if (!::file.isInitialized) return
        try {
            val c = _car.value
            val car = JSONObject().put("plate", c.plate).put("vin", c.vin).put("engine", c.engine).put("odoAt", c.odoAt)
            c.odoKm?.let { car.put("odoKm", it) }
            val iv = JSONObject()
            for ((k, v) in _intervals.value) iv.put(k.name, JSONObject().put("km", v.km).put("months", v.months))
            val arr = JSONArray()
            for (e in _entries.value) {
                val o = JSONObject().put("id", e.id).put("kind", e.kind.name).put("date", e.dateMs).put("note", e.note)
                e.km?.let { o.put("km", it) }
                e.price?.let { o.put("price", it) }
                arr.put(o)
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("car", car).put("intervals", iv).put("entries", arr).toString())
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        } catch (e: Exception) {
            // Neuloženo.
        }
    }

    fun setCar(change: (CarInfo) -> CarInfo) { _car.value = change(_car.value); save() }

    fun setOdometer(km: Int?) {
        setCar { it.copy(odoKm = km, odoAt = System.currentTimeMillis()) }
        if (km != null) Odometer.calibrate(km, "servisní knížka")
    }

    fun setInterval(kind: ServiceKind, interval: Interval) {
        _intervals.value = _intervals.value + (kind to interval)
        save()
    }

    fun add(kind: ServiceKind, date: LocalDate, km: Int?, price: Double?, note: String) {
        val e = ServiceEntry(System.currentTimeMillis(), kind, date.atStartOfDay(zone).toInstant().toEpochMilli(), km, price, note.trim())
        _entries.value = (_entries.value + e).sortedByDescending { it.dateMs }
        // Záznam s kilometry je i čerstvý údaj o tachometru.
        if (km != null && km > (_car.value.odoKm ?: 0)) _car.value = _car.value.copy(odoKm = km, odoAt = System.currentTimeMillis())
        save()
    }

    fun remove(id: Long) {
        _entries.value = _entries.value.filterNot { it.id == id }
        save()
    }

    /** Nejlepší odhad stavu tachometru: nejvyšší z ručního zadání, tankování a záznamů servisu. */
    fun currentKm(fuel: List<Refuel>): Int? {
        val c = _car.value.odoKm
        val f = fuel.maxOfOrNull { it.odometerKm }
        val s = _entries.value.mapNotNull { it.km }.maxOrNull()
        // Odhad z Knihy jízd (poslední stav + km z GPS) bývá nejčerstvější.
        val o = if (_car.value.odoKm != null || f != null) Odometer.km() else null
        return listOfNotNull(c, f, s, o).maxOrNull()
    }

    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun due(kind: ServiceKind, currentKm: Int?, today: LocalDate = LocalDate.now()): DueInfo {
        val last = _entries.value.firstOrNull { it.kind == kind }
        val iv = _intervals.value[kind] ?: Interval(kind.defKm, kind.defMonths)
        if (last == null || (iv.km <= 0 && iv.months <= 0)) return DueInfo(kind, last, null, null, null, null, Level.UNKNOWN)
        val nextKm = if (iv.km > 0 && last.km != null) last.km + iv.km else null
        val nextDate = if (iv.months > 0) dateOf(last.dateMs).plusMonths(iv.months.toLong()) else null
        val remKm = if (nextKm != null && currentKm != null) nextKm - currentKm else null
        val remDays = nextDate?.let { ChronoUnit.DAYS.between(today, it) }
        val level = when {
            (remKm != null && remKm < 0) || (remDays != null && remDays < 0) -> Level.OVERDUE
            (remKm != null && remKm < 1000) || (remDays != null && remDays < 30) -> Level.SOON
            remKm == null && remDays == null -> Level.UNKNOWN
            else -> Level.OK
        }
        return DueInfo(kind, last, nextKm, nextDate, remKm, remDays, level)
    }

    /** Nejnaléhavější položka pro widget na ploše. */
    fun mostUrgent(currentKm: Int?): DueInfo? =
        ServiceKind.values().map { due(it, currentKm) }
            .filter { it.level != Level.UNKNOWN }
            .minWithOrNull(compareByDescending<DueInfo> { it.level.ordinal }.thenBy { it.remainingDays ?: Long.MAX_VALUE })

    /** Kontrola VIN: 17 znaků, bez I, O, Q. Evropské VIN nemají kontrolní číslici, ta se proto neověřuje. */
    fun vinProblem(vin: String): String? {
        if (vin.isEmpty()) return null
        if (vin.length != 17) return "VIN má mít 17 znaků (zatím ${vin.length})."
        if (!Regex("[A-HJ-NPR-Z0-9]{17}").matches(vin)) return "VIN smí obsahovat jen písmena A–Z bez I, O, Q a číslice."
        return null
    }

    /** Výrobce podle prvních tří znaků VIN; jen Peugeot, jinak null. */
    fun makerOf(vin: String): String? = when {
        vin.startsWith("VF3") -> "Peugeot (Francie)"
        else -> null
    }
}
