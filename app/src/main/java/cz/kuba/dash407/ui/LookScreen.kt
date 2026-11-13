package cz.kuba.dash407.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.data.Prefs
import kotlin.math.roundToInt

/** Přizpůsobení vzhledu; změny se projeví hned na všech plochách. */
@Composable
fun LookScreen() {
    val look by Prefs.look.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section(stringResource(R.string.look_accent), stringResource(AccentChoices[look.accent.coerceIn(0, AccentChoices.size - 1)].nameRes)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentChoices.forEachIndexed { i, a ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(a.color)
                            .border(if (i == look.accent) 4.dp else 0.dp, Color.White, CircleShape)
                            .clickable { Prefs.updateLook { it.copy(accent = i) } }
                    )
                }
            }
        }

        Section(stringResource(R.string.look_background), null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackgroundChoices.forEachIndexed { i, b ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { Prefs.updateLook { it.copy(background = i) } }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(listOf(b.top, b.bottom)))
                                .border(if (i == look.background) 3.dp else 1.dp, if (i == look.background) Palette.Accent else Palette.Border, RoundedCornerShape(12.dp))
                        )
                        Text(stringResource(b.nameRes), color = Palette.Text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Section(stringResource(R.string.look_tiles), null) {
            Text(stringResource(R.string.look_opacity, (look.tileAlpha * 100).roundToInt()), color = Palette.Text, fontSize = 16.sp)
            Slider(
                value = look.tileAlpha,
                onValueChange = { v -> Prefs.updateLook { it.copy(tileAlpha = v) } },
                valueRange = 0.4f..1f
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.look_corners), color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                listOf(stringResource(R.string.look_small), stringResource(R.string.look_medium), stringResource(R.string.look_large)),
                look.corner
            ) { i -> Prefs.updateLook { it.copy(corner = i) } }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.look_text_size), color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                listOf(stringResource(R.string.look_small), stringResource(R.string.look_medium), stringResource(R.string.look_large)),
                look.textScale
            ) { i -> Prefs.updateLook { it.copy(textScale = i) } }
            ToggleLine(stringResource(R.string.look_border), null, look.tileBorder) { v -> Prefs.updateLook { it.copy(tileBorder = v) } }
        }

        Section(stringResource(R.string.look_behaviour), null) {
            ToggleLine(stringResource(R.string.look_animations), null, look.animations) { v -> Prefs.updateLook { it.copy(animations = v) } }
            ToggleLine(stringResource(R.string.look_swipe), stringResource(R.string.look_swipe_text), look.swipePages) { v -> Prefs.updateLook { it.copy(swipePages = v) } }
            ToggleLine(stringResource(R.string.look_seconds), null, look.showSeconds) { v -> Prefs.updateLook { it.copy(showSeconds = v) } }
            ToggleLine(stringResource(R.string.look_bar_labels), null, look.barLabels) { v -> Prefs.updateLook { it.copy(barLabels = v) } }
            ToggleLine(stringResource(R.string.look_hide_bars), stringResource(R.string.look_hide_bars_text), look.hideSystemBars) { v ->
                Prefs.updateLook { it.copy(hideSystemBars = v) }
            }
        }

        Section(stringResource(R.string.look_reset_title), stringResource(R.string.look_reset_text)) {
            OutlinedButton(onClick = { Prefs.resetLayout() }) { Text(stringResource(R.string.look_reset_button)) }
        }
        Spacer(Modifier.height(4.dp))
    }
}
