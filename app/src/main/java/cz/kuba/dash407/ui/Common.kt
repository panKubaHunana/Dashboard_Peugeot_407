package cz.kuba.dash407.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Karta widgetu: poloprůhledné pozadí, zaoblení, nadpis v barvě zvýraznění a volitelná akce vpravo. */
@Composable
fun WidgetCard(
    title: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    background: Color? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.corner)
    var m = modifier.fillMaxSize().clip(shape).background(background ?: look.card, shape)
    val border = look.border
    if (border != null) m = m.border(1.dp, border, shape)
    if (onClick != null) m = m.clickable { onClick() }
    Column(m.padding(padding)) {
        if (look.titles && (title != null || action != null)) {
            Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        title.uppercase(),
                        color = look.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (action != null) action()
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

/** Text, který se zvětší nebo zmenší podle místa, které má (velká čísla na widgetech). */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Text,
    maxSp: Float = 120f,
    weight: FontWeight = FontWeight.SemiBold,
    widthPerChar: Float = 0.58f,
    heightPart: Float = 0.82f,
    align: TextAlign = TextAlign.Center
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val chars = text.length.coerceAtLeast(1)
        val byHeight = maxHeight.value * heightPart
        val byWidth = maxWidth.value / (chars * widthPerChar)
        val dpSize = minOf(byHeight, byWidth).coerceAtLeast(8f)
        // Převod z dp na sp tak, aby nastavení „velikost písma“ neroztáhlo text mimo widget.
        val spSize = (dpSize / density.fontScale).coerceAtMost(maxSp)
        Text(
            text,
            color = color,
            fontSize = spSize.sp,
            lineHeight = (spSize * 1.05f).sp,
            fontWeight = weight,
            maxLines = 1,
            softWrap = false,
            textAlign = align
        )
    }
}

/** Malé kulaté tlačítko s ikonou (přehrávač, mapa). */
@Composable
fun RoundButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val look = LocalLook.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) look.accent else Color.White.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(size * 0.55f), tint = if (filled) Color(0xFF06121C) else Color.White)
    }
}

/** Popisek s hodnotou v jednom řádku (Čas … 15 min). */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Muted, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text(value, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Volba ve tvaru „pilulky“ (kategorie, styl mapy, pozadí). */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) look.accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (selected) look.accent else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Záhlaví celoobrazovkového panelu se šipkou zpět. */
@Composable
fun PanelHeader(title: String, onBack: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět", tint = Palette.Text, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(title, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}
