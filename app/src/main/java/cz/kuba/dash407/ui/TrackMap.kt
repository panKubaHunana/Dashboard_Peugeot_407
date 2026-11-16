package cz.kuba.dash407.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.GeoPt
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.home.nightFilter
import cz.kuba.dash407.nav.KeyNeed
import cz.kuba.dash407.nav.MapSources
import cz.kuba.dash407.nav.Tiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

fun mercX(lon: Double): Double = (lon + 180.0) / 360.0
fun mercY(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(r) + 1.0 / Math.cos(r)) / PI) / 2.0
}

/** Pohled na mapu: střed v Mercatorových souřadnicích (0..1) a přiblížení (může být necelé). */
class MapCamera(cx: Double = mercX(15.8328), cy: Double = mercY(50.2092), zoom: Double = 11.0) {
    var cx by mutableDoubleStateOf(cx)
    var cy by mutableDoubleStateOf(cy)
    var zoom by mutableDoubleStateOf(zoom)

    /** Nastaví pohled tak, aby byly vidět všechny body. */
    fun fit(points: List<GeoPt>, widthPx: Float, heightPx: Float, tilePx: Float) {
        val pts = points.filter { it.lat.isFinite() && it.lon.isFinite() }
        if (pts.isEmpty() || widthPx <= 0f || heightPx <= 0f) return
        val xs = pts.map { mercX(it.lon) }
        val ys = pts.map { mercY(it.lat) }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        cx = (minX + maxX) / 2
        cy = (minY + maxY) / 2
        val spanX = (maxX - minX).coerceAtLeast(1e-6)
        val spanY = (maxY - minY).coerceAtLeast(1e-6)
        val z = minOf(log2(widthPx * 0.85 / (spanX * tilePx)), log2(heightPx * 0.85 / (spanY * tilePx)))
        zoom = if (z.isFinite()) z.coerceIn(3.0, 17.0) else 11.0
    }
}

/**
 * Mapa z dlaždic (stejný podklad jako hlavní mapa) s posunem prstem a přiblížením dvěma prsty.
 * [overlay] kreslí nad mapu; dostane převod zeměpisných souřadnic na body obrazovky.
 */
@Composable
fun TrackMap(
    camera: MapCamera,
    modifier: Modifier = Modifier,
    overlay: DrawScope.(project: (Double, Double) -> Offset, world: Double) -> Unit = { _, _ -> }
) {
    val look = LocalLook.current
    val mapyKey by Prefs.mapyKey.collectAsState()
    val googleKey by Prefs.googleKey.collectAsState()
    var src = MapSources.effective(look.mapStyle, look.darkMap)
    val hasKey = when (src.key) {
        KeyNeed.NONE -> true
        KeyNeed.MAPY -> mapyKey.isNotBlank()
        KeyNeed.GOOGLE -> googleKey.isNotBlank()
    }
    if (!hasKey) src = MapSources.effective("carto_voyager", look.darkMap)
    val filter = if (look.darkMap && !src.dark && MapSources.of(look.mapStyle).night == null) nightFilter else null
    val version by Tiles.version.collectAsState()
    val density = LocalDensity.current.density
    val tilePx = 256f * (density * 0.8f).coerceAtLeast(1f)
    val style = src.id
    val maxZoom = src.maxZoom

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .background(if (look.darkMap) Color(0xFF1B2430) else Color(0xFFE8E4DA))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val w = size.width / 2f
                        val h = size.height / 2f
                        val world0 = tilePx * 2.0.pow(camera.zoom)
                        val mx = camera.cx + (centroid.x - w) / world0
                        val my = camera.cy + (centroid.y - h) / world0
                        camera.zoom = (camera.zoom + log2(zoomChange.toDouble())).coerceIn(2.0, maxZoom.toDouble())
                        val world1 = tilePx * 2.0.pow(camera.zoom)
                        camera.cx = mx - (centroid.x - w) / world1 - pan.x / world1
                        camera.cy = (my - (centroid.y - h) / world1 - pan.y / world1).coerceIn(0.0, 1.0)
                    }
                }
        ) {
            if (version < 0) return@Canvas
            val world = tilePx * 2.0.pow(camera.zoom)
            val tz = floor(camera.zoom).toInt().coerceIn(1, maxZoom)
            val n = 1 shl tz
            val tileScreen = world / n
            val left = camera.cx * world - size.width / 2.0
            val top = camera.cy * world - size.height / 2.0
            val x0 = floor(left / tileScreen).toInt()
            val x1 = floor((left + size.width) / tileScreen).toInt()
            val y0 = floor(top / tileScreen).toInt().coerceAtLeast(0)
            val y1 = floor((top + size.height) / tileScreen).toInt().coerceAtMost(n - 1)
            val sz = kotlin.math.ceil(tileScreen).toInt() + 1
            for (tx in x0..x1) for (ty in y0..y1) {
                val ox = (tx * tileScreen - left).roundToInt()
                val oy = (ty * tileScreen - top).roundToInt()
                val img = Tiles.get(style, tz, tx, ty)
                if (img != null) {
                    drawImage(img, dstOffset = IntOffset(ox, oy), dstSize = IntSize(sz, sz), colorFilter = filter)
                } else if (tz > 1) {
                    val parent = Tiles.cached(style, tz - 1, Math.floorDiv(tx, 2), Math.floorDiv(ty, 2))
                    if (parent != null) {
                        val half = parent.width / 2
                        drawImage(
                            parent,
                            srcOffset = IntOffset(Math.floorMod(tx, 2) * half, Math.floorMod(ty, 2) * half),
                            srcSize = IntSize(half, half), dstOffset = IntOffset(ox, oy), dstSize = IntSize(sz, sz), colorFilter = filter
                        )
                    }
                }
            }
            val project = { lat: Double, lon: Double ->
                Offset((mercX(lon) * world - left).toFloat(), (mercY(lat) * world - top).toFloat())
            }
            overlay(project, world)
        }
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            RoundButton(Icons.Filled.Add, "Přiblížit", size = 48.dp) { camera.zoom = (camera.zoom + 1).coerceAtMost(maxZoom.toDouble()) }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            RoundButton(Icons.Filled.Remove, "Oddálit", size = 48.dp) { camera.zoom = (camera.zoom - 1).coerceAtLeast(2.0) }
        }
        Text(
            src.attribution, color = Color.Black.copy(alpha = 0.7f), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 4.dp)
        )
    }
}

/** Velikost dlaždice na obrazovce (stejná jako u hlavní mapy). */
@Composable
fun tilePx(): Float = 256f * (LocalDensity.current.density * 0.8f).coerceAtLeast(1f)

/** Hotová teplotní vrstva a pohled, pro který se spočítala. */
class HeatLayer(val image: ImageBitmap, val cx: Double, val cy: Double, val zoom: Double, val w: Int, val h: Int)

/**
 * Teplotní mapa: body se sečtou do mřížky po 4 px, rozmažou a obarví od modré přes zelenou a žlutou do červené.
 * Počítá se na pozadí po každé změně pohledu; při posouvání se poslední vrstva jen posune a zvětší.
 */
@Composable
fun HeatMap(camera: MapCamera, points: List<GeoPt>, radiusCells: Int, modifier: Modifier = Modifier) {
    val tile = tilePx()
    var layer by remember { mutableStateOf<HeatLayer?>(null) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt()
        val hPx = with(density) { maxHeight.toPx() }.toInt()
        LaunchedEffect(points, camera.cx, camera.cy, camera.zoom, wPx, hPx) {
            delay(150)
            val cx = camera.cx; val cy = camera.cy; val zoom = camera.zoom
            layer = withContext(Dispatchers.Default) { buildHeat(points, cx, cy, zoom, wPx, hPx, tile, radiusCells) }
        }
        TrackMap(camera, Modifier.fillMaxSize()) { _, world ->
            val l = layer ?: return@TrackMap
            val s = 2.0.pow(camera.zoom - l.zoom).toFloat()
            if (!s.isFinite() || !world.isFinite()) return@TrackMap
            val centerX = ((l.cx - camera.cx) * world + size.width / 2.0).toFloat()
            val centerY = ((l.cy - camera.cy) * world + size.height / 2.0).toFloat()
            val dw = (l.w * s).roundToInt()
            val dh = (l.h * s).roundToInt()
            drawImage(
                l.image,
                dstOffset = IntOffset((centerX - dw / 2f).roundToInt(), (centerY - dh / 2f).roundToInt()),
                dstSize = IntSize(dw, dh)
            )
        }
    }
}

private fun buildHeat(points: List<GeoPt>, cx: Double, cy: Double, zoom: Double, w: Int, h: Int, tile: Float, radius: Int): HeatLayer? {
    if (w <= 0 || h <= 0) return null
    val cell = 4
    val gw = (w + cell - 1) / cell
    val gh = (h + cell - 1) / cell
    val grid = FloatArray(gw * gh)
    val world = tile * 2.0.pow(zoom)
    val left = cx * world - w / 2.0
    val top = cy * world - h / 2.0
    for (p in points) {
        if (!p.lat.isFinite() || !p.lon.isFinite()) continue
        val x = ((mercX(p.lon) * world - left) / cell).toInt()
        val y = ((mercY(p.lat) * world - top) / cell).toInt()
        if (x in 0 until gw && y in 0 until gh) grid[y * gw + x] += 1f
    }
    // Dvakrát rozmazání krabičkovým filtrem ≈ Gaussovo rozostření.
    var a = grid
    repeat(2) { a = boxBlur(a, gw, gh, radius) }
    var max = 0f
    for (v in a) if (v > max) max = v
    if (!(max > 0f) || !max.isFinite()) return null
    val colors = IntArray(gw * gh)
    for (i in a.indices) {
        // Průběžný součet v rozmazání může kvůli zaokrouhlení vyjít nepatrně záporný → sqrt = NaN.
        val t = sqrt((a[i] / max).coerceIn(0f, 1f))
        colors[i] = if (!(t >= 0.04f)) 0 else ramp(t)
    }
    val bmp = Bitmap.createBitmap(colors, gw, gh, Bitmap.Config.ARGB_8888)
    return HeatLayer(bmp.asImageBitmap(), cx, cy, zoom, w, h)
}

private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
    if (r <= 0) return src
    val tmp = FloatArray(src.size)
    val out = FloatArray(src.size)
    val div = (2 * r + 1).toFloat()
    for (y in 0 until h) {
        var sum = 0f
        for (x in -r..r) sum += src[y * w + x.coerceIn(0, w - 1)]
        for (x in 0 until w) {
            tmp[y * w + x] = sum / div
            sum += src[y * w + (x + r + 1).coerceAtMost(w - 1)] - src[y * w + (x - r).coerceAtLeast(0)]
        }
    }
    for (x in 0 until w) {
        var sum = 0f
        for (y in -r..r) sum += tmp[y.coerceIn(0, h - 1) * w + x]
        for (y in 0 until h) {
            out[y * w + x] = sum / div
            sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
        }
    }
    return out
}

/** Barevná škála teplotní mapy (průhledná modrá → zelená → žlutá → červená). */
private fun ramp(value: Float): Int {
    val t = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    val stops = floatArrayOf(0f, 0.35f, 0.6f, 0.8f, 1f)
    val cols = intArrayOf(0x0040A0FF, 0x9940C0FF.toInt(), 0xCC3DDC84.toInt(), 0xDDFFD600.toInt(), 0xEEFF3D00.toInt())
    var i = 0
    while (i < stops.size - 2 && t > stops[i + 1]) i++
    val f = ((t - stops[i]) / (stops[i + 1] - stops[i])).coerceIn(0f, 1f)
    fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    fun mix(shift: Int) = (ch(cols[i], shift) + (ch(cols[i + 1], shift) - ch(cols[i], shift)) * f).roundToInt()
    return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}
