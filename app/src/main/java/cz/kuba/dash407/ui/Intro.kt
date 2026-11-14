package cz.kuba.dash407.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import cz.kuba.dash407.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.AppearanceStore
import java.io.File

/** Stav úvodní animace: přehraje se jednou po startu aplikace, nebo na požádání z nastavení. */
object IntroState {
    var playedThisProcess = false
    var preview by mutableStateOf(false)
}

/** Vlastní obrázek pro úvod (třeba logo, které si Kuba sám stáhl). Ukládá se jen v rádiu. */
object IntroLogo {
    private fun file(context: Context) = File(context.applicationContext.filesDir, "intro_logo.png")

    fun exists(context: Context) = file(context).exists()

    fun load(context: Context): Bitmap? = try {
        if (file(context).exists()) BitmapFactory.decodeFile(file(context).path) else null
    } catch (e: Exception) {
        null
    }

    /** Zmenší obrázek na nejvýše 1024 px a uloží ho jako PNG, aby se uložilo i průhledné pozadí. */
    fun save(context: Context, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1536 || bounds.outHeight / sample > 1536) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) false else {
            file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Exception) {
        false
    }

    fun delete(context: Context) {
        try { file(context).delete() } catch (e: Exception) { }
    }
}

val IntroStyles = listOf(
    "photo" to "Peugeot (obrázek)",
    "modern" to "Moderní",
    "retro" to "Retro",
    "custom" to "Vlastní obrázek"
)

/** Obrys štítu v jednotkovém čtverci 1 × 1,15, zmenšený a posunutý na plátno. */
private fun shieldPath(cx: Float, top: Float, width: Float): Path {
    val l = cx - width / 2f
    fun x(u: Float) = l + u * width
    fun y(v: Float) = top + v * width
    return Path().apply {
        moveTo(x(0f), y(0.04f))
        cubicTo(x(0.18f), y(0f), x(0.34f), y(0.0f), x(0.5f), y(0.04f))
        cubicTo(x(0.66f), y(0f), x(0.82f), y(0.0f), x(1f), y(0.04f))
        lineTo(x(1f), y(0.58f))
        cubicTo(x(1f), y(0.86f), x(0.72f), y(1.02f), x(0.5f), y(1.12f))
        cubicTo(x(0.28f), y(1.02f), x(0f), y(0.86f), x(0f), y(0.58f))
        close()
    }
}

/** Úvodní animace přes celou plochu. Klepnutím se přeskočí. */
@Composable
fun IntroOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val a by AppearanceStore.state.collectAsState()
    val p = remember { Animatable(0f) }
    val millis = (a.introSeconds.coerceIn(2f, 8f) * 1000).toInt()
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(millis, easing = LinearEasing))
        onDone()
    }
    val style = if (a.introStyle == "custom" && !IntroLogo.exists(context)) "modern" else a.introStyle
    val custom = remember(style) { if (style == "custom") IntroLogo.load(context)?.asImageBitmap() else null }
    val gold = Color(0xFFD9B35A)
    val bg = when (style) {
        "photo" -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        "retro" -> Brush.verticalGradient(listOf(Color(0xFF1A1208), Color(0xFF0A0703)))
        else -> Brush.verticalGradient(listOf(Color(0xFF0E1726), Color(0xFF05070C)))
    }
    // Úseky animace (0–1): kreslení obrysu, nápis, výdrž, zhasnutí.
    fun seg(from: Float, to: Float) = ((p.value - from) / (to - from)).coerceIn(0f, 1f)

    Box(
        Modifier.fillMaxSize().background(bg)
            .graphicsLayer { alpha = 1f - seg(0.88f, 1f) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        if (style == "photo") {
            Image(
                painterResource(R.drawable.intro_peugeot), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = seg(0f, 0.3f)
                    val z = 1f + 0.06f * p.value
                    scaleX = z
                    scaleY = z
                }
            )
        } else if (style == "custom" && custom != null) {
            Image(
                custom, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.7f).padding(24.dp)
                    .graphicsLayer {
                        val t = seg(0f, 0.45f)
                        alpha = t
                        scaleX = 0.8f + 0.2f * t
                        scaleY = 0.8f + 0.2f * t
                    }
            )
        } else {
            val line = if (style == "retro") gold else Color(0xFFE8EEF7)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth(0.42f).aspectRatio(0.86f)) {
                    val w = size.width * 0.92f
                    val path = shieldPath(size.width / 2f, 6.dp.toPx(), w)
                    val measure = PathMeasure().apply { setPath(path, true) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * seg(0f, 0.45f), part, true)
                    if (style == "retro") {
                        drawPath(part, gold, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(part, gold.copy(alpha = 0.35f), style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        drawPath(part, line, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val b = path.getBounds()
                    val fill = seg(0.35f, 0.6f)
                    if (fill > 0f) {
                        drawPath(
                            path,
                            Brush.verticalGradient(
                                listOf(line.copy(alpha = 0.22f * fill), line.copy(alpha = 0.04f * fill)),
                                startY = b.top, endY = b.bottom
                            )
                        )
                    }
                    // Světelný pruh přejede štítem.
                    val sweep = seg(0.55f, 0.8f)
                    if (sweep > 0f && sweep < 1f) {
                        val sx = b.left + b.width * 1.4f * sweep - b.width * 0.2f
                        drawLine(
                            Color.White.copy(alpha = 0.55f * (1f - kotlin.math.abs(sweep - 0.5f) * 2f)),
                            Offset(sx, b.top), Offset(sx - b.width * 0.15f, b.bottom), strokeWidth = 6.dp.toPx()
                        )
                    }
                }
                Column(
                    Modifier.padding(top = 28.dp)
                        .graphicsLayer {
                            val t = seg(0.4f, 0.7f)
                            alpha = t
                            translationY = (1f - t) * 24f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PEUGEOT",
                        color = if (style == "retro") gold else Color(0xFFF2F5F9),
                        fontSize = if (style == "retro") 46.sp else 44.sp,
                        fontWeight = if (style == "retro") FontWeight.Bold else FontWeight.Light,
                        fontFamily = if (style == "retro") FontFamily.Serif else FontFamily.Default,
                        letterSpacing = if (style == "retro") 6.sp else 14.sp
                    )
                    Text(
                        "407 SW  ·  HDi",
                        color = if (style == "retro") gold.copy(alpha = 0.75f) else Color(0xFF8A96A8),
                        fontSize = 18.sp,
                        letterSpacing = 6.sp,
                        fontFamily = if (style == "retro") FontFamily.Serif else FontFamily.Default,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}
