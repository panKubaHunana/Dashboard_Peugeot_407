package cz.kuba.dash407.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.DialContact
import cz.kuba.dash407.data.QuickDial
import cz.kuba.dash407.ui.LocalLook
import cz.kuba.dash407.ui.Palette
import cz.kuba.dash407.ui.WidgetCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rychlá volba: pět vlastních kontaktů a šesté tlačítko otevře seznam kontaktů.
 * Vysoký úzký widget = svislý sloupec (výchozí), široký = řada, jinak mřížka 3 × 2.
 * Nad zeleným kolečkem s iniciálami je celé jméno (až na dva řádky).
 * Klepnutí zavolá, podržení kontakt upraví, prázdné tlačítko kontakt přidá.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickDialWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val slots by QuickDial.slots.collectAsState()
    var pending by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var choosingApp by remember { mutableStateOf(false) }

    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { QuickDial.call(context, it, granted) }
        pending = null
    }
    fun dial(number: String) {
        if (preview) return
        if (QuickDial.canCall(context)) QuickDial.call(context, number, true)
        else {
            pending = number
            askCall.launch(Manifest.permission.CALL_PHONE)
        }
    }

    WidgetCard(null, modifier, padding = 6.dp) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Tvar widgetu určí rozložení: vysoký a úzký = sloupec, široký a nízký = řada, jinak 3 × 2.
            val gap = 6.dp
            // Jméno nad kolečkem má až dva řádky (např. „Delikomat / Liberec“).
            val label = 40.dp
            val vertical = maxHeight > maxWidth * 1.8f
            val horizontal = !vertical && maxWidth > maxHeight * 1.8f
            val icon = when {
                vertical -> minOf(maxWidth * 0.72f, (maxHeight - gap * 5) / 6 - label - 4.dp, 84.dp)
                horizontal -> minOf((maxWidth - gap * 5) / 6 * 0.85f, maxHeight - label - 4.dp, 84.dp)
                else -> minOf((maxWidth - gap * 2) / 3 * 0.8f, (maxHeight - gap) / 2 - label - 4.dp, 84.dp)
            }.coerceAtLeast(24.dp)
            val nameWidth = when {
                vertical -> maxWidth
                horizontal -> (maxWidth - gap * 5) / 6
                else -> (maxWidth - gap * 2) / 3
            }

            @Composable
            fun Name(text: String, color: Color) {
                Box(Modifier.height(label).width(nameWidth), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        text, color = color, fontSize = 16.sp, lineHeight = 19.sp,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            @Composable
            fun Item(i: Int) {
                // Celé jméno těsně nad kolečkem.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (i < QuickDial.SLOTS) {
                        val c = slots.getOrNull(i)
                        Name(c?.name ?: "Přidat", if (c != null) Palette.Text else Palette.Muted)
                        Box(
                            Modifier.size(icon).clip(CircleShape)
                                .background(if (c != null) DialGreen else Palette.Text.copy(alpha = 0.08f))
                                .combinedClickable(
                                    enabled = !preview,
                                    onClick = { if (c != null) dial(c.number) else editing = i },
                                    onLongClick = { editing = i }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (c != null) {
                                Text(initials(c.name), color = Color.White, fontSize = (icon.value * 0.38f).sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.Add, "Přidat kontakt", tint = Palette.Muted, modifier = Modifier.size(icon * 0.45f))
                            }
                        }
                    } else {
                        Name("Kontakty", Palette.Text)
                        Box(
                            Modifier.size(icon).clip(CircleShape).background(Color(0xFFF1F3F6))
                                .combinedClickable(
                                    enabled = !preview,
                                    // Kontakty: aplikace pro telefon v rádiu; když žádná nejde otevřít, nabídne se výběr.
                                    onClick = { if (!QuickDial.openContacts(context)) choosingApp = true },
                                    onLongClick = { choosingApp = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Contacts, "Seznam kontaktů", tint = Color(0xFF1E88E5), modifier = Modifier.size(icon * 0.5f))
                        }
                    }
                }
            }

            when {
                vertical -> Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (i in 0..QuickDial.SLOTS) Item(i)
                }
                horizontal -> Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0..QuickDial.SLOTS) Item(i)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (r in 0 until 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (col in 0 until 3) Item(r * 3 + col)
                        }
                    }
                }
            }
        }
    }
    editing?.let { i -> SlotDialog(i, slots.getOrNull(i)) { editing = null } }
    if (choosingApp) cz.kuba.dash407.ui.PhoneAppDialog { choosingApp = false }
}

/** Výrazná zelená kolečka kontaktů. */
private val DialGreen = Color(0xFF10E01A)

/** Iniciály do kolečka: „Honza Novák“ → „HN“, „Máma“ → „M“. */
private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

/** Úprava jednoho ze slotů: ručně, nebo výběrem z kontaktů v rádiu. */
@Composable
private fun SlotDialog(index: Int, current: DialContact?, onClose: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(current?.name ?: "") }
    var number by remember { mutableStateOf(current?.number ?: "") }
    var note by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Kontakt ${index + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(16.dp)) { Text("Vybrat z kontaktů", fontSize = 16.sp) }
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("Jméno") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = number, onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20) },
                    singleLine = true, label = { Text("Telefonní číslo") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                note?.let { Text(it, color = Palette.Amber, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = number.isNotBlank(), onClick = {
                QuickDial.setSlot(index, DialContact(name.trim().ifBlank { number.trim() }, number.trim()))
                onClose()
            }) { Text("Uložit") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { QuickDial.setSlot(index, null); onClose() }) { Text("Smazat") }
                TextButton(onClick = onClose) { Text("Zrušit") }
            }
        }
    )
    if (picking) {
        cz.kuba.dash407.ui.ContactPickerDialog(
            onPick = { c -> name = c.name; number = c.number; note = null; picking = false },
            onClose = { picking = false }
        )
    }
}
