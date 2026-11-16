package cz.kuba.dash407.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import cz.kuba.dash407.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Záloha a obnova všech dat Dash 407 v jednom souboru .zip:
 * kniha jízd a trasy, tankování, servisní knížka, rozložení ploch, vzhled a všechna nastavení
 * (včetně OBD, rychlé volby a vlastních API klíčů).
 *
 * Kam se ukládá:
 * - **Stažené soubory/Dash407** v rádiu (ručně i automaticky jednou denně, drží se 5 posledních),
 * - **libovolné místo** přes výběr souboru Androidu – i **Google Disk**, když je v rádiu jeho aplikace;
 *   takový soubor se pak dá jednou denně automaticky přepisovat aktuální zálohou.
 */
object Backup {
    private const val MANIFEST = "dash407-zaloha.json"
    private const val FILES = "soubory/"
    private const val FORMAT = 1
    private const val KEEP_LOCAL = 5
    private const val DAY_MS = 24 * 3_600_000L
    private const val KEY_LAST_AUTO = "backup_last_auto"
    private const val KEY_AUTO = "backup_auto"
    private const val KEY_TARGET = "backup_drive_uri"
    private const val KEY_TARGET_NAME = "backup_drive_name"
    private const val KEY_LAST_RESULT = "backup_last_result"
    const val MIME = "application/zip"

    /** Klíče nastavení, které se nezálohují (patří jen k této instalaci). */
    private val SKIP_PREFS = setOf(KEY_LAST_AUTO, KEY_TARGET, KEY_TARGET_NAME, KEY_LAST_RESULT, "update_pending_notes")
    /** Soubory, které se nezálohují. */
    private val SKIP_FILES = setOf("net_fronta.json", "kniha_jizd.json.tmp")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<String?>(null)
    /** Poslední výsledek (pro Nastavení). */
    val status: StateFlow<String?> = _status

    private val _auto = MutableStateFlow(true)
    val auto: StateFlow<Boolean> = _auto
    private val _target = MutableStateFlow<String?>(null)
    /** Název souboru pro automatickou zálohu jinam (např. na Google Disk), nebo null. */
    val targetName: StateFlow<String?> = _target

    fun init() {
        val p = try { Prefs.shared() } catch (e: Exception) { return }
        _auto.value = p.getBoolean(KEY_AUTO, true)
        _target.value = if (p.getString(KEY_TARGET, null) != null) p.getString(KEY_TARGET_NAME, "vybraný soubor") else null
        _status.value = p.getString(KEY_LAST_RESULT, null)
    }

    fun setAuto(on: Boolean) {
        _auto.value = on
        Prefs.shared().edit().putBoolean(KEY_AUTO, on).apply()
    }

    fun fileName(): String = "Dash407-zaloha-" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date()) + ".zip"

    // --- Vytvoření zálohy -------------------------------------------------------------------

    /** Celá záloha jako bajty .zip. */
    fun create(context: Context): ByteArray {
        val app = context.applicationContext
        Trip.save()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val prefs = JSONObject()
            for ((k, v) in Prefs.shared().all) {
                if (k in SKIP_PREFS || v == null) continue
                val o = JSONObject()
                when (v) {
                    is Boolean -> o.put("t", "b").put("v", v)
                    is Int -> o.put("t", "i").put("v", v)
                    is Long -> o.put("t", "l").put("v", v)
                    is Float -> o.put("t", "f").put("v", v.toDouble())
                    is String -> o.put("t", "s").put("v", v)
                    is Set<*> -> o.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                prefs.put(k, o)
            }
            val root = app.filesDir
            val files = root.walkTopDown().filter { it.isFile && it.name !in SKIP_FILES }.toList()
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("app", "Dash 407")
                .put("version", BuildConfig.VERSION_NAME)
                .put("created", System.currentTimeMillis())
                .put("drives", DriveLog.entries.value.size)
                .put("refuels", FuelLog.entries.value.size)
                .put("files", files.size)
                .put("prefs", prefs)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(1).toByteArray())
            zip.closeEntry()
            for (f in files) {
                val rel = f.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(FILES + rel))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun summary(bytes: ByteArray): String =
        "${DriveLog.entries.value.size} jízd, ${FuelLog.entries.value.size} tankování, ${bytes.size / 1024} kB"

    /** Uloží zálohu do Stažené soubory/Dash407. */
    fun saveToDownloads(context: Context, auto: Boolean = false): String = try {
        val bytes = create(context)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName())
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Dash407")
        }
        val r = context.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("úložiště není dostupné")
        r.openOutputStream(uri)?.use { it.write(bytes) } ?: error("soubor nejde otevřít")
        pruneLocal(context)
        done((if (auto) "Automatická záloha" else "Záloha") + " uložena do Stažené soubory/Dash407 (${summary(bytes)}).")
    } catch (e: Exception) {
        done("Záloha se nepovedla: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Uloží zálohu do souboru vybraného přes Android (třeba na Google Disk). */
    fun saveTo(context: Context, uri: Uri): String = try {
        val bytes = create(context)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("soubor nejde otevřít")
        done("Záloha uložena (${summary(bytes)}).")
    } catch (e: Exception) {
        done("Záloha se nepovedla: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Vybraný soubor (např. na Google Disku) se bude jednou denně přepisovat aktuální zálohou. */
    fun setTarget(context: Context, uri: Uri?) {
        val e = Prefs.shared().edit()
        if (uri == null) {
            e.remove(KEY_TARGET).remove(KEY_TARGET_NAME)
            _target.value = null
        } else {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (x: Exception) {
                // Některá úložiště trvalé oprávnění nedávají – pak to platí jen do restartu.
            }
            val name = displayName(context, uri) ?: "vybraný soubor"
            e.putString(KEY_TARGET, uri.toString()).putString(KEY_TARGET_NAME, name)
            _target.value = name
        }
        e.apply()
    }

    /** Jednou denně: záloha do Stažených souborů a (když je vybraný) do souboru na Google Disku. */
    fun autoIfDue(context: Context) {
        if (!_auto.value) return
        val p = Prefs.shared()
        val last = p.getLong(KEY_LAST_AUTO, 0L)
        if (System.currentTimeMillis() - last < DAY_MS) return
        // Prázdná nová instalace se nezálohuje – nepřepsala by dobrou zálohu prázdnou.
        if (DriveLog.entries.value.isEmpty() && FuelLog.entries.value.isEmpty()) return
        p.edit().putLong(KEY_LAST_AUTO, System.currentTimeMillis()).apply()
        val app = context.applicationContext
        scope.launch {
            saveToDownloads(app, auto = true)
            val target = p.getString(KEY_TARGET, null) ?: return@launch
            if (!NetQueue.online.value) return@launch
            val r = saveTo(app, Uri.parse(target))
            if (r.startsWith("Záloha uložena")) done("Automatická záloha uložena i do „${_target.value}“.")
        }
    }

    private fun pruneLocal(context: Context) {
        try {
            val r = context.contentResolver
            val ids = ArrayList<Long>()
            r.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("Dash407-zaloha-%"),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
            for (id in ids.drop(KEEP_LOCAL)) {
                r.delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null)
            }
        } catch (e: Exception) { }
    }

    // --- Obnova -----------------------------------------------------------------------------

    /** Co záloha obsahuje (pro potvrzovací okno), nebo null, když to není záloha Dash 407. */
    data class Info(val version: String, val created: Long, val drives: Int, val refuels: Int, val files: Int)

    fun inspect(context: Context, uri: Uri): Info? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                var info: Info? = null
                while (e != null && info == null) {
                    if (e.name == MANIFEST) {
                        val o = JSONObject(String(zip.readBytes()))
                        info = Info(o.optString("version"), o.optLong("created"), o.optInt("drives"), o.optInt("refuels"), o.optInt("files"))
                    }
                    e = zip.nextEntry
                }
                info
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Obnoví data ze zálohy: nejdřív se celá přečte a zkontroluje, teprve pak se přepíše
     * nastavení i soubory. Potom se aplikace sama restartuje, aby všechno načetla znovu.
     */
    fun restore(context: Context, uri: Uri): String {
        val app = context.applicationContext
        val input: InputStream = app.contentResolver.openInputStream(uri) ?: return "Soubor nejde otevřít."
        var manifest: JSONObject? = null
        val files = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        when {
                            e.name == MANIFEST -> manifest = JSONObject(String(zip.readBytes()))
                            e.name.startsWith(FILES) -> {
                                val rel = e.name.removePrefix(FILES)
                                // Ochrana: žádné cesty mimo složku aplikace.
                                if (rel.isNotBlank() && !rel.contains("..") && !rel.startsWith("/")) files[rel] = zip.readBytes()
                            }
                        }
                    }
                    e = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return "Soubor se nedá přečíst: ${e.message}"
        }
        val m = manifest ?: return "Tohle není záloha Dash 407."
        if (m.optInt("format") > FORMAT) return "Záloha je z novější verze Dash 407 – nejdřív aplikaci aktualizuj."

        // Před přepsáním si aktuální stav uložíme stranou (Stažené soubory/Dash407).
        try { saveToDownloads(app) } catch (e: Exception) { }

        val keep = Prefs.shared().all.filterKeys { it in SKIP_PREFS }
        val edit = Prefs.shared().edit().clear()
        val prefs = m.optJSONObject("prefs") ?: JSONObject()
        for (k in prefs.keys()) {
            val o = prefs.optJSONObject(k) ?: continue
            when (o.optString("t")) {
                "b" -> edit.putBoolean(k, o.getBoolean("v"))
                "i" -> edit.putInt(k, o.getInt("v"))
                "l" -> edit.putLong(k, o.getLong("v"))
                "f" -> edit.putFloat(k, o.getDouble("v").toFloat())
                "s" -> edit.putString(k, o.getString("v"))
                "set" -> {
                    val a = o.getJSONArray("v")
                    edit.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        for ((k, v) in keep) when (v) {
            is String -> edit.putString(k, v)
            is Long -> edit.putLong(k, v)
            is Boolean -> edit.putBoolean(k, v)
        }
        // Ať se po obnově hned nepřepíše automatickou zálohou.
        edit.putLong(KEY_LAST_AUTO, System.currentTimeMillis())
        edit.commit()

        val root = app.filesDir
        // Staré soubory dat pryč (kromě záznamu pádů), pak zapsat obnovené.
        root.listFiles()?.forEach { f -> if (f.name != "pady") f.deleteRecursively() }
        for ((rel, bytes) in files) {
            val f = File(root, rel)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        done("Obnoveno ze zálohy z ${SimpleDateFormat("d. M. yyyy HH:mm", Locale.ROOT).format(Date(m.optLong("created")))}.")
        restart(app)
        return "Obnoveno. Dash 407 se restartuje."
    }

    private fun restart(context: Context) {
        try {
            val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (i != null) context.startActivity(Intent.makeRestartActivityTask(i.component))
        } catch (e: Exception) { }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun done(text: String): String {
        val stamped = SimpleDateFormat("d. M. HH:mm", Locale.ROOT).format(Date()) + " · " + text
        _status.value = stamped
        try { Prefs.shared().edit().putString(KEY_LAST_RESULT, stamped).apply() } catch (e: Exception) { }
        return text
    }
}
