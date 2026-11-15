package cz.kuba.dash407.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.BuildConfig
import cz.kuba.dash407.R
import cz.kuba.dash407.car.Demo
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.home.LayoutStore
import cz.kuba.dash407.home.Panel
import cz.kuba.dash407.home.Slot
import cz.kuba.dash407.home.UiBus
import cz.kuba.dash407.nav.Mapy
import cz.kuba.dash407.nav.Tiles
import cz.kuba.dash407.service.DriveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val demo by Prefs.demo.collectAsState()
    val gpsAllowed by Gps.allowed.collectAsState()
    val media by NowPlaying.state.collectAsState()
    val mapyKey by Prefs.mapyKey.collectAsState()
    val home by Prefs.homeAddress.collectAsState()
    val work by Prefs.workAddress.collectAsState()
    val split by Prefs.splitScreen.collectAsState()
    val hideTop by Prefs.hideTopBar.collectAsState()
    val hideBottom by Prefs.hideBottomBar.collectAsState()
    val drive by Prefs.driveService.collectAsState()
    val navTarget by Prefs.navTarget.collectAsState()
    val googleKey by Prefs.googleKey.collectAsState()
    var googleText by remember { mutableStateOf(googleKey) }
    var googleResult by remember { mutableStateOf<String?>(null) }
    val navVoice by Prefs.navVoice.collectAsState()
    val navHeading by Prefs.navHeadingUp.collectAsState()
    val pages by LayoutStore.pages.collectAsState()
    var keyText by remember { mutableStateOf(mapyKey) }
    var showMapy by remember { mutableStateOf(false) }
    var showGoogle by remember { mutableStateOf(false) }
    var keyResult by remember { mutableStateOf<String?>(null) }
    var homeText by remember { mutableStateOf(home) }
    var workText by remember { mutableStateOf(work) }
    var confirmReset by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Gps.start(context)
        DriveService.start(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingCard(
            stringResource(R.string.settings_home_title),
            stringResource(R.string.settings_home_text),
            stringResource(R.string.settings_home_button)
        ) { open(context, Settings.ACTION_HOME_SETTINGS) }

        SettingCard(
            "Zvuk, volant a klimatizace", "Zkratky na původní aplikace rádia: DSP a ekvalizér, učení tlačítek na volantu, klimatizace a nastavení vozu.",
            "Otevřít zkratky"
        ) { UiBus.open(Panel.CARAPPS) }

        SettingCard(
            "OBD adaptér", "Bluetooth nebo Wi-Fi adaptér ELM327: otáčky, teplota motoru, tlak v sání a další data z řídicí jednotky.",
            "Otevřít nastavení OBD"
        ) { UiBus.open(Panel.OBD) }

        SettingCard(
            "Servisní knížka", "SPZ, VIN, motorizace, olej, filtry a brzdy s upozorněním na termín.",
            "Otevřít servisní knížku"
        ) { UiBus.open(Panel.SERVICE) }

        Card("Mapy.com", "Mapa v rádiu, hledání míst a plánování trasy používají REST API Mapy.com. " +
            "Bezplatný API klíč získáš na developer.mapy.com (Můj účet › API klíče). Klíč zůstává jen v rádiu. " +
            "Hlasová navigace běží v aplikaci Mapy.com a klíč nepotřebuje.") {
            OutlinedTextField(
                value = keyText, onValueChange = { keyText = it.trim() }, singleLine = true,
                label = { Text("API klíč") }, modifier = Modifier.fillMaxWidth(),
                // Klíč je skrytý tečkami; okem se dá dočasně zobrazit.
                visualTransformation = if (showMapy) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showMapy = !showMapy }) {
                        Icon(if (showMapy) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showMapy) "Skrýt klíč" else "Zobrazit klíč")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Prefs.setMapyKey(keyText)
                    Tiles.clear()
                    keyResult = "Ověřuji…"
                    scope.launch { keyResult = withContext(Dispatchers.IO) { Tiles.test(keyText) } }
                }, shape = RoundedCornerShape(16.dp)) { Text("Uložit a ověřit", fontSize = 16.sp) }
                OutlinedButton(onClick = { Mapy.openWeb(context, "https://developer.mapy.com/") }, shape = RoundedCornerShape(16.dp)) {
                    Text("Získat klíč", fontSize = 16.sp)
                }
            }
            keyResult?.let { Text(it, color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(8.dp))
            SwitchLine("Otevírat Mapy.com v rozdělené obrazovce (zkušebně – jen pokud to jednotka umí)", split) { Prefs.setSplitScreen(it) }
            if (!Mapy.isInstalled(context)) {
                Text("Aplikace Mapy.com v rádiu není. Nainstaluj ji z Obchodu Play.", color = Palette.Amber, fontSize = 15.sp)
            }
        }

        Card("Lišty rádia (zkušebně)", "Skryje lišty jednotky, dokud je Dash 407 vpředu, a uvolní tak místo pro widgety. Funguje jen tehdy, když je rádio kreslí jako systémové lišty Androidu. Tahem prstem od horního (dolního) okraje se lišta na chvíli ukáže.") {
            SwitchLine("Skrýt horní lištu (napětí, hlasitost, ikony aplikací)", hideTop) { Prefs.setHideTopBar(it) }
            SwitchLine("Skrýt spodní lištu (klimatizace, hlasitost, Domů)", hideBottom) { Prefs.setHideBottomBar(it) }
        }

        Card("Navigace", "Navigace krok za krokem přímo v Dash 407: mapa Mapy.com, trasa a české pokyny z otevřeného routeru Valhalla (OpenStreetMap), hlas z převodu textu na řeč v rádiu. " +
            "Když jednotka nemá českou řeč, nainstaluj „Převod textu na řeč Google“ a v nastavení Androidu zvol češtinu.") {
            Text("Tlačítko Navigovat spustí navigaci v:", color = Palette.Text, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, name) in cz.kuba.dash407.nav.ExternalNav.targets) {
                    val ok = cz.kuba.dash407.nav.ExternalNav.installed(context, id)
                    Pill(if (ok) name else "$name (není)", navTarget == id) { Prefs.setNavTarget(id) }
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchLine("Hlasové pokyny", navVoice) { Prefs.setNavVoice(it) }
            SwitchLine("Mapa natočená po směru jízdy", navHeading) { Prefs.setNavHeadingUp(it) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { cz.kuba.dash407.nav.NavEngine.say("Za 300 metrů odbočte vpravo.") }, shape = RoundedCornerShape(16.dp)) {
                Text("Vyzkoušet hlas", fontSize = 16.sp)
            }
            if (!cz.kuba.dash407.nav.NavEngine.voiceAvailable()) {
                Text("Česká řeč zatím není připravená nebo v rádiu chybí.", color = Palette.Amber, fontSize = 14.sp)
            }
        }

        Card("Google mapy", "Podklady Google (mapa, satelit, terén) přes oficiální Map Tiles API. Klíč vytvoříš v Google Cloud Console: " +
            "založ projekt, připoj fakturační účet, zapni „Map Tiles API“ a vytvoř API klíč. Prvních 100 000 dlaždic měsíčně je zdarma, " +
            "pak Google účtuje (cca 0,60 USD za 1 000). Podklad pak vybereš ve Vzhledu › Mapa.") {
            OutlinedTextField(
                value = googleText, onValueChange = { googleText = it.trim() }, singleLine = true,
                label = { Text("Google API klíč") }, modifier = Modifier.fillMaxWidth(),
                // Klíč je skrytý tečkami; okem se dá dočasně zobrazit.
                visualTransformation = if (showGoogle) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showGoogle = !showGoogle }) {
                        Icon(if (showGoogle) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showGoogle) "Skrýt klíč" else "Zobrazit klíč")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Prefs.setGoogleKey(googleText)
                    Tiles.clear()
                    googleResult = "Ověřuji…"
                    scope.launch { googleResult = withContext(Dispatchers.IO) { cz.kuba.dash407.nav.GoogleTiles.test(googleText) } }
                }, shape = RoundedCornerShape(16.dp)) { Text("Uložit a ověřit", fontSize = 16.sp) }
                OutlinedButton(onClick = { Mapy.openWeb(context, "https://console.cloud.google.com/google/maps-apis/") }, shape = RoundedCornerShape(16.dp)) {
                    Text("Google Cloud", fontSize = 16.sp)
                }
            }
            googleResult?.let { Text(it, color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
        }

        Card("Otevřené mapy", "OpenStreetMap a OpenTopoMap fungují bez klíče a zdarma. Bez klíče Mapy.com se cíle hledají v otevřených datech OpenStreetMap " +
            "(Nominatim) a trasa i pokyny jdou z otevřeného routeru Valhalla. Dlaždice se pamatují 7 dní, jak žádají pravidla OpenStreetMap.") {
            Text("Podklad přepneš ve Vzhledu (✦) › Mapa.", color = Palette.Text, fontSize = 15.sp)
        }

        Card("Rychlé cíle", "Tlačítka Domů a Práce na widgetu Navigace. Stačí adresa, Mapy.com ji samy najdou.") {
            OutlinedTextField(value = homeText, onValueChange = { homeText = it }, singleLine = true, label = { Text("Domů") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = workText, onValueChange = { workText = it }, singleLine = true, label = { Text("Práce") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                Prefs.setHomeAddress(homeText.trim())
                Prefs.setWorkAddress(workText.trim())
                android.widget.Toast.makeText(context, "Cíle uloženy", android.widget.Toast.LENGTH_SHORT).show()
            }, shape = RoundedCornerShape(16.dp)) { Text("Uložit cíle", fontSize = 16.sp) }
        }

        Card("Hudba", "Widget Právě hraje má přednost u zvolené aplikace. Když nic nehraje, klepnutí otevře její aplikaci. Výchozí je YouTube Music.") {
            val musicApp by Prefs.musicApp.collectAsState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("YouTube Music", musicApp == Prefs.YT_MUSIC) { Prefs.setMusicApp(Prefs.YT_MUSIC); NowPlaying.refresh(context) }
                Pill("Spotify", musicApp == Prefs.SPOTIFY) { Prefs.setMusicApp(Prefs.SPOTIFY); NowPlaying.refresh(context) }
                Pill("Jakákoli", musicApp.isEmpty()) { Prefs.setMusicApp(""); NowPlaying.refresh(context) }
            }
        }

        Card("Plochy", "Plochy tvoří kříž kolem střední plochy. Přecházíš mezi nimi tahem prstu, aktuální ukazuje kříž ve spodní liště.") {
            for (slot in listOf(Slot.UP, Slot.LEFT, Slot.RIGHT, Slot.DOWN)) {
                val page = pages.firstOrNull { it.slot == slot } ?: continue
                SwitchLine("${page.name} (${slot.where})", page.enabled) {
                    LayoutStore.setEnabled(slot, it)
                    if (!it && UiBus.slot == slot) UiBus.slot = Slot.CENTER
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { confirmReset = true }, shape = RoundedCornerShape(16.dp)) { Text("Obnovit výchozí rozložení ploch", fontSize = 16.sp) }
        }

        SettingCard(
            stringResource(R.string.settings_media_title),
            if (media.allowed) "Přístup k oznámením je povolený. Z oznámení se čte jen navigace Mapy.com (další manévr)."
            else "Dlaždice Právě hraje a další manévr z Mapy.com potřebují přístup k oznámením. Na Androidu 13 a novějším nejdřív v Informacích o aplikaci › ⋮ povol „omezená nastavení“.",
            stringResource(R.string.settings_media_button)
        ) { open(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

        SettingCard(
            stringResource(R.string.settings_location_title),
            if (gpsAllowed) "Oprávnění k poloze je udělené." else "Mapa, rychlost, palubní počítač z GPS a počasí potřebují oprávnění k poloze.",
            if (gpsAllowed) null else stringResource(R.string.settings_location_button)
        ) { askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

        Card("Jízda na pozadí", "Palubní počítač Jízda počítá kilometry i ve chvíli, kdy je vpředu jiná aplikace (třeba navigace). V liště oznámení je pak tichá ikona.") {
            SwitchLine("Počítat jízdu i na pozadí", drive) {
                Prefs.setDriveService(it)
                if (it) DriveService.start(context) else DriveService.stop(context)
            }
        }

        Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_demo_title), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Vymyšlená jízda kolem Hradce Králové pro vyzkoušení mimo auto (rychlost, mapa, palubní počítač).", color = Palette.Muted, fontSize = 15.sp)
                }
                Switch(
                    checked = demo,
                    onCheckedChange = {
                        Prefs.setDemo(it)
                        if (it) Demo.start() else Demo.stop()
                    }
                )
            }
        }

        SettingCard(
            "Aktualizace",
            "Dash 407 se při každém spuštění podívá na GitHub. Když je tam novější verze, nabídne její stažení a rovnou ji nainstaluje; po aktualizaci ukáže novinky. Nainstalováno: ${BuildConfig.VERSION_NAME}.",
            "Zkontrolovat teď"
        ) { cz.kuba.dash407.data.Updater.check(context, manual = true) }

        Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.settings_about_title), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), color = Palette.Muted, fontSize = 15.sp)
                Text(
                    "Vlastní prostředí pro Peugeot 407 SW s jednotkou ROCO K706. Kód je původní. " +
                        "Mapové podklady © Seznam.cz a.s. a další (Mapy.com), počasí Open-Meteo, rychlostní limity © přispěvatelé OpenStreetMap.",
                    color = Palette.Muted, fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Obnovit rozložení?") },
            text = { Text("Všechny plochy dostanou výchozí widgety. Tvoje úpravy rozložení se ztratí.") },
            confirmButton = { TextButton(onClick = { LayoutStore.resetAll(); UiBus.slot = Slot.CENTER; confirmReset = false }) { Text("Obnovit") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušit") } }
        )
    }
}

@Composable
private fun Card(title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Palette.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingCard(title: String, text: String, button: String?, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Palette.Muted, fontSize = 15.sp)
            if (button != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClick, shape = RoundedCornerShape(16.dp)) { Text(button, fontSize = 17.sp) }
            }
        }
    }
}

private fun open(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Jednotka tuhle obrazovku nastavení nemá.
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: Exception) {
        }
    }
}
