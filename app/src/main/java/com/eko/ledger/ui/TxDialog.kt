package com.eko.ledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eko.ledger.Tx
import java.util.UUID

/** Add (initial == null) or edit a transaction. */
@Composable
fun TxDialog(
    initial: Tx?,
    paidBy: String,
    onSave: (Tx) -> Unit,
    onDelete: (Tx) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var debit by remember { mutableStateOf(initial?.isDebit ?: true) }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = amount.replace(",", "").toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ink2,
        title = { Text(if (initial == null) "Add entry" else "Edit entry", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = debit, onClick = { debit = true }, label = { Text("Spent") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Coral.copy(alpha = .25f)))
                    FilterChip(selected = !debit, onClick = { debit = false }, label = { Text("Received") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Mint.copy(alpha = .25f)))
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                if (!initial?.sms.isNullOrBlank()) {
                    Text("From SMS", color = C.Faint, fontSize = 12.sp)
                    Text(initial!!.sms, color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                val base = initial ?: Tx(
                    id = "m_" + UUID.randomUUID().toString().take(12), ts = System.currentTimeMillis(),
                    amount = 0.0, type = "debit", mode = "cash", paidBy = paidBy, source = "manual",
                )
                onSave(base.copy(amount = value!!, type = if (debit) "debit" else "credit", merchant = merchant.trim(), note = note.trim()))
            }) { Text("Save", color = C.Mint) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = {
                    if (confirmDelete) onDelete(initial) else confirmDelete = true
                }) { Text(if (confirmDelete) "Tap again to delete" else "Delete", color = C.Coral) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) }
            }
        },
    )
}
