package cz.kuba.dash407.car

/*
 * Protokol CAN boxu Raise (RZC) pro vozy PSA, jak ho CAN box posílá jednotce:
 *   FD LEN TYP data… SUMA
 * LEN počítá sám sebe, typ, data i sumu; SUMA je spodní bajt součtu LEN + TYP + data.
 * Význam typů 0x33/0x34/0x35 (palubní počítač), 0x38 (dveře) a 0x3A (výstrahy)
 * je zatím neověřený na Peugeotu 407 – proto existuje obrazovka Signály.
 */

const val RZC_TRIP_NOW = 0x33
const val RZC_TRIP_1 = 0x34
const val RZC_TRIP_2 = 0x35
const val RZC_DOORS = 0x38
const val RZC_WARNINGS = 0x3A
const val RZC_ASK = 0x8F

/** Skládá rámce z proudu bajtů, který chodí po kouskách. */
class RzcAssembler(private val onFrame: (type: Int, data: ByteArray) -> Unit) {
    private val pending = ArrayList<Byte>(128)
    private val head: Byte = 0xFD.toByte()

    fun accept(chunk: ByteArray, offset: Int) {
        for (i in offset until chunk.size) pending.add(chunk[i])
        if (pending.size > 1024) pending.clear()
        drain()
    }

    private fun drain() {
        var pos = 0
        while (true) {
            while (pos < pending.size && pending[pos] != head) pos++
            if (pending.size - pos < 4) break
            val len = pending[pos + 1].toInt() and 0xFF
            if (len < 3 || len > 64) {
                pos++
                continue
            }
            val total = len + 1
            if (pending.size - pos < total) break
            var sum = 0
            for (i in 1 until total - 1) sum += pending[pos + i].toInt() and 0xFF
            if ((sum and 0xFF) == (pending[pos + total - 1].toInt() and 0xFF)) {
                val type = pending[pos + 2].toInt() and 0xFF
                val data = ByteArray(total - 4) { pending[pos + 3 + it] }
                onFrame(type, data)
                pos += total
            } else {
                pos++
            }
        }
        if (pos > 0) pending.subList(0, pos).clear()
    }

}

object Rzc {
    private fun word(d: ByteArray, i: Int): Int = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)

    /** Promítne jeden rámec do stavu auta. */
    fun decode(type: Int, d: ByteArray) {
        when (type) {
            RZC_TRIP_NOW -> if (d.size >= 4) {
                val instant = word(d, 0)
                val range = word(d, 2)
                CarRepo.update {
                    it.copy(
                        instantL100 = if (instant <= 300) instant / 10f else null,
                        rangeKm = if (range in 1..2000) range else null
                    )
                }
            }
            RZC_TRIP_1, RZC_TRIP_2 -> if (d.size >= 6) {
                val avg = word(d, 0)
                val speed = word(d, 2)
                val dist = word(d, 4)
                val page = TripPage(
                    avgL100 = if (avg <= 300) avg / 10f else null,
                    avgKmh = if (speed <= 250) speed else null,
                    distanceKm = if (dist <= 9999) dist else null
                )
                CarRepo.update { if (type == RZC_TRIP_1) it.copy(trip1 = page) else it.copy(trip2 = page) }
            }
            RZC_DOORS -> if (d.isNotEmpty()) {
                val bits = d[0].toInt() and 0xFF
                val doors = Doors(
                    frontLeft = bits and 0x80 != 0,
                    frontRight = bits and 0x40 != 0,
                    rearLeft = bits and 0x20 != 0,
                    rearRight = bits and 0x10 != 0,
                    tailgate = bits and 0x08 != 0,
                    bonnet = bits and 0x04 != 0
                )
                CarRepo.update { it.copy(doors = doors) }
            }
            RZC_WARNINGS -> if (d.isNotEmpty()) {
                val count = minOf(d[0].toInt() and 0xFF, d.size - 1)
                val codes = ArrayList<Int>()
                for (i in 1..count) {
                    val code = d[i].toInt() and 0xFF
                    if (code != 0x00 && code != 0xF0 && code != 0xFF && code !in codes) codes.add(code)
                }
                CarRepo.update { it.copy(warnings = codes) }
            }
        }
    }

    /** Dotaz na jednu stránku: FD 04 8F typ suma. */
    fun question(type: Int): ByteArray {
        val frame = byteArrayOf(0xFD.toByte(), 0x04, RZC_ASK.toByte(), type.toByte(), 0)
        var sum = 0
        for (i in 1 until frame.size - 1) sum += frame[i].toInt() and 0xFF
        frame[frame.size - 1] = sum.toByte()
        return frame
    }
}
