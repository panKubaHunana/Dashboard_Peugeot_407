package cz.kuba.dash407.ui

import java.util.Locale

val Czech: Locale = Locale("cs", "CZ")

/** Číslo s desetinnou čárkou, jak se píše česky. */
fun decimal(value: Double, digits: Int = 1): String = String.format(Czech, "%.${digits}f", value)

fun decimal(value: Float, digits: Int = 1): String = decimal(value.toDouble(), digits)

/** Převede text z pole formuláře na číslo; bere čárku i tečku. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

const val DASH = "–"
