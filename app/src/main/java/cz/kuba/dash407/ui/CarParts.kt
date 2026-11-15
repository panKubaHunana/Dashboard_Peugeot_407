package cz.kuba.dash407.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
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
fun DoorsTile(car: CarState, modifier: Modifier) {
    Tile(stringResource(R.string.home_doors_title), modifier) {
        val doors = car.doors
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CarPicture(doors ?: Doors(), Modifier.fillMaxHeight().padding(vertical = 8.dp))
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

/** Poměr šířky a výšky obrázku auta (car_407sw.png). */
private const val CAR_ASPECT = 0.451f

/** Peugeot 407 SW při pohledu shora; otevřené dveře, kapota a páté dveře svítí červeně. */
@Composable
fun CarPicture(doors: Doors, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(0.68f)) {
        Image(
            painterResource(R.drawable.car_407sw), "Peugeot 407 SW shora",
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit
        )
        Canvas(Modifier.fillMaxSize()) { drawCar(doors) }
    }
}

/** Značky dveří nad obrázkem auta. Levá strana vozu je na obrazovce vlevo. */
fun DrawScope.drawCar(doors: Doors) {
    val w = size.width
    val h = size.height
    val bw = minOf(h * CAR_ASPECT, w * 0.7f)
    val left = (w - bw) / 2f
    val red = Palette.Bad
    val outline = Palette.Muted.copy(alpha = 0.7f)
    val line = Stroke(2.dp.toPx())
    // Kapota a páté dveře: jen při otevření, červený nádech přes karoserii.
    if (doors.bonnet) drawRoundRect(red.copy(alpha = 0.45f), Offset(left + bw * 0.1f, h * 0.02f), Size(bw * 0.8f, h * 0.2f), CornerRadius(14f))
    if (doors.tailgate) drawRoundRect(red.copy(alpha = 0.45f), Offset(left + bw * 0.12f, h * 0.8f), Size(bw * 0.76f, h * 0.18f), CornerRadius(14f))
    val doorW = w * 0.13f
    val doorH = h * 0.2f
    fun door(open: Boolean, x: Float, y: Float, bodyX: Float) {
        val tl = Offset(x, y)
        val sz = Size(doorW, doorH)
        if (open) drawRoundRect(red.copy(alpha = 0.75f), tl, sz, CornerRadius(8f))
        drawRoundRect(if (open) red else outline, tl, sz, CornerRadius(8f), style = line)
        if (open) drawRoundRect(red.copy(alpha = 0.35f), Offset(bodyX, y), Size(bw * 0.3f, doorH), CornerRadius(8f))
    }
    val rightBody = left + bw * 0.7f
    door(doors.frontLeft, 0f, h * 0.3f, left)
    door(doors.frontRight, w - doorW, h * 0.3f, rightBody)
    door(doors.rearLeft, 0f, h * 0.55f, left)
    door(doors.rearRight, w - doorW, h * 0.55f, rightBody)
}
