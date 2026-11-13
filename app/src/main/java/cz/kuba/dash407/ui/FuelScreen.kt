package cz.kuba.dash407.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Refuel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FuelScreen() {
    val refuels by FuelLog.entries.collectAsState()
    val stats = FuelLog.stats(refuels)
    val perFill = FuelLog.consumptions(refuels)
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Refuel?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(0.dp)) }
        item {
            Tile(stringResource(R.string.fuel_stats_title), Modifier.fillMaxWidth().height(200.dp)) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Reading(stats.averageL100?.let { decimal(it, 2) } ?: DASH, stringResource(R.string.fuel_average), Modifier.weight(1f))
                    Reading(stats.lastL100?.let { decimal(it, 2) } ?: DASH, stringResource(R.string.fuel_last), Modifier.weight(1f))
                    Reading(stats.pricePerKm?.let { decimal(it, 2) } ?: DASH, stringResource(R.string.fuel_price_km), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Reading(decimal(stats.totalLitres, 1), stringResource(R.string.fuel_total_litres), Modifier.weight(1f))
                    Reading(decimal(stats.totalPrice, 0), stringResource(R.string.fuel_total_price), Modifier.weight(1f))
                    Reading(stats.trackedKm.toString(), stringResource(R.string.fuel_tracked_km), Modifier.weight(1f))
                }
            }
        }
        item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.fuel_add), fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (refuels.isEmpty()) {
            item {
                Text(stringResource(R.string.fuel_empty), color = Palette.Muted, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            }
        }
        items(refuels, key = { it.id }) { r ->
            RefuelRow(r, perFill[r.id], onDelete = { deleting = r })
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (adding) {
        AddRefuelDialog(
            lastKm = refuels.firstOrNull()?.odometerKm,
            onDismiss = { adding = false },
            onSave = { km, litres, price, full ->
                FuelLog.add(km, litres, price, full)
                adding = false
            }
        )
    }
    val toDelete = deleting
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.fuel_delete_title)) },
            text = { Text(stringResource(R.string.fuel_delete_text, toDelete.odometerKm)) },
            confirmButton = {
                TextButton(onClick = {
                    FuelLog.remove(toDelete.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun RefuelRow(r: Refuel, consumption: Double?, onDelete: () -> Unit) {
    val date = Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d. M. yyyy", Czech))
    Surface(shape = RoundedCornerShape(20.dp), color = Palette.Tile, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.fuel_row_main, date, r.odometerKm),
                    color = Palette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.fuel_row_detail, decimal(r.litres, 2), decimal(r.price, 0)) +
                        if (r.full) "" else " · " + stringResource(R.string.fuel_row_partial),
                    color = Palette.Muted,
                    fontSize = 15.sp
                )
            }
            Text(
                consumption?.let { stringResource(R.string.value_l100, decimal(it, 2)) } ?: DASH,
                color = if (consumption != null) Palette.Accent else Palette.Muted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = Palette.Muted)
            }
        }
    }
}

@Composable
private fun AddRefuelDialog(
    lastKm: Int?,
    onDismiss: () -> Unit,
    onSave: (km: Int, litres: Double, price: Double, full: Boolean) -> Unit
) {
    var km by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }

    val kmValue = parseNumber(km)?.toInt()
    val litresValue = parseNumber(litres)
    val priceValue = parseNumber(price) ?: 0.0
    val kmTooLow = kmValue != null && lastKm != null && kmValue <= lastKm
    val valid = kmValue != null && kmValue > 0 && litresValue != null && litresValue > 0.0 && !kmTooLow
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fuel_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it },
                    label = { Text(stringResource(R.string.fuel_field_km)) },
                    supportingText = {
                        if (kmTooLow) Text(stringResource(R.string.fuel_error_km, lastKm ?: 0))
                        else if (lastKm != null) Text(stringResource(R.string.fuel_last_km, lastKm))
                    },
                    isError = kmTooLow,
                    singleLine = true,
                    keyboardOptions = numbers
                )
                OutlinedTextField(
                    value = litres,
                    onValueChange = { litres = it },
                    label = { Text(stringResource(R.string.fuel_field_litres)) },
                    singleLine = true,
                    keyboardOptions = numbers
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.fuel_field_price)) },
                    singleLine = true,
                    keyboardOptions = numbers
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = full, onCheckedChange = { full = it })
                    Text(stringResource(R.string.fuel_field_full))
                }
                Text(stringResource(R.string.fuel_full_hint), color = Palette.Muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (kmValue != null && litresValue != null) onSave(kmValue, litresValue, priceValue, full) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
