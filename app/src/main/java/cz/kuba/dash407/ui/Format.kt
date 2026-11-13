package cz.kuba.dash407.ui

import java.util.Locale

val Czech: Locale = Locale("cs", "CZ")

/** Číslo s desetinnou čárkou, jak se píše česky. */
fun decimal(value: Double, digits: Int = 1): String = String.format(Czech, "%.${digits}f", value)

fun decimal(value: Float, digits: Int = 1): String = decimal(value.toDouble(), digits)

/** Převede text z pole formuláře na číslo; bere čárku i tečku. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

const val DASH = "–"

/** Vzdálenost: pod 10 km na desetiny, jinak celé kilometry. */
fun formatKm(meters: Double): String =
    if (meters < 10_000) decimal(meters / 1000.0) else String.format(Czech, "%.0f", meters / 1000.0)

/** Doba ve tvaru „1 h 06 min“, „15 min 35 s“ nebo „31 s“. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> String.format(Czech, "%d h %02d min", h, m)
        m > 0 -> String.format(Czech, "%d min %02d s", m, s)
        else -> "$s s"
    }
}

/** Kratší doba pro navigaci: „1 h 06 min“ / „12 min“. */
fun formatEta(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600 + 59) / 60
    return if (h > 0) String.format(Czech, "%d h %02d min", h, m) else "$m min"
}
