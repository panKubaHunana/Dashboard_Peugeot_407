package cz.kuba.dash407.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.data.Look

/** Barevný akcent, který si lze vybrat ve Vzhledu. */
data class AccentChoice(val nameRes: Int, val color: Color)

val AccentChoices = listOf(
    AccentChoice(R.string.accent_cyan, Color(0xFF4FC3F7)),
    AccentChoice(R.string.accent_violet, Color(0xFFA78BFA)),
    AccentChoice(R.string.accent_green, Color(0xFF34D399)),
    AccentChoice(R.string.accent_amber, Color(0xFFFFB547)),
    AccentChoice(R.string.accent_red, Color(0xFFFF5D6C)),
    AccentChoice(R.string.accent_blue, Color(0xFF5B8CFF)),
    AccentChoice(R.string.accent_pink, Color(0xFFF472B6)),
    AccentChoice(R.string.accent_white, Color(0xFFE8EEF6))
)

/** Pozadí prostředí: dvojice barev pro svislý přechod. */
data class BackgroundChoice(val nameRes: Int, val top: Color, val bottom: Color)

val BackgroundChoices = listOf(
    BackgroundChoice(R.string.background_night, Color(0xFF0B1222), Color(0xFF16163A)),
    BackgroundChoice(R.string.background_black, Color(0xFF000000), Color(0xFF000000)),
    BackgroundChoice(R.string.background_graphite, Color(0xFF15181D), Color(0xFF232830)),
    BackgroundChoice(R.string.background_violet, Color(0xFF120E24), Color(0xFF26143F))
)

val CornerChoices = listOf(12.dp, 22.dp, 32.dp)
val TextScaleChoices = listOf(0.88f, 1f, 1.15f)

/** Aktuální vzhled; čte se při vykreslení, takže změna se projeví hned. */
object LookState {
    var look by mutableStateOf(Look())
}

object Palette {
    private val look: Look get() = LookState.look

    val Accent: Color get() = AccentChoices[look.accent.coerceIn(0, AccentChoices.size - 1)].color
    val Accent2 = Color(0xFF9C8CFF)
    val Background: Color get() = BackgroundChoices[look.background.coerceIn(0, BackgroundChoices.size - 1)].top
    val BackgroundBrush: Brush
        get() {
            val b = BackgroundChoices[look.background.coerceIn(0, BackgroundChoices.size - 1)]
            return Brush.verticalGradient(listOf(b.top, b.bottom))
        }
    val Tile: Color get() = Color(0xFF141C2E).copy(alpha = look.tileAlpha)
    val TileHigh = Color(0xFF222D45)
    val Border = Color(0xFF2A3754)
    val Amber = Color(0xFFFFB300)
    val Good = Color(0xFF3DDC84)
    val Bad = Color(0xFFFF5252)
    val Text = Color(0xFFEEF2F8)
    val Muted = Color(0xFF8C9AB3)
    val Radius: Dp get() = CornerChoices[look.corner.coerceIn(0, CornerChoices.size - 1)]
    val TileBorder: BorderStroke? get() = if (look.tileBorder) BorderStroke(1.dp, Border) else null
}

@Composable
fun Dash407Theme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Accent,
        onPrimary = Color(0xFF06101C),
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
    val density = LocalDensity.current
    val scale = TextScaleChoices[LookState.look.textScale.coerceIn(0, TextScaleChoices.size - 1)]
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Základní stavební kámen: zaoblená dlaždice s malým nadpisem v barvě akcentu. */
@Composable
fun Tile(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Palette.Radius),
        color = Palette.Tile,
        border = Palette.TileBorder
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (title.isNotEmpty()) TileTitle(title)
            content()
        }
    }
}

@Composable
fun TileTitle(title: String, color: Color = Palette.Accent) {
    Text(
        text = title.uppercase(),
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        maxLines = 1
    )
}

/** Dvouřádkový údaj: velká hodnota a pod ní popisek. */
@Composable
fun Reading(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Palette.Text) {
    Column(modifier) {
        Text(value, color = valueColor, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Palette.Muted, fontSize = 13.sp, maxLines = 1)
    }
}
