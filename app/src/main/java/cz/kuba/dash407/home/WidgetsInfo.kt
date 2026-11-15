package cz.kuba.dash407.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.data.DayForecast
import cz.kuba.dash407.data.Weather
import cz.kuba.dash407.ui.Czech
import cz.kuba.dash407.ui.FitText
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.RoundButton
import cz.kuba.dash407.ui.WidgetCard
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Aktuální čas, obnovovaný každou sekundu. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(initialValue = LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000L - System.currentTimeMillis() % 1000L)
    }
}

fun timeText(t: LocalDateTime, seconds: Boolean): String =
    t.format(DateTimeFormatter.ofPattern(if (seconds) "H:mm:ss" else "H:mm", Czech))

fun dateText(t: LocalDateTime): String = t.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Czech))
    .replaceFirstChar { it.uppercase() }

@Composable
fun ClockWidget(modifier: Modifier) {
    val look = LocalLook.current
    val now by rememberNow()
    WidgetCard(null, modifier, padding = 10.dp) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(timeText(now, look.seconds), Modifier.fillMaxWidth().weight(1f), weight = FontWeight.Bold, maxSp = 140f)
            Text(dateText(now), color = Palette.Muted, fontSize = 17.sp, maxLines = 1)
        }
    }
}

private fun weatherIcon(code: Int?, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    2 -> Icons.Filled.WbCloudy
    3, 45, 48 -> Icons.Filled.Cloud
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Bolt
    null -> Icons.Filled.Cloud
    else -> Icons.Filled.Grain
}

/** Barva ikony počasí: slunce žluté, mraky šedé, déšť a sníh modré, bouřka oranžová. */
private fun weatherTint(code: Int?, day: Boolean): Color = when (code) {
    0, 1 -> if (day) Color(0xFFFFD60A) else Color(0xFFB0BEC5)
    2, 3, 45, 48, null -> Color(0xFFC9C3C4)
    95, 96, 99 -> Color(0xFFFFB300)
    else -> Color(0xFF4FC3F7)
}

/**
 * Počasí: nahoře vpravo místo a obnovení, pod tím předpověď na pět dní
 * a dole velká ikona s aktuální teplotou.
 */
@Composable
fun WeatherWidget(modifier: Modifier, preview: Boolean) {
    DisposableEffect(Unit) {
        Weather.acquire()
        onDispose { Weather.release() }
    }
    val w by Weather.state.collectAsState()
    val place = w.place?.let { if (w.approximate) "$it (bez GPS)" else it }
    WidgetCard(null, modifier, padding = 14.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (place != null) {
                    Icon(Icons.Filled.LocationOn, null, tint = WeatherMuted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(place, color = WeatherMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(14.dp))
                }
                Icon(
                    Icons.Filled.Refresh, "Obnovit", tint = WeatherMuted,
                    modifier = Modifier.size(30.dp).clip(CircleShape).clickable(enabled = !preview) { Weather.refresh() }
                )
            }
            Spacer(Modifier.height(8.dp))
            ForecastRow(w.days)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val temp = w.tempC?.let { "${it.roundToInt()} °C" }
                if (temp == null && w.error != null) {
                    Text(w.error ?: "", color = Palette.Muted, fontSize = 16.sp, maxLines = 2)
                } else {
                    val text = temp ?: "– °C"
                    val iconSize = minOf(maxHeight * 0.8f, maxWidth * 0.3f, 150.dp)
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val byHeight = maxHeight.value * 0.82f
                    val byWidth = (maxWidth - iconSize - 28.dp).value / (text.length * 0.58f)
                    val sp = (minOf(byHeight, byWidth).coerceAtLeast(16f) / density.fontScale).coerceAtMost(130f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(weatherIcon(w.code, w.isDay), null, tint = weatherTint(w.code, w.isDay), modifier = Modifier.size(iconSize))
                        Spacer(Modifier.width(28.dp))
                        Text(text, color = Palette.Text, fontSize = sp.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** Místo a ikona obnovení: světle šedé. */
private val WeatherMuted: Color
    @Composable get() = if (Palette.lightState.value) Palette.Muted else Color(0xFFAEB7C4)

/** Denní předpověď: dnes a další dny, u každého ikona a nejvyšší / nejnižší teplota. */
@Composable
private fun ForecastRow(days: List<DayForecast>) {
    if (days.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = when {
            maxWidth < 230.dp -> 3
            maxWidth < 330.dp -> 4
            else -> 5
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.take(count).forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (i == 0) "Dnes" else d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Czech).replaceFirstChar { it.uppercase() },
                        color = WeatherMuted, fontSize = 17.sp, maxLines = 1
                    )
                    Icon(weatherIcon(d.code, true), null, tint = weatherTint(d.code, true), modifier = Modifier.size(34.dp))
                    Text(
                        (d.maxC?.let { "${it.roundToInt()}°" } ?: "–") + " / " + (d.minC?.let { "${it.roundToInt()}°" } ?: "–"),
                        color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Přehrávač: barevný přechod (nebo obal alba), uprostřed ♪ a název skladby,
 * dole předchozí – přehrát – další přes celou šířku.
 */
@Composable
fun MediaWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val m by NowPlaying.state.collectAsState()
    val musicPkg by Prefs.musicApp.collectAsState()
    val shape = RoundedCornerShape(look.corner)
    Box(modifier.fillMaxSize().clip(shape)) {
        val art = m.art
        if (art != null) {
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.8f)))))
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF3D8FC4), Color(0xFF7466A6), Color(0xFFB8735E), Color(0xFF1A1414)))
                )
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)))))
        }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .clickable(enabled = !preview) {
                    val pkg = m.packageName
                    if (!m.allowed) UiBus.open(Panel.SETTINGS)
                    else if (pkg != null) AppsRepo.launch(context, pkg)
                    else if (musicPkg.isNotBlank()) AppsRepo.launch(context, musicPkg)
                }
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            // 2.13: tři tlačítka se vejdou vedle sebe i v úzkém widgetu (velké + 2 × 0,86 = 2,72 × velké + 2 mezery 8 dp).
            val big = minOf(maxHeight * 0.36f, 76.dp, (maxWidth - 16.dp) / 2.72f).coerceAtLeast(32.dp)
            val small = big * 0.86f
            val titleSp = minOf(maxHeight.value * 0.13f, 32f).coerceAtLeast(16f)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1.1f))
                val title = when {
                    !m.allowed -> "Povol přístup k oznámením"
                    !m.hasSession -> "Nic nehraje"
                    else -> m.title ?: "Neznámá skladba"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size((titleSp * 1.1f).dp))
                    Text(title, color = Color.White, fontSize = titleSp.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                }
                val artist = m.artist
                if (m.hasSession && !artist.isNullOrBlank()) {
                    Text(artist, color = Color.White.copy(alpha = 0.85f), fontSize = (titleSp * 0.65f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TitleShadow)
                }
                Spacer(Modifier.weight(0.5f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Filled.SkipPrevious, "Předchozí", size = small) { if (!preview) NowPlaying.previous() }
                    RoundButton(
                        if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (m.playing) "Pozastavit" else "Přehrát", size = big, filled = true
                    ) {
                        if (!preview) {
                            if (!m.hasSession && musicPkg.isNotBlank()) AppsRepo.launch(context, musicPkg) else NowPlaying.playPause()
                        }
                    }
                    RoundButton(Icons.Filled.SkipNext, "Další", size = small) { if (!preview) NowPlaying.next() }
                }
                Spacer(Modifier.weight(0.15f))
            }
        }
    }
}

/** 2.13: stín pod názvem skladby, aby byl čitelný i přes světlý obal alba. */
private val TitleShadow = androidx.compose.ui.text.TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset(0f, 2f), 6f)
)

@Composable
fun DockWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val favorites by Prefs.favorites.collectAsState()
    val shown = favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    WidgetCard(null, modifier, padding = 8.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val slots = shown.size + 1
            val vertical = maxHeight > maxWidth * 1.4f
            val byMain = (if (vertical) maxHeight else maxWidth) / slots
            val cross = if (vertical) maxWidth else maxHeight
            val icon = minOf(byMain * 0.8f, cross * 0.8f, 84.dp)
            val content: @Composable () -> Unit = {
                if (shown.isEmpty()) {
                    Text(
                        "Dok je prázdný – klepni na tužku a vyber aplikace",
                        color = Palette.Muted, fontSize = 15.sp, maxLines = 2,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                for (app in shown) {
                    val bmp = app.icon
                    Box(
                        Modifier.size(icon).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                            .clickable(enabled = !preview) { AppsRepo.launch(context, app.packageName) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) Image(bmp, app.label, Modifier.size(icon * 0.72f))
                        else Text(app.label.take(2), color = Color.Black, fontSize = 18.sp)
                    }
                }
                Box(
                    Modifier.size(icon * 0.7f).clip(CircleShape)
                        .clickable(enabled = !preview) {
                            UiBus.pickerTab = 1
                            UiBus.open(Panel.PICKER)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (shown.isEmpty()) Icons.Filled.Add else Icons.Filled.Edit, "Upravit dok", tint = Palette.Muted, modifier = Modifier.size(icon * 0.4f))
                }
            }
            if (vertical) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) { content() }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { content() }
            }
        }
    }
}

/** Krátká hláška (např. „Na ploše není místo“). */
fun toast(context: android.content.Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
