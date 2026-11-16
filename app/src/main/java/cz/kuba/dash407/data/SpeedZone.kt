package cz.kuba.dash407.data

import androidx.compose.ui.graphics.Color

/**
 * Barva aktuální rychlosti podle typu silnice (Kubovo pravidlo překročení rychlosti):
 * zelená = v limitu, oranžová = mírně nad limitem, červená = překročení.
 */
enum class RoadZone(val label: String, val orangeFrom: Int, val redFrom: Int) {
    TOWN("v obci", 50, 54),
    RURAL("mimo obec", 90, 99),
    MOTORWAY("dálnice", 130, 145);

    companion object {
        /** Typ silnice z OSM: podle povolené rychlosti, a když chybí, podle druhu silnice (highway). */
        fun of(limitKmh: Int?, highway: String?): RoadZone? = when {
            highway == "motorway" || highway == "motorway_link" -> MOTORWAY
            highway !in CAR_ROADS -> null
            limitKmh != null && limitKmh <= 50 -> TOWN
            limitKmh != null && limitKmh >= 130 -> MOTORWAY
            limitKmh != null -> RURAL
            highway == "residential" || highway == "living_street" -> TOWN
            else -> RURAL
        }

        /** Silnice pro auta (chodníky, cyklostezky a polní cesty se nepočítají). */
        private val CAR_ROADS = setOf(
            "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link", "unclassified", "residential", "living_street",
        )
    }
}

object SpeedZone {
    val Green = Color(0xFF04A439)
    val Orange = Color(0xFFD1E308)
    val Red = Color(0xFFC00404)

    /** Bez známého typu silnice platí pravidlo „mimo obec“. */
    fun color(speedKmh: Int?, zone: RoadZone?, fallback: Color): Color {
        val v = speedKmh ?: return fallback
        val z = zone ?: RoadZone.RURAL
        return when {
            v >= z.redFrom -> Red
            v >= z.orangeFrom -> Orange
            else -> Green
        }
    }
}
