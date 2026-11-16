package cz.kuba.dash407.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.car.Engine
import cz.kuba.dash407.data.Backup
import cz.kuba.dash407.data.CrashLog
import cz.kuba.dash407.data.NetQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val STAMP = SimpleDateFormat("d. M. yyyy HH:mm", Locale.ROOT)

@Composable
private fun DataBox(title: String, text: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Palette.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Nastavení › Záloha a obnova. */
@Composable
fun BackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by Backup.status.collectAsState()
    val auto by Backup.auto.collectAsState()
    val target by Backup.targetName.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreInfo by remember { mutableStateOf<Backup.Info?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run(block: () -> String) {
        busy = true
        scope.launch {
            message = withContext(Dispatchers.IO) { block() }
            busy = false
        }
    }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        if (uri != null) run { Backup.saveTo(context, uri) }
    }
    val chooseTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        if (uri != null) {
            Backup.setTarget(context, uri)
            run { Backup.saveTo(context, uri) }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val info = withContext(Dispatchers.IO) { Backup.inspect(context, uri) }
                busy = false
                if (info == null) message = "Vybraný soubor není záloha Dash 407."
                else { restoreUri = uri; restoreInfo = info }
            }
        }
    }

    DataBox(
        "Záloha a obnova",
        "Kniha jízd s trasami, tankování, servisní knížka, rozložení ploch, vzhled a všechna nastavení v jednom souboru .zip. " +
            "Na Google Disk se ukládá přes výběr souboru Androidu – v rádiu musí být aplikace Disk Google (z Obchodu Play)."
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { run { Backup.saveToDownloads(context) } }, shape = RoundedCornerShape(16.dp)) { Text("Zálohovat teď") }
            OutlinedButton(enabled = !busy, onClick = { saveAs.launch(Backup.fileName()) }, shape = RoundedCornerShape(16.dp)) { Text("Uložit jinam / Google Disk") }
            OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, shape = RoundedCornerShape(16.dp)) { Text("Obnovit ze zálohy") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Automaticky jednou denně (5 posledních ve Stažené soubory/Dash407)", color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Switch(checked = auto, onCheckedChange = { Backup.setAuto(it) })
        }
        Text(
            if (target == null) "Automaticky i na Google Disk: zatím nevybráno."
            else "Automaticky se přepisuje i soubor „$target“ (když je internet).",
            color = Palette.Muted, fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { chooseTarget.launch("Dash407-zaloha.zip") }) { Text(if (target == null) "Vybrat soubor na Google Disku" else "Změnit soubor") }
            if (target != null) TextButton(onClick = { Backup.setTarget(context, null) }) { Text("Zrušit") }
        }
        (message ?: status)?.let { Text(it, color = Palette.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    }

    val info = restoreInfo
    val uri = restoreUri
    if (info != null && uri != null) {
        AlertDialog(
            onDismissRequest = { restoreInfo = null; restoreUri = null },
            title = { Text("Obnovit ze zálohy?") },
            text = {
                Text(
                    "Záloha z ${STAMP.format(Date(info.created))} (Dash 407 ${info.version}): ${info.drives} jízd, ${info.refuels} tankování, ${info.files} souborů.\n\n" +
                        "Všechna současná data v rádiu se nahradí obsahem zálohy. Současný stav se předtím pro jistotu uloží do Stažené soubory/Dash407. " +
                        "Dash 407 se pak restartuje."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreInfo = null; restoreUri = null
                    run { Backup.restore(context, uri) }
                }) { Text("Obnovit", color = Palette.Bad) }
            },
            dismissButton = { TextButton(onClick = { restoreInfo = null; restoreUri = null }) { Text("Zrušit") } }
        )
    }
}

/** Nastavení › Záznam pádů. */
@Composable
fun CrashCard() {
    val context = LocalContext.current
    val crashes by CrashLog.crashes.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    DataBox(
        "Záznam pádů",
        "Když Dash 407 spadne, uloží se, kde a proč. „Odeslat hlášení“ otevře na GitHubu předvyplněný nový úkol (v prohlížeči musíš být přihlášený) " +
            "a celý záznam zkopíruje do schránky. Bez internetu se hlášení nabídne znovu po připojení."
    ) {
        if (crashes.isEmpty()) {
            Text("Zatím žádný pád. 👍", color = Palette.Good, fontSize = 16.sp)
        } else {
            for (c in crashes.take(3)) {
                Text(
                    STAMP.format(Date(c.at)) + (if (c.reported) " · odesláno" else "") + "\n" + c.title,
                    color = Palette.Text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)
                )
            }
            if (crashes.size > 3) Text("… a dalších ${crashes.size - 3}", color = Palette.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { message = CrashLog.send(context, crashes.first()) }, shape = RoundedCornerShape(16.dp)) { Text("Odeslat hlášení") }
                OutlinedButton(onClick = { message = CrashLog.saveToDownloads(context, crashes.first()) }, shape = RoundedCornerShape(16.dp)) { Text("Uložit do souboru") }
                TextButton(onClick = { CrashLog.deleteAll(); message = "Záznamy smazány." }) { Text("Smazat vše") }
            }
        }
        message?.let { Text(it, color = Palette.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Nastavení › Připojení a motor: stav internetu, fronta a jak se pozná start motoru. */
@Composable
fun StatusCard() {
    val online by NetQueue.online.collectAsState()
    val jobs by NetQueue.jobs.collectAsState()
    val engine by Engine.state.collectAsState()
    DataBox(
        "Internet a motor",
        "Bez internetu se dotazy (názvy míst, hlášení pádů) uloží do fronty a odešlou se po připojení. Mapa ukáže dlaždice uložené na disku, " +
            "automatické tankování pozná stanice, které už zná. Jízda v Knize jízd začíná startem motoru a končí jeho vypnutím."
    ) {
        Text(
            (if (online) "Internet: připojeno" else "Internet: bez připojení") +
                (if (jobs.isNotEmpty()) " · ve frontě ${jobs.size}" else ""),
            color = if (online) Palette.Good else Palette.Amber, fontSize = 16.sp
        )
        Text(
            "Motor: " + (if (engine.running) "běží" else "neběží") + " · pozná se podle: " + engine.source,
            color = Palette.Text, fontSize = 16.sp
        )
        Text(
            "Nejpřesnější je OBD adaptér (otáčky). Bez něj se bere zapnutí a vypnutí rádia jako start a stop motoru.",
            color = Palette.Muted, fontSize = 13.sp
        )
    }
}

/** Po spuštění: minule aplikace spadla – nabídnout odeslání hlášení. */
@Composable
fun CrashDialog() {
    val context = LocalContext.current
    val c by CrashLog.offer.collectAsState()
    val crash = c ?: return
    var result by remember(crash.file.name) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { CrashLog.dismissOffer() },
        title = { Text("Dash 407 minule spadl") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${STAMP.format(Date(crash.at))}\n${crash.title}", fontSize = 15.sp)
                Text("Pošli hlášení na GitHub – Claude podle něj chybu opraví.", fontSize = 15.sp)
                result?.let { Text(it, fontSize = 14.sp, color = Palette.Muted) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                result = CrashLog.send(context, crash)
                android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
            }) { Text("Odeslat hlášení") }
        },
        dismissButton = { TextButton(onClick = { CrashLog.dismissOffer() }) { Text("Později") } }
    )
}
