package cz.kuba.dash407.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

/** Rychlá volba: pět vlastních kontaktů, nebo oblíbené kontakty z rádia. */
@Composable
fun QuickDialWidget(modifier: Modifier, preview: Boolean) {
    val context = LocalContext.current
    val look = LocalLook.current
    val mode by QuickDial.mode.collectAsState()
    val slots by QuickDial.slots.collectAsState()
    var canRead by remember { mutableStateOf(QuickDial.canRead(context)) }
    var starred by remember { mutableStateOf<List<DialContact>>(emptyList()) }
    var pending by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(mode, canRead) {
        if (mode == "starred" && canRead) starred = withContext(Dispatchers.IO) { QuickDial.starred(context) }
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canRead = it }
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

    WidgetCard("Rychlá volba", modifier, action = {
        Text(
            if (mode == "mine") "Moje kontakty" else "Oblíbené",
            color = look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !preview) { QuickDial.setMode(if (mode == "mine") "starred" else "mine") }
                .padding(horizontal = 4.dp)
        )
    }) {
        if (mode == "mine") {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until QuickDial.SLOTS) {
                    val c = slots.getOrNull(i)
                    DialRow(
                        name = c?.name ?: "Přidat kontakt",
                        number = c?.number,
                        empty = c == null,
                        modifier = Modifier.weight(1f, fill = false).heightIn(min = 40.dp),
                        onCall = { if (c != null) dial(c.number) else if (!preview) editing = i },
                        onEdit = if (c != null) ({ if (!preview) editing = i }) else null
                    )
                }
            }
        } else if (!canRead) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text("Pro oblíbené kontakty povol přístup ke kontaktům.", color = Palette.Muted, fontSize = 15.sp)
                Spacer(Modifier.size(8.dp))
                Button(onClick = { if (!preview) askContacts.launch(Manifest.permission.READ_CONTACTS) }, shape = RoundedCornerShape(16.dp)) {
                    Text("Povolit kontakty", fontSize = 16.sp)
                }
            }
        } else if (starred.isEmpty()) {
            Text(
                "V kontaktech v rádiu nejsou žádné oblíbené (hvězdička). Přepni na Moje kontakty a zadej si je ručně.",
                color = Palette.Muted, fontSize = 15.sp
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(starred) { c ->
                    DialRow(c.name, c.number, empty = false, modifier = Modifier.heightIn(min = 48.dp), onCall = { dial(c.number) }, onEdit = null)
                }
            }
        }
    }
    editing?.let { i -> SlotDialog(i, slots.getOrNull(i)) { editing = null } }
}

@Composable
private fun DialRow(
    name: String,
    number: String?,
    empty: Boolean,
    modifier: Modifier,
    onCall: () -> Unit,
    onEdit: (() -> Unit)?
) {
    val look = LocalLook.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onCall() }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(look.accent.copy(alpha = if (empty) 0.12f else 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (empty) "+" else name.take(1).uppercase(), color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (empty) Palette.Muted else Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (number != null) Text(number, color = Palette.Muted, fontSize = 13.sp, maxLines = 1)
        }
        if (onEdit != null) {
            Icon(
                Icons.Filled.Edit, "Upravit", tint = Palette.Muted,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onEdit() }.padding(8.dp)
            )
        }
        if (!empty) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Palette.Good.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, "Volat", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

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
