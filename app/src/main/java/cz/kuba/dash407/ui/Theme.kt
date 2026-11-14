package cz.kuba.dash407.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontFamily
import android.graphics.Typeface
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
    /** Světlý motiv; čtení ve @Composable vyvolá překreslení po přepnutí. */
    val lightState = mutableStateOf(false)
    private val light: Boolean get() = lightState.value
    val Background: Color get() = if (light) Color(0xFFEEF1F5) else Color(0xFF0B0F14)
    val Tile: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF151C26)
    val TileHigh: Color get() = if (light) Color(0xFFE3E8EF) else Color(0xFF1D2633)
    val Sheet: Color get() = if (light) Color(0xF2F6F8FB) else Color(0xF2121822)
    val Accent: Color get() = accentState.value
    val Amber: Color get() = if (light) Color(0xFFC77800) else Color(0xFFFFB300)
    val Good: Color get() = if (light) Color(0xFF1B9E55) else Color(0xFF3DDC84)
    val Bad: Color get() = if (light) Color(0xFFD32F2F) else Color(0xFFFF5252)
    val Text: Color get() = if (light) Color(0xFF111820) else Color(0xFFF2F5F9)
    val Muted: Color get() = if (light) Color(0xFF55627A) else Color(0xFF8A96A8)
    /** Čára a jemný rámeček na pozadí. */
    val Line: Color get() = if (light) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.08f)
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
    val background: Int,
    val light: Boolean = false
)

/** Vyhodnotí volbu Tmavý / Světlý / Podle systému. */
fun isLightTheme(a: Appearance, systemDark: Boolean): Boolean = when (a.theme) {
    "light" -> true
    "auto" -> !systemDark
    else -> false
}

fun lookOf(a: Appearance, light: Boolean = a.theme == "light"): Look {
    val accent = Color(a.accent.toInt())
    val dark = Color(0xFF141B26)
    val white = Color(0xFFFFFFFF)
    val base = if (light) {
        if (a.cardTint) lerp(white, accent, 0.10f) else white
    } else {
        if (a.cardTint) lerp(dark, accent, 0.12f) else dark
    }
    val high = if (light) Color(0xFFE3E8EF) else Color(0xFF223042)
    return Look(
        accent = accent,
        card = base.copy(alpha = a.cardAlpha),
        cardHigh = high.copy(alpha = (a.cardAlpha + 0.15f).coerceAtMost(1f)),
        border = if (a.border) (if (light) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.08f)) else null,
        text = if (light) Color(0xFF111820) else Color(0xFFF2F5F9),
        muted = if (light) Color(0xFF55627A) else Color(0xFF8A96A8),
        corner = a.corner.dp,
        gap = a.gap.dp,
        titles = a.titles,
        animations = a.animations,
        darkMap = a.darkMap,
        mapStyle = a.mapStyle,
        seconds = a.seconds,
        background = a.background,
        light = light
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

fun backgroundBrush(index: Int, accent: Color, light: Boolean = Palette.lightState.value): Brush = if (light) when (index) {
    1 -> Brush.linearGradient(
        listOf(Color(0xFFDCE8FA), Color(0xFFEADCF7), Color(0xFFF7DCE6), Color(0xFFF2F4F8)),
        start = Offset(0f, 0f), end = Offset(900f, 1400f)
    )
    2 -> Brush.verticalGradient(listOf(Color(0xFFE9EBEE), Color(0xFFD5D9DF)))
    3 -> Brush.verticalGradient(listOf(Color.White, Color.White))
    4 -> Brush.verticalGradient(listOf(Color(0xFFD8E8F8), Color(0xFFC3D6EC)))
    5 -> Brush.verticalGradient(listOf(Color(0xFFF6DDE8), Color(0xFFF8E4D4), Color(0xFFF1F1F4)))
    6 -> Brush.verticalGradient(listOf(Color(0xFFD7EFE4), Color(0xFFC4E0D3)))
    else -> Brush.verticalGradient(listOf(lerp(Color(0xFFF4F7FB), accent, 0.08f), Color(0xFFE6EBF2)))
} else when (index) {
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

/** Rodiny písma: systémová písma Androidu, takže nic nepřibývá do APK. */
val FontChoices = listOf(
    "default" to "Výchozí (Roboto)",
    "condensed" to "Úzké",
    "light" to "Tenké",
    "medium" to "Střední",
    "serif" to "Patkové",
    "mono" to "Neproporcionální",
    "casual" to "Hravé"
)

fun fontFamilyOf(id: String): FontFamily = when (id) {
    "condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    "light" -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
    "medium" -> FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "casual" -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
    else -> FontFamily.Default
}

private fun typographyOf(f: FontFamily): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f)
    )
}

@Composable
fun Dash407Theme(content: @Composable () -> Unit) {
    val appearance by AppearanceStore.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val light = isLightTheme(appearance, systemDark)
    // Palette se čte při kreslení, proto se přepne ještě před složením obsahu.
    Palette.lightState.value = light
    val look = remember(appearance, light) { lookOf(appearance, light) }
    SideEffect { Palette.accentState.value = look.accent }
    val density = LocalDensity.current
    val typography = remember(appearance.font) { typographyOf(fontFamilyOf(appearance.font)) }
    val scheme = remember(look.accent, light) {
        val onAccent = if (light) Color.White else Color(0xFF06121C)
        if (light) lightColorScheme(
            primary = look.accent, onPrimary = onAccent, secondary = Palette.Amber, onSecondary = Color.White,
            background = Palette.Background, onBackground = Palette.Text,
            surface = Palette.Tile, onSurface = Palette.Text,
            surfaceVariant = Palette.TileHigh, onSurfaceVariant = Palette.Muted,
            surfaceContainer = Palette.Tile, surfaceContainerHigh = Palette.TileHigh,
            error = Palette.Bad
        ) else darkColorScheme(
            primary = look.accent, onPrimary = onAccent, secondary = Palette.Amber, onSecondary = Color.Black,
            background = Palette.Background, onBackground = Palette.Text,
            surface = Palette.Tile, onSurface = Palette.Text,
            surfaceVariant = Palette.TileHigh, onSurfaceVariant = Palette.Muted,
            surfaceContainer = Palette.Tile, surfaceContainerHigh = Palette.TileHigh,
            error = Palette.Bad
        )
    }
    CompositionLocalProvider(
        LocalLook provides look,
        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
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
