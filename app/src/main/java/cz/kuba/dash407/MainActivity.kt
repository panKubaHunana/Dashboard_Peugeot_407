package cz.kuba.dash407

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import cz.kuba.dash407.car.Demo
import cz.kuba.dash407.car.McuLink
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.ui.AppsScreen
import cz.kuba.dash407.ui.CarScreen
import cz.kuba.dash407.ui.Dash407Theme
import cz.kuba.dash407.ui.FuelScreen
import cz.kuba.dash407.ui.HomeScreen
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    CAR(R.string.tab_car, Icons.Filled.DirectionsCar),
    FUEL(R.string.tab_fuel, Icons.Filled.LocalGasStation),
    APPS(R.string.tab_apps, Icons.Filled.Apps),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf(Tab.HOME)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var poll: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        FuelLog.init(this)
        McuLink.start(packageName)
        AppsRepo.reload(this)
        if (Prefs.demo.value) Demo.start()
        setContent {
            Dash407Theme {
                AppRoot(tab = tab, onTab = { tab = it })
            }
        }
    }

    /** Tlačítko Domů, když už je aplikace vpředu: zpět na hlavní obrazovku. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) tab = Tab.HOME
    }

    override fun onResume() {
        super.onResume()
        Gps.start(this)
        NowPlaying.refresh(this)
        AppsRepo.reload(this)
        // Palubní počítač odpovídá jen na dotaz: ptáme se, dokud je aplikace vidět.
        poll = scope.launch {
            while (isActive) {
                McuLink.askTripPages()
                delay(30_000)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Gps.stop()
        poll?.cancel()
        poll = null
    }
}

@Composable
private fun AppRoot(tab: Tab, onTab: (Tab) -> Unit) {
    // Jako spouštěč nemá aplikace kam „zpět“: z ostatních karet se vrací domů.
    BackHandler(enabled = true) {
        if (tab != Tab.HOME) onTab(Tab.HOME)
    }
    Scaffold(
        containerColor = Palette.Background,
        bottomBar = {
            NavigationBar(containerColor = Palette.Tile) {
                for (item in Tab.values()) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { onTab(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes), maxLines = 1) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                Tab.HOME -> HomeScreen(onOpenApps = { onTab(Tab.APPS) }, onOpenFuel = { onTab(Tab.FUEL) })
                Tab.CAR -> CarScreen()
                Tab.FUEL -> FuelScreen()
                Tab.APPS -> AppsScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
