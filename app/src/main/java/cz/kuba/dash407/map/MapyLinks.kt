package cz.kuba.dash407.map

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import com.caverock.androidsvg.SVG
import cz.kuba.dash407.R
import cz.kuba.dash407.data.NavRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spouštění aplikace Mapy.com (balíček cz.seznam.mapy) podle dokumentace
 * „Calling the Mapy.com mobile application“: geo: URI a google.navigation:.
 */
object MapyLinks {
    private const val PKG = NavRepo.MAPY_PACKAGE

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PKG) != null

    /** Otevře Mapy.com; s [bounds] zkusí okno nad mapou (jen když jednotka umí volná okna). */
    fun open(context: Context, bounds: Rect? = null) {
        val intent = context.packageManager.getLaunchIntentForPackage(PKG)
        if (intent == null) {
            notInstalled(context)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent, bounds)
    }

    /** Hledání místa: Mapy.com ukážou výsledky kolem aktuální polohy. */
    fun search(context: Context, query: String, bounds: Rect? = null) {
        val q = query.trim()
        if (q.isEmpty()) {
            open(context, bounds)
            return
        }
        view(context, Uri.parse("geo:0,0?q=" + Uri.encode(q)), bounds)
    }

    /** Navigace na adresu (Mapy.com ji nejdřív vyhledají, pak stačí potvrdit trasu). */
    fun navigate(context: Context, address: String, bounds: Rect? = null) {
        val a = address.trim()
        if (a.isEmpty()) return
        view(context, Uri.parse("google.navigation:q=" + Uri.encode(a)), bounds)
    }

    private fun view(context: Context, uri: Uri, bounds: Rect?) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isInstalled(context)) intent.setPackage(PKG)
        start(context, intent, bounds)
    }

    private fun start(context: Context, intent: Intent, bounds: Rect?) {
        try {
            if (bounds != null && !bounds.isEmpty) {
                val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            notInstalled(context)
        }
    }

    private fun notInstalled(context: Context) {
        Toast.makeText(context, R.string.mapy_not_installed, Toast.LENGTH_LONG).show()
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ignored: Exception) {
        }
    }

    /** Otevře odkaz v prohlížeči (logo Mapy.com, copyright, portál pro API klíč). */
    fun openWeb(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: Exception) {
        }
    }
}

/**
 * Oficiální logo Mapy.com (SVG z api.mapy.com), které podmínky API vyžadují
 * nad mapou. Stáhne se jednou a uloží do soukromého úložiště.
 */
object MapyLogo {
    private const val URL_LOGO = "https://api.mapy.com/img/api/logo.svg"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap
    @Volatile private var loading = false

    /** [heightPx] = výška loga v pixelech (podmínky: nad mapou aspoň 30 px). */
    fun load(context: Context, heightPx: Int) {
        if (loading || _bitmap.value != null) return
        loading = true
        val file = File(context.applicationContext.filesDir, "mapy_logo.svg")
        scope.launch {
            try {
                val text = if (file.exists() && file.length() > 0) {
                    file.readText()
                } else {
                    val c = URL(URL_LOGO).openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = 10_000
                    val t = c.inputStream.bufferedReader().use { it.readText() }
                    c.disconnect()
                    file.writeText(t)
                    t
                }
                val svg = SVG.getFromString(text)
                val ratio = svg.documentAspectRatio.takeIf { it > 0f } ?: 3f
                val h = heightPx.coerceAtLeast(30)
                val w = (h * ratio).toInt().coerceAtLeast(1)
                val picture = svg.renderToPicture(w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawPicture(picture)
                _bitmap.value = bmp
            } catch (e: Exception) {
                file.delete()
            } finally {
                loading = false
            }
        }
    }
}
