package cz.kuba.dash407.data

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stav navigace přečtený z oznámení aplikace Mapy.com.
 * Mapy.com během navigace ukazují trvalé oznámení s příštím manévrem;
 * jeho přesný tvar je potřeba ověřit v autě (viz Nastavení → Navigace).
 */
data class NavState(
    val active: Boolean = false,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val icon: Bitmap? = null,
    val at: Long = 0L
)

object NavRepo {
    const val MAPY_PACKAGE = "cz.seznam.mapy"

    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state

    /** Poslední zachycené oznámení Mapy.com v surové podobě, kvůli ladění. */
    private val _lastRaw = MutableStateFlow<String?>(null)
    val lastRaw: StateFlow<String?> = _lastRaw

    @Volatile private var demo = false

    fun posted(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName != MAPY_PACKAGE || demo) return
        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val sub = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        _lastRaw.value = listOfNotNull(
            "ongoing=" + sbn.isOngoing,
            title?.let { "title=$it" },
            text?.let { "text=$it" },
            big?.let { "big=$it" },
            sub?.let { "sub=$it" }
        ).joinToString("\n")
        // Navigace = trvalé (ongoing) oznámení. Ostatní oznámení Mapy.com ignorujeme.
        if (!sbn.isOngoing) return
        val icon = try {
            n.getLargeIcon()?.loadDrawable(context)?.toBitmap(160, 160)
        } catch (e: Exception) {
            null
        }
        _state.value = NavState(
            active = true,
            title = title,
            text = big ?: text,
            subText = sub,
            icon = icon,
            at = System.currentTimeMillis()
        )
    }

    fun removed(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPY_PACKAGE || demo) return
        if (sbn.isOngoing) _state.value = NavState()
    }

    /** Ukázkový režim: vymyšlený manévr; null ukázku ukončí. */
    fun demo(title: String?, text: String?) {
        demo = title != null
        _state.value = if (title == null) NavState() else NavState(true, title, text, null, null, System.currentTimeMillis())
    }
}
