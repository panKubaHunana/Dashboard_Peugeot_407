package cz.kuba.dash407.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Které dveře jsou otevřené. */
data class Doors(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val tailgate: Boolean = false,
    val bonnet: Boolean = false
) {
    val anyOpen: Boolean
        get() = frontLeft || frontRight || rearLeft || rearRight || tailgate || bonnet
}

/** Jedna stránka palubního počítače (Trip 1 nebo Trip 2). */
data class TripPage(val avgL100: Float?, val avgKmh: Int?, val distanceKm: Int?)

/** Všechno, co o autě právě víme. Hodnota null znamená „zatím nepřišlo“. */
data class CarState(
    val linked: Boolean = false,
    val lastMessageAt: Long = 0L,
    val instantL100: Float? = null,
    val rangeKm: Int? = null,
    val trip1: TripPage? = null,
    val trip2: TripPage? = null,
    val doors: Doors? = null,
    val warnings: List<Int> = emptyList(),
    val volts: Double? = null,
    val headlights: Boolean? = null,
    val handbrake: Boolean? = null
)

/** Poslední podoba jedné zprávy z jednotky, pro obrazovku Signály. */
data class RawEntry(val key: String, val hex: String, val at: Long, val count: Int)

object CarRepo {
    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> = _state

    private val _raw = MutableStateFlow<List<RawEntry>>(emptyList())
    val raw: StateFlow<List<RawEntry>> = _raw

    private val latest = LinkedHashMap<String, RawEntry>()
    private const val HEX = "0123456789ABCDEF"

    @Synchronized
    fun update(change: (CarState) -> CarState) {
        _state.value = change(_state.value)
    }

    @Synchronized
    fun reset() {
        _state.value = CarState()
        latest.clear()
        _raw.value = emptyList()
    }

    /** Zapíše zprávu do přehledu signálů; seznam se mění jen při nové hodnotě. */
    @Synchronized
    fun logRaw(key: String, bytes: ByteArray) {
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(HEX[v shr 4]).append(HEX[v and 0x0F])
        }
        val hex = sb.toString()
        val old = latest[key]
        if (old != null && old.hex == hex) return
        latest[key] = RawEntry(key, hex, System.currentTimeMillis(), (old?.count ?: 0) + 1)
        _raw.value = latest.values.sortedBy { it.key }
    }
}
