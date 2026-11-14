package cz.kuba.dash407.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.GForce
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.Perf
import cz.kuba.dash407.data.PerfState
import cz.kuba.dash407.data.SpeedLimit
import cz.kuba.dash407.data.Trip
import cz.kuba.dash407.ui.Czech
import cz.kuba.dash407.ui.DASH
import cz.kuba.dash407.ui.FitText
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.StatRow
import cz.kuba.dash407.ui.WidgetCard
import cz.kuba.dash407.ui.decimal
import cz.kuba.dash407.ui.formatDuration
import cz.kuba.dash407.ui.formatKm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SpeedWidget(modifier: Modifier) {
    val speed by Gps.speedKmh.collectAsState()
    WidgetCard("Rychlost", modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(speed?.toString() ?: DASH, Modifier.fillMaxWidth().weight(1f), weight = FontWeight.Bold, maxSp = 160f, widthPerChar = 0.62f)
            Text("km/h · GPS", color = Palette.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun SpeedGaugeWidget(modifier: Modifier) {
    val look = LocalLook.current
    val speed by Gps.speedKmh.collectAsState()
    WidgetCard("Rychloměr", modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = (minOf(size.width, size.height) * 0.07f).coerceAtLeast(6f)
                val side = minOf(size.width, size.height) - stroke
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arc = Size(side, side)
                drawArc(Palette.Text.copy(alpha = 0.1f), 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val part = ((speed ?: 0).coerceIn(0, 200)) / 200f
                if (part > 0f) {
                    drawArc(
                        if ((speed ?: 0) > 130) Palette.Amber else look.accent,
                        135f, 270f * part, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(speed?.toString() ?: DASH, color = Palette.Text, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Palette.Muted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun TripWidget(modifier: Modifier, preview: Boolean) {
    val look = LocalLook.current
    val t by Trip.state.collectAsState()
    val refuels by FuelLog.entries.collectAsState()
    val now by rememberNow()
    val stats = FuelLog.stats(refuels)
    val km = t.distanceM / 1000.0
    val litres = stats.averageL100?.let { km * it / 100.0 }
    val lastPricePerL = refuels.firstOrNull()?.let { if (it.litres > 0) it.price / it.litres else null }
    val cost = if (litres != null && lastPricePerL != null) litres * lastPricePerL else null
    val since = DateTimeFormatter.ofPattern("H:mm", Czech).format(Instant.ofEpochMilli(t.startedAt).atZone(ZoneId.systemDefault()))
    WidgetCard("Jízda", modifier, action = {
        Text(
            "Vynulovat", color = look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !preview) { Trip.reset() }.padding(horizontal = 4.dp)
        )
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 260.dp
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TripMain(km, since, litres, cost, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    TripStats(t, now.let { System.currentTimeMillis() - t.startedAt }, Modifier.weight(1.1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TripMain(km, since, litres, cost, Modifier.fillMaxWidth().weight(1f))
                    TripStats(t, System.currentTimeMillis() - t.startedAt, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TripMain(km: Double, since: String, litres: Double?, cost: Double?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
            FitText(formatKm(km * 1000), Modifier.weight(1f).fillMaxHeight(), weight = FontWeight.Bold, maxSp = 56f,
                align = androidx.compose.ui.text.style.TextAlign.Start)
            Text(" KM", color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text("od $since", color = Palette.Muted, fontSize = 14.sp, maxLines = 1)
        Text(
            (litres?.let { decimal(it) + " l" } ?: "– l") + ", " + (cost?.let { decimal(it, 0) + " Kč" } ?: "– Kč"),
            color = Palette.Muted, fontSize = 14.sp, maxLines = 1
        )
    }
}

@Composable
private fun TripStats(t: cz.kuba.dash407.data.TripStats, elapsedMs: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StatRow("Čas", formatDuration(elapsedMs))
        StatRow("V pohybu", formatDuration(t.movingMs))
        StatRow("Průměr", "${t.averageKmh.roundToInt()} km/h")
        StatRow("Maximum", "${t.topKmh.roundToInt()} km/h")
    }
}

@Composable
fun GForceWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    DisposableEffect(Unit) {
        GForce.acquire(context)
        onDispose { GForce.release() }
    }
    val g by GForce.state.collectAsState()
    WidgetCard("Přetížení", modifier, action = {
        Text(
            "Vynulovat", color = look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !preview) { GForce.calibrate() }.padding(horizontal = 4.dp)
        )
    }) {
        if (!g.available) {
            Text("Rádio nemá senzor zrychlení", color = Palette.Muted, fontSize = 15.sp)
            return@WidgetCard
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = minOf(size.width, size.height) / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Palette.Text.copy(alpha = 0.1f), r, c, style = Stroke(2f))
                drawCircle(Palette.Text.copy(alpha = 0.08f), r * 0.5f, c, style = Stroke(2f))
                drawLine(Palette.Text.copy(alpha = 0.08f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2f)
                drawLine(Palette.Text.copy(alpha = 0.08f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f)
                // Celý poloměr = 1 g.
                val p = Offset(c.x + g.lateral.coerceIn(-1f, 1f) * r, c.y - g.longitudinal.coerceIn(-1f, 1f) * r)
                drawCircle(look.accent.copy(alpha = 0.3f), r * 0.16f, p)
                drawCircle(look.accent, r * 0.09f, p)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatRow("Bočně", String.format(Czech, "%+.2f g", g.lateral))
                StatRow("Podélně", String.format(Czech, "%+.2f g", g.longitudinal))
                StatRow("Max. zatáčka", String.format(Czech, "%.2f g", maxOf(g.peakLeft, g.peakRight)))
                StatRow("Max. brzdění", String.format(Czech, "%.2f g", g.peakBrake))
            }
        }
    }
}

@Composable
fun PerfWidget(modifier: Modifier, preview: Boolean) {
    val look = LocalLook.current
    val p by Perf.state.collectAsState()
    WidgetCard("Časomíra", modifier, action = {
        Icon(
            Icons.Filled.Refresh, "Znovu", tint = Palette.Muted,
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable(enabled = !preview) { Perf.reset() }
        )
    }) {
        Column(Modifier.fillMaxSize()) {
            val (text, color) = when (p.phase) {
                PerfState.Phase.WAITING -> "Zastav a připrav se" to Palette.Muted
                PerfState.Phase.READY -> "Připraveno: vyraz, až budeš chtít" to Palette.Good
                PerfState.Phase.RUNNING -> "Měřím…" to look.accent
                PerfState.Phase.DONE -> "Hotovo – klepni na ↻ pro nové měření" to Palette.Text
            }
            Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                PerfValue("0–50", p.t50, Modifier.weight(1f))
                PerfValue("0–100", p.t100, Modifier.weight(1f))
                PerfValue("400 m", p.t400, Modifier.weight(1f))
            }
            Text("Měří se z GPS (1× za sekundu), výsledek je orientační.", color = Palette.Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PerfValue(label: String, seconds: Double?, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Palette.Muted, fontSize = 14.sp)
        FitText(seconds?.let { decimal(it) + " s" } ?: DASH, Modifier.fillMaxWidth().weight(1f), weight = FontWeight.Bold, maxSp = 44f)
    }
}

private fun cardinal(deg: Float): String {
    val names = listOf("S", "SV", "V", "JV", "J", "JZ", "Z", "SZ")
    return names[(((deg % 360f) + 360f) % 360f / 45f + 0.5f).toInt() % 8]
}

@Composable
fun CompassWidget(modifier: Modifier) {
    val look = LocalLook.current
    val fix by Gps.fix.collectAsState()
    val bearing = fix?.bearing ?: 0f
    WidgetCard("Kompas", modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(-bearing)) {
                    val r = minOf(size.width, size.height) / 2f - 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Palette.Text.copy(alpha = 0.12f), r, c, style = Stroke(3f))
                    for (i in 0 until 12) {
                        val a = Math.toRadians(i * 30.0)
                        val inner = if (i % 3 == 0) 0.78f else 0.88f
                        drawLine(
                            Palette.Text.copy(alpha = if (i % 3 == 0) 0.6f else 0.25f),
                            Offset(c.x + (r * inner * Math.sin(a)).toFloat(), c.y - (r * inner * Math.cos(a)).toFloat()),
                            Offset(c.x + (r * Math.sin(a)).toFloat(), c.y - (r * Math.cos(a)).toFloat()),
                            3f
                        )
                    }
                    val north = Path().apply {
                        moveTo(c.x, c.y - r * 0.7f)
                        lineTo(c.x - r * 0.14f, c.y)
                        lineTo(c.x + r * 0.14f, c.y)
                        close()
                    }
                    drawPath(north, Palette.Bad)
                    val south = Path().apply {
                        moveTo(c.x, c.y + r * 0.7f)
                        lineTo(c.x - r * 0.14f, c.y)
                        lineTo(c.x + r * 0.14f, c.y)
                        close()
                    }
                    drawPath(south, Palette.Text.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (fix?.bearing != null) "${bearing.roundToInt()}° ${cardinal(bearing)}" else DASH,
                    color = look.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
                Text(fix?.altitude?.let { "${it.roundToInt()} m n. m." } ?: "výška –", color = Palette.Text, fontSize = 15.sp, maxLines = 1)
                Text(fix?.speedKmh?.let { "${it.roundToInt()} km/h" } ?: "– km/h", color = Palette.Muted, fontSize = 15.sp, maxLines = 1)
                Text(fix?.accuracy?.let { "±${it.roundToInt()} m" } ?: "", color = Palette.Muted, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun GpsWidget(modifier: Modifier) {
    val fix by Gps.fix.collectAsState()
    val sats by Gps.satellites.collectAsState()
    val allowed by Gps.allowed.collectAsState()
    val now by rememberNow()
    val age = fix?.let { (System.currentTimeMillis() - it.time) / 1000 }
    val good = age != null && age < 5
    WidgetCard("GPS", modifier, action = {
        Text(if (good) "Fix" else "Bez fixu", color = if (good) Palette.Good else Palette.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            if (!allowed) {
                Text("Povol polohu v Nastavení", color = Palette.Amber, fontSize = 16.sp)
            }
            Text("${sats.used} z ${sats.visible} satelitů", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Row(Modifier.fillMaxWidth()) {
                SmallValue("Přesnost", fix?.accuracy?.let { "±${it.roundToInt()} m" } ?: DASH, Modifier.weight(1f))
                SmallValue("Výška", fix?.altitude?.let { "${it.roundToInt()} m" } ?: DASH, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                SmallValue("Rychlost", fix?.speedKmh?.let { "${it.roundToInt()} km/h" } ?: DASH, Modifier.weight(1f))
                SmallValue("Stáří", age?.let { "$it s" } ?: DASH, Modifier.weight(1f))
            }
        }
    }
    // Udržuje stáří polohy aktuální.
    now.second
}

@Composable
fun SmallValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Palette.Muted, fontSize = 13.sp, maxLines = 1)
        Text(value, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SpeedLimitWidget(modifier: Modifier, preview: Boolean) {
    DisposableEffect(Unit) {
        SpeedLimit.acquire()
        onDispose { SpeedLimit.release() }
    }
    val limit by SpeedLimit.state.collectAsState()
    val speed by Gps.speedKmh.collectAsState()
    val over = limit.kmh != null && (speed ?: 0) > (limit.kmh ?: 0) + 3
    WidgetCard(null, modifier, padding = 8.dp) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Box(
                    Modifier.size(side).clip(CircleShape).background(Palette.Bad),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.fillMaxSize().padding(side * 0.11f).clip(CircleShape)
                            .background(if (over) Color(0xFFFFE0E0) else Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        FitText(limit.kmh?.toString() ?: "--", Modifier.fillMaxSize().padding(side * 0.08f), color = Color.Black, weight = FontWeight.Bold, widthPerChar = 0.62f)
                    }
                }
            }
            Text(
                limit.road ?: if (limit.known) "" else "Neznámé omezení",
                color = Palette.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
