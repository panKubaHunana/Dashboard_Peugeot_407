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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
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

@Composable
fun WeatherWidget(modifier: Modifier, preview: Boolean) {
    val look = LocalLook.current
    DisposableEffect(Unit) {
        Weather.acquire()
        onDispose { Weather.release() }
    }
    val w by Weather.state.collectAsState()
    WidgetCard("Počasí", modifier, action = {
        Icon(
            Icons.Filled.Refresh, "Obnovit", tint = Palette.Muted,
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable(enabled = !preview) { Weather.refresh() }
        )
    }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(w.code, w.isDay), null, tint = look.accent, modifier = Modifier.fillMaxHeight(0.7f).width(64.dp))
                Spacer(Modifier.width(8.dp))
                FitText(
                    w.tempC?.let { "${it.roundToInt()} °C" } ?: "– °C",
                    Modifier.weight(1f).fillMaxHeight(),
                    weight = FontWeight.Bold,
                    align = androidx.compose.ui.text.style.TextAlign.Start
                )
            }
            Text(
                if (w.error != null && w.tempC == null) w.error ?: "" else Weather.describe(w.code),
                color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            val parts = ArrayList<String>()
            w.feelsC?.let { parts.add("Pocitově ${it.roundToInt()}°") }
            w.windKmh?.let { parts.add("Vítr ${it.roundToInt()} km/h") }
            w.humidity?.let { parts.add("Vlhkost $it %") }
            Text(parts.joinToString(" · "), color = Palette.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MediaWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val m by NowPlaying.state.collectAsState()
    val shape = RoundedCornerShape(look.corner)
    Box(modifier.fillMaxSize().clip(shape)) {
        val art = m.art
        if (art != null) {
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(look.accent, Color(0xFF8E7CC3), Color(0xFFE0876A), Color(0xFF2B2F3A)))
                )
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.72f)))))
        Column(
            Modifier.fillMaxSize()
                .clickable(enabled = !preview) {
                    val pkg = m.packageName
                    if (!m.allowed) UiBus.open(Panel.SETTINGS)
                    else if (pkg != null) AppsRepo.launch(context, pkg)
                }
                .padding(14.dp)
        ) {
            if (look.titles) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PRÁVĚ HRAJE", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            val title = when {
                !m.allowed -> "Povol přístup k oznámením"
                !m.hasSession -> "Nic nehraje"
                else -> m.title ?: "Neznámá skladba"
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val artist = m.artist
            if (m.hasSession && !artist.isNullOrBlank()) {
                Text(artist, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.SkipPrevious, "Předchozí", size = 42.dp) { if (!preview) NowPlaying.previous() }
                RoundButton(
                    if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (m.playing) "Pozastavit" else "Přehrát", size = 50.dp, filled = true
                ) { if (!preview) NowPlaying.playPause() }
                RoundButton(Icons.Filled.SkipNext, "Další", size = 42.dp) { if (!preview) NowPlaying.next() }
            }
        }
    }
}

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
