package cz.kuba.dash407.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0B0F14)
    val Tile = Color(0xFF151C26)
    val TileHigh = Color(0xFF1D2633)
    val Accent = Color(0xFF3D8BFF)
    val Amber = Color(0xFFFFB300)
    val Good = Color(0xFF3DDC84)
    val Bad = Color(0xFFFF5252)
    val Text = Color(0xFFF2F5F9)
    val Muted = Color(0xFF8A96A8)
}

private val Scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Amber,
    onSecondary = Color.Black,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Tile,
    onSurface = Palette.Text,
    surfaceVariant = Palette.TileHigh,
    onSurfaceVariant = Palette.Muted,
    error = Palette.Bad
)

@Composable
fun Dash407Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

/** Základní stavební kámen obrazovek: zaoblená dlaždice s malým nadpisem. */
@Composable
fun Tile(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = Palette.Tile) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = title.uppercase(),
                color = Palette.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            content()
        }
    }
}

/** Dvouřádkový údaj: velká hodnota a pod ní popisek. */
@Composable
fun Reading(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Palette.Text) {
    Column(modifier) {
        Text(value, color = valueColor, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Palette.Muted, fontSize = 13.sp, maxLines = 1)
    }
}
