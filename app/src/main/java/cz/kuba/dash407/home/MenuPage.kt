package cz.kuba.dash407.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.Palette

private data class MenuItem(val icon: ImageVector, val title: String, val action: () -> Unit)

/**
 * Speciální plocha Menu (pod plochou dole). Nahrazuje dřívější spodní lištu:
 * aplikace, úpravy rozložení, vzhled, nastavení a všechny ostatní panely na jednom místě.
 */
@Composable
fun MenuPage(pages: List<Page>) {
    val look = LocalLook.current
    val now by rememberNow()
    val car by CarRepo.state.collectAsState()
    val items = listOf(
        MenuItem(Icons.Filled.Apps, "Aplikace") { UiBus.open(Panel.APPS) },
        MenuItem(Icons.Filled.AutoAwesome, "Vzhled") { UiBus.open(Panel.APPEARANCE) },
        MenuItem(Icons.Filled.Settings, "Nastavení") { UiBus.open(Panel.SETTINGS) },
        MenuItem(Icons.Filled.Search, "Kam jedeme?") { UiBus.open(Panel.DESTINATION) },
        MenuItem(Icons.Filled.LocalGasStation, "Tankování") { UiBus.open(Panel.FUEL) },
        MenuItem(Icons.Filled.DirectionsCar, "Auto a signály") { UiBus.open(Panel.CAR) },
        MenuItem(Icons.Filled.Build, "Servisní knížka") { UiBus.open(Panel.SERVICE) },
        MenuItem(Icons.Filled.Tune, "Zvuk, volant, klima") { UiBus.open(Panel.CARAPPS) },
        MenuItem(Icons.Filled.Bluetooth, "OBD adaptér") { UiBus.open(Panel.OBD) },
        MenuItem(Icons.Filled.GridView, "Plochy") { UiBus.open(Panel.PAGES) }
    )
    val shape = RoundedCornerShape(look.corner.coerceAtMost(28.dp))

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Záhlaví: čas, datum a stav spojení s autem.
        Row(
            Modifier.fillMaxWidth().clip(shape).background(look.card).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Menu", color = look.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(timeText(now, false), color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(dateText(now), color = Palette.Muted, fontSize = 15.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .border(1.dp, if (car.linked) Palette.Good else Palette.Text.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .clickable { UiBus.open(Panel.CAR) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (car.linked) Palette.Good else Palette.Muted))
                Spacer(Modifier.width(8.dp))
                Text(if (car.linked) "AUTO spojeno" else "AUTO nespojeno", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Úprava rozložení: výběr plochy, která se otevře v režimu úprav.
        Column(Modifier.fillMaxWidth().clip(shape).background(look.card).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dashboard, null, tint = look.accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rozložení – kterou plochu upravit?", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in pages.filter { it.enabled }) {
                    Text(
                        p.name, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Text.copy(alpha = 0.12f))
                            .clickable {
                                UiBus.selected = null
                                UiBus.slot = p.slot
                                UiBus.editing = true
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Velká tlačítka: 3 sloupce, výška se přizpůsobí obrazovce (bez posouvání, aby nekolidovalo s tahem mezi plochami).
        val rows = items.chunked(3)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in rows) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in row) {
                        Column(
                            Modifier.weight(1f).fillMaxSize().clip(shape).background(look.card).clickable { item.action() }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(item.icon, null, tint = look.accent, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(item.title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text(
            "Zpět na plochy: táhni prstem dolů, nebo stiskni Domů.",
            color = Palette.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

