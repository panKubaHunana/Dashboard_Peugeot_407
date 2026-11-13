package cz.kuba.dash407.nav

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

/** Načítání mapových dlaždic Mapy.com s vyrovnávací pamětí v RAM. */
object Tiles {
    private val cache = LruCache<String, ImageBitmap>(220)
    private val loading = HashSet<String>()
    private val failed = HashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val limit = Semaphore(6)

    private val _version = MutableStateFlow(0)
    /** Zvýší se po každé nově načtené dlaždici: mapa se překreslí. */
    val version: StateFlow<Int> = _version

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun get(style: String, z: Int, x: Int, y: Int, key: String): ImageBitmap? {
        val n = 1 shl z
        val xx = ((x % n) + n) % n
        if (y < 0 || y >= n) return null
        val id = "$style/$z/$xx/$y"
        cache.get(id)?.let { return it }
        synchronized(loading) {
            if (id in loading) return null
            val f = failed[id]
            if (f != null && System.currentTimeMillis() - f < 20_000) return null
            loading.add(id)
        }
        scope.launch {
            limit.withPermit {
                try {
                    val bytes = Net.getBytes(Mapy.tileUrl(style, z, xx, y, key), 12_000)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) cache.put(id, bmp.asImageBitmap())
                    _error.value = null
                } catch (e: HttpError) {
                    synchronized(loading) { failed[id] = System.currentTimeMillis() }
                    _error.value = if (e.code == 401 || e.code == 403) "API klíč Mapy.com nebyl přijat (${e.code})" else "Mapa: chyba ${e.code}"
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
    fun cached(style: String, z: Int, x: Int, y: Int): ImageBitmap? {
        val n = 1 shl z
        val xx = ((x % n) + n) % n
        return cache.get("$style/$z/$xx/$y")
    }

    fun clear() {
        cache.evictAll()
        synchronized(loading) { failed.clear() }
        _error.value = null
        _version.value = _version.value + 1
    }

    /** Zkusí stáhnout jednu dlaždici: ověření API klíče z Nastavení. */
    fun test(key: String): String = try {
        Net.getBytes(Mapy.tileUrl("basic", 7, 70, 43, key), 10_000)
        "Klíč funguje, mapa se načte."
    } catch (e: HttpError) {
        if (e.code == 401 || e.code == 403) "Klíč nebyl přijat (HTTP ${e.code}). Zkontroluj ho na developer.mapy.com." else "Server vrátil chybu HTTP ${e.code}."
    } catch (e: Exception) {
        "Nepodařilo se spojit se serverem Mapy.com: ${e.message}"
    }
}
