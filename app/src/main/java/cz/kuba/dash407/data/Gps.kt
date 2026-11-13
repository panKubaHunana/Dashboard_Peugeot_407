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

/** Rychlost z GPS. Bez oprávnění k poloze zůstává null. */
object Gps {
    private val _speedKmh = MutableStateFlow<Int?>(null)
    val speedKmh: StateFlow<Int?> = _speedKmh

    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed

    private var manager: LocationManager? = null
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _speedKmh.value = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else null
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            _speedKmh.value = null
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

    /** Ukázkový režim si rychlost nastavuje sám. */
    fun demoSpeed(value: Int?) {
        _speedKmh.value = value
    }
}
