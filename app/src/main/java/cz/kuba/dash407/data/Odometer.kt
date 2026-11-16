package cz.kuba.dash407.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import kotlin.math.roundToInt

/** Poslední známý stav tachometru a kolik metrů GPS už bylo ujeto, když se zadal. */
data class OdoRef(val km: Int, val gpsM: Double, val at: Long, val source: String)

/**
 * Stav tachometru: přímo z CAN boxu (když je naučený a data chodí), jinak odhad – poslední zadaný stav (ručně, z tankování nebo ze servisní knížky)
 * plus kilometry ujeté podle GPS od té doby. Každé nové tankování s tachometrem odhad opraví.
 */
object Odometer {
    private const val KEY = "odometer_v1"
    /** Zdroj stavu, který přišel přímo z CAN boxu (ne odhad). */
    const val SOURCE_CAN = "CAN box"
    private val _ref = MutableStateFlow<OdoRef?>(null)
    val ref: StateFlow<OdoRef?> = _ref
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(raw)
            _ref.value = OdoRef(o.getInt("km"), o.getDouble("gpsM"), o.optLong("at"), o.optString("source", "ručně"))
        } catch (e: Exception) {
            // Poškozené: odhad začne znovu.
        }
    }

    /** Odhad stavu tachometru v km, nebo null, když ještě není z čeho počítat. */
    fun km(): Int? {
        load()
        // Skutečný stav z CAN boxu má přednost před odhadem.
        cz.kuba.dash407.car.CanOdometer.fresh()?.let { return it }
        val r = _ref.value ?: fallback() ?: return null
        val since = (Trip.state.value.totalM - r.gpsM).coerceAtLeast(0.0)
        return (r.km + since / 1000.0).roundToInt()
    }

    /** Nastaví skutečný stav tachometru (např. z tankování nebo ručně v Knize jízd). */
    fun calibrate(km: Int, source: String) {
        load()
        val r = OdoRef(km, Trip.state.value.totalM, System.currentTimeMillis(), source)
        _ref.value = r
        try {
            Prefs.shared().edit().putString(
                KEY, JSONObject().put("km", r.km).put("gpsM", r.gpsM).put("at", r.at).put("source", r.source).toString()
            ).apply()
        } catch (e: Exception) {
            // Neuloženo.
        }
    }

    /** Bez zadání: nejvyšší stav ze servisní knížky nebo z tankování se vezme jako výchozí. */
    private fun fallback(): OdoRef? {
        val car = ServiceBook.car.value.odoKm
        val fuel = FuelLog.entries.value.maxOfOrNull { it.odometerKm }
        val km = listOfNotNull(car, fuel).maxOrNull() ?: return null
        calibrate(km, if (fuel != null && fuel >= (car ?: 0)) "tankování" else "servisní knížka")
        return _ref.value
    }
}
