package cz.kuba.dash407.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.DriveLog
import cz.kuba.dash407.data.DriveRecord
import cz.kuba.dash407.data.DriveTracks
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.GeoPt
import cz.kuba.dash407.data.Odometer
import cz.kuba.dash407.data.Refuel
import cz.kuba.dash407.data.Trip
import cz.kuba.dash407.home.Panel
import cz.kuba.dash407.home.UiBus
import cz.kuba.dash407.home.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Období, které se v knize jízd ukáže, na mapě vykreslí a vyexportuje. */
private enum class Range(val label: String, val file: String) {
    TODAY("Dnes", "dnes"), WEEK("Týden", "tyden"), MONTH("Měsíc", "mesic"), YEAR("Rok", "rok"), ALL("Vše", "vse");

    /** Začátek období v ms (0 = bez omezení). */
    fun from(): Long {
        val today = LocalDate.now()
        val day = when (this) {
            TODAY -> today
            WEEK -> today.with(java.time.DayOfWeek.MONDAY)
            MONTH -> today.withDayOfMonth(1)
            YEAR -> today.withDayOfYear(1)
            ALL -> return 0L
        }
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

private enum class Tab(val label: String) { DRIVES("Jízdy"), STATS("Statistika"), HEAT("Teplotní mapa") }
private enum class HeatMode(val label: String) { ROUTES("Trasy"), ENDS("Starty a cíle"), FUEL("Tankování") }

private val DATE = DateTimeFormatter.ofPattern("EEE d. M.", Czech)
private val TIME = DateTimeFormatter.ofPattern("H:mm", Czech)
private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
private fun km(m: Double) = decimal(m / 1000.0)
private fun hm(ms: Long): String {
    val min = ms / 60_000
    return String.format(Czech, "%d:%02d", min / 60, min % 60)
}
private fun odo(v: Int?) = v?.let { String.format(Czech, "%,d", it).replace(',', ' ') } ?: DASH

/** Řádek seznamu: jízda nebo tankování. */
private sealed class Entry(val at: Long) {
    class Drive(val r: DriveRecord) : Entry(r.start)
    class Fuel(val f: Refuel) : Entry(f.at)
}

/**
 * Kniha jízd: jízdy a tankování v jednom seznamu, statistika po dnech až rocích,
 * teplotní mapa a export do Excelu. Klepnutím na jízdu se otevře detail a trasa na mapě.
 */
@Composable
fun LogbookScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by DriveLog.entries.collectAsState()
    val refuels by FuelLog.entries.collectAsState()
    val t by Trip.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.DRIVES) }
    var range by remember { mutableStateOf(Range.MONTH) }
    var detail by remember { mutableStateOf<DriveRecord?>(null) }
    var route by remember { mutableStateOf<DriveRecord?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var editOdo by remember { mutableStateOf(false) }
    val from = range.from()
    val list = all.filter { it.start >= from }
    val fuelList = refuels.filter { it.at >= from }
    val avgL100 = FuelLog.stats(refuels).averageL100

    fun export() {
        if (list.isEmpty() && fuelList.isEmpty()) { toast(context, "V tomto období nejsou žádné jízdy"); return }
        exporting = true
        val name = "Kniha_jizd_${LocalDate.now()}_${range.file}.xlsx"
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { DriveLog.exportXlsx(context, list, fuelList, name) }
                toast(context, "Uloženo do Stažené soubory/Dash407/$name")
                val send = Intent(Intent.ACTION_SEND)
                    .setType(DriveLog.XLSX_MIME)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "Kniha jízd – ${range.label.lowercase(Locale("cs"))}")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(Intent.createChooser(send, "Odeslat knihu jízd").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // Rádio nemá aplikaci ke sdílení – soubor zůstává ve Stažených souborech.
                }
            } catch (e: Exception) {
                toast(context, "Export se nepovedl: ${e.message ?: "neznámá chyba"}")
            } finally {
                exporting = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (x in Tab.values()) Pill(x.label, x == tab) { tab = x }
                Spacer(Modifier.weight(1f))
                Button(onClick = { export() }, enabled = !exporting, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (exporting) "Ukládám…" else "Excel", fontSize = 16.sp)
                }
            }
            if (tab != Tab.STATS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in Range.values()) Pill(r.label, r == range) { range = r }
                }
            }
            when (tab) {
                Tab.DRIVES -> DrivesTab(list, fuelList, t, avgL100, onOdo = { editOdo = true }, onOpen = { detail = it })
                Tab.STATS -> StatsTab(all)
                Tab.HEAT -> HeatTab(list, fuelList)
            }
        }
        route?.let { r -> RouteOverlay(r) { route = null } }
    }

    detail?.let { r -> DriveDialog(r, avgL100, refuels, onRoute = { route = r; detail = null }) { detail = null } }
    if (editOdo) OdometerDialog { editOdo = false }
}

// --- Jízdy ---------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DrivesTab(
    list: List<DriveRecord>, fuelList: List<Refuel>, t: cz.kuba.dash407.data.TripStats, avgL100: Double?,
    onOdo: () -> Unit, onOpen: (DriveRecord) -> Unit
) {
    val odoRef by Odometer.ref.collectAsState()
    Surface(shape = RoundedCornerShape(20.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Reading(list.size.toString(), "jízd", Modifier.weight(1f))
                Reading(km(list.sumOf { it.distanceM }), "km celkem", Modifier.weight(1f))
                Reading(hm(list.sumOf { it.end - it.start }), "čas jízdy", Modifier.weight(1f))
                val litres = avgL100?.let { a -> list.sumOf { it.distanceM } / 1000.0 * a / 100.0 }
                Reading(litres?.let { decimal(it) } ?: DASH, "litrů (odhad)", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tachometr ≈ ${odo(Odometer.km())} km", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val ref = odoRef
                    Text(
                        if (ref == null) "Zadej stav tachometru, nebo zapiš tankování – pak se počítá sám."
                        else "Podle: ${ref.source} ${DateTimeFormatter.ofPattern("d. M. yyyy", Czech).format(local(ref.at))} (${odo(ref.km)} km) + km z GPS",
                        color = Palette.Muted, fontSize = 13.sp
                    )
                }
                OutlinedButton(onClick = onOdo, shape = RoundedCornerShape(14.dp)) { Text("Zadat stav") }
            }
        }
    }
    if (t.active) {
        Surface(shape = RoundedCornerShape(16.dp), color = Palette.Good.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Právě probíhá jízda od ${TIME.format(local(t.startedAt))} · ${km(t.distanceM)} km. " +
                    "Zapíše se po zastavení na víc než 5 minut (nebo podržením widgetu).",
                color = Palette.Text, fontSize = 15.sp, modifier = Modifier.padding(12.dp)
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Head("Datum", 1.1f); Head("Čas", 1.2f); Head("Odkud → Kam", 2.8f); Head("Tachometr", 1.2f, true); Head("km", 0.8f, true); Head("Ø / max", 1.1f, true)
    }
    val entries = (list.map { Entry.Drive(it) } + fuelList.map { Entry.Fuel(it) }).sortedByDescending { it.at }
    if (entries.isEmpty()) {
        Text(
            "Zatím tu nejsou žádné jízdy. Jízda začne rozjezdem a zapíše se, když auto stojí déle než 5 minut " +
                "(kratší přejezdy pod 300 m se nezapisují).",
            color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(12.dp)
        )
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries, key = { e -> if (e is Entry.Drive) "d${e.r.id}" else "f${(e as Entry.Fuel).f.id}" }) { e ->
            when (e) {
                is Entry.Drive -> {
                    val r = e.r
                    Surface(shape = RoundedCornerShape(14.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth().clickable { onOpen(r) }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cell(DATE.format(local(r.start)), 1.1f)
                            Cell("${TIME.format(local(r.start))}–${TIME.format(local(r.end))}", 1.2f)
                            Cell("${r.from ?: "…"} → ${r.to ?: "…"}", 2.8f, lines = 2)
                            Cell(odo(r.odoEnd), 1.2f, end = true)
                            Cell(km(r.distanceM), 0.8f, end = true, bold = true)
                            Cell("%.0f / %.0f".format(r.avgKmh, r.maxKmh), 1.1f, end = true)
                        }
                    }
                }
                is Entry.Fuel -> {
                    val f = e.f
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = Palette.Amber.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth().clickable { UiBus.open(Panel.FUEL) }
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cell(DATE.format(local(f.at)), 1.1f)
                            Cell(TIME.format(local(f.at)), 1.2f)
                            Row(Modifier.weight(2.8f).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalGasStation, null, tint = Palette.Amber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    (f.station ?: "Tankování") + " · ${decimal(f.litres)} l · ${decimal(f.price, 0)} Kč" + if (f.full) " · plná" else "",
                                    color = Palette.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Cell(odo(f.odometerKm), 1.2f, end = true)
                            Cell("", 0.8f)
                            Cell(if (f.litres > 0) decimal(f.price / f.litres, 2) + " Kč/l" else "", 1.1f, end = true)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

// --- Statistika ----------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StatsTab(all: List<DriveRecord>) {
    var period by remember { mutableStateOf(DriveLog.Period.DAY) }
    // Rychlý přehled: dnes, tento týden, tento měsíc, letos.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in listOf(Range.TODAY, Range.WEEK, Range.MONTH, Range.YEAR)) {
            val l = all.filter { it.start >= r.from() }
            val title = when (r) { Range.TODAY -> "Dnes"; Range.WEEK -> "Tento týden"; Range.MONTH -> "Tento měsíc"; else -> "Letos" }
            Surface(shape = RoundedCornerShape(18.dp), color = Palette.Tile, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text(title, color = Palette.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${km(l.sumOf { it.distanceM })} km", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${hm(l.sumOf { it.end - it.start })} h · ${l.size} jízd", color = Palette.Muted, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in DriveLog.Period.values()) Pill(p.label, p == period) { period = p }
    }
    val rows = DriveLog.stats(all, period)
    val maxKm = rows.maxOfOrNull { it.distanceM }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Head("Období", 3f); Head("Jízd", 0.7f, true); Head("Ujeto km", 1.1f, true); Head("Čas jízdy", 1.1f, true); Head("V pohybu", 1.1f, true)
    }
    if (rows.isEmpty()) Text("Zatím žádné jízdy.", color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows, key = { it.key }) { x ->
            Surface(shape = RoundedCornerShape(14.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(x.label, 3f)
                        Cell(x.drives.toString(), 0.7f, end = true)
                        Cell(km(x.distanceM), 1.1f, end = true, bold = true)
                        Cell(hm(x.driveMs), 1.1f, end = true)
                        Cell(hm(x.movingMs), 1.1f, end = true)
                    }
                    // Pruh: ujetá vzdálenost v porovnání s nejdelším obdobím v seznamu.
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp, start = 3.dp, end = 3.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Text.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxWidth((x.distanceM / maxKm).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(LocalLook.current.accent))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

// --- Teplotní mapa -------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.HeatTab(list: List<DriveRecord>, fuelList: List<Refuel>) {
    var mode by remember { mutableStateOf(HeatMode.ROUTES) }
    val camera = remember { MapCamera() }
    val points by produceState(initialValue = emptyList<GeoPt>(), list, fuelList, mode) {
        value = withContext(Dispatchers.IO) {
            when (mode) {
                HeatMode.ROUTES -> list.flatMap { r -> DriveTracks.load(r.id).ifEmpty { listOf(GeoPt(r.startLat, r.startLon), GeoPt(r.endLat, r.endLon)) } }
                HeatMode.ENDS -> list.flatMap { listOf(GeoPt(it.startLat, it.startLon), GeoPt(it.endLat, it.endLon)) }
                HeatMode.FUEL -> fuelList.mapNotNull { f -> if (f.lat != null && f.lon != null) GeoPt(f.lat, f.lon) else null }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in HeatMode.values()) Pill(m.label, m == mode) { mode = m }
        Spacer(Modifier.width(8.dp))
        Text("méně", color = Palette.Muted, fontSize = 13.sp)
        Box(
            Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF40C0FF), Color(0xFF3DDC84), Color(0xFFFFD600), Color(0xFFFF3D00)))
            )
        )
        Text("více", color = Palette.Muted, fontSize = 13.sp)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp))) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val tile = tilePx()
        LaunchedEffect(points) { if (points.isNotEmpty()) camera.fit(points, w, h, tile) }
        HeatMap(camera, points, radiusCells = if (mode == HeatMode.ROUTES) 2 else 5, modifier = Modifier.fillMaxSize())
        if (points.isEmpty()) {
            Text(
                if (mode == HeatMode.FUEL) "Tankování s polohou zatím není – poloha se ukládá u nových tankování."
                else "Pro toto období zatím nejsou žádné jízdy.",
                color = Color.White, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }
    }
}

// --- Trasa jedné jízdy ---------------------------------------------------------------------------

@Composable
private fun RouteOverlay(r: DriveRecord, onClose: () -> Unit) {
    val look = LocalLook.current
    val camera = remember { MapCamera() }
    val pts by produceState(initialValue = emptyList<GeoPt>(), r.id) {
        value = withContext(Dispatchers.IO) { DriveTracks.load(r.id) }.ifEmpty { listOf(GeoPt(r.startLat, r.startLon), GeoPt(r.endLat, r.endLon)) }
    }
    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${DateTimeFormatter.ofPattern("d. M. yyyy", Czech).format(local(r.start))}  ${TIME.format(local(r.start))}–${TIME.format(local(r.end))} · ${km(r.distanceM)} km",
                    color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Text("${r.from ?: "start"} → ${r.to ?: "cíl"}", color = Palette.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundButton(Icons.Filled.Close, "Zavřít", size = 48.dp) { onClose() }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            val tile = tilePx()
            LaunchedEffect(pts) { camera.fit(pts, w, h, tile) }
            TrackMap(camera, Modifier.fillMaxSize()) { project, _ ->
                if (pts.size > 1) {
                    val path = Path()
                    pts.forEachIndexed { i, p ->
                        val o = project(p.lat, p.lon)
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, Color(0xFF0D2A5C), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, look.accent, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                val s = project(r.startLat, r.startLon)
                val e = project(r.endLat, r.endLon)
                drawCircle(Color.White, 10.dp.toPx(), s); drawCircle(Palette.Good, 7.dp.toPx(), s)
                drawCircle(Color.White, 10.dp.toPx(), e); drawCircle(Palette.Bad, 7.dp.toPx(), e)
            }
        }
    }
}

// --- Pomocné prvky -------------------------------------------------------------------------------

@Composable
private fun RowScope.Head(text: String, w: Float, end: Boolean = false) {
    Text(
        text, color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(w).padding(horizontal = 3.dp)
    )
}

@Composable
private fun RowScope.Cell(text: String, w: Float, end: Boolean = false, bold: Boolean = false, lines: Int = 1) {
    Text(
        text, color = Palette.Text, fontSize = 15.sp, maxLines = lines, overflow = TextOverflow.Ellipsis,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(w).padding(horizontal = 3.dp)
    )
}

/** Ruční zadání stavu tachometru: od něj se dál přičítají km z GPS. */
@Composable
private fun OdometerDialog(onClose: () -> Unit) {
    var text by remember { mutableStateOf(Odometer.km()?.toString() ?: "") }
    val value = parseNumber(text)?.toInt()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Stav tachometru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Opiš kilometry z přístrojového štítu. Dál se k nim přičítají km z GPS; každé tankování se stavem tachometru odhad zase opraví.", fontSize = 15.sp)
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("km") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = { value?.let { Odometer.calibrate(it, "ručně") }; onClose() }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zrušit") } }
    )
}

/** Detail jízdy: místa, tachometr, palivo, souřadnice, poznámka, trasa a smazání. */
@Composable
private fun DriveDialog(r: DriveRecord, avgL100: Double?, refuels: List<Refuel>, onRoute: () -> Unit, onClose: () -> Unit) {
    var note by remember { mutableStateOf(r.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    val s = local(r.start)
    val e = local(r.end)
    val (litres, cost) = DriveLog.fuelFor(r, refuels, avgL100)
    fun coord(lat: Double, lon: Double) = String.format(Locale.US, "%.6f, %.6f", lat, lon)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Jízda ${DateTimeFormatter.ofPattern("d. M. yyyy", Czech).format(s)}  ${TIME.format(s)}–${TIME.format(e)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow("Odkud", r.from ?: "zatím bez názvu")
                StatRow("Kam", r.to ?: "zatím bez názvu")
                StatRow("Tachometr", "${odo(r.odoStart)} → ${odo(r.odoEnd)} km")
                StatRow("Ujeto", km(r.distanceM) + " km")
                StatRow("Čas jízdy / v pohybu", "${hm(r.end - r.start)} / ${hm(r.movingMs)}")
                StatRow("Průměr / maximum", "%.0f / %.0f km/h".format(r.avgKmh, r.maxKmh))
                StatRow("Palivo a náklady (odhad)", (litres?.let { decimal(it) + " l" } ?: "– l") + " · " + (cost?.let { decimal(it, 0) + " Kč" } ?: "– Kč"))
                StatRow("Start (GPS)", coord(r.startLat, r.startLon))
                StatRow("Cíl (GPS)", coord(r.endLat, r.endLon))
                OutlinedButton(onClick = onRoute, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Map, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Trasa na mapě")
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(120) }, label = { Text("Poznámka (účel jízdy)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (avgL100 == null) Text("Palivo se odhadne, až budou v Tankování aspoň dvě plné nádrže.", color = Palette.Muted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { DriveLog.update(r.copy(note = note.trim())); onClose() }) { Text("Uložit") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("Smazat", color = Palette.Bad) }
                TextButton(onClick = onClose) { Text("Zavřít") }
            }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Smazat jízdu?") },
            text = { Text("Jízda i její trasa zmizí z knihy jízd, statistik, map i budoucích exportů.") },
            confirmButton = { TextButton(onClick = { DriveLog.delete(r.id); confirmDelete = false; onClose() }) { Text("Smazat", color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušit") } }
        )
    }
}
