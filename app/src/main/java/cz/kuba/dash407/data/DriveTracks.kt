package cz.kuba.dash407.data

import android.content.Context
import java.io.File
import java.util.Locale

/** Bod trasy (WGS-84). */
data class GeoPt(val lat: Double, val lon: Double)

/**
 * Skutečné trasy jízd: body zhruba po 40 m v souborech trasy/<id jízdy>.txt („šířka,délka“ na řádek).
 * Probíhající jízda se průběžně ukládá do trasy/current.txt, takže přežije vypnutí rádia.
 */
object DriveTracks {
    /** Nový bod trasy, když se auto posune aspoň o tolik metrů. */
    const val STEP_M = 40.0
    private var dir: File? = null
    private val buffer = StringBuilder()
    private var buffered = 0

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.filesDir, "trasy").also { it.mkdirs() }
    }

    private fun current(): File? = dir?.let { File(it, "current.txt") }
    private fun line(lat: Double, lon: Double) = String.format(Locale.US, "%.6f,%.6f\n", lat, lon)

    /** Začátek jízdy: zahodí rozpracovanou trasu a zapíše první bod. */
    @Synchronized
    fun start(lat: Double, lon: Double) {
        if (SafeFile.frozen) return
        buffer.setLength(0)
        buffered = 0
        try { current()?.writeText(line(lat, lon)) } catch (e: Exception) { }
    }

    @Synchronized
    fun append(lat: Double, lon: Double) {
        buffer.append(line(lat, lon))
        buffered++
        if (buffered >= 5) flush()
    }

    @Synchronized
    private fun flush() {
        if (buffer.isEmpty() || SafeFile.frozen) return
        try { current()?.appendText(buffer.toString()) } catch (e: Exception) { }
        buffer.setLength(0)
        buffered = 0
    }

    /** Konec jízdy: trasa dostane id jízdy. */
    @Synchronized
    fun finish(id: Long) {
        if (SafeFile.frozen) return
        flush()
        val d = dir ?: return
        val f = current() ?: return
        if (f.exists()) f.renameTo(File(d, "$id.txt"))
    }

    @Synchronized
    fun discard() {
        buffer.setLength(0)
        buffered = 0
        current()?.delete()
    }

    fun delete(id: Long) {
        dir?.let { File(it, "$id.txt").delete() }
    }

    /** Body trasy jízdy (volat mimo hlavní vlákno). */
    fun load(id: Long): List<GeoPt> {
        val f = dir?.let { File(it, "$id.txt") } ?: return emptyList()
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().mapNotNull { l ->
                val i = l.indexOf(',')
                if (i < 0) null else {
                    val lat = l.substring(0, i).toDoubleOrNull()
                    val lon = l.substring(i + 1).toDoubleOrNull()
                    if (lat == null || lon == null) null else GeoPt(lat, lon)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
