package cz.kuba.dash407

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.kuba.dash407.car.Demo
import cz.kuba.dash407.car.McuLink
import cz.kuba.dash407.data.AppearanceStore
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.data.Trip
import cz.kuba.dash407.home.HomeRoot
import cz.kuba.dash407.home.LayoutStore
import cz.kuba.dash407.home.PanelHost
import cz.kuba.dash407.home.UiBus
import cz.kuba.dash407.nav.MapyLogo
import cz.kuba.dash407.nav.Recent
import cz.kuba.dash407.service.DriveService
import cz.kuba.dash407.ui.Dash407Theme
import cz.kuba.dash407.ui.IntroOverlay
import cz.kuba.dash407.ui.IntroState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Domovská aplikace. Okno se schválně neroztahuje přes systémové lišty:
 * horní lišta s ikonami a spodní lišta klimatizace a hlasitosti rádia zůstávají vidět.
 */
class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var poll: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        AppearanceStore.init()
        LayoutStore.init()
        Trip.init()
        Recent.init()
        MapyLogo.init(this)
        cz.kuba.dash407.nav.Tiles.init(this)
        cz.kuba.dash407.nav.NavEngine.init(this)
        FuelLog.init(this)
        cz.kuba.dash407.data.ServiceBook.init(this)
        cz.kuba.dash407.car.Obd.init()
        cz.kuba.dash407.data.QuickDial.init()
        McuLink.start(packageName)
        AppsRepo.reload(this)
        if (Prefs.demo.value) Demo.start()
        setContent {
            Dash407Theme {
                AppRoot()
            }
        }
    }

    /** Tlačítko Domů, když už je aplikace vpředu: zavřít panely a zpět na střední plochu. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) UiBus.home()
    }

    override fun onResume() {
        super.onResume()
        Gps.start(this)
        DriveService.start(this)
        NowPlaying.refresh(this)
        cz.kuba.dash407.car.Obd.autoStart(this)
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
        Trip.save()
        poll?.cancel()
        poll = null
    }
}

@Composable
private fun AppRoot() {
    val look by AppearanceStore.state.collectAsState()
    var intro by remember { mutableStateOf(look.intro && !IntroState.playedThisProcess) }
    // Jako spouštěč nemá aplikace kam „zpět“: zavře panel nebo úpravy.
    BackHandler(enabled = true) {
        when {
            intro || IntroState.preview -> {
                IntroState.playedThisProcess = true
                IntroState.preview = false
                intro = false
            }
            UiBus.panel != null -> UiBus.close()
            UiBus.editing -> {
                UiBus.editing = false
                UiBus.selected = null
            }
            else -> UiBus.home()
        }
    }
    Box(Modifier.fillMaxSize()) {
        HomeRoot()
        PanelHost()
        if (intro || IntroState.preview) {
            IntroOverlay {
                IntroState.playedThisProcess = true
                IntroState.preview = false
                intro = false
            }
        }
    }
}
