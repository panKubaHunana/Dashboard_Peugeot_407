package cz.kuba.dash407.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import cz.kuba.dash407.nav.RouteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Jedna poloha z GPS. Rychlost v km/h, kurz ve stupních od severu. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val speedKmh: Float?,
    val bearing: Float?,
    val altitude: Double?,
    val accuracy: Float?,
    val time: Long
)

/** Počet satelitů: použité pro výpočet polohy / viditelné. */
data class Satellites(val used: Int = 0, val visible: Int = 0)

/**
 * Poloha z GPS pro celé prostředí. Spouští ji aktivita i služba Jízda,
 * běží, dokud ji potřebuje aspoň jeden z nich.
 */
object Gps {
    private val _speedKmh = MutableStateFlow<Int?>(null)
    val speedKmh: StateFlow<Int?> = _speedKmh

    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix

    private val _satellites = MutableStateFlow(Satellites())
    val satellites: StateFlow<Satellites> = _satellites

    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed

    private var manager: LocationManager? = null
    private var running = false
    private val users = HashSet<String>()
    @Volatile private var demo = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (demo) return
            publish(
                Fix(
                    lat = location.latitude,
                    lon = location.longitude,
                    speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
                    bearing = if (location.hasBearing()) location.bearing else null,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    time = System.currentTimeMillis()
                )
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            if (!demo) _speedKmh.value = null
        }
    }

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _satellites.value = Satellites(used, status.satelliteCount)
        }
    }

    /**
     * [fake] = vymyšlená poloha z ukázkového režimu: jde jen do widgetů, mapy a navigace,
     * nikdy do Knihy jízd, odhadu tachometru ani automatického tankování.
     */
    private fun publish(f: Fix, fake: Boolean = false) {
        _fix.value = f
        _speedKmh.value = f.speedKmh?.roundToInt()
        if (!fake) {
            Trip.onFix(f)
            FuelStops.onFix(f)
        }
        Perf.onFix(f)
        SpeedLimit.onFix(f)
        RouteState.onFix(f)
        cz.kuba.dash407.nav.NavEngine.onFix(f)
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(context: Context, who: String = "app") {
        val ok = hasPermission(context)
        _allowed.value = ok
        if (!ok) return
        users.add(who)
        if (running) return
        val m = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        manager = m
        try {
            if (m.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
                running = true
            }
            @Suppress("DEPRECATION")
            m.registerGnssStatusCallback(gnss, Handler(Looper.getMainLooper()))
            // Poslední známá poloha, aby mapa a počasí nečekaly na první fix.
            if (_fix.value == null) {
                m.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { listener.onLocationChanged(it) }
            }
        } catch (e: Exception) {
            running = false
        }
    }

    @Synchronized
    fun stop(who: String = "app") {
        users.remove(who)
        if (!running || users.isNotEmpty()) return
        try {
            manager?.removeUpdates(listener)
            manager?.unregisterGnssStatusCallback(gnss)
        } catch (e: Exception) {
            // Nic k uklizení.
        }
        running = false
    }

    /** Ukázkový režim si rychlost nastavuje sám. */
    fun demoSpeed(value: Int?) {
        _speedKmh.value = value
    }

    /** Ukázkový režim: vymyšlená poloha místo skutečné. */
    fun demoFix(f: Fix?) {
        demo = f != null
        if (f != null) {
            publish(f, fake = true)
            _satellites.value = Satellites(12, 17)
        }
    }

    /** Vzdálenost dvou bodů v metrech (haversine). */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
