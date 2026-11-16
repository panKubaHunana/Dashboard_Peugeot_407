package cz.kuba.dash407.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import cz.kuba.dash407.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jeden zaznamenaný pád aplikace. */
data class Crash(val file: File, val at: Long, val title: String, val text: String, val reported: Boolean)

/**
 * Záznam pádů: když Dash 407 spadne, uloží se chyba do souboru (složka pady/) a po dalším
 * spuštění se nabídne „Odeslat hlášení“ – otevře se předvyplněný nový úkol (issue) na GitHubu.
 * Odeslání bez internetu počká ve frontě, dokud se rádio nepřipojí.
 */
object CrashLog {
    private const val DIR = "pady"
    private const val KEEP = 20
    private var dir: File? = null
    private var installed = false

    private val _crashes = MutableStateFlow<List<Crash>>(emptyList())
    val crashes: StateFlow<List<Crash>> = _crashes

    /** Pád, který se má po spuštění nabídnout k odeslání (nejnovější neodeslaný). */
    private val _offer = MutableStateFlow<Crash?>(null)
    val offer: StateFlow<Crash?> = _offer

    /** Co nejdřív při startu procesu (z [cz.kuba.dash407.Dash407App]). */
    fun install(context: Context) {
        val app = context.applicationContext
        dir = File(app.filesDir, DIR).also { it.mkdirs() }
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(thread, error)
            } catch (e: Throwable) {
                // Zápis se nepovedl; aspoň necháme Android pád ohlásit po svém.
            }
            previous?.uncaughtException(thread, error)
        }
        reload()
        _offer.value = _crashes.value.firstOrNull { !it.reported && !it.file.name.endsWith(".seen.txt") }
    }

    private fun write(thread: Thread, error: Throwable) {
        val d = dir ?: return
        val now = System.currentTimeMillis()
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("Dash 407 ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Čas: ").append(SimpleDateFormat("d. M. yyyy HH:mm:ss", Locale.ROOT).format(Date(now))).append('\n')
            append("Zařízení: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Vlákno: ").append(thread.name).append("\n\n")
            append(sw.toString())
        }
        File(d, "pad-$now.txt").writeText(text)
        // Starší záznamy uklidit.
        d.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
    }

    fun reload() {
        val d = dir ?: return
        _crashes.value = (d.listFiles() ?: emptyArray())
            .filter { it.name.startsWith("pad-") && it.name.endsWith(".txt") }
            .mapNotNull { f ->
                try {
                    val text = f.readText()
                    val at = f.name.removePrefix("pad-").substringBefore('.').toLongOrNull() ?: f.lastModified()
                    Crash(f, at, titleOf(text), text, f.name.endsWith(".sent.txt"))
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.at }
    }

    /** Krátký název: druh chyby a první místo v kódu Dash 407. */
    private fun titleOf(text: String): String {
        val lines = text.lines()
        val err = lines.firstOrNull { it.contains("Exception") || it.contains("Error") }?.trim()?.take(90) ?: "neznámá chyba"
        val where = lines.firstOrNull { it.trim().startsWith("at cz.kuba.dash407") }?.trim()?.removePrefix("at cz.kuba.dash407.")?.take(70)
        return if (where != null) "$err · $where" else err
    }

    /** Okno po spuštění zavřít; pád zůstane v Nastavení › Záznam pádů. */
    fun dismissOffer() {
        _offer.value?.let { mark(it, ".seen.txt") }
        _offer.value = null
    }

    /**
     * Odeslat hlášení: otevře nový úkol na GitHubu s předvyplněným textem a celý záznam zkopíruje
     * do schránky. Bez internetu se odeslání odloží do fronty.
     */
    fun send(context: Context, c: Crash): String {
        copy(context, c)
        if (!NetQueue.online.value) {
            NetQueue.add(NetQueue.Job("crash", c.file.name))
            _offer.value = null
            return "Rádio není připojené k internetu. Hlášení se nabídne znovu, až bude připojení."
        }
        open(context, c)
        mark(c, ".sent.txt")
        _offer.value = null
        return "Otevřel se GitHub s předvyplněným hlášením. Zkontroluj ho a klepni na Create (Vytvořit). Celý záznam je i ve schránce."
    }

    /** Odložené odeslání z fronty: po připojení se znovu ukáže nabídka. */
    fun offerAgain(fileName: String) {
        reload()
        _crashes.value.firstOrNull { it.file.name == fileName }?.let { _offer.value = it }
    }

    private fun open(context: Context, c: Crash) {
        val title = "Pád aplikace: " + c.title
        // Adresa nesmí být moc dlouhá: posíláme začátek záznamu (to důležité), celý je ve schránce.
        val body = "Dash 407 spadl. Záznam z rádia:\n\n```\n" + c.text.take(5000) + "\n```\n"
        val url = "https://github.com/${BuildConfig.REPO}/issues/new?title=" + enc(title) + "&body=" + enc(body)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Bez prohlížeče: zbývá schránka a soubor ve Stažených souborech.
            saveToDownloads(context, c)
        }
    }

    private fun copy(context: Context, c: Crash) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Dash 407 – pád", c.text))
        } catch (e: Exception) { }
    }

    /** Uloží záznam do Stažené soubory/Dash407 (třeba pro poslání e-mailem). */
    fun saveToDownloads(context: Context, c: Crash): String = try {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, c.file.name.replace(".sent", "").replace(".seen", ""))
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Dash407")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("bez úložiště")
        context.contentResolver.openOutputStream(uri)?.use { it.write(c.text.toByteArray()) }
        "Uloženo do Stažené soubory/Dash407."
    } catch (e: Exception) {
        "Uložení se nepovedlo: ${e.message}"
    }

    fun deleteAll() {
        dir?.listFiles()?.forEach { it.delete() }
        _offer.value = null
        reload()
    }

    private fun mark(c: Crash, suffix: String) {
        val base = c.file.name.removeSuffix(".txt").removeSuffix(".sent").removeSuffix(".seen")
        if (c.file.name.endsWith(".sent.txt")) return
        c.file.renameTo(File(c.file.parentFile, base + suffix))
        reload()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
