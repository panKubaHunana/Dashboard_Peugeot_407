package cz.kuba.dash407.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import cz.kuba.dash407.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Vydání na GitHubu: verze (bez „v“), poznámky a odkaz na APK. */
data class Release(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed class UpdateState {
    object Idle : UpdateState()
    /** Na GitHubu je novější verze – nabídnout stažení. */
    data class Available(val release: Release) : UpdateState()
    /** Stahování; progress 0..1, nebo -1, když server neřekl velikost. */
    data class Downloading(val release: Release, val progress: Float) : UpdateState()
    /** APK je stažené a předané instalátoru Androidu. */
    data class Installing(val release: Release) : UpdateState()
    /** Rádio nemá povolenou instalaci aplikací z Dash 407. */
    data class NeedsPermission(val release: Release) : UpdateState()
    data class Failed(val release: Release?, val message: String) : UpdateState()
}

/**
 * Aktualizace z GitHubu: při každém spuštění se zeptá na nejnovější vydání,
 * nabídne stažení a APK rovnou předá instalátoru. Po aktualizaci ukáže novinky.
 */
object Updater {
    private const val REPO = "panKubaHunana/Dashboard_Peugeot_407"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val BY_TAG = "https://api.github.com/repos/$REPO/releases/tags/v"
    private const val KEY_LAST_VERSION = "update_last_version"
    private const val KEY_PENDING_NOTES = "update_pending_notes"
    private const val ACTION_STATUS = "cz.kuba.dash407.UPDATE_STATUS"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Novinky po aktualizaci: (verze, text), nebo null. */
    private val _changelog = MutableStateFlow<Pair<String, String>?>(null)
    val changelog: StateFlow<Pair<String, String>?> = _changelog

    /** Ruční kontrola (Nastavení) – výsledek „máš nejnovější“. */
    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private var checkedThisProcess = false

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Při spuštění aplikace: novinky po aktualizaci a kontrola nové verze. */
    fun onStart(context: Context) {
        showChangelogIfUpdated()
        if (checkedThisProcess) return
        checkedThisProcess = true
        check(context, manual = false)
    }

    @Suppress("UNUSED_PARAMETER")
    fun check(context: Context, manual: Boolean) {
        scope.launch {
            try {
                val r = parse(JSONObject(Net.getText(LATEST, 15_000)))
                if (r != null && isNewer(r.version, currentVersion)) {
                    _state.value = UpdateState.Available(r)
                } else if (manual) {
                    _info.value = "Máš nejnovější verzi $currentVersion."
                }
            } catch (e: Exception) {
                // Bez internetu se při spuštění nic neukazuje; ruční kontrola chybu oznámí.
                if (manual) _info.value = "Aktualizace se nepodařilo zjistit (${e.message ?: "bez internetu"})."
            }
            if (manual && _state.value is UpdateState.Available) _info.value = null
        }
    }

    fun clearInfo() { _info.value = null }

    fun dismiss() { _state.value = UpdateState.Idle }

    /** Stáhne APK a předá ho instalátoru. */
    fun download(context: Context, r: Release) {
        val app = context.applicationContext
        if (!canInstall(app)) {
            _state.value = UpdateState.NeedsPermission(r)
            return
        }
        _state.value = UpdateState.Downloading(r, 0f)
        scope.launch {
            try {
                val file = File(app.cacheDir, "update.apk")
                fetch(r, file)
                // Novinky si uložíme předem: po instalaci se aplikace spustí znovu a ukáže je.
                Prefs.shared().edit().putString(KEY_PENDING_NOTES, r.version + "\n" + r.notes).commit()
                _state.value = UpdateState.Installing(r)
                install(app, file)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(r, "Stažení se nepovedlo: ${e.message ?: "neznámá chyba"}")
            }
        }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Otevře nastavení „Instalace neznámých aplikací“ pro Dash 407. */
    fun openInstallPermission(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(null, "Rádio nemá nastavení pro instalaci aplikací. Povol „Neznámé zdroje“ ručně.")
        }
    }

    /** Po návratu z nastavení oprávnění: když už je povoleno, pokračovat ve stahování. */
    fun resumeAfterPermission(context: Context) {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && canInstall(context)) download(context, s.release)
    }

    fun closeChangelog() {
        _changelog.value = null
    }

    // ---------------------------------------------------------------------------------------

    private fun showChangelogIfUpdated() {
        val prefs = Prefs.shared()
        val last = prefs.getString(KEY_LAST_VERSION, null)
        val now = currentVersion
        if (last == now) return
        prefs.edit().putString(KEY_LAST_VERSION, now).apply()
        if (last == null) return // první instalace: bez novinek
        val pending = prefs.getString(KEY_PENDING_NOTES, null)
        prefs.edit().remove(KEY_PENDING_NOTES).apply()
        val saved = pending?.substringAfter('\n')?.takeIf { pending.substringBefore('\n') == now }
        if (saved != null) {
            _changelog.value = now to trimNotes(saved)
            return
        }
        // Aktualizace jinou cestou (ručně): novinky stáhneme z GitHubu podle verze.
        scope.launch {
            val text = try {
                JSONObject(Net.getText(BY_TAG + now, 15_000)).optString("body")
            } catch (e: Exception) {
                ""
            }
            _changelog.value = now to (trimNotes(text).ifBlank { "Aplikace byla aktualizována z verze $last na $now." })
        }
    }

    private fun parse(o: JSONObject): Release? {
        val tag = o.optString("tag_name").removePrefix("v").trim()
        if (tag.isEmpty() || o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return Release(tag, o.optString("body"), a.getString("browser_download_url"), a.optLong("size", -1))
            }
        }
        return null
    }

    /** „2.6.20“ > „2.6“ > „2.5.19“: porovnání po číslech. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val b = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Z poznámek k vydání jen část o nejnovější verzi (do řádku „Stáhni…“). */
    fun trimNotes(md: String): String {
        val lines = md.replace("\r", "").lines()
        val cut = lines.indexOfFirst { it.trim().startsWith("Stáhni") }
        return (if (cut > 0) lines.take(cut) else lines).joinToString("\n").trim()
    }

    private fun fetch(r: Release, target: File) {
        var url = r.apkUrl
        var c: HttpURLConnection
        var hops = 0
        // GitHub přesměruje na úložiště souborů; přesměrování sledujeme ručně.
        while (true) {
            c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Dash407-Updater")
            val code = c.responseCode
            if (code in 300..399 && hops < 5) {
                url = c.getHeaderField("Location") ?: throw HttpError(code)
                c.disconnect()
                hops++
                continue
            }
            if (code !in 200..299) throw HttpError(code)
            break
        }
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
            var done = 0L
            var lastShown = -1
            c.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != lastShown) {
                            lastShown = pct
                            _state.value = UpdateState.Downloading(r, if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IllegalStateException("soubor není celý")
        } finally {
            c.disconnect()
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Aktualizace sebe sama: Android 12+ ji může nainstalovat bez dalšího potvrzování.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("dash407.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Výsledek instalace od Androidu. */
    internal fun onStatus(context: Context, intent: Intent) {
        val r = (_state.value as? UpdateState.Installing)?.release
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android chce potvrzení: ukážeme jeho okno „Aktualizovat“.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            else -> {
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    _state.value = r?.let { UpdateState.Available(it) } ?: UpdateState.Idle
                } else {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kód $status"
                    _state.value = UpdateState.Failed(r, "Instalace se nepovedla: $msg")
                }
            }
        }
    }
}

/** Přijímá od Androidu stav instalace aktualizace. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater.onStatus(context, intent)
    }
}
