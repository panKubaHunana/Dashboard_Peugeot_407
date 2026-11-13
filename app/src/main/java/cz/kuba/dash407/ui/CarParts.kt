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
fun DrawScope.drawCar(doors: Doors) {
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

