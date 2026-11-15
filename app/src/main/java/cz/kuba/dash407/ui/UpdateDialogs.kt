package cz.kuba.dash407.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import cz.kuba.dash407.data.UpdateState
import cz.kuba.dash407.data.Updater
import kotlin.math.roundToInt

/** Okna aktualizace a novinek; vložit jednou nad celou aplikaci. */
@Composable
fun UpdateDialogs() {
    val context = LocalContext.current
    val state by Updater.state.collectAsState()
    val changelog by Updater.changelog.collectAsState()
    val info by Updater.info.collectAsState()

    // Kontrola při spuštění a pokračování po povolení instalace v nastavení.
    LaunchedEffect(Unit) { Updater.onStart(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) Updater.resumeAfterPermission(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Nová verze ${s.release.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Máš verzi ${Updater.currentVersion}. Na GitHubu je novější – stáhnout a rovnou nainstalovat?", fontSize = 16.sp)
                    if (s.release.sizeBytes > 0) {
                        Text("Velikost ${(s.release.sizeBytes / 1_048_576.0).roundToInt()} MB", color = Palette.Muted, fontSize = 14.sp)
                    }
                    NotesBox(Updater.trimNotes(s.release.notes), maxHeight = 260)
                }
            },
            confirmButton = { Button(onClick = { Updater.download(context, s.release) }) { Text("Stáhnout a nainstalovat") } },
            dismissButton = { TextButton(onClick = { Updater.dismiss() }) { Text("Později") } }
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Stahuji verzi ${s.release.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.progress >= 0f) {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(s.progress * 100).roundToInt()} %", fontSize = 16.sp)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text("Po stažení se rovnou spustí instalace.", color = Palette.Muted, fontSize = 14.sp)
                }
            },
            confirmButton = {}
        )
        is UpdateState.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Instaluji verzi ${s.release.version}") },
            text = { Text("Potvrď instalaci v okně Androidu. Dash 407 se pak sám znovu spustí a ukáže novinky.", fontSize = 16.sp) },
            confirmButton = { TextButton(onClick = { Updater.dismiss() }) { Text("Skrýt") } }
        )
        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Povol instalaci aktualizací") },
            text = {
                Text(
                    "Aby se mohla nová verze nainstalovat, povol pro Dash 407 „Instalovat neznámé aplikace“. " +
                        "Po návratu se stahování spustí samo.", fontSize = 16.sp
                )
            },
            confirmButton = { Button(onClick = { Updater.openInstallPermission(context) }) { Text("Otevřít nastavení") } },
            dismissButton = { TextButton(onClick = { Updater.dismiss() }) { Text("Zrušit") } }
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Aktualizace se nepovedla") },
            text = { Text(s.message, fontSize = 16.sp) },
            confirmButton = {
                val r = s.release
                if (r != null) Button(onClick = { Updater.download(context, r) }) { Text("Zkusit znovu") }
                else TextButton(onClick = { Updater.dismiss() }) { Text("Zavřít") }
            },
            dismissButton = { if (s.release != null) TextButton(onClick = { Updater.dismiss() }) { Text("Zavřít") } }
        )
        UpdateState.Idle -> Unit
    }

    // Novinky po aktualizaci (jen když zrovna neběží jiné okno aktualizace).
    val log = changelog
    if (log != null && state == UpdateState.Idle) {
        AlertDialog(
            onDismissRequest = { Updater.closeChangelog() },
            title = { Text("Co je nového ve verzi ${log.first}") },
            text = { NotesBox(log.second, maxHeight = 460) },
            confirmButton = { Button(onClick = { Updater.closeChangelog() }) { Text("Rozumím") } }
        )
    }

    info?.let { msg ->
        AlertDialog(
            onDismissRequest = { Updater.clearInfo() },
            title = { Text("Aktualizace") },
            text = { Text(msg, fontSize = 16.sp) },
            confirmButton = { TextButton(onClick = { Updater.clearInfo() }) { Text("OK") } }
        )
    }
}

/** Posuvný text poznámek k vydání se zjednodušeným Markdownem (nadpisy, odrážky, tučné). */
@Composable
private fun NotesBox(md: String, maxHeight: Int) {
    if (md.isBlank()) return
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.width(1.dp).padding(top = 2.dp))
                line.startsWith("#") -> Text(
                    rich(line.trimStart('#').trim()), fontSize = if (line.startsWith("## ")) 19.sp else 17.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)
                )
                line.trimStart().startsWith("- ") -> Row {
                    Text("•", fontSize = 15.sp, modifier = Modifier.width(16.dp))
                    Text(rich(line.trimStart().removePrefix("- ")), fontSize = 15.sp)
                }
                else -> Text(rich(line), fontSize = 15.sp)
            }
        }
    }
}

/** **tučné** a `kód` z Markdownu. */
private fun rich(text: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            bold = !bold
            i += 2
            continue
        }
        val ch = text[i]
        if (ch == '`') { i++; continue }
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(ch) } else append(ch)
        i++
    }
}
