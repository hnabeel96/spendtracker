package com.eko.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eko.ledger.Tx
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Add (initial == null) or edit a transaction. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxDialog(
    initial: Tx?,
    paidBy: String,
    categories: List<String>,
    onAddCategory: (String) -> String,
    subcategories: Map<String, List<String>>,
    onAddSubcategory: (String, String) -> String,
    onSave: (Tx) -> Unit,
    onDelete: (Tx) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    var amount by remember { mutableStateOf(initial?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var debit by remember { mutableStateOf(initial?.isDebit ?: true) }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var subcategory by remember { mutableStateOf(initial?.subcategory ?: "") }
    var newSub by remember { mutableStateOf<String?>(null) }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var ts by remember { mutableLongStateOf(initial?.ts ?: System.currentTimeMillis()) }
    var newCat by remember { mutableStateOf<String?>(null) }   // null = "+ New" field hidden
    var confirmDelete by remember { mutableStateOf(false) }
    val value = amount.replace(",", "").toDoubleOrNull()

    // Categories in the picker, plus this entry's category even if it was removed from the list.
    val chips = remember(categories, category) {
        if (category.isNotEmpty() && categories.none { it.equals(category, true) }) categories + category else categories
    }

    fun pickDate() {
        val cur = Instant.ofEpochMilli(ts).atZone(zone)
        DatePickerDialog(ctx, { _, y, m, d ->
            ts = cur.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli()
        }, cur.year, cur.monthValue - 1, cur.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    fun setCategory(c: String) {
        if (!c.equals(category, true)) { subcategory = ""; newSub = null }
        category = c
    }

    fun commitNewCat() {
        val n = newCat?.trim().orEmpty()
        if (n.isNotEmpty()) setCategory(onAddCategory(n))
        newCat = null
    }

    fun commitNewSub() {
        val n = newSub?.trim().orEmpty()
        if (n.isNotEmpty() && category.isNotBlank()) subcategory = onAddSubcategory(category, n)
        newSub = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ink2,
        title = { Text(if (initial == null) "Add entry" else "Edit entry", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = debit, onClick = { debit = true }, label = { Text("Spent") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Coral.copy(alpha = .25f)))
                    FilterChip(selected = !debit, onClick = { debit = false }, label = { Text("Received") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Mint.copy(alpha = .25f)))
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                // ---- category ----
                Text("Category", color = C.Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    chips.forEach { c ->
                        FilterChip(
                            selected = category.equals(c, true),
                            onClick = { setCategory(if (category.equals(c, true)) "" else c) },
                            label = { Text(c) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Mint.copy(alpha = .25f)),
                        )
                    }
                    AssistChip(onClick = { newCat = "" }, label = { Text("New") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
                }
                if (newCat != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newCat!!, { newCat = it }, label = { Text("New category") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitNewCat() }),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { commitNewCat() }) { Text("Add", color = C.Mint) }
                    }
                }

                // ---- subcategory (only once a category is chosen) ----
                if (category.isNotBlank()) {
                    val subs = (subcategories[category].orEmpty()).let {
                        if (subcategory.isNotEmpty() && it.none { s -> s.equals(subcategory, true) }) it + subcategory else it
                    }
                    Text("Subcategory · $category", color = C.Muted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        subs.forEach { sc ->
                            FilterChip(
                                selected = subcategory.equals(sc, true),
                                onClick = { subcategory = if (subcategory.equals(sc, true)) "" else sc },
                                label = { Text(sc) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Amber.copy(alpha = .22f)),
                            )
                        }
                        AssistChip(onClick = { newSub = "" }, label = { Text(if (subs.isEmpty()) "Add subcategory" else "New") },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
                    }
                    if (newSub != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                newSub!!, { newSub = it }, label = { Text("New subcategory") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitNewSub() }),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { commitNewSub() }) { Text("Add", color = C.Mint) }
                        }
                    }
                }

                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())

                // ---- date ----
                val day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val today = LocalDate.now()
                    FilterChip(selected = day == today, onClick = {
                        ts = Instant.ofEpochMilli(ts).atZone(zone).with(today).toInstant().toEpochMilli()
                    }, label = { Text("Today") })
                    FilterChip(selected = day == today.minusDays(1), onClick = {
                        ts = Instant.ofEpochMilli(ts).atZone(zone).with(today.minusDays(1)).toInstant().toEpochMilli()
                    }, label = { Text("Yesterday") })
                    AssistChip(onClick = { pickDate() },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) },
                        label = { Text(if (day != today && day != today.minusDays(1)) day.format(DateTimeFormatter.ofPattern("d MMM")) else "Pick") })
                }

                if (!initial?.sms.isNullOrBlank()) {
                    Text("From SMS", color = C.Faint, fontSize = 12.sp)
                    Text(initial!!.sms, color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }

                if (initial != null) {
                    OutlinedButton(
                        onClick = { if (confirmDelete) onDelete(initial) else confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Coral),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (confirmDelete) "Tap again to delete (also removes it from the Sheet)" else "Delete entry")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                if (newCat != null) commitNewCat()
                if (newSub != null) commitNewSub()
                val base = initial ?: Tx(
                    id = "m_" + UUID.randomUUID().toString().take(12), ts = ts,
                    amount = 0.0, type = "debit", mode = "cash", paidBy = paidBy, source = "manual",
                )
                onSave(base.copy(
                    amount = value!!, type = if (debit) "debit" else "credit", ts = ts,
                    category = category, subcategory = if (category.isBlank()) "" else subcategory, merchant = merchant.trim(), note = note.trim(),
                ))
            }) { Text("Save", color = C.Mint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}
