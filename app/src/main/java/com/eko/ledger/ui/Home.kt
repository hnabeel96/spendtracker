package com.eko.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eko.ledger.Money
import com.eko.ledger.Tx
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Fix(val title: String, val body: String, val action: String, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    txs: List<Tx>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    fixes: List<Fix>,
    pending: Int,
    syncError: String,
    connected: Boolean,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Tx) -> Unit,
    onDelete: (Tx) -> Unit,
    snackbar: SnackbarHostState,
) {
    val zone = ZoneId.systemDefault()
    val monthTxs = txs.filter { YearMonth.from(Instant.ofEpochMilli(it.ts).atZone(zone)) == month }
    val out = monthTxs.filter { it.isDebit }.sumOf { it.amount }
    val inn = monthTxs.filter { !it.isDebit }.sumOf { it.amount }
    val days = monthTxs.sortedByDescending { it.ts }
        .groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() }

    Box(Modifier.fillMaxSize().background(Backdrop)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ledger", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, "Settings", tint = C.Muted) }
                }
            }
            item { Hero(month, onMonth, out, inn, monthTxs.size) }
            item { CategoryBreakdown(monthTxs) }
            item { SyncPill(pending, syncError, connected, onSync, onSettings) }
            items(fixes) { FixCard(it) }

            if (days.isEmpty()) item {
                Text(
                    "Nothing logged this month yet.\nBank SMS will appear here on their own — or tap Add entry.\nTip: tap an entry to edit or set its category, swipe left to delete.",
                    color = C.Faint, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
            days.forEach { (day, list) ->
                item(key = "h$day") { DayHeader(day, list) }
                items(list, key = { it.id }) { SwipeRow(it, onOpen, onDelete) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            containerColor = C.Mint, contentColor = C.Ink, shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add entry") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(22.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp, start = 16.dp, end = 16.dp))
    }
}

@Composable
private fun Hero(month: YearMonth, onMonth: (YearMonth) -> Unit, out: Double, inn: Double, count: Int) {
    GlassCard(Modifier.fillMaxWidth(), radius = 28.dp, fill = HeroGlow, pad = 22.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = C.Muted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                SmallIcon(Icons.Default.ChevronLeft) { onMonth(month.minusMonths(1)) }
                Spacer(Modifier.width(6.dp))
                SmallIcon(Icons.Default.ChevronRight, enabled = month < YearMonth.now()) { onMonth(month.plusMonths(1)) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Spent", color = C.Muted, fontSize = 13.sp)
            Text(Money.fmt(out), color = C.Text, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(14.dp))
            // in vs out bar
            val total = (out + inn).takeIf { it > 0 } ?: 1.0
            Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x14FFFFFF))) {
                if (out > 0) Box(Modifier.weight((out / total).toFloat()).fillMaxHeight().background(C.Coral))
                if (inn > 0) Box(Modifier.weight((inn / total).toFloat()).fillMaxHeight().background(C.Mint))
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Stat("In", Money.fmt(inn), C.Mint, Modifier.weight(1f))
                Stat("Net", (if (inn - out >= 0) "+" else "−") + Money.fmt(kotlin.math.abs(inn - out)), C.Text, Modifier.weight(1f))
                Stat("Entries", count.toString(), C.Text, Modifier.weight(0.7f))
            }
        }
    }
}

/** Where the month's money went, by category. */
@Composable
private fun CategoryBreakdown(monthTxs: List<Tx>) {
    val spends = monthTxs.filter { it.isDebit }
    if (spends.isEmpty()) return
    val total = spends.sumOf { it.amount }
    val byCat = spends.groupBy { it.category.ifBlank { "Uncategorised" } }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    GlassCard(Modifier.fillMaxWidth(), pad = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("By category", color = C.Muted, fontSize = 13.sp)
            byCat.take(6).forEach { (cat, amt) ->
                val frac = (amt / total).toFloat()
                Column {
                    Row {
                        Text(cat, color = if (cat == "Uncategorised") C.Faint else C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(Money.fmt(amt), color = C.Text, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x14FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                            .background(if (cat == "Uncategorised") C.Faint else C.Coral))
                    }
                }
            }
            if (byCat.size > 6) Text("+${byCat.size - 6} more", color = C.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = C.Faint, fontSize = 12.sp)
        Text(value, color = color, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color(0x14FFFFFF))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) C.Text else C.Faint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun SyncPill(pending: Int, error: String, connected: Boolean, onSync: () -> Unit, onSettings: () -> Unit) {
    val (dot, text, click) = when {
        !connected -> Triple(C.Amber, "Sheet not connected — entries are saved on the phone. Set up →", onSettings)
        pending > 0 && error.isNotEmpty() -> Triple(C.Coral, "$pending waiting · $error · tap to retry", onSync)
        pending > 0 -> Triple(C.Amber, "$pending waiting to sync · tap to sync now", onSync)
        else -> Triple(C.Mint, "All synced to your sheet", onSync)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Glass).clickable(onClick = click)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text, color = C.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FixCard(f: Fix) {
    GlassCard(Modifier.fillMaxWidth(), pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.title, color = C.Text, fontSize = 15.sp)
                Text(f.body, color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = f.onClick) { Text(f.action, color = C.Mint) }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, list: List<Tx>) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    val spent = list.filter { it.isDebit }.sumOf { it.amount }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp)) {
        Text(label, color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (spent > 0) Text("−" + Money.fmt(spent), color = C.Faint, fontSize = 13.sp)
    }
}

/** Swipe left to delete. */
@Composable
private fun SwipeRow(tx: Tx, onOpen: (Tx) -> Unit, onDelete: (Tx) -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(tx); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(C.Coral.copy(alpha = 0.22f)).padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete", color = C.Coral, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.DeleteOutline, null, tint = C.Coral)
                }
            }
        },
    ) { TxRow(tx, onOpen) }
}

@Composable
private fun TxRow(tx: Tx, onOpen: (Tx) -> Unit) {
    val color = if (tx.isDebit) C.Coral else C.Mint
    val time = Instant.ofEpochMilli(tx.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
    GlassCard(Modifier.fillMaxWidth().clickable { onOpen(tx) }, radius = 18.dp, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (tx.isDebit) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    null, tint = color, modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx.merchant.ifEmpty { if (tx.isDebit) "Payment" else "Money in" },
                    color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tx.category.isNotBlank()) {
                        Text(
                            tx.category, color = C.Mint, fontSize = 11.sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.Mint.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Text("+ category", color = C.Amber.copy(alpha = 0.8f), fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    val meta = listOf(tx.mode.uppercase(), time, tx.note).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(meta, color = C.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text((if (tx.isDebit) "−" else "+") + Money.fmt(tx.amount), color = color, fontSize = 15.sp)
                if (!tx.synced) Icon(Icons.Default.CloudOff, "Not synced", tint = C.Amber, modifier = Modifier.size(14.dp))
            }
        }
    }
}
