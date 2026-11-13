package cz.kuba.dash407.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppEntry(val label: String, val packageName: String, val icon: ImageBitmap?)

/** Nainstalované aplikace, které jdou spustit. */
object AppsRepo {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val scope = CoroutineScope(Dispatchers.IO)

    fun reload(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val pm = app.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            val list = ArrayList<AppEntry>()
            for (info in pm.queryIntentActivities(intent, 0)) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == app.packageName || !seen.add(pkg)) continue
                val icon = try {
                    info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                list.add(AppEntry(info.loadLabel(pm).toString(), pkg, icon))
            }
            _apps.value = list.sortedBy { it.label.lowercase() }
        }
    }

    fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Aplikace mezitím zmizela nebo ji nejde spustit.
        }
    }
}
