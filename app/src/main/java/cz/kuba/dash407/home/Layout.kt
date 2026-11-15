package cz.kuba.dash407.home

import cz.kuba.dash407.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Pět ploch uspořádaných do kříže: střed a čtyři strany. */
enum class Slot(val dx: Int, val dy: Int, val where: String) {
    CENTER(0, 0, "uprostřed"),
    UP(0, -1, "nahoře"),
    DOWN(0, 1, "dole"),
    LEFT(-1, 0, "vlevo"),
    RIGHT(1, 0, "vpravo"),
    /** Speciální plocha Menu pod plochou „dole“ – nemá widgety, nahrazuje spodní lištu. */
    MENU(0, 2, "menu");

    companion object {
        fun at(dx: Int, dy: Int): Slot? = values().firstOrNull { it.dx == dx && it.dy == dy }
    }
}

/** Jeden widget na ploše: typ a obdélník v mřížce (sloupec, řádek, šířka, výška). */
data class Cell(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val scale: Float = 1f
) {
    fun overlaps(o: Cell): Boolean = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
}

data class Page(val slot: Slot, val name: String, val enabled: Boolean, val cells: List<Cell>)

/** Převod z původní mřížky 4 × 12 na aktuální čtverečkovou mřížku (24 sloupců, řádků podle plochy). */
object Grid {
    const val OLD_COLS = 4
    const val OLD_ROWS = 12
    fun col(v: Int): Int = v * LayoutStore.COLS / OLD_COLS
    /** Hrana řádku: zaokrouhlení zachová, že se sousední widgety nepřekrývají ani nevznikají mezery. */
    fun row(v: Int): Int = (v * LayoutStore.ROWS + OLD_ROWS / 2) / OLD_ROWS
    fun cell(c: Cell): Cell {
        val y = row(c.y)
        val x = col(c.x)
        return c.copy(x = x, y = y, w = (col(c.x + c.w) - x).coerceAtLeast(1), h = (row(c.y + c.h) - y).coerceAtLeast(1))
    }
}

/** Rozložení všech ploch, uložené jako JSON. */
object LayoutStore {
    /** 24 sloupců; počet řádků se dopočítá z tvaru plochy, aby buňky byly čtverce (verze 2.11). */
    const val COLS = 24
    private const val ROWS_KEY = "layout_rows"
    /** Počet řádků mřížky; mění se jen při změně tvaru plochy (např. skryté lišty rádia). */
    var ROWS by androidx.compose.runtime.mutableIntStateOf(32)
        private set
    private const val GRID_FLAG = "layout_grid_8x32"
    /** Rozložení už je ve 24 sloupcích. */
    private const val COLS24_FLAG = "layout_grid_24cols"
    /** Domů jednorázově podle vzoru pro skutečný tvar obrazovky (2.11). */
    private const val HOME_FLAG = "layout_home_v211"
    /** Rozložení už je ve čtverečkové mřížce 16 × 32. */
    private const val SQUARE_FLAG = "layout_grid_16x32"
    private const val KEY = "layout_v1"

    private val _pages = MutableStateFlow(defaults())
    val pages: StateFlow<List<Page>> = _pages
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        ROWS = try { Prefs.shared().getInt(ROWS_KEY, 32) } catch (e: Exception) { 32 }
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null }
        if (raw == null) {
            // Nová instalace: výchozí rozložení už je v aktuální mřížce.
            _pages.value = defaults()
            try {
                Prefs.shared().edit().putBoolean(GRID_FLAG, true).putBoolean(SQUARE_FLAG, true)
                    .putBoolean(COLS24_FLAG, true).putBoolean(HOME_FLAG, true).apply()
            } catch (e: Exception) { }
            return
        }
        try {
            val a = JSONArray(raw)
            val list = ArrayList<Page>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val slot = Slot.valueOf(o.getString("slot"))
                val cs = o.getJSONArray("cells")
                val cells = (0 until cs.length()).map {
                    val c = cs.getJSONObject(it)
                    Cell(
                        c.getString("id"), c.getString("type"),
                        c.getInt("x"), c.getInt("y"), c.getInt("w"), c.getInt("h"),
                        c.optDouble("scale", 1.0).toFloat()
                    )
                }
                list.add(Page(slot, o.optString("name", slot.where), o.optBoolean("enabled", true), cells))
            }
            // Chybějící plochy doplníme z výchozího rozložení.
            val merged = defaults().map { d -> list.firstOrNull { it.slot == d.slot } ?: d }
            _pages.value = merged.map { if (it.slot == Slot.CENTER) it.copy(enabled = true) else it }
            migrateV2()
            migrateGrid()
            migrateSquare()
            migrate24()
            migrateHome()
        } catch (e: Exception) {
            _pages.value = defaults()
        }
    }

    fun page(slot: Slot): Page = _pages.value.first { it.slot == slot }

    /**
     * Jednorázový převod na verzi 2.0: widgety Palubní počítač a Jízda se sloučily do jednoho.
     * Na místě Palubního počítače bude sloučený widget, na místě Jízdy Rychlá volba
     * (když se tam nevejde, Servis). Když je na ploše jen jeden z nich, nahradí se sloučeným.
     */
    private fun migrateV2() {
        val flag = "layout_migrated_v2"
        if (Prefs.shared().getBoolean(flag, false)) return
        val comp = WidgetType.CAR_COMPUTER.name
        val trip = WidgetType.TRIP.name
        _pages.value = _pages.value.map { p ->
            val hasComp = p.cells.any { it.type == comp }
            val hasTrip = p.cells.any { it.type == trip }
            if (!hasComp && !hasTrip) return@map p
            val mergedId = (p.cells.firstOrNull { it.type == comp } ?: p.cells.first { it.type == trip }).id
            p.copy(cells = p.cells.map { c ->
                when {
                    c.id == mergedId -> c.copy(type = WidgetType.TRIP_COMPUTER.name)
                    c.type == trip -> c.copy(type = if (c.w >= 2 && c.h >= 3) WidgetType.PHONE_DIAL.name else WidgetType.SERVICE.name)
                    else -> c
                }
            })
        }
        save()
        Prefs.shared().edit().putBoolean(flag, true).apply()
    }

    /** Jednorázový převod uloženého rozložení z mřížky 4 × 12 rovnou na 16 × 32. */
    private fun migrateGrid() {
        if (Prefs.shared().getBoolean(GRID_FLAG, false)) return
        _pages.value = _pages.value.map { p -> p.copy(cells = p.cells.map { Grid.cell(it) }) }
        save()
        Prefs.shared().edit().putBoolean(GRID_FLAG, true).putBoolean(SQUARE_FLAG, true).putBoolean(COLS24_FLAG, true).apply()
    }

    /** Jednorázový převod z 16 na 24 sloupců (hrany × 1,5), widgety zůstanou na místě. */
    private fun migrate24() {
        if (Prefs.shared().getBoolean(COLS24_FLAG, false)) return
        _pages.value = _pages.value.map { p -> p.copy(cells = p.cells.map { scaleCols(it, 16) }) }
        save()
        Prefs.shared().edit().putBoolean(COLS24_FLAG, true).apply()
    }

    /** Jednorázově: plocha Domů podle vzoru (všech šest widgetů) pro skutečný tvar obrazovky rádia. */
    private fun migrateHome() {
        if (Prefs.shared().getBoolean(HOME_FLAG, false)) return
        val home = defaults().first { it.slot == Slot.CENTER }
        _pages.value = _pages.value.map { if (it.slot == Slot.CENTER) it.copy(cells = home.cells) else it }
        save()
        Prefs.shared().edit().putBoolean(HOME_FLAG, true).apply()
    }

    private fun edge(v: Int, from: Int, to: Int): Int = (v * to + from / 2) / from

    private fun scaleCols(c: Cell, from: Int): Cell {
        val x = edge(c.x, from, COLS)
        return c.copy(x = x, w = (edge(c.x + c.w, from, COLS) - x).coerceAtLeast(1))
    }

    private fun scaleRows(c: Cell, from: Int, to: Int): Cell {
        val y = edge(c.y, from, to)
        return c.copy(y = y, h = (edge(c.y + c.h, from, to) - y).coerceAtLeast(1))
    }

    /**
     * Nový počet řádků podle tvaru plochy: všechny plochy se přepočítají (hrany úměrně),
     * takže widgety zůstanou na stejném místě a ve stejné velikosti, jen buňky jsou čtverce.
     */
    fun setRows(rows: Int) {
        val old = ROWS
        if (rows == old || rows < 8) return
        ROWS = rows
        _pages.value = _pages.value.map { p -> p.copy(cells = p.cells.map { scaleRows(it, old, rows) }) }
        save()
        try { Prefs.shared().edit().putInt(ROWS_KEY, rows).apply() } catch (e: Exception) { }
    }

    /** Jednorázový převod z 8 × 32 na 16 × 32: každý sloupec se rozdělí napůl, widgety zůstanou na místě. */
    private fun migrateSquare() {
        if (Prefs.shared().getBoolean(SQUARE_FLAG, false)) return
        _pages.value = _pages.value.map { p -> p.copy(cells = p.cells.map { it.copy(x = it.x * 2, w = it.w * 2) }) }
        save()
        Prefs.shared().edit().putBoolean(SQUARE_FLAG, true).apply()
    }

    private fun save() {
        val a = JSONArray()
        for (p in _pages.value) {
            val cs = JSONArray()
            for (c in p.cells) {
                cs.put(
                    JSONObject().put("id", c.id).put("type", c.type)
                        .put("x", c.x).put("y", c.y).put("w", c.w).put("h", c.h)
                        .put("scale", c.scale.toDouble())
                )
            }
            a.put(JSONObject().put("slot", p.slot.name).put("name", p.name).put("enabled", p.enabled).put("cells", cs))
        }
        try {
            Prefs.shared().edit().putString(KEY, a.toString()).apply()
        } catch (e: Exception) {
            // Neuloženo.
        }
    }

    private fun change(slot: Slot, f: (Page) -> Page) {
        _pages.value = _pages.value.map { if (it.slot == slot) f(it) else it }
        save()
    }

    fun setCells(slot: Slot, cells: List<Cell>) = change(slot) { it.copy(cells = cells) }
    fun rename(slot: Slot, name: String) = change(slot) { it.copy(name = name.ifBlank { it.name }) }
    fun setEnabled(slot: Slot, enabled: Boolean) = change(slot) { it.copy(enabled = enabled || slot == Slot.CENTER) }

    fun resetAll() {
        _pages.value = defaults()
        save()
    }

    /** Volné místo pro widget dané velikosti, nebo null. */
    fun findSpace(cells: List<Cell>, w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0..ROWS - h) for (x in 0..COLS - w) {
            val probe = Cell("_", "_", x, y, w, h)
            if (cells.none { it.overlaps(probe) }) return x to y
        }
        return null
    }

    fun fits(cells: List<Cell>, c: Cell): Boolean =
        c.x >= 0 && c.y >= 0 && c.w >= 1 && c.h >= 1 && c.x + c.w <= COLS && c.y + c.h <= ROWS &&
            cells.none { it.id != c.id && it.overlaps(c) }

    private var counter = 0
    fun newId(): String = "w" + System.currentTimeMillis().toString(36) + (counter++)

    // Výchozí rozložení je zapsané v původní mřížce 4 × 12 a převádí se.
    private fun c(type: WidgetType, x: Int, y: Int, w: Int, h: Int) = Grid.cell(Cell(newId(), type.name, x, y, w, h))

    // Widget zapsaný v mřížce 24 × 21 (tvar plochy rádia s lištami); řádky se přepočítají na aktuální.
    private fun f(type: WidgetType, x: Int, y: Int, w: Int, h: Int) = scaleRows(Cell(newId(), type.name, x, y, w, h), 21, ROWS)

    fun defaults(): List<Page> = listOf(
        Page(
            Slot.CENTER, "Domů", true, listOf(
                // Vzor z 15. 11. 2026 upravený na skutečný tvar plochy rádia (asi 750 × 680 px).
                f(WidgetType.PHONE_DIAL, 0, 0, 3, 21),
                f(WidgetType.SPEED_GAUGE, 3, 0, 10, 8),
                f(WidgetType.TRIP_COMPUTER, 13, 0, 11, 8),
                f(WidgetType.WEATHER, 3, 8, 13, 7),
                f(WidgetType.MEDIA, 16, 8, 8, 7),
                f(WidgetType.DOORS, 3, 15, 21, 6)
            )
        ),
        Page(
            Slot.UP, "Mapa", true, listOf(
                c(WidgetType.MAP, 0, 0, 4, 10),
                c(WidgetType.DIRECTIONS, 0, 10, 2, 2),
                c(WidgetType.SPEED_LIMIT, 2, 10, 1, 2),
                c(WidgetType.SPEED, 3, 10, 1, 2)
            )
        ),
        Page(
            Slot.LEFT, "Auto", true, listOf(
                c(WidgetType.TRIP_COMPUTER, 0, 0, 2, 4),
                c(WidgetType.SPEED_GAUGE, 2, 0, 2, 4),
                c(WidgetType.DOORS, 0, 4, 2, 4),
                c(WidgetType.VOLTAGE, 2, 4, 2, 2),
                c(WidgetType.FUEL, 2, 6, 2, 2),
                c(WidgetType.CLOCK, 0, 8, 4, 2),
                c(WidgetType.DOCK, 0, 10, 4, 2)
            )
        ),
        Page(
            Slot.RIGHT, "Hudba", true, listOf(
                c(WidgetType.MEDIA, 0, 0, 4, 6),
                c(WidgetType.CLOCK, 0, 6, 4, 3),
                c(WidgetType.WEATHER, 0, 9, 2, 3),
                c(WidgetType.DIRECTIONS, 2, 9, 2, 3)
            )
        ),
        Page(
            Slot.DOWN, "Jízda", true, listOf(
                c(WidgetType.SPEED, 0, 0, 2, 3),
                c(WidgetType.COMPASS, 2, 0, 2, 3),
                c(WidgetType.GPS, 0, 3, 2, 3),
                c(WidgetType.GFORCE, 2, 3, 2, 3),
                c(WidgetType.PERF, 0, 6, 4, 3),
                c(WidgetType.SPEED_LIMIT, 0, 9, 2, 3),
                c(WidgetType.PHONE_DIAL, 2, 9, 2, 3)
            )
        )
    )
}
