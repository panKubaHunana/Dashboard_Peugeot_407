package cz.kuba.dash407.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Skrytí lišt rádia systémovým nastavením „policy_control“ (celoobrazovkový režim jen pro Dash 407).
 * Funguje do Androidu 10 a jen s oprávněním WRITE_SECURE_SETTINGS, které se přidělí jednou přes ADB:
 *   adb shell pm grant cz.kuba.dash407 android.permission.WRITE_SECURE_SETTINGS
 */
object Bars {
    private const val KEY = "policy_control"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /** Systém tento způsob podporuje (Android 10 a starší). */
    val supported: Boolean get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    fun current(context: Context): String? = try { Settings.Global.getString(context.contentResolver, KEY) } catch (e: Exception) { null }

    /** Zapíše nastavení podle přepínačů; vrací text výsledku pro diagnostiku. */
    fun applySystem(context: Context?, hideTop: Boolean, hideBottom: Boolean): String {
        if (context == null) return "bez okna"
        if (!hasPermission(context)) return "chybí oprávnění (ADB)"
        val pkg = context.packageName
        val value = when {
            hideTop && hideBottom -> "immersive.full=$pkg"
            hideTop -> "immersive.status=$pkg"
            hideBottom -> "immersive.navigation=$pkg"
            else -> null
        }
        return try {
            if (value == null) {
                // Mazat jen to, co zapsal Dash 407 – nastavení jiné aplikace nebo firmwaru zůstane.
                val cur = current(context)
                if (cur.isNullOrBlank() || !cur.contains(pkg)) return "beze změny"
            }
            Settings.Global.putString(context.contentResolver, KEY, value)
            if (value == null) "vypnuto" else "nastaveno: $value"
        } catch (e: Exception) {
            "nepovedlo se: ${e.message}"
        }
    }
}
