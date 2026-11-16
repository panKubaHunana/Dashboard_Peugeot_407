package cz.kuba.dash407.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Chování bez internetu. Hlídá, jestli je rádio připojené, a drží frontu věcí, které se mají
 * udělat, až připojení bude (uloženou v souboru, přežije i vypnutí rádia):
 * - názvy míst u jízd (Nominatim),
 * - odeslání hlášení o pádu,
 * - obnovení počasí, čerpacích stanic a nenačtených mapových dlaždic.
 */
object NetQueue {
    /** Jeden odložený úkol: druh a parametr (např. "crash" a název souboru). */
    data class Job(val type: String, val arg: String = "", val at: Long = System.currentTimeMillis())

    private const val FILE = "net_fronta.json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs
    private var file: File? = null
    private var started = false

    fun init(context: Context) {
        synchronized(this) {
            if (started) return
            started = true
            file = File(context.applicationContext.filesDir, FILE)
            load()
        }
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        _online.value = isOnline(cm)
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    setOnline(ok)
                }

                override fun onLost(network: Network) {
                    setOnline(isOnline(cm))
                }
            })
        } catch (e: Exception) {
            // Bez sledování sítě se fronta zpracuje aspoň při dalším spuštění.
        }
        if (_online.value) flush()
    }

    private fun isOnline(cm: ConnectivityManager): Boolean = try {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        true
    }

    private fun setOnline(ok: Boolean) {
        val was = _online.value
        _online.value = ok
        if (ok && !was) flush()
    }

    /** Přidá úkol (stejný úkol jen jednou). */
    fun add(job: Job) {
        synchronized(this) {
            if (_jobs.value.any { it.type == job.type && it.arg == job.arg }) return
            _jobs.value = _jobs.value + job
            save()
        }
        if (_online.value) flush()
    }

    /** Připojení je zpět: zpracovat frontu a obnovit věci, které se bez internetu nenačetly. */
    fun flush() {
        scope.launch {
            val todo = synchronized(this@NetQueue) {
                val list = _jobs.value
                _jobs.value = emptyList()
                save()
                list
            }
            for (j in todo) {
                try {
                    when (j.type) {
                        "crash" -> CrashLog.offerAgain(j.arg)
                        "places" -> DriveLog.retryPlaces()
                    }
                } catch (e: Exception) {
                    // Úkol se nepovedl; nevracíme ho, aby fronta nerostla donekonečna.
                }
            }
            // Vždy po připojení: nenačtené dlaždice znovu, chybějící názvy míst a počasí.
            try { cz.kuba.dash407.nav.Tiles.retryFailed() } catch (e: Exception) { }
            try { DriveLog.retryPlaces() } catch (e: Exception) { }
            try { Weather.refresh() } catch (e: Exception) { }
        }
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            val a = JSONArray(f.readText())
            _jobs.value = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Job(o.getString("type"), o.optString("arg"), o.optLong("at"))
            }
        } catch (e: Exception) {
            f.delete()
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            val a = JSONArray()
            for (j in _jobs.value) a.put(JSONObject().put("type", j.type).put("arg", j.arg).put("at", j.at))
            f.writeText(a.toString())
        } catch (e: Exception) { }
    }
}
