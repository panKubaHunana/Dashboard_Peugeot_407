package cz.kuba.dash407.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Skupiny ve výběru widgetů. */
enum class Category(val label: String) {
    DRIVING("Jízda"),
    NAVIGATION("Navigace"),
    VEHICLE("Vozidlo"),
    INFO("Info"),
    MEDIA("Média"),
    PHONE("Telefon")
}

/** Všechny widgety, které jdou dát na plochu, s výchozí a nejmenší velikostí v mřížce 4 × 12. */
/**
 * Velikosti jsou zapsané v původní mřížce 4 × 12; skutečné hodnoty v jemné mřížce 8 × 32
 * se dopočítají v mřížce 16 × 32 (šířka × 4, výška × 32/12). Minimum je jemnější, aby šly widgety víc zmenšit.
 */
enum class WidgetType(
    val title: String,
    val description: String,
    val category: Category,
    baseW: Int,
    baseH: Int,
    baseMinW: Int = 1,
    baseMinH: Int = 1
) {
    MAP("Mapa", "Mapa Mapy.com s polohou auta a naplánovanou trasou", Category.NAVIGATION, 4, 4, 2, 2),
    DIRECTIONS("Navigace", "Další manévr z Mapy.com, cíl a příjezd, rychlé cíle Domů a Práce", Category.NAVIGATION, 2, 3, 2, 2),
    SPEED_LIMIT("Povolená rychlost", "Omezení na silnici, kde jedeš (OpenStreetMap)", Category.NAVIGATION, 1, 2, 1, 2),
    COMPASS("Kompas", "Směr jízdy, nadmořská výška a rychlost z GPS", Category.NAVIGATION, 2, 3, 1, 2),
    GPS("GPS", "Satelity, přesnost, výška a stáří polohy", Category.NAVIGATION, 2, 3, 2, 2),
    SPEED("Rychlost", "Velká digitální rychlost z GPS", Category.DRIVING, 2, 3, 1, 2),
    SPEED_GAUGE("Rychloměr a spotřeba", "Obloukový rychloměr a pod ním okamžitá a průměrná spotřeba, ujeto a dojezd z auta", Category.DRIVING, 2, 4, 2, 2),
    TRIP("Jízda", "Ujetá vzdálenost, čas, průměr, maximum, odhad paliva a ceny", Category.DRIVING, 2, 3, 2, 2),
    GFORCE("Přetížení", "Boční a podélné přetížení v g se špičkami", Category.DRIVING, 2, 3, 2, 2),
    PERF("Časomíra", "0–50, 0–100 km/h a 400 m s pevným startem", Category.DRIVING, 4, 3, 2, 2),
    TRIP_COMPUTER("Jízda", "Ujeto, čas v pohybu, průměrná a nejvyšší rychlost, začátek, počet jízd a průměrná spotřeba; podržením vynuluješ", Category.VEHICLE, 2, 6, 2, 4),
    CAR_COMPUTER("Palubní počítač", "Okamžitá spotřeba, dojezd a průměr z auta", Category.VEHICLE, 2, 4, 2, 2),
    DOORS("Stav vozu", "Auto shora s dveřmi, výstrahy, světla, ruční brzda a napětí", Category.VEHICLE, 2, 4, 2, 2),
    VOLTAGE("Napětí", "Napětí palubní sítě a spojení s autem", Category.VEHICLE, 2, 2, 1, 2),
    CAR_APPS("Zvuk, volant, klima", "Rychlé zkratky: DSP a ekvalizér, učení tlačítek na volantu, klimatizace, nastavení vozu", Category.VEHICLE, 4, 2, 2, 1),
    SERVICE("Servis", "Nejbližší termín z servisní knížky: olej, filtry, brzdy", Category.VEHICLE, 2, 2, 2, 2),
    FUEL("Spotřeba z tankování", "Dlouhodobý průměr a poslední nádrž", Category.VEHICLE, 2, 2, 2, 2),
    CLOCK("Hodiny", "Čas a datum", Category.INFO, 4, 2, 1, 1),
    WEATHER("Počasí", "Aktuální teplota a předpověď na 5 dní v místě, kde jsi", Category.INFO, 2, 3, 2, 2),
    MEDIA("Právě hraje", "Skladba, obal alba a ovládání přehrávání", Category.MEDIA, 2, 3, 2, 2),
    PHONE_DIAL("Rychlá volba", "Pět kontaktů v zelených kolečkách se jménem a seznam kontaktů, volání jedním klepnutím", Category.PHONE, 1, 7, 1, 1),
    DOCK("Dok aplikací", "Oblíbené aplikace na jedno klepnutí", Category.MEDIA, 4, 2, 1, 1);

    val w: Int = Grid.col(baseW)
    val h: Int = Grid.row(baseH)
    // Nejmenší šířka zůstává fyzicky stejná jako v 8 × 32 (jeden dřívější sloupec = dva nové).
    val minW: Int = baseMinW * 2
    val minH: Int = baseMinH * 2

    companion object {
        fun of(name: String): WidgetType? = values().firstOrNull { it.name == name }

        /** Nahrazené widgety: na plochách zůstanou, ale nenabízejí se k přidání. */
        val retired: Set<WidgetType> get() = setOf(CAR_COMPUTER, TRIP)
    }
}

/** Vykreslí obsah widgetu. V náhledu (výběr widgetů) jsou tlačítka vypnutá. */
@Composable
fun WidgetContent(type: WidgetType, cell: Cell?, modifier: Modifier = Modifier, preview: Boolean = false) {
    when (type) {
        WidgetType.MAP -> MapWidget(modifier, preview)
        WidgetType.DIRECTIONS -> DirectionsWidget(modifier, preview)
        WidgetType.SPEED_LIMIT -> SpeedLimitWidget(modifier, preview)
        WidgetType.COMPASS -> CompassWidget(modifier)
        WidgetType.GPS -> GpsWidget(modifier)
        WidgetType.SPEED -> SpeedWidget(modifier)
        WidgetType.SPEED_GAUGE -> SpeedGaugeWidget(modifier)
        WidgetType.TRIP -> TripWidget(modifier, preview)
        WidgetType.GFORCE -> GForceWidget(modifier, preview)
        WidgetType.PERF -> PerfWidget(modifier, preview)
        WidgetType.CAR_COMPUTER -> CarComputerWidget(modifier)
        WidgetType.TRIP_COMPUTER -> TripComputerWidget(modifier, preview)
        WidgetType.DOORS -> DoorsWidget(modifier)
        WidgetType.VOLTAGE -> VoltageWidget(modifier)
        WidgetType.FUEL -> FuelWidget(modifier, preview)
        WidgetType.SERVICE -> ServiceWidget(modifier, preview)
        WidgetType.CAR_APPS -> CarAppsWidget(modifier, preview)
        WidgetType.CLOCK -> ClockWidget(modifier)
        WidgetType.WEATHER -> WeatherWidget(modifier, preview)
        WidgetType.MEDIA -> MediaWidget(modifier, preview)
        WidgetType.DOCK -> DockWidget(modifier, preview)
        WidgetType.PHONE_DIAL -> QuickDialWidget(modifier, preview)
    }
}
