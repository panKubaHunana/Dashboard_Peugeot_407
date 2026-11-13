package cz.kuba.dash407.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * Přetížení v g. Rádio stojí v palubní desce displejem k řidiči: osa X je do strany,
 * osa Z míří k řidiči (dozadu). Zrychlení dopředu je tedy kladné longitudinal.
 */
data class GState(
    val lateral: Float = 0f,
    val longitudinal: Float = 0f,
    val peakLeft: Float = 0f,
    val peakRight: Float = 0f,
    val peakAccel: Float = 0f,
    val peakBrake: Float = 0f,
    val available: Boolean = true
)

object GForce {
    private const val G = 9.80665f
    private val _state = MutableStateFlow(GState())
    val state: StateFlow<GState> = _state

    private var manager: SensorManager? = null
    private var users = 0
    private var linear = false
    private val gravity = FloatArray(3)
    private var smoothX = 0f
    private var smoothZ = 0f
    private var zeroX = 0f
    private var zeroZ = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            var x = event.values[0]
            var z = event.values[2]
            if (!linear) {
                // Bez senzoru lineárního zrychlení: gravitaci odfiltrujeme dolní propustí.
                for (i in 0..2) gravity[i] = 0.9f * gravity[i] + 0.1f * event.values[i]
                x -= gravity[0]
                z -= gravity[2]
            }
            smoothX = 0.85f * smoothX + 0.15f * (x / G)
            smoothZ = 0.85f * smoothZ + 0.15f * (z / G)
            val lat = smoothX - zeroX
            val lon = -(smoothZ - zeroZ)
            val s = _state.value
            _state.value = s.copy(
                lateral = lat,
                longitudinal = lon,
                peakLeft = if (lat < 0) max(s.peakLeft, abs(lat)) else s.peakLeft,
                peakRight = if (lat > 0) max(s.peakRight, lat) else s.peakRight,
                peakAccel = if (lon > 0) max(s.peakAccel, lon) else s.peakAccel,
                peakBrake = if (lon < 0) max(s.peakBrake, abs(lon)) else s.peakBrake
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    @Synchronized
    fun acquire(context: Context) {
        users++
        if (users > 1) return
        val m = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        manager = m
        val sensor = m?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: m?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (m == null || sensor == null) {
            _state.value = GState(available = false)
            return
        }
        linear = sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
        m.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) manager?.unregisterListener(listener)
    }

    /** Vynuluje špičky a aktuální hodnotu vezme jako nulu (rádio nemusí stát svisle). */
    fun calibrate() {
        zeroX = smoothX
        zeroZ = smoothZ
        _state.value = GState(available = _state.value.available)
    }
}
