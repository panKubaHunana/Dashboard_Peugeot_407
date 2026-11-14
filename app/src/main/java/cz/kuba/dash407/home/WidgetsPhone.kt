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
 * Rychlá volba ve stylu doku: pět vlastních kontaktů a šesté tlačítko otevře seznam kontaktů.
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

    WidgetCard(null, modifier, padding = 8.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Široký widget (4 × 2): šest tlačítek v řadě. Užší: dvě řady po třech.
            val cols = if (maxWidth > maxHeight * 1.8f) 6 else 3
            val rows = 6 / cols
            val label = 18.dp
            val icon = minOf(maxWidth / cols * 0.78f, (maxHeight / rows - label - 6.dp) * 0.9f, 84.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        for (col in 0 until cols) {
                            val i = r * cols + col
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (i < QuickDial.SLOTS) {
                                    val c = slots.getOrNull(i)
                                    Text(
                                        c?.name?.substringBefore(' ') ?: "Přidat",
                                        color = if (c != null) Palette.Text else Palette.Muted, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.height(label).padding(horizontal = 2.dp)
                                    )
                                    Box(
                                        Modifier.size(icon).clip(CircleShape)
                                            .background(if (c != null) Color.White.copy(alpha = 0.92f) else Palette.Text.copy(alpha = 0.08f))
                                            .combinedClickable(
                                                enabled = !preview,
                                                onClick = { if (c != null) dial(c.number) else editing = i },
                                                onLongClick = { editing = i }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (c != null) {
                                            Text(initials(c.name), color = look.accent, fontSize = (icon.value * 0.36f).sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Filled.Add, "Přidat kontakt", tint = Palette.Muted, modifier = Modifier.size(icon * 0.45f))
                                        }
                                    }
                                } else {
                                    Text("Kontakty", color = Palette.Text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.height(label))
                                    Box(
                                        Modifier.size(icon).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                                            .clickable(enabled = !preview) { QuickDial.openContacts(context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Contacts, "Seznam kontaktů", tint = look.accent, modifier = Modifier.size(icon * 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { i -> SlotDialog(i, slots.getOrNull(i)) { editing = null } }
}

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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            val c = QuickDial.fromPick(context, uri)
            if (c != null) { name = c.name; number = c.number; note = null } else note = "Kontakt se nepodařilo přečíst."
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Kontakt ${index + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try {
                        pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    } catch (e: Exception) {
                        note = "Rádio nemá aplikaci s kontakty. Zadej jméno a číslo ručně."
                    }
                }, shape = RoundedCornerShape(16.dp)) { Text("Vybrat z kontaktů", fontSize = 16.sp) }
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
}
