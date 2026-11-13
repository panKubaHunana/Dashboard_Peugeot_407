package cz.kuba.dash407.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Skupiny ve výběru widgetů. */
enum class Category(val label: String) {
    DRIVING("Jízda"),
    NAVIGATION("Navigace"),
    VEHICLE("Vozidlo"),
    INFO("Info"),
    MEDIA("Média")
}

/** Všechny widgety, které jdou dát na plochu, s výchozí a nejmenší velikostí v mřížce 4 × 12. */
enum class WidgetType(
    val title: String,
    val description: String,
    val category: Category,
    val w: Int,
    val h: Int,
    val minW: Int = 1,
    val minH: Int = 1
) {
    MAP("Mapa", "Mapa Mapy.com s polohou auta a naplánovanou trasou", Category.NAVIGATION, 4, 4, 2, 2),
    DIRECTIONS("Navigace", "Další manévr z Mapy.com, cíl a příjezd, rychlé cíle Domů a Práce", Category.NAVIGATION, 2, 3, 2, 2),
    SPEED_LIMIT("Povolená rychlost", "Omezení na silnici, kde jedeš (OpenStreetMap)", Category.NAVIGATION, 1, 2, 1, 2),
    COMPASS("Kompas", "Směr jízdy, nadmořská výška a rychlost z GPS", Category.NAVIGATION, 2, 3, 1, 2),
    GPS("GPS", "Satelity, přesnost, výška a stáří polohy", Category.NAVIGATION, 2, 3, 2, 2),
    SPEED("Rychlost", "Velká digitální rychlost z GPS", Category.DRIVING, 2, 3, 1, 2),
    SPEED_GAUGE("Rychloměr", "Ručičkový rychloměr do 200 km/h", Category.DRIVING, 2, 4, 2, 2),
    TRIP("Jízda", "Ujetá vzdálenost, čas, průměr, maximum, odhad paliva a ceny", Category.DRIVING, 2, 3, 2, 2),
    GFORCE("Přetížení", "Boční a podélné přetížení v g se špičkami", Category.DRIVING, 2, 3, 2, 2),
    PERF("Časomíra", "0–50, 0–100 km/h a 400 m s pevným startem", Category.DRIVING, 4, 3, 2, 2),
    CAR_COMPUTER("Palubní počítač", "Okamžitá spotřeba, dojezd a průměr z auta", Category.VEHICLE, 2, 4, 2, 2),
    DOORS("Dveře", "Otevřené dveře, kapota a páté dveře", Category.VEHICLE, 2, 4, 2, 2),
    VOLTAGE("Napětí", "Napětí palubní sítě a spojení s autem", Category.VEHICLE, 2, 2, 1, 2),
    FUEL("Spotřeba z tankování", "Dlouhodobý průměr a poslední nádrž", Category.VEHICLE, 2, 2, 2, 2),
    CLOCK("Hodiny", "Čas a datum", Category.INFO, 4, 2, 1, 1),
    WEATHER("Počasí", "Aktuální počasí v místě, kde jsi", Category.INFO, 2, 3, 2, 2),
    MEDIA("Právě hraje", "Skladba, obal alba a ovládání přehrávání", Category.MEDIA, 2, 3, 2, 2),
    DOCK("Dok aplikací", "Oblíbené aplikace na jedno klepnutí", Category.MEDIA, 4, 2, 1, 1);

    companion object {
        fun of(name: String): WidgetType? = values().firstOrNull { it.name == name }
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
        WidgetType.DOORS -> DoorsWidget(modifier)
        WidgetType.VOLTAGE -> VoltageWidget(modifier)
        WidgetType.FUEL -> FuelWidget(modifier, preview)
        WidgetType.CLOCK -> ClockWidget(modifier)
        WidgetType.WEATHER -> WeatherWidget(modifier, preview)
        WidgetType.MEDIA -> MediaWidget(modifier, preview)
        WidgetType.DOCK -> DockWidget(modifier, preview)
    }
}
