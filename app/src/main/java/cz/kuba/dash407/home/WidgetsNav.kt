package cz.kuba.dash407.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.nav.Mapy
import cz.kuba.dash407.nav.MapyLogo
import cz.kuba.dash407.nav.MapSources
import cz.kuba.dash407.nav.KeyNeed
import cz.kuba.dash407.nav.GoogleTiles
import cz.kuba.dash407.nav.NavEngine
import cz.kuba.dash407.nav.NavNotice
import cz.kuba.dash407.nav.Navigator
import cz.kuba.dash407.nav.RouteState
import cz.kuba.dash407.nav.Tiles
import cz.kuba.dash407.ui.FitText
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.RoundButton
import cz.kuba.dash407.ui.WidgetCard
import cz.kuba.dash407.ui.formatEta
import cz.kuba.dash407.ui.formatKm
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

private const val HK_LAT = 50.2092
private const val HK_LON = 15.8328

private fun mercX(lon: Double): Double = (lon + 180.0) / 360.0
private fun mercY(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(r) + 1.0 / Math.cos(r)) / PI) / 2.0
}

/** Matice pro „noční“ mapu: převrácení jasu a otočení odstínu o 180°, aby voda zůstala modrá. */
private val nightFilter: ColorFilter by lazy {
    val invert = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
    val hue = floatArrayOf(
        -0.574f, 1.43f, 0.144f, 0f, 0f,
        0.426f, 0.43f, 0.144f, 0f, 0f,
        0.426f, 1.43f, -0.856f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    // Nejdřív převrátit, pak otočit odstín a lehce ztlumit.
    val out = FloatArray(20)
    for (r in 0 until 4) {
        for (c in 0 until 5) {
            var v = 0f
            for (k in 0 until 4) v += hue[r * 5 + k] * invert[k * 5 + c]
            if (c == 4) v += hue[r * 5 + 4]
            out[r * 5 + c] = v
        }
    }
    for (r in 0 until 3) for (c in 0 until 5) out[r * 5 + c] *= 0.82f
    ColorFilter.colorMatrix(ColorMatrix(out))
}

/** Mapa z dlaždic Mapy.com s autem a trasou. Bez posouvání prstem – vždy sleduje auto. */
@Composable
fun MapView(modifier: Modifier, zoomIn: Int) {
    val look = LocalLook.current
    val mapyKey by Prefs.mapyKey.collectAsState()
    val googleKey by Prefs.googleKey.collectAsState()
    val src = MapSources.effective(look.mapStyle, look.darkMap)
    val zoom = zoomIn.coerceAtMost(src.maxZoom)
    val hasKey = when (src.key) {
        KeyNeed.NONE -> true
        KeyNeed.MAPY -> mapyKey.isNotBlank()
        KeyNeed.GOOGLE -> googleKey.isNotBlank()
    }
    val fix by Gps.fix.collectAsState()
    val route by RouteState.route.collectAsState()
    val nav by NavEngine.state.collectAsState()
    val headingUp by Prefs.navHeadingUp.collectAsState()
    val version by Tiles.version.collectAsState()
    val density = LocalDensity.current.density
    val tileSize = 256f * (density * 0.8f).coerceAtLeast(1f)
    // Tmavé podklady (CARTO Tmavá, satelit) a noční varianty se nepřebarvují.
    val filter = if (look.darkMap && !src.dark && MapSources.of(look.mapStyle).night == null) nightFilter else null
    val style = src.id
    val accent = look.accent
    Canvas(modifier.background(if (look.darkMap) Color(0xFF1B2430) else Color(0xFFE8E4DA))) {
        if (version < 0) return@Canvas // čtení verze: po načtení dlaždice se mapa překreslí
        val f = fix
        val navRoute = nav.route
        val navigating = nav.active && navRoute != null
        val cLat = f?.lat ?: HK_LAT
        val cLon = f?.lon ?: HK_LON
        val world = tileSize * (1 shl zoom)
        val cx = mercX(cLon) * world
        val cy = mercY(cLat) * world
        val ay = if (f == null) size.height / 2f else if (navigating) size.height * 0.72f else size.height * 0.62f
        val left = cx - size.width / 2.0
        val top = cy - ay
        val pivot = Offset(size.width / 2f, ay)
        // Při navigaci se mapa otáčí po směru jízdy (lze vypnout v Nastavení).
        val rotation = if (navigating && headingUp && f?.bearing != null) -(f?.bearing ?: 0f) else 0f
        rotate(rotation, pivot) {
            if (hasKey) {
                val reach = if (rotation != 0f) kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()) else 0.0
                val x0 = floor((left - reach) / tileSize).toInt()
                val x1 = floor((left + size.width + reach) / tileSize).toInt()
                val y0 = floor((top - reach) / tileSize).toInt().coerceAtLeast(0)
                val y1 = floor((top + size.height + reach) / tileSize).toInt().coerceAtMost((1 shl zoom) - 1)
                val sz = ceil(tileSize).toInt() + 1
                for (tx in x0..x1) for (ty in y0..y1) {
                    val ox = (tx * tileSize - left).roundToInt()
                    val oy = (ty * tileSize - top).roundToInt()
                    val img = Tiles.get(style, zoom, tx, ty)
                    if (img != null) {
                        drawImage(img, dstOffset = IntOffset(ox, oy), dstSize = IntSize(sz, sz), colorFilter = filter)
                    } else if (zoom > 1) {
                        // Než dorazí ostrá dlaždice, roztáhneme čtvrtinu dlaždice z menšího přiblížení.
                        val parent = Tiles.cached(style, zoom - 1, Math.floorDiv(tx, 2), Math.floorDiv(ty, 2))
                        if (parent != null) {
                            val half = parent.width / 2
                            drawImage(
                                parent,
                                srcOffset = IntOffset(Math.floorMod(tx, 2) * half, Math.floorMod(ty, 2) * half),
                                srcSize = IntSize(half, half),
                                dstOffset = IntOffset(ox, oy),
                                dstSize = IntSize(sz, sz),
                                colorFilter = filter
                            )
                        }
                    }
                }
            }
            // Trasa: při navigaci jen zbývající část, jinak náhled trasy z Mapy.com.
            val pts = if (navigating) navRoute!!.points else route?.points
            val from = if (navigating) nav.shapeIndex.coerceIn(0, (pts?.size ?: 1) - 1) else 0
            if (pts != null && pts.size - from > 1) {
                val path = Path()
                if (navigating && f != null) path.moveTo(pivot.x, pivot.y)
                for (i in from until pts.size) {
                    val p = pts[i]
                    val px = (mercX(p.lon) * world - left).toFloat()
                    val py = (mercY(p.lat) * world - top).toFloat()
                    if (i == from && !(navigating && f != null)) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, Color(0xFF0D2A5C), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color(0xFF3D8BFF), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val d = pts.last()
                val destPt = Offset((mercX(d.lon) * world - left).toFloat(), (mercY(d.lat) * world - top).toFloat())
                drawCircle(Color.White, 9.dp.toPx(), destPt)
                drawCircle(Palette.Bad, 6.dp.toPx(), destPt)
            }
        }
        // Auto: šipka natočená podle směru jízdy (při otáčení mapy míří vždy nahoru).
        if (f != null) {
            val s = 16.dp.toPx()
            rotate(if (rotation != 0f) 0f else (f.bearing ?: 0f), pivot) {
                val arrow = Path().apply {
                    moveTo(pivot.x, pivot.y - s)
                    lineTo(pivot.x + s * 0.75f, pivot.y + s * 0.8f)
                    lineTo(pivot.x, pivot.y + s * 0.4f)
                    lineTo(pivot.x - s * 0.75f, pivot.y + s * 0.8f)
                    close()
                }
                drawCircle(accent.copy(alpha = 0.18f), s * 1.9f, pivot)
                drawPath(arrow, Color.White, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
                drawPath(arrow, accent)
            }
        }
    }
}

/** Šipka manévru nakreslená podle typu z Valhally (odbočení, kruhový objezd, cíl …). */
@Composable
fun ManeuverIcon(type: Int, exitCount: Int, modifier: Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.13f
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun head(tip: Offset, angleDeg: Double) {
            val a = Math.toRadians(angleDeg)
            val len = w * 0.22f
            val back = Offset(tip.x - (len * Math.sin(a)).toFloat(), tip.y + (len * Math.cos(a)).toFloat())
            val nx = (Math.cos(a) * len * 0.75).toFloat()
            val ny = (Math.sin(a) * len * 0.75).toFloat()
            val p = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(back.x + nx, back.y + ny)
                lineTo(back.x - nx, back.y - ny)
                close()
            }
            drawPath(p, color)
        }
        when (type) {
            4, 5, 6 -> {
                // Cíl: špendlík.
                drawCircle(color, w * 0.26f, Offset(w / 2, h * 0.38f), style = stroke)
                drawCircle(color, w * 0.08f, Offset(w / 2, h * 0.38f))
                drawLine(color, Offset(w / 2, h * 0.64f), Offset(w / 2, h * 0.92f), sw, StrokeCap.Round)
            }
            26, 27 -> {
                // Kruhový objezd s číslem výjezdu.
                val c = Offset(w / 2, h * 0.45f)
                val r = w * 0.22f
                drawCircle(color, r, c, style = stroke)
                drawLine(color, Offset(w / 2, h * 0.95f), Offset(w / 2, c.y + r), sw, StrokeCap.Round)
                val ex = Offset(c.x + r * 0.7f, c.y - r * 0.7f)
                val tip = Offset(w * 0.9f, h * 0.1f)
                drawLine(color, ex, Offset(tip.x - w * 0.08f, tip.y + w * 0.08f), sw, StrokeCap.Round)
                head(tip, 45.0)
                if (exitCount > 0) {
                    drawCircle(color, w * 0.11f, c)
                }
            }
            12, 13 -> {
                // Otočka.
                val dir = if (type == 12) 1f else -1f
                val x1 = w / 2 - dir * w * 0.16f
                val x2 = w / 2 + dir * w * 0.16f
                val p = Path().apply {
                    moveTo(x1, h * 0.95f)
                    lineTo(x1, h * 0.4f)
                    cubicTo(x1, h * 0.1f, x2, h * 0.1f, x2, h * 0.4f)
                    lineTo(x2, h * 0.58f)
                }
                drawPath(p, color, style = stroke)
                head(Offset(x2, h * 0.78f), 180.0)
            }
            else -> {
                val angle = when (type) {
                    9, 18, 20, 23 -> 45.0
                    16, 19, 21, 24 -> -45.0
                    2, 10, 37 -> 90.0
                    3, 15, 38 -> -90.0
                    11 -> 135.0
                    14 -> -135.0
                    else -> 0.0
                }
                val mid = Offset(w / 2, h * 0.55f)
                val a = Math.toRadians(angle)
                val len = h * 0.42f
                val tip = Offset(mid.x + (len * Math.sin(a)).toFloat(), mid.y - (len * Math.cos(a)).toFloat())
                val shaftEnd = Offset(mid.x + (len * 0.6 * Math.sin(a)).toFloat(), mid.y - (len * 0.6 * Math.cos(a)).toFloat())
                val p = Path().apply {
                    moveTo(w / 2, h * 0.97f)
                    lineTo(mid.x, mid.y)
                    lineTo(shaftEnd.x, shaftEnd.y)
                }
                drawPath(p, color, style = stroke)
                head(tip, angle)
            }
        }
    }
}

/** Zelený pruh s dalším manévrem nahoře na mapě (jako v navigaci Mapy.com). */
@Composable
fun NavBanner(nav: cz.kuba.dash407.nav.NavState, modifier: Modifier) {
    val next = nav.next
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E8E3E)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            nav.loading -> Text("Plánuji trasu…", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            nav.arrived -> {
                ManeuverIcon(4, 0, Modifier.size(56.dp))
                Spacer(Modifier.width(10.dp))
                Text("Jsi v cíli", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            next != null -> {
                ManeuverIcon(next.type, next.exitCount, Modifier.size(60.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (nav.rerouting) "Přepočítávám…" else NavEngine.shownDistance(nav.distToNextM),
                        color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Text(
                        next.street ?: next.instruction, color = Color.White, fontSize = 17.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                val after = nav.after
                if (after != null && nav.distToNextM < 1500) {
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.2f)).padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Pak", color = Color.White, fontSize = 12.sp)
                        ManeuverIcon(after.type, after.exitCount, Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MapWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val mapyKey by Prefs.mapyKey.collectAsState()
    val googleKey by Prefs.googleKey.collectAsState()
    val src = MapSources.effective(look.mapStyle, look.darkMap)
    val hasKey = when (src.key) {
        KeyNeed.NONE -> true
        KeyNeed.MAPY -> mapyKey.isNotBlank()
        KeyNeed.GOOGLE -> googleKey.isNotBlank()
    }
    val googleCopyright by GoogleTiles.copyright.collectAsState()
    val route by RouteState.route.collectAsState()
    val progress by RouteState.progress.collectAsState()
    val nav by NavEngine.state.collectAsState()
    val voice by Prefs.navVoice.collectAsState()
    val error by Tiles.error.collectAsState()
    val fix by Gps.fix.collectAsState()
    var zoom by rememberSaveable { mutableIntStateOf(15) }
    var manualAt by remember { mutableStateOf(0L) }
    val navigating = nav.active
    // Při navigaci se přiblížení řídí rychlostí, dokud ho ručně nezměníš (pak 20 s platí tvoje).
    val speed = fix?.speedKmh ?: 0f
    val shownZoom = if (navigating && System.currentTimeMillis() - manualAt > 20_000) {
        when {
            speed > 95 -> 14
            speed > 55 -> 15
            else -> 16
        }
    } else zoom
    val shape = RoundedCornerShape(look.corner)
    Box(modifier.fillMaxSize().clip(shape)) {
        MapView(Modifier.fillMaxSize(), shownZoom)
        if (src.key == KeyNeed.GOOGLE && hasKey && !preview) {
            // Text autorských práv Googlu se mění podle výřezu mapy: obnovujeme ho jednou za minutu.
            androidx.compose.runtime.LaunchedEffect(src.id) {
                while (true) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GoogleTiles.refreshCopyright(src.id.removePrefix("g_"), shownZoom, Gps.fix.value)
                    }
                    kotlinx.coroutines.delay(60_000)
                }
            }
        }
        if (!hasKey) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = !preview) { UiBus.open(Panel.SETTINGS) }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Map, null, tint = look.accent, modifier = Modifier.size(36.dp))
                Text("Podklad ${src.group} potřebuje API klíč", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Klepni a zadej ho v Nastavení, nebo ve Vzhledu přepni na CARTO či Esri (bez klíče).", color = Palette.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        // Povinné označení podkladů: u Mapy.com logo, u všech autorská práva s odkazem.
        if (src.key == KeyNeed.MAPY) {
            MapyLogo.Logo(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.25f))
                    .clickable(enabled = !preview) { Mapy.openWeb(context, "https://mapy.com/") }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .height(30.dp),
                30
            )
        } else if (src.key == KeyNeed.GOOGLE) {
            Text(
                "Google", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        val credit = if (src.key == KeyNeed.GOOGLE) googleCopyright else src.attribution
        Text(
            if (navigating) "$credit · trasa © OpenStreetMap" else credit,
            color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(end = 160.dp).padding(6.dp).clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = !preview) { Mapy.openWeb(context, src.link) }
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        if (navigating) {
            NavBanner(nav, Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 8.dp, top = 48.dp).fillMaxWidth())
        }
        val err = error
        if (err != null && hasKey && !navigating) {
            Text(
                err, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp).clip(RoundedCornerShape(6.dp))
                    .background(Palette.Bad.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton(Icons.Filled.Add, "Přiblížit", size = 46.dp) {
                if (!preview) { zoom = (shownZoom + 1).coerceAtMost(18); manualAt = System.currentTimeMillis() }
            }
            RoundButton(Icons.Filled.Remove, "Oddálit", size = 46.dp) {
                if (!preview) { zoom = (shownZoom - 1).coerceAtLeast(5); manualAt = System.currentTimeMillis() }
            }
            if (navigating) {
                RoundButton(if (voice) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Hlas", size = 46.dp) {
                    if (!preview) Prefs.setNavVoice(!voice)
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(start = 150.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val r = route
            val pr = progress
            val nr = nav.route
            if (navigating) {
                Row(
                    Modifier.weight(1f, fill = false).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (nr == null) "…" else "${formatKm(nav.remainingM)} km · ${formatEta(nav.remainingS)} · ${arrival(nav.remainingS)}",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Icon(
                        Icons.Filled.Close, "Ukončit navigaci", tint = Color.White,
                        modifier = Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).clickable(enabled = !preview) { NavEngine.stop() }.padding(4.dp)
                    )
                }
                if (nr != null) ActionChip(Icons.Filled.Map, "Mapy.com", false) { if (!preview) Mapy.navigate(context, nr.dest) }
            } else if (r != null) {
                Row(
                    Modifier.weight(1f, fill = false).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.65f))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (pr?.arrived == true) "V cíli" else (formatKm(pr?.remainingM ?: r.lengthM.toDouble()) + " km · " + arrival(pr?.remainingS ?: r.durationS)),
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Icon(
                        Icons.Filled.Close, "Zrušit trasu", tint = Color.White,
                        modifier = Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).clickable(enabled = !preview) { RouteState.clear() }.padding(4.dp)
                    )
                }
                ActionChip(Icons.Filled.Navigation, "Navigovat", true) { if (!preview) Navigator.go(context, r.dest) }
            } else {
                ActionChip(Icons.Filled.Search, "Kam?", true) { if (!preview) UiBus.open(Panel.DESTINATION) }
                ActionChip(Icons.Filled.Map, "Mapy.com", false) { if (!preview) Mapy.openApp(context) }
            }
        }
    }
}

/** Čas příjezdu ve tvaru 19:47. */
fun arrival(remainingS: Int): String =
    LocalDateTime.now().plusSeconds(remainingS.toLong()).format(DateTimeFormatter.ofPattern("H:mm"))

@Composable
fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, primary: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) look.accent else Color.Black.copy(alpha = 0.65f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (primary) Color(0xFF06121C) else Color.White
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun DirectionsWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val notice by NavNotice.state.collectAsState()
    val nav by NavEngine.state.collectAsState()
    val route by RouteState.route.collectAsState()
    val progress by RouteState.progress.collectAsState()
    val home by Prefs.homeAddress.collectAsState()
    val work by Prefs.workAddress.collectAsState()
    val now by rememberNow()
    val n = notice
    val r = route
    val status = when {
        nav.active -> "Navigace"
        n != null -> "Mapy.com"
        r != null -> "Trasa"
        else -> "Bez trasy"
    }
    WidgetCard("Navigace", modifier, onClick = if (preview) null else ({
        if (nav.active) Unit else if (n != null) Mapy.openApp(context) else UiBus.open(Panel.DESTINATION)
    }), action = {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (nav.active || n != null || r != null) Palette.Good else Palette.Muted))
        Spacer(Modifier.width(6.dp))
        Text(status, color = Palette.Muted, fontSize = 13.sp)
    }) {
        val next = nav.next
        val nr = nav.route
        when {
            nav.active -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (next != null && !nav.loading) {
                        ManeuverIcon(if (nav.arrived) 4 else next.type, next.exitCount, Modifier.size(56.dp), look.accent)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                nav.loading -> "Plánuji trasu…"
                                nav.arrived -> "Jsi v cíli"
                                nav.rerouting -> "Přepočítávám…"
                                else -> NavEngine.shownDistance(nav.distToNextM)
                            },
                            color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                        if (next != null && !nav.arrived) {
                            Text(next.instruction, color = Palette.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (nr == null) "" else "${formatKm(nav.remainingM)} km · příjezd ${arrival(nav.remainingS)}",
                        color = look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Ukončit", color = Palette.Bad, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !preview) { NavEngine.stop() }.padding(6.dp)
                    )
                }
            }
            n != null -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val icon = n.icon
                if (icon != null) {
                    Image(icon, null, Modifier.size(64.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(n.title ?: "", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(n.text ?: "", color = Palette.Text, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!n.sub.isNullOrBlank()) Text(n.sub, color = Palette.Muted, fontSize = 14.sp, maxLines = 1)
                }
            }
            r != null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(r.dest.name, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pr = progress
                Text(
                    if (pr?.arrived == true) "Jsi v cíli"
                    else "${formatKm(pr?.remainingM ?: r.lengthM.toDouble())} km · ${formatEta(pr?.remainingS ?: r.durationS)}",
                    color = look.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                Text("Příjezd ${arrival(pr?.remainingS ?: r.durationS)}", color = Palette.Muted, fontSize = 15.sp)
                ActionChip(Icons.Filled.Navigation, "Navigovat", true) { if (!preview) Navigator.go(context, r.dest) }
            }
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                FitText(timeText(now, false), Modifier.fillMaxWidth().weight(1f), weight = FontWeight.Bold, maxSp = 80f)
                Text(dateText(now), color = Palette.Muted, fontSize = 15.sp, maxLines = 1)
                if (home.isNotBlank() || work.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (home.isNotBlank()) ActionChip(Icons.Filled.Home, "Domů", false) { if (!preview) Navigator.go(context, home) }
                        if (work.isNotBlank()) ActionChip(Icons.Filled.Work, "Práce", false) { if (!preview) Navigator.go(context, work) }
                    }
                }
            }
        }
    }
}
