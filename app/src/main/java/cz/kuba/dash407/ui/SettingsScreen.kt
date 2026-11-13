package cz.kuba.dash407.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.BuildConfig
import cz.kuba.dash407.R
import cz.kuba.dash407.car.Demo
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.MapSettings
import cz.kuba.dash407.data.NavRepo
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.map.MapyLinks

@Composable
fun SettingsScreen(nav: (Screen) -> Unit) {
    val context = LocalContext.current
    val demo by Prefs.demo.collectAsState()
    val gpsAllowed by Gps.allowed.collectAsState()
    val media by NowPlaying.state.collectAsState()
    val map by Prefs.map.collectAsState()
    val navRaw by NavRepo.lastRaw.collectAsState()
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Gps.start(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------------------------------------- Mapy.com
        Section(stringResource(R.string.settings_map_title), stringResource(R.string.settings_map_text)) {
            var key by remember { mutableStateOf(map.apiKey) }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text(stringResource(R.string.settings_map_key)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Prefs.updateMap { it.copy(apiKey = key) } }, enabled = key != map.apiKey) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = { MapyLinks.openWeb(context, "https://developer.mapy.com/account/") }) {
                    Text(stringResource(R.string.settings_map_get_key))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.settings_map_style), color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = MapSettings.MAPSETS.map { stringResource(mapsetName(it)) },
                selected = MapSettings.MAPSETS.indexOf(map.mapset)
            ) { i -> Prefs.updateMap { it.copy(mapset = MapSettings.MAPSETS[i]) } }
            ToggleLine(stringResource(R.string.settings_map_dark), stringResource(R.string.settings_map_dark_text), map.dark) { v ->
                Prefs.updateMap { it.copy(dark = v) }
            }
            ToggleLine(stringResource(R.string.settings_map_follow), null, map.follow) { v ->
                Prefs.updateMap { it.copy(follow = v) }
            }
        }

        // ---------------------------------------------- Navigace
        Section(stringResource(R.string.settings_nav_title), stringResource(R.string.settings_nav_text)) {
            var home by remember { mutableStateOf(map.homeAddress) }
            var work by remember { mutableStateOf(map.workAddress) }
            OutlinedTextField(
                value = home,
                onValueChange = { home = it },
                label = { Text(stringResource(R.string.settings_nav_home)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = work,
                onValueChange = { work = it },
                label = { Text(stringResource(R.string.settings_nav_work)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { Prefs.updateMap { it.copy(homeAddress = home.trim(), workAddress = work.trim()) } },
                enabled = home.trim() != map.homeAddress || work.trim() != map.workAddress
            ) { Text(stringResource(R.string.action_save)) }
            ToggleLine(stringResource(R.string.settings_nav_window), stringResource(R.string.settings_nav_window_text), map.windowMode) { v ->
                Prefs.updateMap { it.copy(windowMode = v) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_nav_raw), color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                navRaw ?: stringResource(R.string.settings_nav_raw_none),
                color = Palette.Muted,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ---------------------------------------------- Vzhled
        SettingCard(
            stringResource(R.string.tab_look),
            stringResource(R.string.settings_look_text),
            stringResource(R.string.settings_look_button)
        ) { nav(Screen.LOOK) }

        SettingCard(
            stringResource(R.string.settings_home_title),
            stringResource(R.string.settings_home_text),
            stringResource(R.string.settings_home_button)
        ) { open(context, Settings.ACTION_HOME_SETTINGS) }

        SettingCard(
            stringResource(R.string.settings_media_title),
            stringResource(if (media.allowed) R.string.settings_media_allowed else R.string.settings_media_text),
            stringResource(R.string.settings_media_button)
        ) { open(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

        SettingCard(
            stringResource(R.string.settings_location_title),
            stringResource(if (gpsAllowed) R.string.settings_location_allowed else R.string.settings_location_text),
            if (gpsAllowed) null else stringResource(R.string.settings_location_button)
        ) { askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

        Section(stringResource(R.string.settings_demo_title), stringResource(R.string.settings_demo_text)) {
            ToggleLine(stringResource(R.string.settings_demo_switch), null, demo) {
                Prefs.setDemo(it)
                if (it) Demo.start() else Demo.stop()
            }
        }

        Section(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME)) {
            Text(stringResource(R.string.settings_about_text), color = Palette.Muted, fontSize = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun mapsetName(mapset: String): Int = when (mapset) {
    "outdoor" -> R.string.mapset_outdoor
    "winter" -> R.string.mapset_winter
    "aerial" -> R.string.mapset_aerial
    else -> R.string.mapset_basic
}

@Composable
fun Section(title: String, text: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(Palette.Radius), color = Palette.Tile, border = Palette.TileBorder, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (text != null) Text(text, color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            content()
        }
    }
}

@Composable
fun ToggleLine(title: String, text: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 17.sp)
            if (text != null) Text(text, color = Palette.Muted, fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Řada voleb, z nichž je vybraná jedna. */
@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) Color(0xFF06101C) else Palette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.Accent else Color(0x1FFFFFFF))
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp, horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun SettingCard(title: String, text: String, button: String?, onClick: () -> Unit) {
    Section(title, text) {
        if (button != null) {
            Button(onClick = onClick, shape = RoundedCornerShape(16.dp)) { Text(button, fontSize = 17.sp) }
        }
    }
}

private fun open(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Jednotka tuhle obrazovku nastavení nemá.
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (ignored: Exception) {
        }
    }
}
