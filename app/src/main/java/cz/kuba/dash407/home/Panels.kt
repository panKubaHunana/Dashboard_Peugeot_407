package cz.kuba.dash407.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.AppearanceStore
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.nav.Mapy
import cz.kuba.dash407.nav.Navigator
import cz.kuba.dash407.nav.MapSources
import cz.kuba.dash407.nav.Nominatim
import cz.kuba.dash407.nav.Place
import cz.kuba.dash407.nav.Recent
import cz.kuba.dash407.nav.RouteState
import cz.kuba.dash407.nav.Tiles
import cz.kuba.dash407.ui.AccentChoices
import cz.kuba.dash407.ui.AppsScreen
import cz.kuba.dash407.ui.BackgroundNames
import cz.kuba.dash407.ui.CarScreen
import cz.kuba.dash407.ui.FuelScreen
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.PanelHeader
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.Pill
import cz.kuba.dash407.ui.FontChoices
import cz.kuba.dash407.ui.IntroLogo
import cz.kuba.dash407.ui.IntroState
import cz.kuba.dash407.ui.IntroStyles
import cz.kuba.dash407.ui.fontFamilyOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import cz.kuba.dash407.ui.SettingsScreen
import cz.kuba.dash407.ui.ObdScreen
import cz.kuba.dash407.ui.ServiceScreen
import cz.kuba.dash407.ui.CarAppsScreen
import cz.kuba.dash407.ui.backgroundBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Vykreslí otevřený panel přes plochy. */
@Composable
fun PanelHost() {
    val look = LocalLook.current
    val p = UiBus.panel ?: return
    if (p == Panel.PAGES) return
    if (p == Panel.APPEARANCE) {
        AppearancePanel()
        return
    }
    Column(Modifier.fillMaxSize().background(backgroundBrush(look.background, look.accent))) {
        val title = when (p) {
            Panel.APPS -> "Aplikace"
            Panel.SETTINGS -> "Nastavení"
            Panel.FUEL -> "Tankování"
            Panel.CAR -> "Auto a signály"
            Panel.DESTINATION -> "Kam jedeme?"
            Panel.PICKER -> "Přidat na plochu"
            Panel.OBD -> "OBD adaptér"
            Panel.SERVICE -> "Servisní knížka"
            Panel.CARAPPS -> "Zvuk, volant a klimatizace"
            else -> ""
        }
        PanelHeader(title, onBack = { UiBus.close() })
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (p) {
                Panel.APPS -> AppsScreen()
                Panel.SETTINGS -> SettingsScreen()
                Panel.FUEL -> FuelScreen()
                Panel.CAR -> CarScreen()
                Panel.DESTINATION -> DestinationPanel()
                Panel.PICKER -> PickerPanel()
                Panel.OBD -> ObdScreen()
                Panel.SERVICE -> ServiceScreen()
                Panel.CARAPPS -> CarAppsScreen()
                else -> Unit
            }
        }
    }
}

/** Výběr widgetů (s živým náhledem) a aplikací do doku. */
@Composable
fun PickerPanel() {
    val context = LocalContext.current
    val look = LocalLook.current
    var category by remember { mutableStateOf<Category?>(null) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Widgety", UiBus.pickerTab == 0) { UiBus.pickerTab = 0 }
            Pill("Aplikace do doku", UiBus.pickerTab == 1) { UiBus.pickerTab = 1 }
            Spacer(Modifier.width(8.dp))
            if (UiBus.pickerTab == 0) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Hledat") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (UiBus.pickerTab == 1) {
            Spacer(Modifier.height(8.dp))
            AppsScreen(favoritesMode = true)
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("Vše", category == null) { category = null }
            for (c in Category.values()) Pill(c.label, category == c) { category = c }
        }
        val list = WidgetType.values().filter {
            it !in WidgetType.retired &&
            (category == null || it.category == category) &&
                (query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.name }) { type ->
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).clickable { addWidget(context, type) }
                ) {
                    // Živý náhled widgetu; klepnutí kamkoli ho přidá na aktuální plochu.
                    val ratio = (type.h.toFloat() / LayoutStore.ROWS * 2.4f) / (type.w.toFloat() / LayoutStore.COLS)
                    val height = (170f * ratio.coerceIn(0.45f, 1.6f)).dp
                    Box(Modifier.fillMaxWidth().height(height)) {
                        WidgetContent(type, null, Modifier.fillMaxSize(), preview = true)
                        Box(Modifier.fillMaxSize().clickable { addWidget(context, type) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(type.title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(type.description, color = Palette.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${type.w} × ${type.h} · ${type.category.label}", color = look.accent, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun addWidget(context: android.content.Context, type: WidgetType) {
    val slot = UiBus.slot
    val page = LayoutStore.page(slot)
    var w = type.w
    var h = type.h
    var spot = LayoutStore.findSpace(page.cells, w, h)
    while (spot == null && (w > type.minW || h > type.minH)) {
        if (h > type.minH) h-- else w--
        spot = LayoutStore.findSpace(page.cells, w, h)
    }
    if (spot == null) {
        toast(context, "Na ploše „${page.name}“ není místo. Zmenši nebo smaž jiný widget.")
        return
    }
    val cell = Cell(LayoutStore.newId(), type.name, spot.first, spot.second, w, h)
    LayoutStore.setCells(slot, page.cells + cell)
    UiBus.editing = true
    UiBus.selected = cell.id
    UiBus.close()
    toast(context, "${type.title} přidán na plochu ${page.name}")
}

/** Panel Vzhled: dolní polovina obrazovky, změny jsou hned vidět na ploše nad ním. */
@Composable
fun AppearancePanel() {
    val look = LocalLook.current
    val a by AppearanceStore.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable { UiBus.close() })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Palette.Sheet)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Vzhled", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "Výchozí", color = look.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { AppearanceStore.reset() }.padding(10.dp)
                )
                Icon(
                    Icons.Filled.Close, "Zavřít", tint = Palette.Text,
                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable { UiBus.close() }.padding(10.dp)
                )
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Section("Barva zvýraznění")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in AccentChoices) {
                        val sel = a.accent == c
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                                .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { AppearanceStore.update { it.copy(accent = c) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Filled.Check, null, tint = Color(0xFF06121C))
                        }
                    }
                }
                Section("Pozadí")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BackgroundNames.forEachIndexed { i, name ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { AppearanceStore.update { it.copy(background = i) } }) {
                            Box(
                                Modifier.size(64.dp, 86.dp).clip(RoundedCornerShape(12.dp))
                                    .background(backgroundBrush(i, look.accent))
                                    .border(if (a.background == i) 3.dp else 1.dp, if (a.background == i) look.accent else Palette.Text.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            )
                            Text(name, color = Palette.Muted, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                SliderRow("Průhlednost karet", "${(a.cardAlpha * 100).roundToInt()} %", a.cardAlpha, 0.2f..1f) { v -> AppearanceStore.update { it.copy(cardAlpha = v) } }
                SliderRow("Zaoblení rohů", "${a.corner} dp", a.corner.toFloat(), 0f..36f) { v -> AppearanceStore.update { it.copy(corner = v.roundToInt()) } }
                SliderRow("Mezery mezi widgety", "${a.gap} dp", a.gap.toFloat(), 0f..20f) { v -> AppearanceStore.update { it.copy(gap = v.roundToInt()) } }
                SliderRow("Velikost písma", "${(a.textScale * 100).roundToInt()} %", a.textScale, 0.8f..1.4f) { v -> AppearanceStore.update { it.copy(textScale = (v * 20).roundToInt() / 20f) } }
                SwitchRow("Rámeček karet", a.border) { v -> AppearanceStore.update { it.copy(border = v) } }
                SwitchRow("Karty podbarvené zvýrazněním", a.cardTint) { v -> AppearanceStore.update { it.copy(cardTint = v) } }
                SwitchRow("Nadpisy widgetů", a.titles) { v -> AppearanceStore.update { it.copy(titles = v) } }
                SwitchRow("Animace při přechodu mezi plochami", a.animations) { v -> AppearanceStore.update { it.copy(animations = v) } }
                SwitchRow("Sekundy v hodinách", a.seconds) { v -> AppearanceStore.update { it.copy(seconds = v) } }
                Section("Motiv")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((id, name) in listOf("dark" to "Tmavý", "light" to "Světlý", "auto" to "Podle systému")) {
                        Pill(name, a.theme == id) { AppearanceStore.update { it.copy(theme = id) } }
                    }
                }
                Text("Světlý motiv je pro den; v noci oslňuje, proto je výchozí tmavý. „Podle systému“ se řídí nastavením Androidu v rádiu.", color = Palette.Muted, fontSize = 13.sp)
                Section("Písmo")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((id, name) in FontChoices) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (a.font == id) look.accent.copy(alpha = 0.28f) else Palette.Text.copy(alpha = 0.07f))
                                .border(1.dp, if (a.font == id) look.accent else Palette.Text.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .clickable { AppearanceStore.update { it.copy(font = id) } }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(name, color = Palette.Text, fontSize = 16.sp, fontFamily = fontFamilyOf(id), maxLines = 1)
                        }
                    }
                }
                Section("Úvodní animace")
                SwitchRow("Přehrát po spuštění", a.intro) { v -> AppearanceStore.update { it.copy(intro = v) } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((id, name) in IntroStyles) {
                        Pill(name, a.introStyle == id) { AppearanceStore.update { it.copy(introStyle = id) } }
                    }
                }
                SliderRow("Délka", "${"%.1f".format(a.introSeconds)} s", a.introSeconds, 2f..8f) { v -> AppearanceStore.update { it.copy(introSeconds = (v * 2).roundToInt() / 2f) } }
                val ctx = LocalContext.current
                var logoNote by remember { mutableStateOf(if (IntroLogo.exists(ctx)) "Vlastní obrázek je nahraný." else "Vlastní obrázek zatím není nahraný.") }
                val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val ok = IntroLogo.save(ctx, uri)
                        logoNote = if (ok) "Vlastní obrázek je nahraný." else "Obrázek se nepodařilo načíst."
                        if (ok) AppearanceStore.update { it.copy(introStyle = "custom") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Vybrat obrázek…", false) { pickLogo.launch(arrayOf("image/*")) }
                    Pill("Smazat obrázek", false) { IntroLogo.delete(ctx); logoNote = "Vlastní obrázek zatím není nahraný." }
                    Pill("Přehrát náhled", true) { UiBus.close(); IntroState.preview = true }
                }
                Text("$logoNote Nejlépe PNG s průhledným pozadím. Logo Peugeot je ochranná známka Peugeot / Stellantis; obrázek si dodáváš sám a zůstává jen v rádiu.", color = Palette.Muted, fontSize = 13.sp)
                Section("Mapa")
                SwitchRow("Tmavá (noční) mapa", a.darkMap) { v -> AppearanceStore.update { it.copy(darkMap = v) } }
                for (group in MapSources.all.map { it.group }.distinct()) {
                    val note = when (group) {
                        "Mapy.com" -> "klíč Mapy.com"
                        "Google" -> "klíč Google, placené nad limit"
                        else -> "zdarma, bez klíče"
                    }
                    Text("$group · $note", color = Palette.Muted, fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (src in MapSources.all.filter { it.group == group }) {
                            Pill(src.name, a.mapStyle == src.id) {
                                AppearanceStore.update { it.copy(mapStyle = src.id) }
                                Tiles.clear()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text.uppercase(), color = LocalLook.current.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
}

@Composable
private fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = Palette.Muted, fontSize = 16.sp)
        }
        Slider(value = current.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Hledání cíle přes Mapy.com, rychlé cíle a poslední cíle. */
@Composable
fun DestinationPanel() {
    val context = LocalContext.current
    val look = LocalLook.current
    val key by Prefs.mapyKey.collectAsState()
    val home by Prefs.homeAddress.collectAsState()
    val work by Prefs.workAddress.collectAsState()
    val recent by Recent.list.collectAsState()
    val busy by RouteState.busy.collectAsState()
    val routeError by RouteState.error.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var info by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        info = null
        scope.launch {
            try {
                // S klíčem hledá Mapy.com, bez něj otevřená data OpenStreetMap (Nominatim).
                val list = withContext(Dispatchers.IO) {
                    if (key.isNotBlank()) Mapy.search(q, Gps.fix.value, key) else Nominatim.search(q, Gps.fix.value)
                }
                results = list
                if (list.isEmpty()) info = "Nic jsem nenašel"
            } catch (e: Exception) {
                info = "Hledání se nepovedlo: ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    fun plan(p: Place) {
        RouteState.plan(p) { ok -> if (ok) UiBus.close() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Adresa, město nebo místo") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }, enabled = !searching) { Text(if (searching) "Hledám…" else "Hledat", fontSize = 16.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (query.isNotBlank()) ActionChip(Icons.Filled.Navigation, "Navigovat na „${query.take(18)}“", true) { Navigator.go(context, query.trim()) }
            if (home.isNotBlank()) ActionChip(Icons.Filled.Home, "Domů", false) { Navigator.go(context, home) }
            if (work.isNotBlank()) ActionChip(Icons.Filled.Work, "Práce", false) { Navigator.go(context, work) }
        }
        val msg = info ?: routeError
        if (msg != null) {
            Spacer(Modifier.height(8.dp))
            Text(msg, color = Palette.Amber, fontSize = 15.sp)
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Text("Plánuji trasu…", color = look.accent, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (results.isEmpty() && recent.isNotEmpty()) {
                item { Text("NAPOSLEDY", color = look.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp) }
                items(recent) { p -> PlaceRow(p, Icons.Filled.History, true, { plan(p) }) { Navigator.go(context, p) } }
            }
            items(results) { p -> PlaceRow(p, Icons.Filled.Place, true, { plan(p) }) { Recent.add(p); Navigator.go(context, p) } }
            if (results.isEmpty() && recent.isEmpty()) {
                item {
                    Text(
                        "Napiš cíl. Hledám přes Mapy.com (s klíčem) nebo v otevřených datech OpenStreetMap (bez klíče) a ukážu trasu na mapě; " +
                            "tlačítkem Navigovat spustíš navigaci s hlasovými pokyny přímo v Dash 407 (nebo v aplikaci Mapy.com – volba v Nastavení). Adresu Domů a Práce nastavíš v Nastavení.",
                        color = Palette.Muted, fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    p: Place,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    canPlan: Boolean,
    onRoute: () -> Unit,
    onNavigate: () -> Unit
) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = look.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(p.label, p.location).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = Palette.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canPlan) {
            ActionChip(Icons.Filled.Directions, "Trasa", false) { onRoute() }
            Spacer(Modifier.width(6.dp))
        }
        ActionChip(Icons.Filled.Navigation, "Navigovat", true) { onNavigate() }
    }
}
