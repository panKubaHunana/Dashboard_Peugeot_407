package cz.kuba.dash407.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.BuildConfig
import cz.kuba.dash407.R
import cz.kuba.dash407.car.Demo
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val demo by Prefs.demo.collectAsState()
    val gpsAllowed by Gps.allowed.collectAsState()
    val media by NowPlaying.state.collectAsState()
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Gps.start(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_demo_title), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_demo_text), color = Palette.Muted, fontSize = 15.sp)
                }
                Switch(
                    checked = demo,
                    onCheckedChange = {
                        Prefs.setDemo(it)
                        if (it) Demo.start() else Demo.stop()
                    }
                )
            }
        }

        Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.settings_about_title), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), color = Palette.Muted, fontSize = 15.sp)
                Text(stringResource(R.string.settings_about_text), color = Palette.Muted, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SettingCard(title: String, text: String, button: String?, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Palette.Muted, fontSize = 15.sp)
            if (button != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClick, shape = RoundedCornerShape(16.dp)) { Text(button, fontSize = 17.sp) }
            }
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
