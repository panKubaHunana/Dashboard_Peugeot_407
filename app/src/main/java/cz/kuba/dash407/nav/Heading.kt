package cz.kuba.dash407.nav

import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Gps
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stabilní směr jízdy pro natočení mapy a šipku auta.
 *
 * Směr z GPS se počítá z pohybu: při stání a pomalé jízdě náhodně skáče (proto se mapa
 * při navigaci nesmyslně otáčela). Bere se jen při rychlosti aspoň [MIN_KMH] a slušné přesnosti,
 * jinak platí poslední dobrý směr. Když auto ještě nejelo, mapa se natočí podle směru trasy.
 */
object Heading {
    const val MIN_KMH = 8f
    private var last: Float? = null

    /** Poslední spolehlivý směr z GPS (stupně od severu), nebo null. */
    fun stable(f: Fix?): Float? {
        if (f == null) return last
        val b = f.bearing
        if (b != null && (f.speedKmh ?: 0f) >= MIN_KMH && (f.accuracy ?: 0f) <= 30f) last = b
        return last
    }

    /** Směr pro natočení mapy při navigaci; null = sever nahoře. */
    fun forMap(f: Fix?, nav: NavState): Float? {
        stable(f)?.let { return it }
        if (f == null) return null
        val pts = nav.route?.points ?: return null
        if (pts.isEmpty()) return null
        // Auto ještě nejelo: směr k bodu trasy aspoň 25 m před autem.
        for (i in nav.shapeIndex.coerceIn(0, pts.size - 1) until pts.size) {
            val p = pts[i]
            if (Gps.distance(f.lat, f.lon, p.lat, p.lon) >= 25.0) return bearing(f.lat, f.lon, p.lat, p.lon)
        }
        return null
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
