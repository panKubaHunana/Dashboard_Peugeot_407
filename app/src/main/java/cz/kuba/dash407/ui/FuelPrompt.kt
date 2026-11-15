package cz.kuba.dash407.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.FuelStops
import cz.kuba.dash407.data.Odometer

/**
 * Okno automatického tankování: „Tankoval jsi u …?“ s předvyplněným tachometrem.
 * Zapsat / Později (ukáže se znovu při další zastávce) / Netankoval jsem.
 */
@Composable
fun FuelPromptDialog() {
    val prompt by FuelStops.prompt.collectAsState()
    val p = prompt ?: return
    val refuels by FuelLog.entries.collectAsState()
    val lastKm = refuels.maxOfOrNull { it.odometerKm }
    var km by remember(p.at) { mutableStateOf(Odometer.km()?.takeIf { lastKm == null || it > lastKm }?.toString() ?: "") }
    var litres by remember(p.at) { mutableStateOf("") }
    var price by remember(p.at) { mutableStateOf("") }
    var full by remember(p.at) { mutableStateOf(true) }

    val kmValue = parseNumber(km)?.toInt()
    val litresValue = parseNumber(litres)
    val priceValue = parseNumber(price)
    val kmTooLow = kmValue != null && lastKm != null && kmValue <= lastKm
    val valid = kmValue != null && kmValue > 0 && litresValue != null && litresValue > 0.0 && !kmTooLow
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = { FuelStops.later() },
        icon = { Icon(Icons.Filled.LocalGasStation, null, tint = Palette.Amber, modifier = Modifier.size(34.dp)) },
        title = { Text("Tankoval jsi?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.station.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = km, onValueChange = { km = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("Stav tachometru (km)") }, singleLine = true, isError = kmTooLow,
                    supportingText = {
                        when {
                            kmTooLow -> Text("Musí být víc než poslední tankování (${lastKm} km)")
                            Odometer.km() != null -> Text("Předvyplněno odhadem z Knihy jízd – oprav podle štítu")
                            else -> {}
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = litres, onValueChange = { litres = it.take(7) }, label = { Text("Litrů") },
                        singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = price, onValueChange = { price = it.take(8) }, label = { Text("Cena celkem Kč") },
                        singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f)
                    )
                }
                if (litresValue != null && litresValue > 0 && priceValue != null && priceValue > 0) {
                    Text("${decimal(priceValue / litresValue, 2)} Kč/l", color = Palette.Muted, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = full, onCheckedChange = { full = it })
                    Text("Plná nádrž (počítá se z ní spotřeba)")
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                if (kmValue != null && litresValue != null) {
                    FuelLog.add(kmValue, litresValue, priceValue ?: 0.0, full, p.station)
                    FuelStops.saved()
                }
            }) { Text("Zapsat") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { FuelStops.decline() }) { Text("Netankoval jsem") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { FuelStops.later() }) { Text("Později") }
            }
        }
    )
}
