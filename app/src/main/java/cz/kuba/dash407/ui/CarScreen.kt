package cz.kuba.dash407.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.car.TripPage

@Composable
fun CarScreen() {
    val car by CarRepo.state.collectAsState()
    val raw by CarRepo.raw.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(0.dp)) }
        item {
            Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TripPageTile(stringResource(R.string.car_trip1), car.trip1, Modifier.weight(1f))
                TripPageTile(stringResource(R.string.car_trip2), car.trip2, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DoorsTile(car, Modifier.weight(1f))
                Tile(stringResource(R.string.car_status_title), Modifier.weight(1f)) {
                    Spacer(Modifier.height(8.dp))
                    StatusLine(stringResource(R.string.car_volts), car.volts?.let { stringResource(R.string.home_volts, decimal(it)) })
                    StatusLine(stringResource(R.string.car_lights), car.headlights?.let { stringResource(if (it) R.string.state_on else R.string.state_off) })
                    StatusLine(stringResource(R.string.car_handbrake), car.handbrake?.let { stringResource(if (it) R.string.state_applied else R.string.state_released) })
                    StatusLine(stringResource(R.string.car_range), car.rangeKm?.let { stringResource(R.string.value_km, it) })
                    StatusLine(stringResource(R.string.car_instant), car.instantL100?.let { stringResource(R.string.value_l100, decimal(it)) })
                }
            }
        }
        item {
            Tile(stringResource(R.string.car_warnings_title), Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                if (car.warnings.isEmpty()) {
                    Text(stringResource(R.string.car_warnings_none), color = Palette.Muted, fontSize = 16.sp)
                } else {
                    for (code in car.warnings) {
                        Text(
                            stringResource(R.string.car_warning_code, String.format("%02X", code)),
                            color = Palette.Amber,
                            fontSize = 17.sp
                        )
                    }
                    Text(stringResource(R.string.car_warnings_hint), color = Palette.Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Tile(stringResource(R.string.signals_title), Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when {
                            raw.isNotEmpty() -> R.string.signals_hint
                            car.linked -> R.string.signals_waiting
                            else -> R.string.signals_no_service
                        }
                    ),
                    color = Palette.Muted,
                    fontSize = 14.sp
                )
            }
        }
        items(raw, key = { it.key }) { entry ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    stringResource(R.string.signals_entry, entry.key, entry.count),
                    color = Palette.Accent,
                    fontSize = 14.sp
                )
                Text(entry.hex, color = Palette.Text, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TripPageTile(title: String, page: TripPage?, modifier: Modifier) {
    Tile(title, modifier) {
        Spacer(Modifier.height(6.dp))
        Reading(page?.avgL100?.let { decimal(it) } ?: DASH, stringResource(R.string.car_trip_average))
        Spacer(Modifier.height(6.dp))
        Reading(page?.distanceKm?.toString() ?: DASH, stringResource(R.string.car_trip_distance))
        Spacer(Modifier.height(6.dp))
        Reading(page?.avgKmh?.toString() ?: DASH, stringResource(R.string.car_trip_speed))
    }
}

@Composable
private fun StatusLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Palette.Muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value ?: DASH, color = Palette.Text, fontSize = 16.sp)
    }
}
