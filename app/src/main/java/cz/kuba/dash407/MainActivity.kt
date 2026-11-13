package cz.kuba.dash407

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cz.kuba.dash407.car.Demo
import cz.kuba.dash407.car.McuLink
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.PagePos
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.map.MapHub
import cz.kuba.dash407.ui.AppsScreen
import cz.kuba.dash407.ui.CarScreen
import cz.kuba.dash407.ui.Dash407Theme
import cz.kuba.dash407.ui.Dashboard
import cz.kuba.dash407.ui.FuelScreen
import cz.kuba.dash407.ui.LookScreen
import cz.kuba.dash407.ui.LookState
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.Screen
import cz.kuba.dash407.ui.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.DASH)
    private var page by mutableStateOf(PagePos.HOME)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var poll: Job? = null
    private var barsHidden: Boolean? = null

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Gps.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        FuelLog.init(this)
        MapHub.configure(this)
        McuLink.start(packageName)
        AppsRepo.reload(this)
        if (Prefs.demo.value) Demo.start()
        LookState.look = Prefs.look.value
        scope.launch {
            Prefs.look.collect {
                LookState.look = it
                applySystemBars(it.hideSystemBars)
            }
        }
        if (Gps.hasPermission(this)) Gps.start(this) else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        setContent {
            Dash407Theme {
                AppRoot(
                    screen = screen,
                    page = page,
                    onScreen = { screen = it },
                    onPage = { page = it }
                )
            }
        }
    }

    /** Tlačítko Domů: z jiné obrazovky zpět na plochy, na plochách na plochu Domů. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (screen == Screen.DASH) page = PagePos.HOME else screen = Screen.DASH
        }
    }

    override fun onResume() {
        super.onResume()
        Gps.start(this)
        NowPlaying.refresh(this)
        AppsRepo.reload(this)
        MapHub.resume()
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
        MapHub.pause()
        poll?.cancel()
        poll = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Gps.stop()
    }

    /** Lišty rádia (horní ikony, panel klimatizace) zůstávají vidět, pokud je uživatel nevypne. */
    private fun applySystemBars(hide: Boolean) {
        if (barsHidden == hide) return
        barsHidden = hide
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun AppRoot(screen: Screen, page: PagePos, onScreen: (Screen) -> Unit, onPage: (PagePos) -> Unit) {
    BackHandler(enabled = screen != Screen.DASH) { onScreen(Screen.DASH) }
    Box(Modifier.fillMaxSize().background(Palette.BackgroundBrush)) {
        when (screen) {
            Screen.DASH -> Dashboard(page = page, onPage = onPage, nav = onScreen)
            Screen.APPS -> SubScreen(R.string.tab_apps, onScreen) { AppsScreen() }
            Screen.CAR -> SubScreen(R.string.tab_car, onScreen) { CarScreen() }
            Screen.FUEL -> SubScreen(R.string.tab_fuel, onScreen) { FuelScreen() }
            Screen.SETTINGS -> SubScreen(R.string.tab_settings, onScreen) { SettingsScreen(onScreen) }
            Screen.LOOK -> SubScreen(R.string.tab_look, onScreen) { LookScreen() }
        }
    }
}

/** Obrazovka přes celou plochu s hlavičkou „zpět“. */
@Composable
private fun SubScreen(titleRes: Int, onScreen: (Screen) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onScreen(Screen.DASH) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Palette.Text, modifier = Modifier.size(30.dp))
            }
            Text(stringResource(titleRes), color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
