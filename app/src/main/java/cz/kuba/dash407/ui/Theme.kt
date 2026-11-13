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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.Appearance
import cz.kuba.dash407.data.AppearanceStore

/** Pevné barvy pro obrazovky Auto a Tankování; zvýraznění se řídí panelem Vzhled. */
object Palette {
    val accentState = mutableStateOf(Color(0xFF4FC3F7))
    val Background = Color(0xFF0B0F14)
    val Tile = Color(0xFF151C26)
    val TileHigh = Color(0xFF1D2633)
    val Accent: Color get() = accentState.value
    val Amber = Color(0xFFFFB300)
    val Good = Color(0xFF3DDC84)
    val Bad = Color(0xFFFF5252)
    val Text = Color(0xFFF2F5F9)
    val Muted = Color(0xFF8A96A8)
}

/** Vypočtený vzhled, který čtou widgety. */
data class Look(
    val accent: Color,
    val card: Color,
    val cardHigh: Color,
    val border: Color?,
    val text: Color,
    val muted: Color,
    val corner: Dp,
    val gap: Dp,
    val titles: Boolean,
    val animations: Boolean,
    val darkMap: Boolean,
    val mapStyle: String,
    val seconds: Boolean,
    val background: Int
)

fun lookOf(a: Appearance): Look {
    val accent = Color(a.accent.toInt())
    val base = if (a.cardTint) lerp(Color(0xFF141B26), accent, 0.12f) else Color(0xFF141B26)
    return Look(
        accent = accent,
        card = base.copy(alpha = a.cardAlpha),
        cardHigh = Color(0xFF223042).copy(alpha = (a.cardAlpha + 0.15f).coerceAtMost(1f)),
        border = if (a.border) Color.White.copy(alpha = 0.08f) else null,
        text = Palette.Text,
        muted = Palette.Muted,
        corner = a.corner.dp,
        gap = a.gap.dp,
        titles = a.titles,
        animations = a.animations,
        darkMap = a.darkMap,
        mapStyle = a.mapStyle,
        seconds = a.seconds,
        background = a.background
    )
}

val LocalLook = compositionLocalOf { lookOf(Appearance()) }

/** Barvy zvýraznění na výběr v panelu Vzhled. */
val AccentChoices = listOf(
    0xFF4FC3F7, 0xFF3D8BFF, 0xFF7C4DFF, 0xFFE040FB, 0xFFFF4081, 0xFFFF5252,
    0xFFFF9100, 0xFFFFD600, 0xFF69F0AE, 0xFF1DE9B6, 0xFF00E5FF, 0xFFECEFF1
)

/** Pozadí ploch. */
val BackgroundNames = listOf("Noc", "Polární záře", "Grafit", "Černá", "Půlnoční modrá", "Západ slunce", "Les")

fun backgroundBrush(index: Int, accent: Color): Brush = when (index) {
    1 -> Brush.linearGradient(
        listOf(Color(0xFF0B1D3A), Color(0xFF1B0F33), Color(0xFF2A0F1F), Color(0xFF0A0E14)),
        start = Offset(0f, 0f), end = Offset(900f, 1400f)
    )
    2 -> Brush.verticalGradient(listOf(Color(0xFF1C1F24), Color(0xFF0E1013)))
    3 -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    4 -> Brush.verticalGradient(listOf(Color(0xFF0A2540), Color(0xFF050B18)))
    5 -> Brush.verticalGradient(listOf(Color(0xFF2B1530), Color(0xFF3A1E12), Color(0xFF0B0B10)))
    6 -> Brush.verticalGradient(listOf(Color(0xFF0E2A22), Color(0xFF06110E)))
    else -> Brush.verticalGradient(listOf(lerp(Color(0xFF0E1726), accent, 0.06f), Color(0xFF070A10)))
}

@Composable
fun Dash407Theme(content: @Composable () -> Unit) {
    val appearance by AppearanceStore.state.collectAsState()
    val look = remember(appearance) { lookOf(appearance) }
    SideEffect { Palette.accentState.value = look.accent }
    val density = LocalDensity.current
    val scheme = remember(look.accent) {
        darkColorScheme(
            primary = look.accent,
            onPrimary = Color(0xFF06121C),
            secondary = Palette.Amber,
            onSecondary = Color.Black,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Tile,
            onSurface = Palette.Text,
            surfaceVariant = Palette.TileHigh,
            onSurfaceVariant = Palette.Muted,
            surfaceContainer = Palette.Tile,
            surfaceContainerHigh = Palette.TileHigh,
            error = Palette.Bad
        )
    }
    CompositionLocalProvider(
        LocalLook provides look,
        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Základní stavební kámen obrazovek Auto a Tankování: zaoblená dlaždice s malým nadpisem. */
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
