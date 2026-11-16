package cz.kuba.dash407.car

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Nastavení OBD adaptéru ELM327 (Bluetooth nebo Wi-Fi). */
data class ObdConfig(
    /** "bt" nebo "wifi" */
    val mode: String = "bt",
    val btAddress: String = "",
    val btName: String = "",
    val host: String = "192.168.0.10",
    val port: Int = 35000,
    val autoConnect: Boolean = false,
    val intervalMs: Int = 500
)

/** Spárované zařízení Bluetooth. [notAdapter] = podle třídy zařízení jde o telefon, počítač nebo audio, ne o OBD adaptér. */
data class BtDevice(val name: String, val address: String, val notAdapter: String?)

/** Poslední známé hodnoty z auta přes OBD; null = adaptér je neposlal. */
data class ObdLive(
    val connected: Boolean = false,
    val busy: Boolean = false,
    val status: String = "Odpojeno",
    val adapter: String? = null,
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val intakeC: Int? = null,
    val mapKpa: Int? = null,
    val loadPct: Int? = null,
    val throttlePct: Int? = null,
    val volts: Double? = null,
    val updatedAt: Long = 0L
)

/**
 * Klient adaptéru ELM327. Posílá standardní dotazy OBD-II (režim 01), proto vidí jen
 * to, co motor vystavuje běžně: otáčky, rychlost, teplotu chladicí kapaliny a sání,
 * tlak v sání, zatížení a škrticí klapku. Hodnoty z FAP, tlaku paliva a podobně jsou u PSA
 * v rozšířeném režimu a tímto adaptérem se nečtou.
 */
object Obd {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val KEY = "obd_v1"

    private val _config = MutableStateFlow(ObdConfig())
    val config: StateFlow<ObdConfig> = _config
    private val _live = MutableStateFlow(ObdLive())
    val live: StateFlow<ObdLive> = _live
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(raw)
            val d = ObdConfig()
            _config.value = ObdConfig(
                mode = o.optString("mode", d.mode),
                btAddress = o.optString("btAddress", d.btAddress),
                btName = o.optString("btName", d.btName),
                host = o.optString("host", d.host),
                port = o.optInt("port", d.port),
                autoConnect = o.optBoolean("autoConnect", d.autoConnect),
                intervalMs = o.optInt("intervalMs", d.intervalMs)
            )
        } catch (e: Exception) {
            // Poškozené nastavení: výchozí hodnoty.
        }
    }

    fun update(change: (ObdConfig) -> ObdConfig) {
        val c = change(_config.value)
        _config.value = c
        val o = JSONObject().put("mode", c.mode).put("btAddress", c.btAddress).put("btName", c.btName)
            .put("host", c.host).put("port", c.port).put("autoConnect", c.autoConnect).put("intervalMs", c.intervalMs)
        try { Prefs.shared().edit().putString(KEY, o.toString()).apply() } catch (e: Exception) { }
    }

    private fun log(line: String) {
        _log.value = (_log.value + line).takeLast(14)
    }

    /** Spárovaná zařízení Bluetooth. Bez oprávnění nebo bez Bluetooth vrací prázdný seznam. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<BtDevice> = try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.bondedDevices?.map { d ->
            val major = try { d.bluetoothClass?.majorDeviceClass } catch (e: Exception) { null }
            val kind = when (major) {
                BluetoothClass.Device.Major.PHONE -> "telefon"
                BluetoothClass.Device.Major.COMPUTER -> "počítač"
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "sluchátka / audio"
                BluetoothClass.Device.Major.WEARABLE -> "hodinky"
                else -> null
            }
            BtDevice(d.name ?: d.address, d.address, kind)
        }?.sortedWith(compareBy({ it.notAdapter != null }, { it.name })) ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Otevře sériový kanál (SPP) k adaptéru. Levné klony ELM327 často nemají správný záznam služby,
     * proto se zkouší postupně: zabezpečené spojení, nezabezpečené a nakonec přímo kanál 1.
     */
    @SuppressLint("MissingPermission")
    private fun openRfcomm(device: BluetoothDevice): BluetoothSocket {
        var last: Exception? = null
        val attempts = listOf<Pair<String, () -> BluetoothSocket>>(
            "SPP" to { device.createRfcommSocketToServiceRecord(SPP) },
            "SPP bez zabezpečení" to { device.createInsecureRfcommSocketToServiceRecord(SPP) },
            "kanál 1" to {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            }
        )
        for ((label, make) in attempts) {
            var s: BluetoothSocket? = null
            try {
                s = make()
                s.connect()
                log("Spojeno ($label)")
                return s
            } catch (e: SecurityException) {
                try { s?.close() } catch (x: Exception) { }
                throw e
            } catch (e: Exception) {
                try { s?.close() } catch (x: Exception) { }
                log("$label: ${e.message ?: e.javaClass.simpleName}")
                last = e
            }
        }
        throw last ?: java.io.IOException("spojení selhalo")
    }

    /** Srozumitelný český text pro typické chyby Bluetooth. */
    private fun explain(e: Exception, cfg: ObdConfig): String {
        val m = (e.message ?: "").lowercase()
        return when {
            cfg.mode == "wifi" -> "Adaptér Wi-Fi neodpovídá na ${cfg.host}:${cfg.port}. Je rádio připojené k Wi-Fi síti adaptéru?"
            m.contains("read failed") || m.contains("socket might closed") || m.contains("timeout") ->
                "Zařízení „${cfg.btName.ifBlank { cfg.btAddress }}“ spojení odmítlo. Nejde o OBD adaptér ELM327, nebo není zapojený v zásuvce OBD a zapalování je vypnuté."
            m.contains("refused") || m.contains("discovery") -> "Adaptér je mimo dosah nebo právě spojený s jiným zařízením (telefonem)."
            else -> "Spojení selhalo: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun bluetoothAvailable(context: Context): Boolean =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter != null

    fun autoStart(context: Context) {
        if (_config.value.autoConnect && job?.isActive != true) connect(context)
    }

    fun disconnect() {
        job?.cancel()
        job = null
        Engine.onObdDisconnected()
        _live.value = ObdLive(status = "Odpojeno")
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context) {
        if (job?.isActive == true) return
        val cfg = _config.value
        _log.value = emptyList()
        _live.value = ObdLive(busy = true, status = "Připojuji…")
        job = scope.launch {
            var socket: BluetoothSocket? = null
            var tcp: Socket? = null
            try {
                val input: InputStream
                val output: OutputStream
                if (cfg.mode == "wifi") {
                    log("Wi-Fi ${cfg.host}:${cfg.port}")
                    tcp = Socket().also { it.connect(InetSocketAddress(cfg.host, cfg.port), 5000); it.soTimeout = 4000 }
                    input = tcp.getInputStream(); output = tcp.getOutputStream()
                } else {
                    if (cfg.btAddress.isBlank()) { fail("Vyber adaptér v seznamu spárovaných zařízení."); return@launch }
                    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    if (adapter == null || !adapter.isEnabled) { fail("Bluetooth je vypnutý."); return@launch }
                    log("Bluetooth ${cfg.btName.ifBlank { cfg.btAddress }}")
                    adapter.cancelDiscovery()
                    socket = openRfcomm(adapter.getRemoteDevice(cfg.btAddress))
                    input = socket.inputStream; output = socket.outputStream
                }
                val port = Port(input, output)
                val ver = port.ask("ATZ", 3500)
                log("ATZ → ${clean(ver)}")
                val adapterName = clean(ver).substringAfter("ELM", "").let { if (it.isNotEmpty()) "ELM$it" else clean(ver) }
                for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) log("$c → ${clean(port.ask(c, 1500))}")
                _live.value = ObdLive(connected = true, status = "Připojeno · hledám řídicí jednotku…", adapter = adapterName)
                // První dotaz spustí vyhledání protokolu (až několik sekund).
                val probe = clean(port.ask("0100", 8000))
                log("0100 → $probe")
                if (probe.contains("UNABLE") || probe.contains("ERROR") || probe.contains("NODATA")) {
                    _live.value = _live.value.copy(status = "Adaptér odpovídá, ale s autem se nespojil. Zapni zapalování.")
                } else {
                    _live.value = _live.value.copy(status = "Čtu data z auta")
                }
                while (isActive) {
                    var l = _live.value
                    val rpmNow = pid(port, "010C", 2)?.let { (it[0] * 256 + it[1]) / 4 }
                    // Start a stop motoru pro Knihu jízd.
                    Engine.onObdRpm(rpmNow)
                    l = l.copy(
                        rpm = rpmNow ?: l.rpm,
                        speedKmh = pid(port, "010D", 1)?.get(0) ?: l.speedKmh,
                        coolantC = pid(port, "0105", 1)?.let { it[0] - 40 } ?: l.coolantC,
                        intakeC = pid(port, "010F", 1)?.let { it[0] - 40 } ?: l.intakeC,
                        mapKpa = pid(port, "010B", 1)?.get(0) ?: l.mapKpa,
                        loadPct = pid(port, "0104", 1)?.let { it[0] * 100 / 255 } ?: l.loadPct,
                        throttlePct = pid(port, "0111", 1)?.let { it[0] * 100 / 255 } ?: l.throttlePct,
                        volts = parseVolts(port.ask("ATRV", 1500)) ?: l.volts,
                        updatedAt = System.currentTimeMillis()
                    )
                    _live.value = l.copy(status = "Čtu data z auta")
                    delay(cfg.intervalMs.toLong())
                }
            } catch (e: SecurityException) {
                fail("Chybí oprávnění Bluetooth. Povol ho v nastavení aplikace.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(explain(e, cfg))
            } finally {
                try { socket?.close() } catch (e: Exception) { }
                try { tcp?.close() } catch (e: Exception) { }
                Engine.onObdDisconnected()
                if (_live.value.connected) _live.value = ObdLive(status = "Odpojeno")
            }
        }
    }

    private fun fail(message: String) {
        log(message)
        _live.value = ObdLive(status = message)
    }

    private fun clean(s: String) = s.replace("\r", " ").replace("\n", " ").replace(">", "").trim().replace(Regex("\\s+"), " ")

    private fun parseVolts(s: String): Double? =
        Regex("([0-9]+(\\.[0-9])?)\\s*V").find(clean(s))?.groupValues?.get(1)?.toDoubleOrNull()

    /** Odpověď režimu 01: „41 0C 1A F8“ → datové bajty za hlavičkou. */
    private fun pid(port: Port, cmd: String, bytes: Int): IntArray? {
        val raw = clean(port.ask(cmd, 2500)).replace(" ", "").uppercase()
        val head = "41" + cmd.substring(2)
        val i = raw.indexOf(head)
        if (i < 0) return null
        val data = raw.substring(i + head.length)
        if (data.length < bytes * 2) return null
        return IntArray(bytes) { data.substring(it * 2, it * 2 + 2).toIntOrNull(16) ?: return null }
    }

    /** Textový dialog s ELM327: příkaz zakončený CR, odpověď končí znakem „>“. */
    private class Port(val input: InputStream, val output: OutputStream) {
        fun ask(cmd: String, timeoutMs: Int): String {
            while (input.available() > 0) input.read()
            output.write((cmd + "\r").toByteArray())
            output.flush()
            val sb = StringBuilder()
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                if (input.available() > 0) {
                    val c = input.read()
                    if (c < 0) break
                    if (c.toChar() == '>') break
                    sb.append(c.toChar())
                } else {
                    Thread.sleep(15)
                }
            }
            return sb.toString()
        }
    }
}
