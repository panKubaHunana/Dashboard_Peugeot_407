package cz.kuba.dash407.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Poloha a rychlost z GPS. Běží po celou dobu života aplikace (i když je vpředu
 * Mapy.com), aby počítadlo jízdy nevynechávalo úseky.
 */
object Gps {
    private val _speedKmh = MutableStateFlow<Int?>(null)
    val speedKmh: StateFlow<Int?> = _speedKmh

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed

    private var manager: LocationManager? = null
    private var running = false
    @Volatile private var demo = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (demo) return
            _speedKmh.value = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else null
            _location.value = location
            TripMeter.onLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            if (!demo) _speedKmh.value = null
        }
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val ok = hasPermission(context)
        _allowed.value = ok
        if (!ok || running) return
        val m = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        manager = m
        try {
            // Poslední známá poloha hned po startu (počasí, mapa), než chytí GPS.
            if (_location.value == null) {
                val last = m.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: m.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (last != null) _location.value = last
            }
            if (m.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
                running = true
            }
        } catch (e: Exception) {
            running = false
        }
    }

    fun stop() {
        if (!running) return
        try {
            manager?.removeUpdates(listener)
        } catch (e: Exception) {
            // Nic k uklizení.
        }
        running = false
    }

    /** Ukázkový režim si rychlost nastavuje sám; null ukázku ukončí. */
    fun demoSpeed(value: Int?) {
        demo = value != null
        _speedKmh.value = value
    }
}

/** Stav počítadla jízdy. */
data class TripState(
    val startedAt: Long,
    val distanceM: Double = 0.0,
    val movingMs: Long = 0L,
    val topKmh: Int = 0
) {
    /** Průměrná rychlost v pohybu (km/h), nebo null, dokud auto nejelo. */
    val averageKmh: Int?
        get() = if (movingMs < 5_000L) null else (distanceM / 1000.0 / (movingMs / 3_600_000.0)).roundToInt()
}

/** Počítadlo jízdy z GPS: vzdálenost, čas v pohybu, průměr a maximum. */
object TripMeter {
    private const val MAX_ACCURACY_M = 35f
    private const val MOVING_KMH = 3f

    private val _state = MutableStateFlow(TripState(System.currentTimeMillis()))
    val state: StateFlow<TripState> = _state

    private var last: Location? = null

    @Synchronized
    fun onLocation(loc: Location) {
        val accurate = !loc.hasAccuracy() || loc.accuracy <= MAX_ACCURACY_M
        if (!accurate) return
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        var s = _state.value
        val prev = last
        if (prev != null) {
            val dt = loc.time - prev.time
            if (dt in 1..10_000 && kmh >= MOVING_KMH) {
                s = s.copy(distanceM = s.distanceM + prev.distanceTo(loc), movingMs = s.movingMs + dt)
            }
        }
        val rounded = kmh.roundToInt()
        if (rounded > s.topKmh) s = s.copy(topKmh = rounded)
        _state.value = s
        last = loc
    }

    /** Ukázkový režim: jedna sekunda jízdy danou rychlostí. */
    @Synchronized
    fun demoTick(kmh: Int) {
        val s = _state.value
        _state.value = s.copy(
            distanceM = s.distanceM + kmh / 3.6,
            movingMs = s.movingMs + if (kmh > 0) 1000L else 0L,
            topKmh = maxOf(s.topKmh, kmh)
        )
    }

    @Synchronized
    fun reset() {
        last = null
        _state.value = TripState(System.currentTimeMillis())
    }
}
