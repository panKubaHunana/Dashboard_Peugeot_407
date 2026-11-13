package cz.kuba.dash407.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Jedno tankování. */
data class Refuel(
    val id: Long,
    val at: Long,
    val odometerKm: Int,
    val litres: Double,
    val price: Double,
    val full: Boolean
)

/** Souhrn knihy tankování. */
data class FuelStats(
    val averageL100: Double?,
    val lastL100: Double?,
    val totalLitres: Double,
    val totalPrice: Double,
    val pricePerKm: Double?,
    val trackedKm: Int
)

/** Kniha tankování uložená jako JSON v soukromém úložišti aplikace. */
object FuelLog {
    private lateinit var file: File

    private val _entries = MutableStateFlow<List<Refuel>>(emptyList())
    /** Od nejnovějšího po nejstarší. */
    val entries: StateFlow<List<Refuel>> = _entries

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "tankovani.json")
        _entries.value = read()
    }

    private fun read(): List<Refuel> = try {
        if (!file.exists()) emptyList() else {
            val array = JSONArray(file.readText())
            val list = ArrayList<Refuel>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    Refuel(
                        id = o.getLong("id"),
                        at = o.getLong("at"),
                        odometerKm = o.getInt("km"),
                        litres = o.getDouble("litres"),
                        price = o.getDouble("price"),
                        full = o.optBoolean("full", true)
                    )
                )
            }
            list.sortedByDescending { it.odometerKm }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(list: List<Refuel>) {
        val array = JSONArray()
        for (r in list) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("at", r.at)
                    .put("km", r.odometerKm)
                    .put("litres", r.litres)
                    .put("price", r.price)
                    .put("full", r.full)
            )
        }
        try {
            file.writeText(array.toString())
        } catch (e: Exception) {
            // Zápis se nepovedl: záznam zůstane aspoň v paměti do konce běhu.
        }
    }

    fun add(odometerKm: Int, litres: Double, price: Double, full: Boolean) {
        val now = System.currentTimeMillis()
        val next = (_entries.value + Refuel(now, now, odometerKm, litres, price, full))
            .sortedByDescending { it.odometerKm }
        _entries.value = next
        write(next)
    }

    fun remove(id: Long) {
        val next = _entries.value.filter { it.id != id }
        _entries.value = next
        write(next)
    }

    /**
     * Spotřeba mezi dvěma plnými nádržemi: litry natankované po předchozí plné
     * až do této plné, dělené ujetými kilometry. Klíčem je id tankování.
     */
    fun consumptions(list: List<Refuel>): Map<Long, Double> {
        val result = HashMap<Long, Double>()
        val ascending = list.sortedBy { it.odometerKm }
        var lastFullKm: Int? = null
        var litresSince = 0.0
        for (r in ascending) {
            val from = lastFullKm
            if (from == null) {
                if (r.full) lastFullKm = r.odometerKm
                continue
            }
            litresSince += r.litres
            if (r.full) {
                val km = r.odometerKm - from
                if (km > 0) result[r.id] = litresSince / km * 100.0
                lastFullKm = r.odometerKm
                litresSince = 0.0
            }
        }
        return result
    }

    fun stats(list: List<Refuel>): FuelStats {
        val ascending = list.sortedBy { it.odometerKm }
        var firstFullKm: Int? = null
        var lastFullKm: Int? = null
        var litresSince = 0.0
        var litresCounted = 0.0
        var priceSince = 0.0
        var priceCounted = 0.0
        for (r in ascending) {
            if (firstFullKm == null) {
                if (r.full) {
                    firstFullKm = r.odometerKm
                    lastFullKm = r.odometerKm
                }
                continue
            }
            litresSince += r.litres
            priceSince += r.price
            if (r.full) {
                litresCounted += litresSince
                priceCounted += priceSince
                litresSince = 0.0
                priceSince = 0.0
                lastFullKm = r.odometerKm
            }
        }
        val km = if (firstFullKm != null && lastFullKm != null) lastFullKm - firstFullKm else 0
        val perFill = consumptions(list)
        val newestMeasured = list.firstOrNull { perFill.containsKey(it.id) }
        return FuelStats(
            averageL100 = if (km > 0) litresCounted / km * 100.0 else null,
            lastL100 = newestMeasured?.let { perFill[it.id] },
            totalLitres = list.sumOf { it.litres },
            totalPrice = list.sumOf { it.price },
            pricePerKm = if (km > 0) priceCounted / km else null,
            trackedKm = km
        )
    }
}
