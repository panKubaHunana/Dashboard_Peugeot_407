package cz.kuba.dash407.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Interval
import cz.kuba.dash407.data.Level
import cz.kuba.dash407.data.ServiceBook
import cz.kuba.dash407.data.ServiceKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val dayInput = DateTimeFormatter.ofPattern("d.M.yyyy")

fun levelColor(level: Level): Color = when (level) {
    Level.OVERDUE -> Palette.Bad
    Level.SOON -> Palette.Amber
    Level.OK -> Palette.Good
    Level.UNKNOWN -> Palette.Muted
}

fun levelText(level: Level): String = when (level) {
    Level.OVERDUE -> "Po termínu"
    Level.SOON -> "Brzy"
    Level.OK -> "V pořádku"
    Level.UNKNOWN -> "Bez záznamu"
}

fun kmText(km: Int): String = String.format(java.util.Locale("cs", "CZ"), "%,d", km).replace(' ', ' ') + " km"

/** Servisní knížka: údaje o voze, přehled servisních úkonů a historie. */
@Composable
fun ServiceScreen() {
    val car by ServiceBook.car.collectAsState()
    val entries by ServiceBook.entries.collectAsState()
    val intervals by ServiceBook.intervals.collectAsState()
    val fuel by FuelLog.entries.collectAsState()
    val currentKm = ServiceBook.currentKm(fuel)
    var plate by remember { mutableStateOf(car.plate) }
    var vin by remember { mutableStateOf(car.vin) }
    var engine by remember { mutableStateOf(car.engine) }
    var odo by remember { mutableStateOf(car.odoKm?.toString() ?: "") }
    var adding by remember { mutableStateOf<ServiceKind?>(null) }
    var editing by remember { mutableStateOf<ServiceKind?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceCard("Vůz", "") {
            OutlinedTextField(
                value = plate, onValueChange = { v -> plate = v.uppercase().filter { it.isLetterOrDigit() || it == ' ' }.take(10); ServiceBook.setCar { it.copy(plate = plate.trim()) } },
                singleLine = true, label = { Text("SPZ") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val problem = ServiceBook.vinProblem(vin)
            OutlinedTextField(
                value = vin,
                onValueChange = { v -> vin = v.uppercase().filter { it.isLetterOrDigit() }.take(17); ServiceBook.setCar { it.copy(vin = vin) } },
                singleLine = true, label = { Text("VIN") }, isError = problem != null,
                supportingText = { Text(problem ?: ServiceBook.makerOf(vin)?.let { "Výrobce podle VIN: $it" } ?: "17 znaků, najdeš ho v malém techničáku") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = engine, onValueChange = { v -> engine = v.take(40); ServiceBook.setCar { it.copy(engine = engine) } },
                singleLine = true, label = { Text("Motorizace") }, placeholder = { Text("např. 2.0 HDi, 100 kW") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = odo, onValueChange = { v -> odo = v.filter { it.isDigit() }.take(7); ServiceBook.setOdometer(odo.toIntOrNull()) },
                singleLine = true, label = { Text("Stav tachometru (km)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Počítá se vyšší hodnota z tohoto pole, tankování a servisních záznamů: ${currentKm?.let { kmText(it) } ?: "zatím neznámý"}") }
            )
        }

        for (kind in ServiceKind.values()) {
            val d = ServiceBook.due(kind, currentKm)
            val iv = intervals[kind] ?: Interval(kind.defKm, kind.defMonths)
            ServiceCard(kind.label, "") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(levelColor(d.level)))
                    Spacer(Modifier.width(8.dp))
                    Text(levelText(d.level), color = levelColor(d.level), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                StatRow("Poslední", d.last?.let { e -> ServiceBook.dateOf(e.dateMs).format(ServiceBook.dateFormat) + (e.km?.let { " · ${kmText(it)}" } ?: "") } ?: "bez záznamu")
                StatRow(
                    "Další",
                    when {
                        d.last == null -> "po prvním záznamu"
                        d.nextDate == null && d.nextKm == null -> "nesledováno"
                        else -> listOfNotNull(d.nextDate?.format(ServiceBook.dateFormat), d.nextKm?.let { kmText(it) }).joinToString(" · ")
                    }
                )
                val remain = listOfNotNull(
                    d.remainingKm?.let { if (it >= 0) "zbývá ${kmText(it)}" else "o ${kmText(-it)} přes" },
                    d.remainingDays?.let { if (it >= 0) "za $it dní" else "o ${-it} dní přes" }
                ).joinToString(" · ")
                if (remain.isNotEmpty()) Text(remain, color = Palette.Muted, fontSize = 14.sp)
                Text(
                    "Interval: " + listOfNotNull(
                        iv.km.takeIf { it > 0 }?.let { kmText(it) }, iv.months.takeIf { it > 0 }?.let { "$it měs." }
                    ).joinToString(" nebo ").ifEmpty { "nesledováno" },
                    color = Palette.Muted, fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { adding = kind }, shape = RoundedCornerShape(16.dp)) { Text("Zapsat servis", fontSize = 16.sp) }
                    OutlinedButton(onClick = { editing = kind }, shape = RoundedCornerShape(16.dp)) { Text("Interval", fontSize = 16.sp) }
                }
            }
        }

        ServiceCard("Historie", if (entries.isEmpty()) "Zatím nic nezapsáno." else "") {
            for (e in entries) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.kind.label, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                ServiceBook.dateOf(e.dateMs).format(ServiceBook.dateFormat), e.km?.let { kmText(it) },
                                e.price?.let { String.format(java.util.Locale("cs", "CZ"), "%.0f Kč", it) }
                            ).joinToString(" · "),
                            color = Palette.Muted, fontSize = 14.sp
                        )
                        if (e.note.isNotEmpty()) Text(e.note, color = Palette.Muted, fontSize = 14.sp)
                    }
                    IconButton(onClick = { ServiceBook.remove(e.id) }) { Icon(Icons.Filled.Delete, "Smazat záznam", tint = Palette.Muted) }
                }
            }
        }
        Text(
            "Intervaly jsou výchozí odhad. Skutečné lhůty pro tvou motorizaci najdeš v servisní knížce vozu a podle nich je nastav tlačítkem Interval.",
            color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
    }

    adding?.let { kind -> AddDialog(kind, currentKm) { adding = null } }
    editing?.let { kind -> IntervalDialog(kind, intervals[kind] ?: Interval(kind.defKm, kind.defMonths)) { editing = null } }
}

@Composable
private fun AddDialog(kind: ServiceKind, currentKm: Int?, onClose: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().format(dayInput)) }
    var km by remember { mutableStateOf(currentKm?.toString() ?: "") }
    var price by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = try { LocalDate.parse(date.trim(), dayInput) } catch (e: DateTimeParseException) { null }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(kind.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) }, singleLine = true,
                    label = { Text("Datum (d.M.rrrr)") }, isError = parsed == null, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = km, onValueChange = { km = it.filter { c -> c.isDigit() }.take(7) }, singleLine = true,
                    label = { Text("Stav tachometru (km)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) }, singleLine = true,
                    label = { Text("Cena (Kč), nepovinné") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, singleLine = true,
                    label = { Text("Poznámka, nepovinné") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                ServiceBook.add(kind, parsed!!, km.toIntOrNull(), price.replace(',', '.').toDoubleOrNull(), note)
                onClose()
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zrušit") } }
    )
}

@Composable
private fun IntervalDialog(kind: ServiceKind, current: Interval, onClose: () -> Unit) {
    var km by remember { mutableStateOf(current.km.toString()) }
    var months by remember { mutableStateOf(current.months.toString()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Interval: ${kind.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zadej 0, pokud se to má nesledovat. Platí, co nastane dřív.", color = Palette.Muted, fontSize = 14.sp)
                OutlinedTextField(
                    value = km, onValueChange = { km = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true,
                    label = { Text("Kilometry") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = months, onValueChange = { months = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true,
                    label = { Text("Měsíce") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ServiceBook.setInterval(kind, Interval(km.toIntOrNull() ?: 0, months.toIntOrNull() ?: 0))
                onClose()
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zrušit") } }
    )
}

@Composable
private fun ServiceCard(title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (text.isNotEmpty()) Text(text, color = Palette.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
