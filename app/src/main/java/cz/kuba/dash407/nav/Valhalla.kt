package cz.kuba.dash407.nav

import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.Net
import org.json.JSONArray
import org.json.JSONObject

/** Jeden manévr navigace (odbočení, kruhový objezd, cíl …) s českými texty. */
data class Maneuver(
    val type: Int,
    val instruction: String,
    val alert: String?,
    val pre: String?,
    val post: String?,
    val street: String?,
    val beginIndex: Int,
    val lengthM: Double,
    val exitCount: Int
)

/** Trasa pro navigaci krok za krokem. */
class NavRoute(
    val dest: Place,
    val points: List<LatLon>,
    val maneuvers: List<Maneuver>,
    val lengthM: Double,
    val timeS: Double
) {
    /** Kumulativní vzdálenost od startu ke každému bodu tvaru trasy, v metrech. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { out ->
        for (i in 1 until points.size) {
            out[i] = out[i - 1] + Gps.distance(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
        }
    }
    val total: Double get() = if (cumulative.isNotEmpty()) cumulative.last().coerceAtLeast(1.0) else 1.0
}

/**
 * Navigační pokyny z otevřeného routeru Valhalla nad daty OpenStreetMap (veřejný server FOSSGIS).
 * REST API Mapy.com vrací jen tvar trasy bez pokynů, proto pokyny bereme odsud.
 */
object Valhalla {
    private const val URL = "https://valhalla1.openstreetmap.de/route"

    fun route(from: Fix, to: Place): NavRoute {
        val start = JSONObject().put("lat", from.lat).put("lon", from.lon)
        val b = from.bearing
        if (b != null && (from.speedKmh ?: 0f) > 8f) {
            // Směr jízdy pomůže při přepočtu trasy za jízdy (neotáčet auto o 180°).
            start.put("heading", b.toInt()).put("heading_tolerance", 60)
        }
        val body = JSONObject()
            .put("locations", JSONArray().put(start).put(JSONObject().put("lat", to.lat).put("lon", to.lon)))
            .put("costing", "auto")
            .put("directions_options", JSONObject().put("language", "cs-CZ").put("units", "kilometers"))
            .put("id", "dash407")
        val o = JSONObject(Net.postJson(URL, body.toString(), 20_000))
        val trip = o.getJSONObject("trip")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val points = decode(leg.getString("shape"))
        val ms = leg.getJSONArray("maneuvers")
        val list = ArrayList<Maneuver>(ms.length())
        for (i in 0 until ms.length()) {
            val m = ms.getJSONObject(i)
            val streets = m.optJSONArray("street_names")
            list.add(
                Maneuver(
                    type = m.optInt("type"),
                    instruction = m.optString("instruction"),
                    alert = m.optString("verbal_transition_alert_instruction").ifBlank { null },
                    pre = m.optString("verbal_pre_transition_instruction").ifBlank { null },
                    post = m.optString("verbal_post_transition_instruction").ifBlank { null },
                    street = if (streets != null && streets.length() > 0) streets.optString(0) else null,
                    beginIndex = m.optInt("begin_shape_index").coerceIn(0, (points.size - 1).coerceAtLeast(0)),
                    lengthM = m.optDouble("length", 0.0) * 1000.0,
                    exitCount = m.optInt("roundabout_exit_count", 0)
                )
            )
        }
        val summary = trip.getJSONObject("summary")
        return NavRoute(to, points, list, summary.optDouble("length") * 1000.0, summary.optDouble("time"))
    }

    /** Dekódování lomené čáry (encoded polyline) s přesností 6 desetinných míst. */
    fun decode(s: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < s.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = s[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < s.length)
            lat += if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
            result = 0L
            shift = 0
            do {
                b = s[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < s.length)
            lon += if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
            out.add(LatLon(lat / 1e6, lon / 1e6))
        }
        return out
    }
}
