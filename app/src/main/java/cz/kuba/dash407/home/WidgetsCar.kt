package cz.kuba.dash407.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.car.Doors
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.ui.DASH
import cz.kuba.dash407.ui.FitText
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.Reading
import cz.kuba.dash407.ui.WidgetCard
import cz.kuba.dash407.ui.decimal
import cz.kuba.dash407.ui.CarPicture

@Composable
fun CarComputerWidget(modifier: Modifier) {
    val car by CarRepo.state.collectAsState()
    WidgetCard("Palubní počítač", modifier, onClick = { UiBus.open(Panel.CAR) }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Reading(car.instantL100?.let { decimal(it) } ?: DASH, "okamžitá l/100 km")
            Reading(
                car.rangeKm?.toString() ?: DASH, "dojezd km",
                valueColor = if ((car.rangeKm ?: 999) < 80) Palette.Amber else Palette.Text
            )
            Reading(car.trip1?.avgL100?.let { decimal(it) } ?: DASH, "průměr l/100 km (Trip 1)")
            if (!car.linked) Text("Bez dat z auta", color = Palette.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun DoorsWidget(modifier: Modifier) {
    val car by CarRepo.state.collectAsState()
    val doors = car.doors
    WidgetCard("Dveře", modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CarPicture(doors ?: Doors(), Modifier.fillMaxHeight().padding(vertical = 4.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        doors == null -> "Čekám na data"
                        doors.anyOpen -> "Otevřeno"
                        else -> "Vše zavřeno"
                    },
                    color = when {
                        doors == null -> Palette.Muted
                        doors.anyOpen -> Palette.Bad
                        else -> Palette.Good
                    },
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (car.handbrake == true) Text("Ruční brzda zatažená", color = Palette.Amber, fontSize = 14.sp)
                if (car.warnings.isNotEmpty()) Text("Výstrahy vozu: ${car.warnings.size}", color = Palette.Amber, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun VoltageWidget(modifier: Modifier) {
    val car by CarRepo.state.collectAsState()
    val volts = car.volts
    WidgetCard("Napětí", modifier, onClick = { UiBus.open(Panel.CAR) }) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(
                volts?.let { decimal(it) + " V" } ?: "– V",
                Modifier.fillMaxWidth().weight(1f),
                color = if (volts != null && volts < 11.8) Palette.Bad else Palette.Text,
                weight = FontWeight.Bold, maxSp = 64f
            )
            Text(
                if (car.linked) "Auto připojeno" else "Bez dat z auta",
                color = if (car.linked) Palette.Good else Palette.Muted, fontSize = 13.sp, maxLines = 1
            )
        }
    }
}

@Composable
fun FuelWidget(modifier: Modifier, preview: Boolean) {
    val refuels by FuelLog.entries.collectAsState()
    val stats = FuelLog.stats(refuels)
    WidgetCard("Spotřeba", modifier, onClick = if (preview) null else ({ UiBus.open(Panel.FUEL) })) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Reading(stats.averageL100?.let { decimal(it) } ?: DASH, "průměr l/100", Modifier.weight(1f))
            Reading(stats.lastL100?.let { decimal(it) } ?: DASH, "poslední nádrž", Modifier.weight(1f))
        }
        if (refuels.isEmpty()) {
            Text("Klepni a zapiš tankování", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
    }
}

/** Nejnaléhavější termín ze servisní knížky. */
@Composable
fun ServiceWidget(modifier: Modifier, preview: Boolean = false) {
    val entries by cz.kuba.dash407.data.ServiceBook.entries.collectAsState()
    val intervals by cz.kuba.dash407.data.ServiceBook.intervals.collectAsState()
    val fuel by FuelLog.entries.collectAsState()
    val km = cz.kuba.dash407.data.ServiceBook.currentKm(fuel)
    // entries a intervals jen spouštějí překreslení po změně v knížce.
    val due = remember(entries, intervals, km) { cz.kuba.dash407.data.ServiceBook.mostUrgent(km) }
    WidgetCard("Servis", modifier, onClick = if (preview) null else ({ UiBus.open(Panel.SERVICE) })) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            if (due == null) {
                Text("Zapiš první servis", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Servisní knížka je prázdná", color = Palette.Muted, fontSize = 13.sp)
            } else {
                Text(due.kind.label, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    cz.kuba.dash407.ui.levelText(due.level), color = cz.kuba.dash407.ui.levelColor(due.level),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                val line = listOfNotNull(
                    due.remainingKm?.let { if (it >= 0) "zbývá ${cz.kuba.dash407.ui.kmText(it)}" else "o ${cz.kuba.dash407.ui.kmText(-it)} přes" },
                    due.remainingDays?.let { if (it >= 0) "za $it dní" else "o ${-it} dní přes" }
                ).joinToString("\n")
                if (line.isNotEmpty()) Text(line, color = Palette.Muted, fontSize = 13.sp)
            }
        }
    }
}

/** Čtyři zkratky na původní aplikace rádia. Nenalezená aplikace otevře výběr. */
@Composable
fun CarAppsWidget(modifier: Modifier, preview: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val look = cz.kuba.dash407.ui.LocalLook.current
    val apps by cz.kuba.dash407.data.AppsRepo.apps.collectAsState()
    // chosen jen spouští překreslení po ručním výběru aplikace.
    val chosen by cz.kuba.dash407.data.CarApps.chosen.collectAsState()
    val items = listOf(
        Triple("dsp", "DSP", androidx.compose.material.icons.Icons.Filled.GraphicEq),
        Triple("keys", "Volant", androidx.compose.material.icons.Icons.Filled.TouchApp),
        Triple("climate", "Klima", androidx.compose.material.icons.Icons.Filled.AcUnit),
        Triple("car", "Vůz", androidx.compose.material.icons.Icons.Filled.DirectionsCar)
    )
    WidgetCard(null, modifier, padding = 8.dp) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            for ((id, label, icon) in items) {
                val slot = cz.kuba.dash407.data.CarApps.slots.first { it.id == id }
                val app = remember(apps, chosen) { cz.kuba.dash407.data.CarApps.resolve(slot, apps, context.packageName) }
                Column(
                    Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .clickable(enabled = !preview) {
                            if (app != null) cz.kuba.dash407.data.AppsRepo.launch(context, app.packageName) else UiBus.open(Panel.CARAPPS)
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.Icon(icon, label, tint = if (app != null) look.accent else Palette.Muted, modifier = Modifier.size(34.dp))
                    Text(label, color = Palette.Text, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}
