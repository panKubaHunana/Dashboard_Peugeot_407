package cz.kuba.dash407.nav

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cz.kuba.dash407.data.HttpError
import cz.kuba.dash407.data.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** Načítání mapových dlaždic (Mapy.com, OpenStreetMap, OpenTopoMap, Google) s pamětí v RAM a pro otevřené mapy i na disku. */
object Tiles {
    private const val DISK_DAYS = 7L
    // Paměť podle velikosti (kB): dlaždice @2x mají 512 × 512 px, tedy asi 1 MB.
    private val cache = object : LruCache<String, ImageBitmap>(96 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val loading = HashSet<String>()
    private val failed = HashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val limit = Semaphore(4)
    private var diskDir: File? = null

    private val _version = MutableStateFlow(0)
    /** Zvýší se po každé nově načtené dlaždici: mapa se překreslí. */
    val version: StateFlow<Int> = _version

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun init(context: Context) {
        if (diskDir == null) diskDir = File(context.applicationContext.cacheDir, "tiles")
    }

    private fun diskFile(source: String, z: Int, x: Int, y: Int): File? =
        diskDir?.let { File(it, "$source/$z/$x/$y.png") }

    fun get(source: String, z: Int, x: Int, y: Int): ImageBitmap? {
        val n = 1 shl z
        val xx = ((x % n) + n) % n
        if (y < 0 || y >= n) return null
        val id = "$source/$z/$xx/$y"
        cache.get(id)?.let { return it }
        synchronized(loading) {
            if (id in loading) return null
            val f = failed[id]
            if (f != null && System.currentTimeMillis() - f < 20_000) return null
            loading.add(id)
        }
        val src = MapSources.of(source)
        scope.launch {
            limit.withPermit {
                try {
                    val file = if (src.diskCache) diskFile(source, z, xx, y) else null
                    val fresh = file != null && file.exists() &&
                        System.currentTimeMillis() - file.lastModified() < DISK_DAYS * 86_400_000L
                    val bytes: ByteArray = if (fresh && file != null) file.readBytes() else {
                        val b = Net.getBytes(MapSources.url(source, z, xx, y), 12_000)
                        if (file != null) {
                            try {
                                file.parentFile?.mkdirs()
                                file.writeBytes(b)
                            } catch (e: Exception) {
                                // Disk plný: dlaždice zůstane jen v paměti.
                            }
                        }
                        b
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) cache.put(id, bmp.asImageBitmap())
                    _error.value = null
                } catch (e: HttpError) {
                    synchronized(loading) { failed[id] = System.currentTimeMillis() }
                    if (src.key == KeyNeed.GOOGLE && (e.code == 401 || e.code == 403)) GoogleTiles.reset()
                    _error.value = when {
                        e.code == 401 || e.code == 403 -> "${src.group}: klíč nebyl přijat (${e.code})"
                        e.code == 429 -> "${src.group}: server žádá zpomalit"
                        else -> "${src.group}: chyba ${e.code}"
                    }
                } catch (e: Exception) {
                    synchronized(loading) { failed[id] = System.currentTimeMillis() }
                    _error.value = "Mapa se načítá bez připojení k internetu"
                } finally {
                    synchronized(loading) { loading.remove(id) }
                    _version.value = _version.value + 1
                }
            }
        }
        return null
    }

    /** Vrátí dlaždici, jen pokud už je v paměti (pro náhradu z nižšího přiblížení). */
    fun cached(source: String, z: Int, x: Int, y: Int): ImageBitmap? {
        val n = 1 shl z
        val xx = ((x % n) + n) % n
        return cache.get("$source/$z/$xx/$y")
    }

    fun clear() {
        cache.evictAll()
        synchronized(loading) { failed.clear() }
        _error.value = null
        _version.value = _version.value + 1
    }

    /** Zkusí stáhnout jednu dlaždici Mapy.com: ověření API klíče z Nastavení. */
    fun test(key: String): String = try {
        Net.getBytes(Mapy.tileUrl("basic", 7, 70, 43, key), 10_000)
        "Klíč funguje, mapa se načte."
    } catch (e: HttpError) {
        if (e.code == 401 || e.code == 403) "Klíč nebyl přijat (HTTP ${e.code}). Zkontroluj ho na developer.mapy.com." else "Server vrátil chybu HTTP ${e.code}."
    } catch (e: Exception) {
        "Nepodařilo se spojit se serverem Mapy.com: ${e.message}"
    }
}
