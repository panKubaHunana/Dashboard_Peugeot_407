package cz.kuba.dash407.car

import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Gps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/** Ukázkový režim: vymyšlená jízda, aby šlo prostředí vyzkoušet i mimo auto. */
object Demo {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        McuLink.setPaused(true)
        job = scope.launch {
            var t = 0
            var angle = 0.0
            while (isActive) {
                val wave = sin(t / 9.0)
                val speed = (70 + 45 * wave).roundToInt().coerceAtLeast(0)
                val instant = (5.6 + 2.4 * sin(t / 4.0)).toFloat()
                val doorOpen = t % 40 in 30..34
                // Vymyšlená jízda po kruhu kolem centra Hradce Králové.
                angle += speed / 3.6 / 1500.0
                val lat = 50.2092 + 0.0135 * cos(angle)
                val lon = 15.8328 + 0.021 * sin(angle)
                val bearing = ((Math.toDegrees(angle) + 90.0) % 360.0).toFloat()
                Gps.demoFix(Fix(lat, lon, speed.toFloat(), bearing, 240.0, 5f, System.currentTimeMillis()))
                Gps.demoSpeed(speed)
                CarRepo.update {
                    it.copy(
                        linked = true,
                        lastMessageAt = System.currentTimeMillis(),
                        instantL100 = (instant * 10).roundToInt() / 10f,
                        rangeKm = 640 - t % 600,
                        trip1 = TripPage(6.1f, 62, 348 + t / 20),
                        trip2 = TripPage(6.4f, 54, 2715 + t / 20),
                        doors = Doors(tailgate = doorOpen),
                        volts = 14.1,
                        headlights = true,
                        handbrake = false,
                        warnings = emptyList()
                    )
                }
                t++
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        McuLink.setPaused(false)
        Gps.demoFix(null)
        Gps.demoSpeed(null)
        CarRepo.reset()
        CarRepo.update { it.copy(linked = McuLink.isLinked) }
    }
}
