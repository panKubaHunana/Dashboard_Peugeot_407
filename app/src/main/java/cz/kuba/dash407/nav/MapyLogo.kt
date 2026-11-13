package cz.kuba.dash407.nav

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Jedna vyplněná cesta loga. */
class LogoPath(val path: Path, val color: Color)

/**
 * Oficiální logo Mapy.com (povinné u mapových podkladů). Stáhne se jednou
 * z https://api.mapy.com/img/api/logo.svg, uloží do aplikace a kreslí se jako vektor.
 */
object MapyLogo {
    private const val URL = "https://api.mapy.com/img/api/logo.svg"
    private val _paths = MutableStateFlow<List<LogoPath>>(emptyList())
    private var width = 100f
    private var height = 30f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val file = File(context.applicationContext.filesDir, "mapy_logo.svg")
        scope.launch {
            try {
                if (file.exists()) parse(file.readText())
                if (_paths.value.isEmpty()) {
                    val svg = Net.getText(URL)
                    parse(svg)
                    if (_paths.value.isNotEmpty()) file.writeText(svg)
                }
            } catch (e: Exception) {
                // Bez internetu zůstane textové označení; zkusí se při dalším spuštění.
                started = false
            }
        }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\s$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

    private fun color(value: String?, opacity: String?): Color? {
        val alpha = opacity?.toFloatOrNull() ?: 1f
        val base = when {
            value == null || value == "none" -> return null
            value == "white" -> Color.White
            value == "black" -> Color.Black
            value.startsWith("#") && value.length == 7 -> Color(("FF" + value.substring(1)).toLong(16))
            else -> Color.White
        }
        return base.copy(alpha = alpha)
    }

    private fun parse(svg: String) {
        val root = Regex("""<svg[^>]*>""").find(svg)?.value ?: return
        attr(root, "viewBox")?.split(' ', ',')?.mapNotNull { it.toFloatOrNull() }?.let {
            if (it.size == 4) { width = it[2]; height = it[3] }
        }
        val list = ArrayList<LogoPath>()
        for (m in Regex("""<path[^>]*>""").findAll(svg)) {
            val tag = m.value
            val d = attr(tag, "d") ?: continue
            val c = color(attr(tag, "fill"), attr(tag, "fill-opacity")) ?: continue
            val p = PathParser().parsePathString(d).toPath()
            if (attr(tag, "fill-rule") == "evenodd") p.fillType = PathFillType.EvenOdd
            list.add(LogoPath(p, c))
        }
        _paths.value = list
    }

    /** Logo v dané výšce; než se stáhne, je místo něj text „Mapy.com“. */
    @Composable
    fun Logo(modifier: Modifier, heightDp: Int = 30) {
        val paths by _paths.collectAsState()
        if (paths.isEmpty()) {
            Box(modifier) {
                Text("Mapy.com", color = Color.White, fontSize = (heightDp * 0.55f).sp, fontWeight = FontWeight.Bold)
            }
            return
        }
        val w = (heightDp * width / height).dp
        Canvas(modifier.width(w)) {
            val s = size.height / height
            scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                for (lp in paths) drawPath(lp.path, lp.color)
            }
        }
    }
}
