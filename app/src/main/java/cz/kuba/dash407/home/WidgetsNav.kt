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
import cz.kuba.dash407.nav.NavNotice
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
fun MapView(modifier: Modifier, zoom: Int) {
    val look = LocalLook.current
    val key by Prefs.mapyKey.collectAsState()
    val fix by Gps.fix.collectAsState()
    val route by RouteState.route.collectAsState()
    val version by Tiles.version.collectAsState()
    val density = LocalDensity.current.density
    val tileSize = 256f * (density * 0.8f).coerceAtLeast(1f)
    val filter = if (look.darkMap) nightFilter else null
    val style = look.mapStyle
    val accent = look.accent
    Canvas(modifier.background(if (look.darkMap) Color(0xFF1B2430) else Color(0xFFE8E4DA))) {
        if (version < 0) return@Canvas // čtení verze: po načtení dlaždice se mapa překreslí
        val f = fix
        val cLat = f?.lat ?: HK_LAT
        val cLon = f?.lon ?: HK_LON
        val world = tileSize * (1 shl zoom)
        val cx = mercX(cLon) * world
        val cy = mercY(cLat) * world
        val ay = if (f != null) size.height * 0.62f else size.height / 2f
        val left = cx - size.width / 2.0
        val top = cy - ay
        if (key.isNotBlank()) {
            val x0 = floor(left / tileSize).toInt()
            val x1 = floor((left + size.width) / tileSize).toInt()
            val y0 = floor(top / tileSize).toInt().coerceAtLeast(0)
            val y1 = floor((top + size.height) / tileSize).toInt().coerceAtMost((1 shl zoom) - 1)
            val sz = ceil(tileSize).toInt() + 1
            for (tx in x0..x1) for (ty in y0..y1) {
                val ox = (tx * tileSize - left).roundToInt()
                val oy = (ty * tileSize - top).roundToInt()
                val img = Tiles.get(style, zoom, tx, ty, key)
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
        // Trasa.
        val r = route
        if (r != null && r.points.size > 1) {
            val path = Path()
            r.points.forEachIndexed { i, p ->
                val px = (mercX(p.lon) * world - left).toFloat()
                val py = (mercY(p.lat) * world - top).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Color(0xFF0D2A5C), style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Color(0xFF3D8BFF), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val d = r.points.last()
            val destPt = Offset((mercX(d.lon) * world - left).toFloat(), (mercY(d.lat) * world - top).toFloat())
            drawCircle(Color.White, 9.dp.toPx(), destPt)
            drawCircle(Palette.Bad, 6.dp.toPx(), destPt)
        }
        // Auto: šipka natočená podle směru jízdy.
        if (f != null) {
            val p = Offset(size.width / 2f, ay)
            val s = 16.dp.toPx()
            rotate(f.bearing ?: 0f, p) {
                val arrow = Path().apply {
                    moveTo(p.x, p.y - s)
                    lineTo(p.x + s * 0.75f, p.y + s * 0.8f)
                    lineTo(p.x, p.y + s * 0.4f)
                    lineTo(p.x - s * 0.75f, p.y + s * 0.8f)
                    close()
                }
                drawCircle(accent.copy(alpha = 0.18f), s * 1.9f, p)
                drawPath(arrow, Color.White, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
                drawPath(arrow, accent)
            }
        }
    }
}

@Composable
fun MapWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val key by Prefs.mapyKey.collectAsState()
    val route by RouteState.route.collectAsState()
    val progress by RouteState.progress.collectAsState()
    val error by Tiles.error.collectAsState()
    var zoom by rememberSaveable { mutableIntStateOf(15) }
    val shape = RoundedCornerShape(look.corner)
    Box(modifier.fillMaxSize().clip(shape)) {
        MapView(Modifier.fillMaxSize(), zoom)
        if (key.isBlank()) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = !preview) { UiBus.open(Panel.SETTINGS) }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Map, null, tint = look.accent, modifier = Modifier.size(36.dp))
                Text("Mapa Mapy.com potřebuje API klíč", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Klepni a zadej ho v Nastavení › Mapy.com. Navigace v aplikaci Mapy.com funguje i bez něj.", color = Palette.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        // Povinné označení podkladů Mapy.com: logo a autorská práva, obojí s odkazem.
        MapyLogo.Logo(
            Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.25f))
                .clickable(enabled = !preview) { Mapy.openWeb(context, "https://mapy.com/") }
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .height(30.dp),
            30
        )
        Text(
            "© Seznam.cz a.s. a další",
            color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = !preview) { Mapy.openWeb(context, "https://api.mapy.com/copyright") }
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        val err = error
        if (err != null && key.isNotBlank()) {
            Text(
                err, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp).clip(RoundedCornerShape(6.dp))
                    .background(Palette.Bad.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton(Icons.Filled.Add, "Přiblížit", size = 46.dp) { if (!preview) zoom = (zoom + 1).coerceAtMost(18) }
            RoundButton(Icons.Filled.Remove, "Oddálit", size = 46.dp) { if (!preview) zoom = (zoom - 1).coerceAtLeast(5) }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(start = 150.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val r = route
            val pr = progress
            if (r != null) {
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
                ActionChip(Icons.Filled.Navigation, "Navigovat", true) { if (!preview) Mapy.navigate(context, r.dest) }
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
    val route by RouteState.route.collectAsState()
    val progress by RouteState.progress.collectAsState()
    val home by Prefs.homeAddress.collectAsState()
    val work by Prefs.workAddress.collectAsState()
    val now by rememberNow()
    val n = notice
    val r = route
    val status = when {
        n != null -> "Mapy.com"
        r != null -> "Trasa"
        else -> "Bez trasy"
    }
    WidgetCard("Navigace", modifier, onClick = if (preview) null else ({
        if (n != null) Mapy.openApp(context) else UiBus.open(Panel.DESTINATION)
    }), action = {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (n != null || r != null) Palette.Good else Palette.Muted))
        Spacer(Modifier.width(6.dp))
        Text(status, color = Palette.Muted, fontSize = 13.sp)
    }) {
        when {
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
                ActionChip(Icons.Filled.Navigation, "Navigovat v Mapy.com", true) { if (!preview) Mapy.navigate(context, r.dest) }
            }
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                FitText(timeText(now, false), Modifier.fillMaxWidth().weight(1f), weight = FontWeight.Bold, maxSp = 80f)
                Text(dateText(now), color = Palette.Muted, fontSize = 15.sp, maxLines = 1)
                if (home.isNotBlank() || work.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (home.isNotBlank()) ActionChip(Icons.Filled.Home, "Domů", false) { if (!preview) Mapy.navigate(context, home) }
                        if (work.isNotBlank()) ActionChip(Icons.Filled.Work, "Práce", false) { if (!preview) Mapy.navigate(context, work) }
                    }
                }
            }
        }
    }
}
