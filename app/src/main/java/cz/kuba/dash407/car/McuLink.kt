package cz.kuba.dash407.car

import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log

/*
 * Spojení se službou MCU na jednotkách s firmwarem QF (ROCO K706).
 * Služba "mcu_service" (rozhraní android.qf.mcu.IMcuManager) předává každé
 * zaregistrované aplikaci zprávy [příkaz, data…]: proud z CAN boxu, napětí,
 * světla a ruční brzdu. Nepotřebuje root.
 * Na jiném zařízení (tablet, telefon) služba není – aplikace pak běží bez dat z auta.
 */
object McuLink {
    private const val TAG = "McuLink"
    private const val SERVICE_NAME = "mcu_service"
    private const val IFACE_MANAGER = "android.qf.mcu.IMcuManager"
    private const val IFACE_LISTENER = "android.qf.mcu.IMcuListener"
    private const val CALL_REGISTER = 2
    private const val CALL_SEND = 29
    private const val CALLBACK_MESSAGE = 1

    private const val MSG_HEADLIGHTS = 34
    private const val MSG_HANDBRAKE = 35
    private const val MSG_VOLTAGE = 41
    private const val MSG_CAN = 65
    private const val SEND_TO_CAN = 0x40

    private val ui = Handler(Looper.getMainLooper())
    private val worker: Handler by lazy {
        val thread = HandlerThread("mcu-link")
        thread.start()
        Handler(thread.looper)
    }
    private val assembler = RzcAssembler { type, data -> Rzc.decode(type, data) }

    private var packageName: String? = null
    private var remote: IBinder? = null
    private var waitMs = 2_000L
    @Volatile private var paused = false

    val isLinked: Boolean
        get() = remote != null

    private val callback = object : Binder() {
        init {
            attachInterface(null, IFACE_LISTENER)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != CALLBACK_MESSAGE) return super.onTransact(code, data, reply, flags)
            var message: ByteArray? = null
            try {
                data.enforceInterface(IFACE_LISTENER)
                message = data.createByteArray()
            } catch (e: Exception) {
                Log.w(TAG, "nečitelná zpráva", e)
            }
            reply?.writeNoException()
            val m = message
            if (m != null && m.isNotEmpty()) worker.post { handle(m) }
            return true
        }
    }

    fun start(appPackage: String) {
        if (packageName != null) return
        packageName = appPackage
        connect()
    }

    /** V ukázkovém režimu se skutečné zprávy zahazují. */
    fun setPaused(value: Boolean) {
        paused = value
    }

    private fun lookup(): IBinder? = try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, SERVICE_NAME) as? IBinder
    } catch (e: Throwable) {
        null
    }

    private fun connect() {
        val pkg = packageName ?: return
        val binder = lookup()
        if (binder == null) {
            retry()
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(IFACE_MANAGER)
            data.writeStrongBinder(callback)
            data.writeString(pkg)
            binder.transact(CALL_REGISTER, data, reply, 0)
            reply.readException()
            binder.linkToDeath({
                remote = null
                CarRepo.update { it.copy(linked = false) }
                retry()
            }, 0)
            remote = binder
            waitMs = 2_000L
            CarRepo.update { it.copy(linked = true) }
            Log.i(TAG, "spojeno s MCU")
        } catch (e: Throwable) {
            Log.w(TAG, "spojení s MCU se nepovedlo", e)
            retry()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun retry() {
        ui.postDelayed({ connect() }, waitMs)
        waitMs = (waitMs * 2).coerceAtMost(60_000L)
    }

    private fun handle(m: ByteArray) {
        if (paused) return
        val cmd = m[0].toInt() and 0xFF
        val key = if (cmd == MSG_CAN && m.size >= 4 && m[1] == 0xFD.toByte()) {
            "CAN " + String.format("%02X", m[3].toInt() and 0xFF)
        } else {
            "MCU " + cmd
        }
        CarRepo.logRaw(key, m)
        CarRepo.update { it.copy(lastMessageAt = System.currentTimeMillis()) }
        when (cmd) {
            MSG_CAN -> assembler.accept(m, 1)
            MSG_HEADLIGHTS -> if (m.size > 1) {
                val on = m[1].toInt() == 0
                CarRepo.update { it.copy(headlights = on) }
            }
            MSG_HANDBRAKE -> if (m.size > 1) {
                val on = m[1].toInt() == 1
                CarRepo.update { it.copy(handbrake = on) }
            }
            MSG_VOLTAGE -> if (m.size > 1) {
                val volts = (m[1].toInt() and 0xFF) / 10.0
                CarRepo.update { it.copy(volts = volts) }
            }
        }
    }

    /** Požádá CAN box o stránky palubního počítače a o výstrahy. */
    fun askTripPages() {
        worker.post {
            val types = intArrayOf(RZC_TRIP_NOW, RZC_TRIP_1, RZC_TRIP_2, RZC_WARNINGS).toMutableList()
            // Naučený tachometr: zeptat se i na jeho rámec (kdyby ho CAN box sám neposílal).
            CanOdometer.field.value?.type?.let { if (it !in types && it in 0x30..0x3F) types.add(it) }
            for (type in types) {
                if (!send(SEND_TO_CAN, Rzc.question(type))) return@post
                Thread.sleep(40)
            }
        }
    }

    /**
     * Požádá CAN box o všechny stránky 0x30–0x3F (jen čtecí dotaz 0x8F). Používá se při hledání
     * tachometru: některé údaje CAN box posílá, až když se na ně aplikace zeptá.
     */
    fun askAllPages() {
        worker.post {
            for (type in 0x30..0x3F) {
                if (!send(SEND_TO_CAN, Rzc.question(type))) return@post
                Thread.sleep(40)
            }
        }
    }

    private fun send(cmd: Int, payload: ByteArray): Boolean {
        val binder = remote ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IFACE_MANAGER)
            data.writeByte(cmd.toByte())
            data.writeByteArray(payload)
            data.writeInt(payload.size)
            binder.transact(CALL_SEND, data, reply, 0)
            reply.readException()
            true
        } catch (e: Throwable) {
            Log.w(TAG, "odeslání do MCU se nepovedlo", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
