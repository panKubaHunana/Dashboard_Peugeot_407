package cz.kuba.dash407.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.car.CarState
import cz.kuba.dash407.car.Doors
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.NowPlayingState
import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onOpenApps: () -> Unit, onOpenFuel: () -> Unit) {
    val car by CarRepo.state.collectAsState()
    val speed by Gps.speedKmh.collectAsState()
    val media by NowPlaying.state.collectAsState()
    val refuels by FuelLog.entries.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClockBar(car)
        Row(Modifier.weight(1.25f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedTile(speed, Modifier.weight(1f).fillMaxHeight())
            TripTile(car, Modifier.weight(1f).fillMaxHeight())
        }
        MediaTile(media, Modifier.weight(0.8f).fillMaxWidth())
        Row(Modifier.weight(1.1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DoorsTile(car, Modifier.weight(1f).fillMaxHeight())
            Tile(
                stringResource(R.string.home_fuel_title),
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).clickable { onOpenFuel() }
            ) {
                val stats = FuelLog.stats(refuels)
                Spacer(Modifier.height(6.dp))
                Reading(
                    stats.averageL100?.let { decimal(it) } ?: DASH,
                    stringResource(R.string.home_fuel_average)
                )
                Spacer(Modifier.height(6.dp))
                Reading(
                    stats.lastL100?.let { decimal(it) } ?: DASH,
                    stringResource(R.string.home_fuel_last)
                )
                if (refuels.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.home_fuel_empty), color = Palette.Muted, fontSize = 13.sp)
                }
            }
        }
        Dock(onOpenApps)
    }
}

@Composable
private fun ClockBar(car: CarState) {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                now.format(DateTimeFormatter.ofPattern("H:mm", Czech)),
                color = Palette.Text,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Czech)),
                color = Palette.Muted,
                fontSize = 18.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val volts = car.volts
            Text(
                if (volts != null) stringResource(R.string.home_volts, decimal(volts)) else stringResource(R.string.home_volts_none),
                color = if (volts != null && volts < 11.8) Palette.Bad else Palette.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(if (car.linked) R.string.home_car_linked else R.string.home_car_not_linked),
                color = if (car.linked) Palette.Good else Palette.Muted,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SpeedTile(speed: Int?, modifier: Modifier) {
    Tile(stringResource(R.string.home_speed_title), modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val stroke = 14.dp.toPx()
                val side = minOf(size.width, size.height) - stroke
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arc = Size(side, side)
                drawArc(Palette.TileHigh, 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val part = ((speed ?: 0).coerceIn(0, 200)) / 200f
                if (part > 0f) {
                    drawArc(
                        if ((speed ?: 0) > 135) Palette.Amber else Palette.Accent,
                        135f, 270f * part, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    speed?.toString() ?: DASH,
                    color = Palette.Text,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.unit_kmh), color = Palette.Muted, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TripTile(car: CarState, modifier: Modifier) {
    Tile(stringResource(R.string.home_trip_title), modifier) {
        Spacer(Modifier.height(6.dp))
        Reading(
            car.instantL100?.let { decimal(it) } ?: DASH,
            stringResource(R.string.home_trip_instant)
        )
        Spacer(Modifier.height(6.dp))
        Reading(
            car.rangeKm?.toString() ?: DASH,
            stringResource(R.string.home_trip_range),
            valueColor = if ((car.rangeKm ?: 999) < 80) Palette.Amber else Palette.Text
        )
        Spacer(Modifier.height(6.dp))
        Reading(
            car.trip1?.avgL100?.let { decimal(it) } ?: DASH,
            stringResource(R.string.home_trip_average)
        )
    }
}

@Composable
private fun MediaTile(media: NowPlayingState, modifier: Modifier) {
    Tile(stringResource(R.string.home_media_title), modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = when {
                    !media.allowed -> stringResource(R.string.home_media_not_allowed)
                    !media.hasSession -> stringResource(R.string.home_media_nothing)
                    else -> media.title ?: stringResource(R.string.home_media_unknown)
                }
                Text(
                    title,
                    color = Palette.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val artist = media.artist
                if (media.hasSession && !artist.isNullOrBlank()) {
                    Text(artist, color = Palette.Muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (media.hasSession) {
                IconButton(onClick = { NowPlaying.previous() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.SkipPrevious, stringResource(R.string.media_previous), Modifier.size(40.dp), tint = Palette.Text)
                }
                Surface(shape = CircleShape, color = Palette.Accent, modifier = Modifier.size(72.dp)) {
                    IconButton(onClick = { NowPlaying.playPause() }) {
                        Icon(
                            if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (media.playing) R.string.media_pause else R.string.media_play),
                            Modifier.size(44.dp),
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = { NowPlaying.next() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.SkipNext, stringResource(R.string.media_next), Modifier.size(40.dp), tint = Palette.Text)
                }
            }
        }
    }
}

@Composable
fun DoorsTile(car: CarState, modifier: Modifier) {
    Tile(stringResource(R.string.home_doors_title), modifier) {
        val doors = car.doors
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(0.55f).padding(vertical = 8.dp)) {
                drawCar(doors ?: Doors())
            }
            Spacer(Modifier.width(12.dp))
            Column {
                val text = when {
                    doors == null -> stringResource(R.string.home_doors_unknown)
                    doors.anyOpen -> stringResource(R.string.home_doors_open)
                    else -> stringResource(R.string.home_doors_closed)
                }
                Text(
                    text,
                    color = when {
                        doors == null -> Palette.Muted
                        doors.anyOpen -> Palette.Bad
                        else -> Palette.Good
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (car.handbrake == true) {
                    Text(stringResource(R.string.home_handbrake_on), color = Palette.Amber, fontSize = 15.sp)
                }
                if (car.warnings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_warnings_count, car.warnings.size),
                        color = Palette.Amber,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/** Auto při pohledu shora; otevřené dveře jsou červené. */
private fun DrawScope.drawCar(doors: Doors) {
    val w = size.width
    val h = size.height
    val bodyLeft = w * 0.2f
    val bodyWidth = w * 0.6f
    val closed = Palette.TileHigh
    val open = Palette.Bad
    drawRoundRect(
        color = Palette.Muted,
        topLeft = Offset(bodyLeft, 0f),
        size = Size(bodyWidth, h),
        cornerRadius = CornerRadius(bodyWidth * 0.35f),
        style = Stroke(3.dp.toPx())
    )
    // Kapota a páté dveře.
    drawRoundRect(if (doors.bonnet) open else closed, Offset(bodyLeft + bodyWidth * 0.15f, h * 0.04f), Size(bodyWidth * 0.7f, h * 0.16f), CornerRadius(12f))
    drawRoundRect(if (doors.tailgate) open else closed, Offset(bodyLeft + bodyWidth * 0.15f, h * 0.84f), Size(bodyWidth * 0.7f, h * 0.12f), CornerRadius(12f))
    // Boční dveře: levá strana vozu je na obrazovce vlevo.
    val doorW = w * 0.14f
    val doorH = h * 0.2f
    drawRoundRect(if (doors.frontLeft) open else closed, Offset(0f, h * 0.3f), Size(doorW, doorH), CornerRadius(8f))
    drawRoundRect(if (doors.frontRight) open else closed, Offset(w - doorW, h * 0.3f), Size(doorW, doorH), CornerRadius(8f))
    drawRoundRect(if (doors.rearLeft) open else closed, Offset(0f, h * 0.55f), Size(doorW, doorH), CornerRadius(8f))
    drawRoundRect(if (doors.rearRight) open else closed, Offset(w - doorW, h * 0.55f), Size(doorW, doorH), CornerRadius(8f))
}

@Composable
private fun Dock(onOpenApps: () -> Unit) {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val favorites by Prefs.favorites.collectAsState()
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth().height(96.dp)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shown = favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.home_dock_empty),
                    color = Palette.Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            }
            for (app in shown) {
                val icon = app.icon
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).clickable { AppsRepo.launch(context, app.packageName) },
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = app.label, modifier = Modifier.size(60.dp))
                    } else {
                        Text(app.label.take(2), color = Palette.Text, fontSize = 22.sp)
                    }
                }
            }
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).clickable { onOpenApps() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Apps, stringResource(R.string.tab_apps), Modifier.size(44.dp), tint = Palette.Accent)
            }
        }
    }
}
