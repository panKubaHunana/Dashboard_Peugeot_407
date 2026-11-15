package cz.kuba.dash407

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.car.TripPage
import cz.kuba.dash407.data.Fix
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.home.CompassWidget
import cz.kuba.dash407.home.SpeedGaugeWidget
import cz.kuba.dash407.home.SpeedWidget
import cz.kuba.dash407.ui.Dash407Theme
import cz.kuba.dash407.ui.Palette
import org.junit.Rule
import org.junit.Test

/**
 * Snímky widgetů ze skutečného kódu rádia (Paparazzi, bez zařízení).
 * Spouští je jen kontrola návrhu na GitHubu (check.yml); do APK se nedostanou.
 * Rozměry 1:1 s rádiem: 1 dp = 1 px, buňka mřížky 32 × 32,4 px (24 × 21 v ploše 768 × 680).
 */
class WidgetSnapshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 768, screenHeight = 1024, density = Density.MEDIUM, xdpi = 160, ydpi = 160, softButtons = false
        ),
        renderingMode = SessionParams.RenderingMode.SHRINK,
        showSystemUi = false,
        maxPercentDifference = 100.0,
    )

    private val cellW = 32f
    private val cellH = 680f / 21f

    private fun setCar() {
        CarRepo.update {
            it.copy(
                linked = true, instantL100 = 6.4f, rangeKm = 412,
                trip1 = TripPage(5.9f, 62, 348), trip2 = TripPage(6.1f, 54, 2715), volts = 14.1
            )
        }
    }

    private fun setSpeed(v: Int) {
        Gps.demoSpeed(v)
        Gps.demoFix(Fix(50.21, 15.83, v.toFloat(), 75f, 240.0, 5f, System.currentTimeMillis()))
    }

    /** Typ silnice pro barvu rychlosti (od 2.14); ve starším kódu se přeskočí. */
    private fun setZone(name: String): Boolean = try {
        val zoneCls = Class.forName("cz.kuba.dash407.data.RoadZone")
        val zone = zoneCls.enumConstants.first { (it as Enum<*>).name == name }
        val stateCls = Class.forName("cz.kuba.dash407.data.LimitState")
        val ctor = stateCls.declaredConstructors.first { it.parameterCount == 5 }
        val state = ctor.newInstance(null, null, false, 0L, zone)
        val limit = Class.forName("cz.kuba.dash407.data.SpeedLimit")
        val f = limit.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (f.get(null) as kotlinx.coroutines.flow.MutableStateFlow<Any?>).value = state
        true
    } catch (e: Throwable) { false }

    @Composable
    private fun Cell(w: Int, h: Int, content: @Composable (Modifier) -> Unit) =
        content(Modifier.size((w * cellW).dp, (h * cellH).dp).padding(4.dp))

    @Composable
    private fun Sheet(label: String, content: @Composable () -> Unit) {
        Dash407Theme {
            Column(Modifier.background(Palette.Background).padding(8.dp).wrapContentSize()) {
                Text(label, color = Palette.Text, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }

    /** Navigace (12 × 7, plocha Mapa), Rychloměr (10 × 12, Domů) a Kompas pro danou rychlost. */
    private fun speedSheet(name: String, label: String, speed: Int) {
        setCar(); setSpeed(speed)
        paparazzi.snapshot(name) {
            Sheet(label) {
                Row {
                    Cell(12, 7) { SpeedWidget(it) }
                    Spacer(Modifier.width(8.dp))
                    Cell(10, 12) { SpeedGaugeWidget(it) }
                }
                Spacer(Modifier.height(8.dp))
                Cell(12, 4) { CompassWidget(it) }
            }
        }
    }

    @Test fun rychlost_v_obci() {
        val ok = setZone("TOWN")
        for (v in listOf(49, 52, 55)) speedSheet("obec_$v", if (ok) "v obci – $v km/h" else "bez typu silnice – $v km/h", v)
    }

    @Test fun rychlost_mimo_obec() {
        setZone("RURAL")
        for (v in listOf(85, 96, 101)) speedSheet("mimo_obec_$v", "mimo obec – $v km/h", v)
    }

    @Test fun rychlost_dalnice() {
        setZone("MOTORWAY")
        for (v in listOf(129, 140, 150)) speedSheet("dalnice_$v", "dálnice – $v km/h", v)
    }
}
