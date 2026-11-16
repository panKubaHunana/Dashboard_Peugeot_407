package cz.kuba.dash407.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
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
 * Zhasnutá obrazovka (bez vypnutí rádia) jízdu neukončí, dokud OBD hlásí běžící motor – displej
 * jde zhasnout i za jízdy. Když ale rádio mezitím spalo (OBD během tmy skoro neodpovídalo),
 * jízda se po rozsvícení uzavře k okamžiku zhasnutí a začne nová. Vypnutí rádia (SHUTDOWN)
 * jízdu ukončí vždy.
 */
object Engine {
    const val RPM_RUNNING = 350
    private const val STOP_CONFIRM_MS = 3_000L
    private const val ECU_SILENT_MS = 6_000L
    /** Krátké zhasnutí se nikdy nedělí. */
    private const val DARK_SPLIT_MS = 60_000L
    /** Při běžícím OBD přijde odpověď zhruba každé 2 s; za tmy musí chodit aspoň jedna za tuto dobu. */
    private const val DARK_ANSWER_MS = 8_000L

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    /** Rádio je zapnuté (obrazovka svítí). Skutečný stav se zjistí v [init]. */
    @Volatile private var accOn = true
    /** Rádio se vypíná: jízda končí vždy, i když OBD ještě hlásí otáčky. */
    private var shutdown = false
    /** Co říká OBD; null = OBD není připojené a nic neví. */
    private var obdRunning: Boolean? = null
    /** OBD naposledy nahlásilo vypnutý motor a pak se odpojilo – bez nového zapnutí rádia jízda nezačne. */
    private var obdSaidOff = false
    private var lowSince = 0L
    private var lastAnswerAt = 0L
    /** První dotaz po připojení OBD – od něj se počítá mlčení řídicí jednotky. */
    private var firstAskAt = 0L
    /** Kdy obrazovka zhasla (0 = svítí) a kolik odpovědí OBD od té doby přišlo. */
    private var darkSince = 0L
    private var darkAnswers = 0
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> onAcc(false, shuttingDown = true)
                Intent.ACTION_SCREEN_OFF -> onAcc(false)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onAcc(true)
            }
        }
    }

    /** Volat po [Trip.init]: zaregistruje sledování obrazovky a hned vyhodnotí stav. */
    fun init(context: Context) {
        synchronized(this) {
            if (!registered) {
                registered = true
                // Aplikace mohla naběhnout na pozadí, když rádio spí (obnovená služba Jízda):
                // jízda nesmí začít jen proto, že výchozí hodnota je „zapnuto“.
                try {
                    val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm != null && !pm.isInteractive) {
                        accOn = false
                        darkSince = System.currentTimeMillis()
                    }
                } catch (e: Exception) { }
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
    fun onAcc(on: Boolean, shuttingDown: Boolean = false) {
        var splitAt = 0L
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (!on) {
                if (accOn) {
                    darkSince = now
                    darkAnswers = 0
                }
                accOn = false
                if (shuttingDown) shutdown = true
            } else if (!accOn) {
                // Obrazovka se rozsvítila. Jízda držená přes OBD: dělila se, jestli rádio mezitím spalo?
                val gap = if (darkSince > 0) now - darkSince else 0L
                if (_state.value.running && gap > DARK_SPLIT_MS && darkAnswers * DARK_ANSWER_MS < gap) splitAt = darkSince
                accOn = true
                shutdown = false
                obdSaidOff = false
                darkSince = 0L
                darkAnswers = 0
            }
        }
        if (splitAt > 0) {
            _state.value = EngineState(false, "rádio spalo", splitAt)
            Trip.onEngine(false, splitAt)
        }
        evaluate()
    }

    /** Otáčky z OBD po každém dotazu; null = řídicí jednotka neodpověděla. */
    fun onObdRpm(rpm: Int?) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (firstAskAt == 0L) firstAskAt = now
            if (rpm != null) {
                lastAnswerAt = now
                if (!accOn) darkAnswers++
                if (rpm >= RPM_RUNNING) {
                    obdRunning = true
                    obdSaidOff = false
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

    /** OBD adaptér se odpojil: dál rozhoduje rádio (ale motor, který OBD vidělo vypnutý, sám nenaskočí). */
    fun onObdDisconnected() {
        synchronized(this) {
            if (obdRunning == false) obdSaidOff = true
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
        val since = System.currentTimeMillis()
        synchronized(this) {
            val obd = obdRunning
            when {
                !accOn && (shutdown || obd != true) -> { running = false; source = "rádio vypnuté" }
                !accOn -> { running = true; source = "otáčky z OBD (obrazovka zhasnutá)" }
                obd != null -> { running = obd; source = "otáčky z OBD" }
                obdSaidOff -> { running = false; source = "OBD hlásilo vypnutý motor" }
                else -> { running = true; source = "zapalování (rádio zapnuté)" }
            }
            val old = _state.value
            if (old.running == running) {
                if (old.source != source) _state.value = old.copy(source = source)
                return
            }
            _state.value = EngineState(running, source, since)
        }
        Trip.onEngine(running, since)
    }
}
