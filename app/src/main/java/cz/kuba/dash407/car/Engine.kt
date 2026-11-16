package cz.kuba.dash407.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import cz.kuba.dash407.data.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Běží motor? [source] říká, podle čeho se to pozná. */
data class EngineState(
    val running: Boolean = false,
    val source: String = "zatím nevím",
    val since: Long = System.currentTimeMillis()
)

/**
 * Start a stop motoru pro Knihu jízd.
 *
 * 1. **OBD adaptér** (když je připojený): motor běží, když řídicí jednotka hlásí otáčky aspoň [RPM_RUNNING].
 *    Otáčky pod touto hranicí po dobu [STOP_CONFIRM_MS], nebo řídicí jednotka mlčí déle než [ECU_SILENT_MS]
 *    (vypnuté zapalování), znamenají stop motoru.
 * 2. **Zapalování podle rádia** (bez OBD): rádio běží jen se zapnutým klíčem. Zapnutí rádia = start,
 *    vypnutí obrazovky nebo vypnutí rádia = stop.
 *
 * Vypnuté rádio má vždy přednost: jízda skončí, i když OBD ještě nestihlo nic nahlásit.
 */
object Engine {
    const val RPM_RUNNING = 350
    private const val STOP_CONFIRM_MS = 3_000L
    private const val ECU_SILENT_MS = 6_000L

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    /** Rádio je zapnuté (obrazovka svítí). */
    @Volatile private var accOn = true
    /** Co říká OBD; null = OBD není připojené a nic neví. */
    private var obdRunning: Boolean? = null
    private var lowSince = 0L
    private var lastAnswerAt = 0L
    /** První dotaz po připojení OBD – od něj se počítá mlčení řídicí jednotky. */
    private var firstAskAt = 0L
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> onAcc(false)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onAcc(true)
            }
        }
    }

    /** Volat po [Trip.init]: zaregistruje sledování obrazovky a hned vyhodnotí stav. */
    fun init(context: Context) {
        synchronized(this) {
            if (!registered) {
                registered = true
                val f = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SHUTDOWN)
                }
                try {
                    context.applicationContext.registerReceiver(receiver, f)
                } catch (e: Exception) {
                    // Bez sledování obrazovky zůstane jen OBD a spuštění aplikace.
                }
            }
        }
        evaluate()
    }

    /** Zapnutí / vypnutí rádia (obrazovky). */
    fun onAcc(on: Boolean) {
        synchronized(this) { accOn = on }
        evaluate()
    }

    /** Otáčky z OBD po každém dotazu; null = řídicí jednotka neodpověděla. */
    fun onObdRpm(rpm: Int?) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (firstAskAt == 0L) firstAskAt = now
            if (rpm != null) {
                lastAnswerAt = now
                if (rpm >= RPM_RUNNING) {
                    obdRunning = true
                    lowSince = 0L
                } else {
                    if (lowSince == 0L) lowSince = now
                    if (now - lowSince >= STOP_CONFIRM_MS || obdRunning == null) obdRunning = false
                }
            } else if (now - (if (lastAnswerAt == 0L) firstAskAt else lastAnswerAt) >= ECU_SILENT_MS) {
                obdRunning = false
            }
        }
        evaluate()
    }

    /** OBD adaptér se odpojil: dál rozhoduje rádio. */
    fun onObdDisconnected() {
        synchronized(this) {
            obdRunning = null
            lowSince = 0L
            lastAnswerAt = 0L
            firstAskAt = 0L
        }
        evaluate()
    }

    private fun evaluate() {
        val running: Boolean
        val source: String
        synchronized(this) {
            val obd = obdRunning
            when {
                !accOn -> { running = false; source = "rádio vypnuté" }
                obd != null -> { running = obd; source = "otáčky z OBD" }
                else -> { running = true; source = "zapalování (rádio zapnuté)" }
            }
        }
        val old = _state.value
        if (old.running == running) {
            if (old.source != source) _state.value = old.copy(source = source)
            return
        }
        val now = System.currentTimeMillis()
        _state.value = EngineState(running, source, now)
        Trip.onEngine(running, now)
    }
}
