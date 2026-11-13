package cz.kuba.dash407.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Net
import cz.kuba.dash407.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Místo z hledání Mapy.com. */
data class Place(
    val name: String,
    val label: String = "",
    val location: String = "",
    val lat: Double,
    val lon: Double
)

data class LatLon(val lat: Double, val lon: Double)

/**
 * Propojení s Mapy.com:
 *  – REST API (https://developer.mapy.com) pro mapové dlaždice, hledání a plánování trasy,
 *  – aplikace Mapy.com v rádiu pro hlasovou navigaci.
 */
object Mapy {
    const val APP = "cz.seznam.mapy"
    private const val API = "https://api.mapy.com/v1"

    /** Mapové podklady, které REST API nabízí. */
    val styles = listOf(
        "basic" to "Základní",
        "outdoor" to "Turistická",
        "winter" to "Zimní",
        "aerial" to "Letecká"
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun tileUrl(style: String, z: Int, x: Int, y: Int, key: String): String =
        "$API/maptiles/$style/256/$z/$x/$y?apikey=${enc(key)}"

    /** Hledání míst a adres (geocode). Volat mimo hlavní vlákno. */
    fun search(query: String, near: Fix?, key: String): List<Place> {
        val sb = StringBuilder("$API/geocode?lang=cs&limit=8&query=").append(enc(query))
        for (t in listOf("regional.municipality", "regional.municipality_part", "regional.street", "regional.address", "poi")) {
            sb.append("&type=").append(t)
        }
        if (near != null) sb.append(String.format(Locale.US, "&preferNear=%.5f,%.5f", near.lon, near.lat))
        sb.append("&apikey=").append(enc(key))
        val items = JSONObject(Net.getText(sb.toString())).optJSONArray("items") ?: JSONArray()
        val list = ArrayList<Place>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val pos = o.optJSONObject("position") ?: continue
            list.add(
                Place(
                    name = o.optString("name"),
                    label = o.optString("label"),
                    location = o.optString("location"),
                    lat = pos.getDouble("lat"),
                    lon = pos.getDouble("lon")
                )
            )
        }
        return list
    }

    /** Plánování trasy autem s ohledem na dopravu. Volat mimo hlavní vlákno. */
    fun route(from: Fix, to: Place, key: String): Route {
        val url = String.format(
            Locale.US,
            "%s/routing/route?start=%.6f,%.6f&end=%.6f,%.6f&routeType=car_fast_traffic&format=geojson&lang=cs&apikey=%s",
            API, from.lon, from.lat, to.lon, to.lat, enc(key)
        )
        val o = JSONObject(Net.getText(url, 15_000))
        val g = o.optJSONObject("geometry")
        val geometry = g?.optJSONObject("geometry") ?: g
        val coords = geometry?.optJSONArray("coordinates") ?: JSONArray()
        val points = ArrayList<LatLon>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            points.add(LatLon(c.getDouble(1), c.getDouble(0)))
        }
        return Route(to, points, o.optInt("length"), o.optInt("duration"), System.currentTimeMillis())
    }

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(APP) != null

    private fun launchFlags(intent: Intent): Intent {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Prefs.splitScreen.value) {
            // Rozdělená obrazovka: Android ji použije jen tam, kde ji jednotka podporuje.
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        return intent
    }

    /** Spustí hlasovou navigaci v aplikaci Mapy.com do zadaného bodu. */
    fun navigate(context: Context, place: Place) {
        val uri = Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f&mode=d", place.lat, place.lon))
        start(context, uri)
    }

    /** Navigace na adresu zadanou textem (Mapy.com si ji samy dohledají, nepotřebuje API klíč). */
    fun navigate(context: Context, address: String) {
        start(context, Uri.parse("google.navigation:q=" + Uri.encode(address)))
    }

    private fun start(context: Context, uri: Uri) {
        val intent = launchFlags(Intent(Intent.ACTION_VIEW, uri).setPackage(APP))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Aplikace Mapy.com není nainstalovaná", Toast.LENGTH_LONG).show()
        }
    }

    /** Otevře aplikaci Mapy.com tak, jak byla naposledy. */
    fun openApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(APP)
        if (intent == null) {
            Toast.makeText(context, "Aplikace Mapy.com není nainstalovaná", Toast.LENGTH_LONG).show()
            return
        }
        try {
            context.startActivity(launchFlags(intent))
        } catch (e: Exception) {
            // Nejde spustit.
        }
    }

    fun openWeb(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Prohlížeč se nepodařilo otevřít", Toast.LENGTH_SHORT).show()
        }
    }
}
