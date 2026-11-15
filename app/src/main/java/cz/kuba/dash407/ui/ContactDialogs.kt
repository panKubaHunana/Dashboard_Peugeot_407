package cz.kuba.dash407.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.DialContact
import cz.kuba.dash407.data.QuickDial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Výběr kontaktu z kontaktů uložených v rádiu (vlastní seznam – rádio nemá aplikaci pro výběr).
 * Když rádio žádné kontakty nesdílí, vysvětlí proč a nabídne aplikaci pro telefon.
 */
@Composable
fun ContactPickerDialog(onPick: (DialContact) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(QuickDial.canRead(context)) }
    var list by remember { mutableStateOf<List<DialContact>?>(null) }
    var query by remember { mutableStateOf("") }
    var choosingApp by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (!it) list = emptyList() }
    LaunchedEffect(granted) {
        if (!granted) ask.launch(Manifest.permission.READ_CONTACTS)
        else list = withContext(Dispatchers.IO) { QuickDial.allContacts(context) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Vybrat kontakt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val l = list
                when {
                    l == null -> Text("Načítám kontakty…", color = Palette.Muted)
                    l.isEmpty() -> {
                        Text(
                            if (!granted) "Bez povolení „Kontakty“ je nejde přečíst. Povol ho v Nastavení › Aplikace › Dash 407 › Oprávnění."
                            else "V rádiu nejsou žádné kontakty, které by jiná aplikace mohla přečíst. Telefonní seznam z mobilu " +
                                "si rádio drží jen ve své aplikaci pro Bluetooth telefon. Zadej jméno a číslo ručně, nebo seznam otevři tam.",
                            fontSize = 15.sp
                        )
                        OutlinedButton(onClick = { if (!QuickDial.openContacts(context)) choosingApp = true }, shape = RoundedCornerShape(14.dp)) {
                            Text("Otevřít telefon v rádiu")
                        }
                    }
                    else -> {
                        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Hledat") }, modifier = Modifier.fillMaxWidth())
                        val shown = l.filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query) }
                        Text("${shown.size} z ${l.size} kontaktů", color = Palette.Muted, fontSize = 13.sp)
                        // Obyčejný posuvný sloupec (seznam uvnitř dialogu), nejvýš 300 kontaktů najednou.
                        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                            for (c in shown.take(300)) {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(c) }.padding(vertical = 8.dp, horizontal = 6.dp)) {
                                    Text(c.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Text(c.number, color = Palette.Muted, fontSize = 14.sp)
                                }
                                HorizontalDivider(color = Palette.Line)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Zavřít") } }
    )
    if (choosingApp) PhoneAppDialog { choosingApp = false }
}

/** Výběr aplikace, která v rádiu obsluhuje telefon (Bluetooth): otevře se tlačítkem Kontakty. */
@Composable
fun PhoneAppDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val candidates = QuickDial.phoneCandidates(apps)
    val others = apps.filter { a -> candidates.none { it.packageName == a.packageName } && a.packageName != context.packageName }
    val current = QuickDial.phoneApp()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Aplikace pro telefon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Vyber aplikaci rádia, ve které je telefonní seznam z mobilu (obvykle ikona sluchátek s Bluetooth). " +
                        "Tlačítko Kontakty ji pak bude otevírat. Změnit to jde podržením tlačítka Kontakty.",
                    fontSize = 14.sp, color = Palette.Muted
                )
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (candidates.isNotEmpty()) Text("Nejspíš některá z těchto:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                    for (a in candidates + others) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                QuickDial.setPhoneApp(a.packageName)
                                QuickDial.openContacts(context)
                                onClose()
                            }.padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = a.icon
                            if (icon != null) Image(icon, null, Modifier.size(34.dp)) else Spacer(Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label + if (a.packageName == current) "  ✓" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(a.packageName, fontSize = 12.sp, color = Palette.Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Zavřít") } }
    )
}
