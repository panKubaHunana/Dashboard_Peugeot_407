package cz.kuba.dash407.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.AppEntry
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.CarAppSlot
import cz.kuba.dash407.data.CarApps

/** Zkratky na původní aplikace rádia: DSP, učení tlačítek na volantu, klimatizace, nastavení vozu. */
@Composable
fun CarAppsScreen() {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val chosen by CarApps.chosen.collectAsState()
    var picking by remember { mutableStateOf<CarAppSlot?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Zatím otevírají původní aplikace rádia. Vlastní obrazovky přijdou, až z nich zjistíme příkazy jednotky.",
            color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp)
        )
        for (slot in CarApps.slots) {
            val app = CarApps.resolve(slot, apps, context.packageName)
            Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(slot.title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(slot.description, color = Palette.Muted, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 40)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app?.label ?: "V rádiu nenalezeno", color = if (app != null) Palette.Text else Palette.Amber, fontSize = 16.sp)
                            Text(
                                when {
                                    app == null -> "Vyber aplikaci ručně"
                                    chosen[slot.id] != null -> "vybráno ručně"
                                    else -> "nalezeno podle názvu"
                                },
                                color = Palette.Muted, fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = app != null, onClick = { app?.let { AppsRepo.launch(context, it.packageName) } }, shape = RoundedCornerShape(16.dp)) {
                            Text("Otevřít", fontSize = 16.sp)
                        }
                        OutlinedButton(onClick = { picking = slot }, shape = RoundedCornerShape(16.dp)) { Text("Vybrat aplikaci", fontSize = 16.sp) }
                    }
                }
            }
        }
    }
    picking?.let { slot -> AppPickDialog(slot, apps) { picking = null } }
}

@Composable
private fun AppIcon(app: AppEntry?, sizeDp: Int) {
    val bmp = app?.icon
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Color.White.copy(alpha = if (app != null) 0.92f else 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp, null, Modifier.size((sizeDp * 0.72f).dp))
        else Text(app?.label?.take(1) ?: "?", color = if (app != null) Color.Black else Palette.Muted, fontSize = 16.sp)
    }
}

@Composable
private fun AppPickDialog(slot: CarAppSlot, apps: List<AppEntry>, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(slot.title) },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Hledat aplikaci") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { CarApps.choose(slot.id, a.packageName); onClose() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(a, 32)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(a.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(a.packageName, color = Palette.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Zavřít") } },
        dismissButton = { TextButton(onClick = { CarApps.choose(slot.id, null); onClose() }) { Text("Najít automaticky") } }
    )
}
