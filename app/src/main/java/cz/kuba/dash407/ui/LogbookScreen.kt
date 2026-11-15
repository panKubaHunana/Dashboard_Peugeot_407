package cz.kuba.dash407.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.DriveLog
import cz.kuba.dash407.data.DriveRecord
import cz.kuba.dash407.data.Trip
import cz.kuba.dash407.home.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Období, které se v knize jízd ukáže a vyexportuje. */
private enum class Range(val label: String, val file: String) {
    TODAY("Dnes", "dnes"), WEEK("Týden", "tyden"), MONTH("Měsíc", "mesic"), ALL("Vše", "vse");

    /** Začátek období v ms (0 = bez omezení). */
    fun from(): Long {
        val today = LocalDate.now()
        val day = when (this) {
            TODAY -> today
            WEEK -> today.minusDays(6)
            MONTH -> today.withDayOfMonth(1)
            ALL -> return 0L
        }
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEE d. M.", Czech)
private val TIME = DateTimeFormatter.ofPattern("H:mm", Czech)
private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
private fun km(m: Double) = decimal(m / 1000.0)
private fun hm(ms: Long): String {
    val min = ms / 60_000
    return String.format(Czech, "%d:%02d", min / 60, min % 60)
}

/**
 * Kniha jízd: tabulka uzavřených jízd s filtrem období, součty a exportem do Excelu.
 * Klepnutím na jízdu se otevře detail se souřadnicemi, poznámkou a smazáním.
 */
@Composable
fun LogbookScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by DriveLog.entries.collectAsState()
    val t by Trip.state.collectAsState()
    var range by remember { mutableStateOf(Range.MONTH) }
    var detail by remember { mutableStateOf<DriveRecord?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val from = range.from()
    val list = all.filter { it.start >= from }

    fun export() {
        if (list.isEmpty()) { toast(context, "V tomto období nejsou žádné jízdy"); return }
        exporting = true
        val name = "Kniha_jizd_${LocalDate.now()}_${range.file}.xlsx"
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { DriveLog.exportXlsx(context, list, name) }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in Range.values()) Pill(r.label, r == range) { range = r }
            Spacer(Modifier.weight(1f))
            Button(onClick = { export() }, enabled = !exporting, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (exporting) "Ukládám…" else "Export do Excelu", fontSize = 16.sp)
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp)) {
                Reading(list.size.toString(), "jízd", Modifier.weight(1f))
                Reading(km(list.sumOf { it.distanceM }), "km celkem", Modifier.weight(1f))
                Reading(hm(list.sumOf { it.movingMs }), "v pohybu", Modifier.weight(1f))
                Reading(list.maxOfOrNull { it.maxKmh }?.let { "%.0f".format(it) } ?: DASH, "max km/h", Modifier.weight(1f))
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

        // Záhlaví tabulky
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Head("Datum", 1.1f); Head("Čas", 1.2f); Head("Odkud → Kam", 3f); Head("km", 0.8f, true); Head("Ø / max", 1.1f, true)
        }
        if (list.isEmpty()) {
            Text(
                "Zatím tu nejsou žádné jízdy. Jízda začne rozjezdem a zapíše se, když auto stojí déle než 5 minut " +
                    "(kratší přejezdy pod 300 m se nezapisují).",
                color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(12.dp)
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { r ->
                Surface(
                    shape = RoundedCornerShape(14.dp), color = Palette.Tile,
                    modifier = Modifier.fillMaxWidth().clickable { detail = r }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cell(DATE.format(local(r.start)), 1.1f)
                        Cell("${TIME.format(local(r.start))}–${TIME.format(local(r.end))}", 1.2f)
                        Cell("${r.from ?: "…"} → ${r.to ?: "…"}", 3f, lines = 2)
                        Cell(km(r.distanceM), 0.8f, end = true, bold = true)
                        Cell("%.0f / %.0f".format(r.avgKmh, r.maxKmh), 1.1f, end = true)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    detail?.let { r -> DriveDialog(r) { detail = null } }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Head(text: String, w: Float, end: Boolean = false) {
    Text(
        text, color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        textAlign = if (end) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        modifier = Modifier.weight(w).padding(horizontal = 3.dp)
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, w: Float, end: Boolean = false, bold: Boolean = false, lines: Int = 1) {
    Text(
        text, color = Palette.Text, fontSize = 15.sp, maxLines = lines, overflow = TextOverflow.Ellipsis,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = if (end) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        modifier = Modifier.weight(w).padding(horizontal = 3.dp)
    )
}

/** Detail jízdy: místa, časy, souřadnice startu a cíle, poznámka a smazání. */
@Composable
private fun DriveDialog(r: DriveRecord, onClose: () -> Unit) {
    var note by remember { mutableStateOf(r.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    val s = local(r.start)
    val e = local(r.end)
    fun coord(lat: Double, lon: Double) = String.format(Locale.US, "%.6f, %.6f", lat, lon)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Jízda ${DateTimeFormatter.ofPattern("d. M. yyyy", Czech).format(s)}  ${TIME.format(s)}–${TIME.format(e)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow("Odkud", r.from ?: "zatím bez názvu")
                StatRow("Kam", r.to ?: "zatím bez názvu")
                StatRow("Ujeto", km(r.distanceM) + " km")
                StatRow("V pohybu", hm(r.movingMs))
                StatRow("Průměr / maximum", "%.0f / %.0f km/h".format(r.avgKmh, r.maxKmh))
                StatRow("Start (GPS)", coord(r.startLat, r.startLon))
                StatRow("Cíl (GPS)", coord(r.endLat, r.endLon))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(120) }, label = { Text("Poznámka (účel jízdy)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
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
            text = { Text("Jízda zmizí z knihy jízd i z budoucích exportů.") },
            confirmButton = { TextButton(onClick = { DriveLog.delete(r.id); confirmDelete = false; onClose() }) { Text("Smazat", color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušit") } }
        )
    }
}
