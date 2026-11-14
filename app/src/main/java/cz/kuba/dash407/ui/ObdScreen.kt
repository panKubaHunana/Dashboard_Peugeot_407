package cz.kuba.dash407.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.car.Obd
import kotlin.math.roundToInt

/** Nastavení a živá data OBD adaptéru ELM327. */
@Composable
fun ObdScreen() {
    val context = LocalContext.current
    val cfg by Obd.config.collectAsState()
    val live by Obd.live.collectAsState()
    val log by Obd.log.collectAsState()
    var devices by remember { mutableStateOf(Obd.pairedDevices(context)) }
    var hostText by remember(cfg.host) { mutableStateOf(cfg.host) }
    var portText by remember(cfg.port) { mutableStateOf(cfg.port.toString()) }
    var note by remember { mutableStateOf<String?>(null) }

    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        devices = Obd.pairedDevices(context)
        note = if (granted) null else "Bez oprávnění Bluetooth nejde adaptér použít."
    }
    fun needsPermission() = Build.VERSION.SDK_INT >= 31 &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box2("Stav", "Adaptér ELM327 doplní to, co CAN box rádia neposílá: teplotu chladicí kapaliny, otáčky nebo tlak v sání.") {
            Text(live.status, color = if (live.connected) Palette.Good else Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            live.adapter?.let { Text(it, color = Palette.Muted, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (live.connected || live.busy) {
                    Button(onClick = { Obd.disconnect() }, shape = RoundedCornerShape(16.dp)) { Text("Odpojit", fontSize = 16.sp) }
                } else {
                    Button(onClick = {
                        if (cfg.mode == "bt" && needsPermission()) askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        else Obd.connect(context)
                    }, shape = RoundedCornerShape(16.dp)) { Text("Připojit a otestovat", fontSize = 16.sp) }
                }
            }
            note?.let { Text(it, color = Palette.Amber, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        }

        Box2("Připojení", "Bluetooth: adaptér nejdřív spáruj v nastavení Bluetooth rádia (kód bývá 1234 nebo 0000). Wi-Fi: rádio se musí připojit k síti adaptéru.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Bluetooth", cfg.mode == "bt") { Obd.update { it.copy(mode = "bt") } }
                Pill("Wi-Fi", cfg.mode == "wifi") { Obd.update { it.copy(mode = "wifi") } }
            }
            Spacer(Modifier.height(10.dp))
            if (cfg.mode == "bt") {
                if (!Obd.bluetoothAvailable(context)) {
                    Text("Tohle zařízení Bluetooth nemá.", color = Palette.Amber, fontSize = 15.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spárovaná zařízení", color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            if (needsPermission()) askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            else devices = Obd.pairedDevices(context)
                        }, shape = RoundedCornerShape(16.dp)) { Text("Obnovit", fontSize = 15.sp) }
                    }
                    if (devices.isEmpty()) {
                        Text("Žádné zařízení. Spáruj adaptér a klepni na Obnovit.", color = Palette.Muted, fontSize = 14.sp)
                    }
                    for (dev in devices) {
                        val sel = cfg.btAddress == dev.address
                        Row(
                            Modifier.fillMaxWidth().clickable { Obd.update { it.copy(btAddress = dev.address, btName = dev.name) } }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (sel) "●" else "○", color = if (sel) LocalLook.current.accent else Palette.Muted, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(dev.name, color = Palette.Text, fontSize = 16.sp)
                                Text(dev.address, color = Palette.Muted, fontSize = 13.sp)
                                dev.notAdapter?.let {
                                    Text("Tohle je $it, ne OBD adaptér – data z auta neposílá.", color = Palette.Amber, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Text(
                        "Potřebuješ adaptér ELM327 zapojený do zásuvky OBD pod volantem. Telefon (ani iPhone) se jako adaptér použít nedá. " +
                            "Bluetooth adaptéry „BLE / iOS“ rádio přes tento seznam nevidí – vyber klasický Bluetooth (SPP) nebo Wi-Fi.",
                        color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                OutlinedTextField(
                    value = hostText, onValueChange = { hostText = it.trim(); Obd.update { c -> c.copy(host = it.trim()) } },
                    singleLine = true, label = { Text("Adresa adaptéru") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { v ->
                        portText = v.filter { it.isDigit() }.take(5)
                        portText.toIntOrNull()?.let { p -> Obd.update { c -> c.copy(port = p) } }
                    },
                    singleLine = true, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("Běžné adaptéry Wi-Fi ELM327 mají adresu 192.168.0.10 a port 35000.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Box2("Chování", "") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Připojit automaticky po spuštění", color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(checked = cfg.autoConnect, onCheckedChange = { v -> Obd.update { it.copy(autoConnect = v) } })
            }
            Row {
                Text("Obnovování dat", color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("${cfg.intervalMs} ms", color = Palette.Muted, fontSize = 16.sp)
            }
            Slider(
                value = cfg.intervalMs.toFloat(), valueRange = 200f..2000f,
                onValueChange = { v -> Obd.update { it.copy(intervalMs = (v / 100f).roundToInt() * 100) } }
            )
        }

        Box2("Živá data", "Jen standardní hodnoty OBD-II. Co auto neposílá, zůstane „–“.") {
            val dash = "–"
            StatRow("Otáčky motoru", live.rpm?.let { "$it ot/min" } ?: dash)
            StatRow("Rychlost z řídicí jednotky", live.speedKmh?.let { "$it km/h" } ?: dash)
            StatRow("Chladicí kapalina", live.coolantC?.let { "$it °C" } ?: dash)
            StatRow("Teplota nasávaného vzduchu", live.intakeC?.let { "$it °C" } ?: dash)
            StatRow("Tlak v sání (MAP)", live.mapKpa?.let { "$it kPa" } ?: dash)
            StatRow("Zatížení motoru", live.loadPct?.let { "$it %" } ?: dash)
            StatRow("Plyn / škrticí klapka", live.throttlePct?.let { "$it %" } ?: dash)
            StatRow("Napětí (z adaptéru)", live.volts?.let { decimal(it.toFloat()) + " V" } ?: dash)
        }

        if (log.isNotEmpty()) {
            Box2("Záznam spojení", "Pomůže, když se adaptér nepřipojí.") {
                for (line in log) Text(line, color = Palette.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Box2(title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (text.isNotEmpty()) Text(text, color = Palette.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
