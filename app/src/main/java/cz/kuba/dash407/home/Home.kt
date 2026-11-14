package cz.kuba.dash407.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.backgroundBrush
import kotlin.math.abs
import kotlin.math.roundToInt

/** Domovská obrazovka: plochy v kříži, spodní lišta a režim úprav. */
@Composable
fun HomeRoot() {
    val look = LocalLook.current
    val pages by LayoutStore.pages.collectAsState()
    val slot = UiBus.slot
    val editing = UiBus.editing
    val undo = remember { mutableStateListOf<Pair<Slot, List<Cell>>>() }
    val currentPages by rememberUpdatedState(pages)

    fun go(dx: Int, dy: Int) {
        val now = UiBus.slot
        // Vypnuté plochy se přeskočí (třeba když je plocha „dole“ vypnutá, tah vede rovnou do Menu).
        var x = now.dx
        var y = now.dy
        for (step in 0 until 3) {
            x += dx
            y += dy
            val target = Slot.at(x, y) ?: return
            if (target == Slot.MENU || currentPages.firstOrNull { it.slot == target }?.enabled == true) {
                UiBus.selected = null
                UiBus.slot = target
                return
            }
        }
    }

    Column(Modifier.fillMaxSize().background(backgroundBrush(look.background, look.accent))) {
        Box(
            Modifier.weight(1f).fillMaxWidth().then(
                if (editing) Modifier else Modifier.pointerInput(Unit) {
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDragEnd = {
                            val limit = 90.dp.toPx()
                            if (abs(total.x) > abs(total.y) && abs(total.x) > limit) {
                                // Tah prstem doleva = plocha vpravo (obsah ujede doleva).
                                go(if (total.x < 0) 1 else -1, 0)
                            } else if (abs(total.y) > limit) {
                                go(0, if (total.y < 0) 1 else -1)
                            }
                        }
                    ) { change, amount ->
                        total += amount
                        if (total.getDistance() > 24f) change.consume()
                    }
                }
            )
        ) {
            AnimatedContent(
                targetState = slot,
                transitionSpec = { pageTransition(initialState, targetState, look.animations) },
                label = "plochy"
            ) { s ->
                if (s == Slot.MENU) {
                    MenuPage(pages)
                } else {
                    val page = pages.firstOrNull { it.slot == s } ?: pages.first()
                    PageGrid(page, editing, undo)
                }
            }
            if (!editing) PageHint(pages, slot, Modifier.align(Alignment.BottomCenter))
        }
        // Spodní lišta zmizela (místo pro widgety); její tlačítka jsou na ploše Menu. Zůstává jen lišta úprav.
        if (editing && slot != Slot.MENU) EditBar(pages, undo)
    }

    when (UiBus.panel) {
        Panel.PAGES -> PagesDialog(pages)
        else -> Unit
    }
}

private fun pageTransition(from: Slot, to: Slot, animate: Boolean): ContentTransform {
    if (!animate) return EnterTransition.None togetherWith ExitTransition.None
    val dx = to.dx - from.dx
    val dy = to.dy - from.dy
    val spec = tween<androidx.compose.ui.unit.IntOffset>(320)
    return if (dx != 0) {
        (slideInHorizontally(spec) { it * dx } + fadeIn(tween(320))) togetherWith
            (slideOutHorizontally(spec) { -it * dx } + fadeOut(tween(320)))
    } else {
        (slideInVertically(spec) { it * dy } + fadeIn(tween(320))) togetherWith
            (slideOutVertically(spec) { -it * dy } + fadeOut(tween(320)))
    }
}

/** Mřížka 4 × 12 s widgety jedné plochy. V režimu úprav jde widget posunout a roztáhnout. */
@Composable
fun PageGrid(page: Page, editing: Boolean, undo: MutableList<Pair<Slot, List<Cell>>>) {
    val look = LocalLook.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(look.gap / 2)) {
        val cw = maxWidth / LayoutStore.COLS
        val ch = maxHeight / LayoutStore.ROWS
        if (editing && UiBus.showGrid) {
            Canvas(Modifier.fillMaxSize()) {
                val cwp = size.width / LayoutStore.COLS
                val chp = size.height / LayoutStore.ROWS
                for (i in 0..LayoutStore.COLS) drawLine(Color.White.copy(alpha = 0.10f), Offset(i * cwp, 0f), Offset(i * cwp, size.height), 1.5f)
                for (j in 0..LayoutStore.ROWS) drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, j * chp), Offset(size.width, j * chp), 1.5f)
            }
        }
        if (editing) {
            // Klepnutí do prázdna zruší výběr.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { UiBus.selected = null } })
        }
        if (page.cells.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Plocha „${page.name}“ je prázdná", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (editing) "Klepni na + a přidej widget" else "V Menu klepni na Rozložení a přidej widgety",
                    color = Palette.Muted, fontSize = 15.sp
                )
            }
        }
        for (cell in page.cells) {
            key(cell.id) {
                CellBox(page, cell, editing, cw, ch, undo)
            }
        }
    }
}

@Composable
private fun CellBox(
    page: Page,
    cell: Cell,
    editing: Boolean,
    cw: androidx.compose.ui.unit.Dp,
    ch: androidx.compose.ui.unit.Dp,
    undo: MutableList<Pair<Slot, List<Cell>>>
) {
    val look = LocalLook.current
    val density = LocalDensity.current
    val type = WidgetType.of(cell.type) ?: return
    val current by rememberUpdatedState(cell)
    val currentPage by rememberUpdatedState(page)
    var drag by remember { mutableStateOf(Offset.Zero) }
    var grow by remember { mutableStateOf(Offset.Zero) }
    val selected = editing && UiBus.selected == cell.id
    val cwPx = with(density) { cw.toPx() }
    val chPx = with(density) { ch.toPx() }

    fun commit(next: Cell) {
        val p = currentPage
        if (next == current || !LayoutStore.fits(p.cells, next)) return
        undo.add(p.slot to p.cells)
        LayoutStore.setCells(p.slot, p.cells.map { if (it.id == next.id) next else it })
    }

    val wDp = with(density) { (cell.w * cwPx + grow.x).coerceAtLeast(cwPx * 0.8f).toDp() }
    val hDp = with(density) { (cell.h * chPx + grow.y).coerceAtLeast(chPx * 0.8f).toDp() }
    Box(
        Modifier
            .offset { IntOffset((cell.x * cwPx + drag.x).roundToInt(), (cell.y * chPx + drag.y).roundToInt()) }
            .size(wDp, hDp)
            .zIndex(if (drag != Offset.Zero || grow != Offset.Zero) 2f else if (selected) 1f else 0f)
            .padding(look.gap / 2)
    ) {
        val base = LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides Density(base.density, base.fontScale * cell.scale)
        ) {
            WidgetContent(type, cell, Modifier.fillMaxSize(), preview = editing)
        }
        if (editing) {
            val shape = RoundedCornerShape(look.corner)
            Box(
                Modifier.fillMaxSize()
                    .clip(shape)
                    .background(Color.Black.copy(alpha = if (selected) 0.05f else 0.22f))
                    .border(if (selected) 3.dp else 1.dp, if (selected) look.accent else Color.White.copy(alpha = 0.35f), shape)
                    .pointerInput(cell.id) { detectTapGestures { UiBus.selected = current.id } }
                    .pointerInput(cell.id, cwPx, chPx) {
                        detectDragGestures(
                            onDragStart = { UiBus.selected = current.id },
                            onDragEnd = {
                                val c = current
                                val nx = ((c.x * cwPx + drag.x) / cwPx).roundToInt().coerceIn(0, LayoutStore.COLS - c.w)
                                val ny = ((c.y * chPx + drag.y) / chPx).roundToInt().coerceIn(0, LayoutStore.ROWS - c.h)
                                commit(c.copy(x = nx, y = ny))
                                drag = Offset.Zero
                            },
                            onDragCancel = { drag = Offset.Zero }
                        ) { change, amount ->
                            change.consume()
                            drag += amount
                        }
                    }
            )
            if (selected) {
                Text(
                    type.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                        .background(look.accent.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(40.dp).clip(CircleShape)
                        .background(look.accent)
                        .pointerInput(cell.id, cwPx, chPx) {
                            detectDragGestures(
                                onDragEnd = {
                                    val c = current
                                    val nw = ((c.w * cwPx + grow.x) / cwPx).roundToInt().coerceIn(type.minW, LayoutStore.COLS - c.x)
                                    val nh = ((c.h * chPx + grow.y) / chPx).roundToInt().coerceIn(type.minH, LayoutStore.ROWS - c.y)
                                    commit(c.copy(w = nw, h = nh))
                                    grow = Offset.Zero
                                },
                                onDragCancel = { grow = Offset.Zero }
                            ) { change, amount ->
                                change.consume()
                                grow += amount
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.OpenInFull, "Změnit velikost", tint = Color(0xFF06121C), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Po přechodu na jinou plochu se na 2,5 s ukáže kříž s názvem plochy, pak zmizí. */
@Composable
private fun PageHint(pages: List<Page>, slot: Slot, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(slot) {
        visible = true
        kotlinx.coroutines.delay(2500)
        visible = false
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(600)), modifier = modifier.padding(bottom = 14.dp)
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CrossIndicator(pages, slot, 8.dp)
            Spacer(Modifier.width(10.dp))
            val name = if (slot == Slot.MENU) "Menu" else pages.firstOrNull { it.slot == slot }?.name ?: ""
            Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Kříž se zvýrazněnou aktuální plochou (jako v Dashwheel); pod ním malé Menu. */
@Composable
fun CrossIndicator(pages: List<Page>, current: Slot, cell: androidx.compose.ui.unit.Dp = 10.dp, onClick: (() -> Unit)? = null) {
    val look = LocalLook.current
    val gap = cell * 0.25f
    Column(
        Modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(4.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in -1..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in -1..1) {
                    val s = Slot.at(col, row)
                    val page = s?.let { sl -> pages.firstOrNull { it.slot == sl } }
                    val shape = RoundedCornerShape(cell * 0.28f)
                    val m = Modifier.size(cell)
                    when {
                        s == null -> Spacer(m)
                        s == current -> Box(m.clip(shape).background(look.accent))
                        s == Slot.MENU -> Box(m.clip(CircleShape).border(1.5.dp, look.accent.copy(alpha = 0.8f), CircleShape))
                        page?.enabled == true -> Box(m.clip(shape).border(1.5.dp, Color.White.copy(alpha = 0.7f), shape))
                        else -> Box(m.clip(shape).border(1.dp, Color.White.copy(alpha = 0.18f), shape))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, text: String?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, text, tint = Palette.Text, modifier = Modifier.size(26.dp))
        if (text != null) {
            Spacer(Modifier.width(6.dp))
            Text(text, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Lišta režimu úprav: přidat, zpět, mřížka, velikost písma, smazat, duplikovat, hotovo. */
@Composable
fun EditBar(pages: List<Page>, undo: MutableList<Pair<Slot, List<Cell>>>) {
    val look = LocalLook.current
    val context = LocalContext.current
    val slot = UiBus.slot
    val page = pages.first { it.slot == slot }
    val selected = page.cells.firstOrNull { it.id == UiBus.selected }
    var rename by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(look.corner.coerceAtMost(30.dp)))
            .background(look.cardHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(look.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                "Upravuji: ${page.name}", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { rename = true }.padding(4.dp)
            )
            Icon(Icons.Filled.Edit, "Přejmenovat", tint = Palette.Muted, modifier = Modifier.size(20.dp).clickable { rename = true })
            Spacer(Modifier.weight(1f))
            Text(
                if (selected != null) "Táhni widget, roh mění velikost" else "Klepni na widget",
                color = Palette.Muted, fontSize = 14.sp, maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            CrossIndicator(pages, slot, 9.dp) { UiBus.open(Panel.PAGES) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BarButton(Icons.Filled.Add, null) {
                UiBus.pickerTab = 0
                UiBus.open(Panel.PICKER)
            }
            BarButton(Icons.AutoMirrored.Filled.Undo, null) {
                val last = undo.removeLastOrNull()
                if (last != null) LayoutStore.setCells(last.first, last.second)
            }
            BarButton(Icons.Filled.GridOn, null) { UiBus.showGrid = !UiBus.showGrid }
            BarButton(Icons.Filled.TextFields, null) {
                if (selected == null) toast(context, "Nejdřív klepni na widget")
                else {
                    val steps = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
                    val i = steps.indexOfFirst { abs(it - selected.scale) < 0.01f }
                    val next = steps[(i + 1) % steps.size]
                    undo.add(slot to page.cells)
                    LayoutStore.setCells(slot, page.cells.map { if (it.id == selected.id) it.copy(scale = next) else it })
                    toast(context, "Velikost textu ${(next * 100).roundToInt()} %")
                }
            }
            BarButton(Icons.Filled.Delete, null) {
                if (selected == null) toast(context, "Nejdřív klepni na widget")
                else {
                    undo.add(slot to page.cells)
                    LayoutStore.setCells(slot, page.cells.filterNot { it.id == selected.id })
                    UiBus.selected = null
                }
            }
            BarButton(Icons.Filled.ContentCopy, null) {
                if (selected == null) toast(context, "Nejdřív klepni na widget")
                else {
                    val spot = LayoutStore.findSpace(page.cells, selected.w, selected.h)
                    if (spot == null) toast(context, "Na ploše není místo pro kopii")
                    else {
                        undo.add(slot to page.cells)
                        val copy = selected.copy(id = LayoutStore.newId(), x = spot.first, y = spot.second)
                        LayoutStore.setCells(slot, page.cells + copy)
                        UiBus.selected = copy.id
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(look.accent)
                    .clickable {
                        UiBus.editing = false
                        UiBus.selected = null
                        undo.clear()
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Check, null, tint = Color(0xFF06121C), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hotovo", color = Color(0xFF06121C), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (rename) {
        var text by remember { mutableStateOf(page.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Název plochy") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(20) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { LayoutStore.rename(slot, text.trim()); rename = false }) { Text("Uložit") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Zrušit") } }
        )
    }
}

/** Výběr plochy: velký kříž, vypnuté plochy jde zapnout. */
@Composable
fun PagesDialog(pages: List<Page>) {
    val look = LocalLook.current
    AlertDialog(
        onDismissRequest = { UiBus.close() },
        title = { Text("Plochy") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Mezi plochami přejíždíš tahem prstu. Menu s nastavením je pod plochou dole. Klepni na plochu, na kterou chceš přejít.", color = Palette.Muted, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                for (row in -1..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in -1..1) {
                            val s = Slot.at(col, row)
                            val page = s?.let { sl -> pages.firstOrNull { it.slot == sl } }
                            if (s == null || page == null) {
                                Spacer(Modifier.size(104.dp, 72.dp))
                            } else {
                                val current = s == UiBus.slot
                                Box(
                                    Modifier.size(104.dp, 72.dp).clip(RoundedCornerShape(14.dp))
                                        .background(if (current) look.accent else Palette.Text.copy(alpha = if (page.enabled) 0.12f else 0.04f))
                                        .clickable {
                                            if (!page.enabled) LayoutStore.setEnabled(s, true)
                                            UiBus.selected = null
                                            UiBus.slot = s
                                            UiBus.close()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            page.name, color = if (current) Color(0xFF06121C) else Palette.Text,
                                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                        )
                                        if (!page.enabled) Text("zapnout", color = Palette.Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val onMenu = UiBus.slot == Slot.MENU
                Box(
                    Modifier.size(104.dp, 56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (onMenu) look.accent else Palette.Text.copy(alpha = 0.12f))
                        .clickable {
                            UiBus.selected = null
                            UiBus.slot = Slot.MENU
                            UiBus.close()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Menu", color = if (onMenu) Color(0xFF06121C) else Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = { UiBus.close() }) { Text("Zavřít") } }
    )
}
