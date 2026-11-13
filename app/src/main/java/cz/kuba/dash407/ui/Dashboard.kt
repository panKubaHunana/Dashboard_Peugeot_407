package cz.kuba.dash407.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.data.DashLayout
import cz.kuba.dash407.data.PagePos
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.data.Slot
import cz.kuba.dash407.data.SlotEdit
import cz.kuba.dash407.data.Templates
import cz.kuba.dash407.data.WidgetGroup
import cz.kuba.dash407.data.WidgetType
import cz.kuba.dash407.data.rowsOf
import kotlin.math.abs

/** Obrazovky aplikace. DASH = domovské plochy, ostatní se otevírají přes celou plochu. */
enum class Screen { DASH, APPS, CAR, FUEL, SETTINGS, LOOK }

private enum class Overlay { NONE, CATALOG, TEMPLATES, PAGES }

fun pageName(pos: PagePos): Int = when (pos) {
    PagePos.HOME -> R.string.page_home
    PagePos.UP -> R.string.page_up
    PagePos.DOWN -> R.string.page_down
    PagePos.LEFT -> R.string.page_left
    PagePos.RIGHT -> R.string.page_right
}

/** Domovské plochy v kříži, spodní lišta a úpravy ploch. */
@Composable
fun Dashboard(page: PagePos, onPage: (PagePos) -> Unit, nav: (Screen) -> Unit) {
    val layout by Prefs.layout.collectAsState()
    val canUndo by Prefs.canUndo.collectAsState()
    val look = LookState.look
    var editing by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }

    BackHandler(enabled = editing || overlay != Overlay.NONE) {
        if (overlay != Overlay.NONE) overlay = Overlay.NONE else editing = false
    }

    val swipe = if (look.swipePages && !editing) {
        Modifier.pointerInput(page) {
            var total = Offset.Zero
            val threshold = 90.dp.toPx()
            detectDragGestures(
                onDragStart = { total = Offset.Zero },
                onDragEnd = {
                    val dx = total.x
                    val dy = total.y
                    val target = when {
                        abs(dx) > abs(dy) && abs(dx) > threshold -> page.neighbour(if (dx < 0) 1 else -1, 0)
                        abs(dy) > threshold -> page.neighbour(0, if (dy < 0) 1 else -1)
                        else -> null
                    }
                    if (target != null) onPage(target)
                },
                onDrag = { change, amount ->
                    change.consume()
                    total += amount
                }
            )
        }
    } else {
        Modifier
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().then(swipe)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val dc = targetState.col - initialState.col
                        val dr = targetState.row - initialState.row
                        when {
                            !look.animations -> EnterTransition.None togetherWith ExitTransition.None
                            dc != 0 -> (slideInHorizontally { it * dc } + fadeIn()) togetherWith (slideOutHorizontally { -it * dc } + fadeOut())
                            else -> (slideInVertically { it * dr } + fadeIn()) togetherWith (slideOutVertically { -it * dr } + fadeOut())
                        }
                    },
                    label = "page"
                ) { shown ->
                    PageView(
                        slots = layout.page(shown),
                        editing = editing,
                        nav = nav,
                        onFill = { overlay = Overlay.TEMPLATES },
                        onAdd = { overlay = Overlay.CATALOG },
                        onPages = { overlay = Overlay.PAGES },
                        onEdit = { change -> Prefs.updatePage(shown, change) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (editing) {
                EditBar(
                    page = page,
                    canUndo = canUndo,
                    onAdd = { overlay = Overlay.CATALOG },
                    onTemplates = { overlay = Overlay.TEMPLATES },
                    onClear = { Prefs.updatePage(page) { emptyList() } },
                    onDone = { editing = false }
                )
            } else {
                BottomBar(
                    page = page,
                    layout = layout,
                    nav = nav,
                    onEdit = { editing = true },
                    onPages = { overlay = Overlay.PAGES }
                )
            }
        }

        when (overlay) {
            Overlay.CATALOG -> CatalogOverlay(
                onAdd = { type ->
                    Prefs.updatePage(page) { SlotEdit.add(it, type) }
                    overlay = Overlay.NONE
                    editing = true
                },
                onClose = { overlay = Overlay.NONE }
            )
            Overlay.TEMPLATES -> TemplatesOverlay(
                page = page,
                onPick = { t ->
                    Prefs.updatePage(page) { t.build() }
                    overlay = Overlay.NONE
                },
                onClose = { overlay = Overlay.NONE }
            )
            Overlay.PAGES -> PagesOverlay(
                layout = layout,
                current = page,
                onPick = {
                    onPage(it)
                    overlay = Overlay.NONE
                },
                onClose = { overlay = Overlay.NONE }
            )
            Overlay.NONE -> Unit
        }
    }
}

// ---------------------------------------------------------------- Plocha

@Composable
private fun PageView(
    slots: List<Slot>,
    editing: Boolean,
    nav: (Screen) -> Unit,
    onFill: () -> Unit,
    onAdd: () -> Unit,
    onPages: () -> Unit,
    onEdit: ((List<Slot>) -> List<Slot>) -> Unit
) {
    if (slots.isEmpty()) {
        EmptyPage(editing, onFill, onAdd, onPages)
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val rows = rowsOf(slots)
        for (row in rows) {
            val h = row.maxOf { it.height }
            Row(Modifier.weight(h.toFloat()).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (slot in row) {
                    key(slot.id) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            WidgetHost(slot.type, Modifier.fillMaxSize(), nav)
                            if (editing) {
                                EditOverlay(
                                    slot = slot,
                                    first = slots.firstOrNull()?.id == slot.id,
                                    last = slots.lastOrNull()?.id == slot.id,
                                    onEdit = onEdit
                                )
                            }
                        }
                    }
                }
                if (row.size == 1 && !row[0].wide) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyPage(editing: Boolean, onFill: () -> Unit, onAdd: () -> Unit, onPages: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.empty_title), color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.empty_text),
            color = Palette.Muted,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onFill, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.empty_fill), fontSize = 17.sp)
            }
            OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.empty_add), fontSize = 17.sp)
            }
            if (!editing) {
                TextButton(onClick = onPages) { Text(stringResource(R.string.empty_all_pages), fontSize = 17.sp) }
            }
        }
    }
}

/** Ovládání jedné dlaždice v režimu úprav. */
@Composable
private fun EditOverlay(slot: Slot, first: Boolean, last: Boolean, onEdit: ((List<Slot>) -> List<Slot>) -> Unit) {
    val shape = RoundedCornerShape(Palette.Radius)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0xC20A0F1C))
            .border(2.dp, Palette.Accent, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(8.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(widgetName(slot.type)), color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                stringResource(if (slot.wide) R.string.edit_size_wide else R.string.edit_size_half, slot.height),
                color = Palette.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.edit_earlier), enabled = !first) {
                    onEdit { SlotEdit.move(it, slot.id, -1) }
                }
                EditButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.edit_later), enabled = !last) {
                    onEdit { SlotEdit.move(it, slot.id, 1) }
                }
                EditTextButton(if (slot.wide) "½" else "↔", stringResource(R.string.edit_width)) {
                    onEdit { SlotEdit.toggleWide(it, slot.id) }
                }
                EditButton(Icons.Filled.Remove, stringResource(R.string.edit_lower), enabled = slot.height > 1) {
                    onEdit { SlotEdit.resize(it, slot.id, -1) }
                }
                EditButton(Icons.Filled.Add, stringResource(R.string.edit_taller), enabled = slot.height < 4) {
                    onEdit { SlotEdit.resize(it, slot.id, 1) }
                }
                EditButton(Icons.Filled.Delete, stringResource(R.string.edit_remove), tint = Palette.Bad) {
                    onEdit { SlotEdit.remove(it, slot.id) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.EditButton(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color = Palette.Text, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x26FFFFFF))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(24.dp), tint = if (enabled) tint else Palette.Muted.copy(alpha = 0.4f))
    }
}

@Composable
private fun RowScope.EditTextButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x26FFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Color.Transparent, fontSize = 1.sp)
    }
}

// ---------------------------------------------------------------- Lišty

@Composable
private fun BottomBar(page: PagePos, layout: DashLayout, nav: (Screen) -> Unit, onEdit: () -> Unit, onPages: () -> Unit) {
    val look = LookState.look
    val now = rememberNow()
    val car by CarRepo.state.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(Palette.Radius),
        color = Palette.Tile,
        border = Palette.TileBorder,
        modifier = Modifier.fillMaxWidth().height(70.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BarButton(Icons.Filled.Apps, stringResource(R.string.tab_apps), look.barLabels) { nav(Screen.APPS) }
            BarButton(Icons.Filled.Dashboard, stringResource(R.string.bar_edit), look.barLabels) { onEdit() }
            Spacer(Modifier.weight(1f))
            Text(timeText(now, false), color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(14.dp))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPages() }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CrossIndicator(page, layout, 34.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(pageName(page)), color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, if (car.linked) Palette.Good else Palette.Border, RoundedCornerShape(18.dp))
                    .clickable { nav(Screen.CAR) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (car.linked) Palette.Good else Palette.Muted))
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.bar_car), color = if (car.linked) Palette.Text else Palette.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.bar_menu), tint = Palette.Text, modifier = Modifier.size(28.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(Icons.Filled.DirectionsCar, stringResource(R.string.tab_car)) { menu = false; nav(Screen.CAR) }
                    MenuItem(Icons.Filled.LocalGasStation, stringResource(R.string.tab_fuel)) { menu = false; nav(Screen.FUEL) }
                    MenuItem(Icons.Filled.Palette, stringResource(R.string.tab_look)) { menu = false; nav(Screen.LOOK) }
                    MenuItem(Icons.Filled.Settings, stringResource(R.string.tab_settings)) { menu = false; nav(Screen.SETTINGS) }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 18.sp) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null) }
    )
}

@Composable
private fun BarButton(icon: ImageVector, label: String, showLabel: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, Modifier.size(28.dp), tint = Palette.Text)
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Palette.Text, fontSize = 18.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EditBar(page: PagePos, canUndo: Boolean, onAdd: () -> Unit, onTemplates: () -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Palette.Radius),
        color = Palette.Tile,
        border = Palette.TileBorder,
        modifier = Modifier.fillMaxWidth().height(70.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.Accent))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.edit_title, stringResource(pageName(page))),
                color = Palette.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, stringResource(R.string.edit_add), tint = Palette.Text, modifier = Modifier.size(28.dp)) }
            IconButton(onClick = onTemplates) { Icon(Icons.Filled.Dashboard, stringResource(R.string.edit_templates), tint = Palette.Text, modifier = Modifier.size(28.dp)) }
            IconButton(onClick = { Prefs.undo() }, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.edit_undo), tint = if (canUndo) Palette.Text else Palette.Muted.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, stringResource(R.string.edit_clear), tint = Palette.Text, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color(0xFF06101C)),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.edit_done), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Ukazatel aktuální plochy: pět čtverečků do kříže. */
@Composable
fun CrossIndicator(current: PagePos, layout: DashLayout, size: Dp) {
    val accent = Palette.Accent
    val muted = Palette.Muted
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / 3f
        val gap = cell * 0.18f
        for (pos in PagePos.values()) {
            val topLeft = Offset(pos.col * cell + gap / 2f, pos.row * cell + gap / 2f)
            val box = Size(cell - gap, cell - gap)
            val radius = CornerRadius(cell * 0.22f)
            when {
                pos == current -> drawRoundRect(accent, topLeft, box, radius)
                layout.page(pos).isNotEmpty() -> drawRoundRect(muted, topLeft, box, radius, style = Stroke(cell * 0.12f))
                else -> drawRoundRect(muted.copy(alpha = 0.35f), topLeft, box, radius, style = Stroke(cell * 0.08f))
            }
        }
    }
}

// ---------------------------------------------------------------- Překryvné panely

@Composable
private fun OverlayPanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC05080F))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(Palette.Radius),
            color = Color(0xFF111827),
            border = Palette.TileBorder,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(12.dp)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Palette.Text) }
                }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

private fun widgetIcon(type: WidgetType): ImageVector = when (type) {
    WidgetType.MAP -> Icons.Filled.Map
    WidgetType.NAV -> Icons.Filled.Navigation
    WidgetType.CLOCK -> Icons.Filled.Schedule
    WidgetType.WEATHER -> Icons.Filled.WbSunny
    WidgetType.MEDIA -> Icons.Filled.MusicNote
    WidgetType.TRIP -> Icons.Filled.Timer
    WidgetType.SPEED -> Icons.Filled.Speed
    WidgetType.CAR -> Icons.Filled.DirectionsCar
    WidgetType.DOORS -> Icons.Filled.MeetingRoom
    WidgetType.FUEL -> Icons.Filled.LocalGasStation
    WidgetType.DOCK -> Icons.Filled.Apps
}

private fun groupName(group: WidgetGroup?): Int = when (group) {
    null -> R.string.group_all
    WidgetGroup.DRIVING -> R.string.group_driving
    WidgetGroup.NAVIGATION -> R.string.group_navigation
    WidgetGroup.VEHICLE -> R.string.group_vehicle
    WidgetGroup.INFO -> R.string.group_info
    WidgetGroup.MEDIA -> R.string.group_media
}

private fun groupColor(group: WidgetGroup): Color = when (group) {
    WidgetGroup.DRIVING -> Color(0xFF34D399)
    WidgetGroup.NAVIGATION -> Color(0xFF4F8BFF)
    WidgetGroup.VEHICLE -> Color(0xFFFFB547)
    WidgetGroup.INFO -> Color(0xFF4FC3F7)
    WidgetGroup.MEDIA -> Color(0xFFA78BFA)
}

/** Katalog dlaždic s filtrem podle skupin. */
@Composable
private fun CatalogOverlay(onAdd: (WidgetType) -> Unit, onClose: () -> Unit) {
    var filter by remember { mutableStateOf<WidgetGroup?>(null) }
    OverlayPanel(stringResource(R.string.catalog_title), onClose) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val groups: List<WidgetGroup?> = listOf<WidgetGroup?>(null) + WidgetGroup.values().toList()
            for (g in groups) {
                val selected = g == filter
                Text(
                    stringResource(groupName(g)),
                    color = if (selected) Color(0xFF06101C) else Palette.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Palette.Accent else Color(0x1FFFFFFF))
                        .clickable { filter = g }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val types = WidgetType.values().filter { filter == null || it.group == filter }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(types, key = { it.key }) { type ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A2335),
                    border = Palette.TileBorder,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onAdd(type) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(groupColor(type.group).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(widgetIcon(type), null, Modifier.size(44.dp), tint = groupColor(type.group))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(widgetName(type)), color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(widgetDescription(type)), color = Palette.Muted, fontSize = 14.sp, maxLines = 3)
                    }
                }
            }
        }
    }
}

private fun templateName(t: Templates): Int = when (t) {
    Templates.ROAD -> R.string.template_road
    Templates.MAP_MUSIC -> R.string.template_map_music
    Templates.CAR -> R.string.template_car
    Templates.MEDIA -> R.string.template_media
    Templates.BIG_MAP -> R.string.template_big_map
}

/** Výběr šablony pro aktuální plochu, s náhledem rozložení. */
@Composable
private fun TemplatesOverlay(page: PagePos, onPick: (Templates) -> Unit, onClose: () -> Unit) {
    OverlayPanel(stringResource(R.string.templates_title, stringResource(pageName(page))), onClose) {
        Text(stringResource(R.string.templates_hint), color = Palette.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(Templates.values().toList(), key = { it.name }) { t ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(t) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MiniLayout(t.preview(), Modifier.fillMaxWidth().aspectRatio(0.75f))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(templateName(t)), color = Palette.Text, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

/** Zmenšený náhled rozložení plochy (barvy podle skupin dlaždic). */
@Composable
private fun MiniLayout(slots: List<Slot>, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B1222)).border(1.dp, Palette.Border, RoundedCornerShape(12.dp)).padding(6.dp)) {
        if (slots.isEmpty()) return@Box
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in rowsOf(slots)) {
                Row(Modifier.weight(row.maxOf { it.height }.toFloat()).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (slot in row) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(groupColor(slot.type.group).copy(alpha = 0.75f))
                        )
                    }
                    if (row.size == 1 && !row[0].wide) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Přehled všech ploch v kříži; klepnutím se na plochu přejde. */
@Composable
private fun PagesOverlay(layout: DashLayout, current: PagePos, onPick: (PagePos) -> Unit, onClose: () -> Unit) {
    OverlayPanel(stringResource(R.string.pages_title), onClose) {
        Text(stringResource(R.string.pages_hint), color = Palette.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0..2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0..2) {
                        val pos = PagePos.values().firstOrNull { it.col == c && it.row == r }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (pos != null) {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            if (pos == current) 3.dp else 1.dp,
                                            if (pos == current) Palette.Accent else Palette.Border,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onPick(pos) }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    MiniLayout(layout.page(pos), Modifier.weight(1f).fillMaxWidth())
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(pageName(pos)),
                                        color = if (pos == current) Palette.Accent else Palette.Text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
