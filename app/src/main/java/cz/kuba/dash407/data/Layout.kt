package cz.kuba.dash407.data

import org.json.JSONArray
import org.json.JSONObject

/** Skupiny dlaždic v katalogu (filtr nahoře v „Přidat dlaždici“). */
enum class WidgetGroup { DRIVING, NAVIGATION, VEHICLE, INFO, MEDIA }

/**
 * Druhy dlaždic. [wide] = výchozí šířka (true = přes celý řádek),
 * [height] = výchozí výška v jednotkách 1–4.
 */
enum class WidgetType(val key: String, val group: WidgetGroup, val wide: Boolean, val height: Int) {
    MAP("map", WidgetGroup.NAVIGATION, true, 4),
    NAV("nav", WidgetGroup.NAVIGATION, false, 2),
    CLOCK("clock", WidgetGroup.INFO, false, 2),
    WEATHER("weather", WidgetGroup.INFO, false, 2),
    MEDIA("media", WidgetGroup.MEDIA, false, 2),
    TRIP("trip", WidgetGroup.DRIVING, false, 2),
    SPEED("speed", WidgetGroup.DRIVING, false, 2),
    CAR("car", WidgetGroup.VEHICLE, false, 2),
    DOORS("doors", WidgetGroup.VEHICLE, false, 2),
    FUEL("fuel", WidgetGroup.VEHICLE, false, 2),
    DOCK("dock", WidgetGroup.INFO, true, 1);

    companion object {
        fun of(key: String): WidgetType? = values().firstOrNull { it.key == key }
    }
}

/** Jedna dlaždice na ploše. */
data class Slot(val id: Long, val type: WidgetType, val wide: Boolean, val height: Int)

/**
 * Plochy rozmístěné do kříže. [col]/[row] jsou souřadnice v mřížce 3 × 3,
 * Domů je uprostřed. Přejetím prstem se jde na sousední plochu.
 */
enum class PagePos(val key: String, val col: Int, val row: Int) {
    UP("up", 1, 0),
    LEFT("left", 0, 1),
    HOME("home", 1, 1),
    RIGHT("right", 2, 1),
    DOWN("down", 1, 2);

    /** Soused ve směru (dc, dr), nebo null, když tam žádná plocha není. */
    fun neighbour(dc: Int, dr: Int): PagePos? = values().firstOrNull { it.col == col + dc && it.row == row + dr }

    companion object {
        fun of(key: String): PagePos? = values().firstOrNull { it.key == key }
    }
}

/** Rozložení všech ploch. */
data class DashLayout(val pages: Map<PagePos, List<Slot>>) {
    fun page(pos: PagePos): List<Slot> = pages[pos] ?: emptyList()

    fun with(pos: PagePos, slots: List<Slot>): DashLayout = DashLayout(pages + (pos to slots))

    fun toJson(): String {
        val root = JSONObject()
        for ((pos, slots) in pages) {
            val array = JSONArray()
            for (s in slots) {
                array.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("type", s.type.key)
                        .put("wide", s.wide)
                        .put("h", s.height)
                )
            }
            root.put(pos.key, array)
        }
        return root.toString()
    }

    companion object {
        fun fromJson(text: String?): DashLayout? {
            if (text.isNullOrBlank()) return null
            return try {
                val root = JSONObject(text)
                val map = LinkedHashMap<PagePos, List<Slot>>()
                for (pos in PagePos.values()) {
                    val array = root.optJSONArray(pos.key) ?: continue
                    val list = ArrayList<Slot>()
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val type = WidgetType.of(o.optString("type")) ?: continue
                        list.add(Slot(o.optLong("id", nextId()), type, o.optBoolean("wide", type.wide), o.optInt("h", type.height).coerceIn(1, 4)))
                    }
                    map[pos] = list
                }
                DashLayout(map)
            } catch (e: Exception) {
                null
            }
        }

        private var counter = System.currentTimeMillis()

        @Synchronized
        fun nextId(): Long = ++counter

        fun slot(type: WidgetType, wide: Boolean = type.wide, height: Int = type.height): Slot =
            Slot(nextId(), type, wide, height)

        /** Výchozí plochy po prvním spuštění. Domů odpovídá návrhu z obrázku. */
        fun default(): DashLayout = DashLayout(
            mapOf(
                PagePos.HOME to Templates.ROAD.build(),
                PagePos.LEFT to Templates.MEDIA.build(),
                PagePos.RIGHT to Templates.CAR.build(),
                PagePos.DOWN to Templates.BIG_MAP.build(),
                PagePos.UP to emptyList()
            )
        )
    }
}

/**
 * Rozdělí dlaždice do řádků: široká zabere celý řádek,
 * dvě poloviční za sebou se spojí vedle sebe.
 */
fun rowsOf(slots: List<Slot>): List<List<Slot>> {
    val rows = ArrayList<List<Slot>>()
    var pending: Slot? = null
    for (s in slots) {
        val waiting = pending
        if (s.wide) {
            if (waiting != null) rows.add(listOf(waiting))
            pending = null
            rows.add(listOf(s))
        } else if (waiting == null) {
            pending = s
        } else {
            rows.add(listOf(waiting, s))
            pending = null
        }
    }
    val last = pending
    if (last != null) rows.add(listOf(last))
    return rows
}

/** Hotové šablony ploch. */
enum class Templates(private val parts: List<Triple<WidgetType, Boolean, Int>>) {
    /** Jako návrh: velká mapa, navigace + počasí, hudba + jízda, dok. */
    ROAD(
        listOf(
            Triple(WidgetType.MAP, true, 4),
            Triple(WidgetType.NAV, false, 2),
            Triple(WidgetType.WEATHER, false, 2),
            Triple(WidgetType.MEDIA, false, 2),
            Triple(WidgetType.TRIP, false, 2),
            Triple(WidgetType.DOCK, true, 1)
        )
    ),
    /** Mapa a hudba nahoře. */
    MAP_MUSIC(
        listOf(
            Triple(WidgetType.MAP, true, 3),
            Triple(WidgetType.SPEED, false, 2),
            Triple(WidgetType.MEDIA, false, 2),
            Triple(WidgetType.DOCK, true, 1)
        )
    ),
    /** Data z auta. */
    CAR(
        listOf(
            Triple(WidgetType.SPEED, false, 2),
            Triple(WidgetType.CAR, false, 2),
            Triple(WidgetType.DOORS, false, 2),
            Triple(WidgetType.FUEL, false, 2),
            Triple(WidgetType.TRIP, true, 2),
            Triple(WidgetType.DOCK, true, 1)
        )
    ),
    /** Hudba a informace. */
    MEDIA(
        listOf(
            Triple(WidgetType.MEDIA, true, 3),
            Triple(WidgetType.CLOCK, false, 2),
            Triple(WidgetType.WEATHER, false, 2),
            Triple(WidgetType.DOCK, true, 1)
        )
    ),
    /** Celá plocha mapa, dole rychlost a navigace. */
    BIG_MAP(
        listOf(
            Triple(WidgetType.MAP, true, 4),
            Triple(WidgetType.SPEED, false, 1),
            Triple(WidgetType.NAV, false, 1)
        )
    );

    fun build(): List<Slot> = parts.map { (type, wide, h) -> DashLayout.slot(type, wide, h) }

    /** Náhled pro výběr šablony (bez nových ID). */
    fun preview(): List<Slot> = parts.mapIndexed { i, (type, wide, h) -> Slot(i.toLong(), type, wide, h) }
}
