package com.eko.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    url0: String, token0: String, name0: String,
    lastSync: String, status: String, busy: Boolean,
    onSave: (String, String, String) -> Unit,
    onTest: (String, String) -> Unit,
    onImport: () -> Unit,
    onSync: () -> Unit,
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    subcategories: Map<String, List<String>>,
    onAddSubcategory: (String, String) -> Unit,
    onRemoveSubcategory: (String, String) -> Unit,
    onResendAll: () -> Unit,
    onBack: () -> Unit,
) {
    var newCat by remember { mutableStateOf("") }
    var newSub by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    confirmRemove?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null }, containerColor = C.Ink2,
            title = { Text("Remove “$c”?", color = C.Text) },
            text = { Text("It disappears from the pickers. Entries already tagged $c keep it.", color = C.Muted) },
            confirmButton = { TextButton(onClick = { onRemoveCategory(c); confirmRemove = null }) { Text("Remove", color = C.Coral) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel", color = C.Muted) } },
        )
    }
    var url by remember { mutableStateOf(url0) }
    var token by remember { mutableStateOf(token0) }
    var name by remember { mutableStateOf(name0) }

    Box(Modifier.fillMaxSize().background(Backdrop)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
                Text("Settings", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Light)
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Google Sheet", color = C.Text, fontSize = 16.sp)
                    OutlinedTextField(url, { url = it }, label = { Text("Web app URL (…/exec)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(token, { token = it }, label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Your name (paid_by column)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onSave(url, token, name) },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Mint, contentColor = C.Ink)) { Text("Save") }
                        OutlinedButton(enabled = !busy, onClick = { onSave(url, token, name); onTest(url, token) }) { Text("Test connection") }
                    }
                    if (status.isNotEmpty()) Text(status, color = C.Muted, fontSize = 13.sp)
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Categories & subcategories", color = C.Text, fontSize = 16.sp)
                    Text("Tap a category to see and add its subcategories. Removing only hides it from pickers — past entries keep it.",
                        color = C.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                    categories.forEach { c ->
                        val open = expanded == c
                        val subs = subcategories[c].orEmpty()
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Glass)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded = if (open) null else c; newSub = "" }
                                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c, color = C.Text, fontSize = 15.sp)
                                if (subs.isNotEmpty()) Text("  · ${subs.size} sub", color = C.Faint, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = C.Muted)
                                IconButton(onClick = { confirmRemove = c }) { Icon(Icons.Default.Close, "Remove $c", tint = C.Faint, modifier = Modifier.size(18.dp)) }
                            }
                            if (open) Column(Modifier.padding(start = 14.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    subs.forEach { sc ->
                                        InputChip(
                                            selected = false, onClick = {}, label = { Text(sc) },
                                            trailingIcon = {
                                                Icon(Icons.Default.Close, "Remove $sc",
                                                    Modifier.size(16.dp).clickable { onRemoveSubcategory(c, sc) })
                                            },
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        newSub, { newSub = it }, label = { Text("New subcategory in $c") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { onAddSubcategory(c, newSub); newSub = "" }),
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(enabled = newSub.isNotBlank(), onClick = { onAddSubcategory(c, newSub); newSub = "" }) { Text("Add", color = C.Mint) }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newCat, { newCat = it }, label = { Text("New category") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onAddCategory(newCat); newCat = "" }),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = newCat.isNotBlank(), onClick = { onAddCategory(newCat); newCat = "" }) { Text("Add", color = C.Mint) }
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sync", color = C.Text, fontSize = 16.sp)
                    Text("Last successful sync: $lastSync", color = C.Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onSync) { Text("Sync now") }
                        OutlinedButton(enabled = !busy, onClick = onImport) { Text("Import last 30 days") }
                    }
                    OutlinedButton(onClick = onResendAll) { Text("Re-send all to Sheet") }
                    Text(
                        "Import reads bank SMS already in your inbox. Duplicates are skipped, so it's safe to run any time.",
                        color = C.Faint, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Setting up the Sheet", color = C.Text, fontSize = 16.sp)
                    listOf(
                        "1. New Google Sheet → Extensions → Apps Script",
                        "2. Paste backend/Code.gs from the spendtracker repo",
                        "3. Change TOKEN at the top to a long random string",
                        "4. Deploy → New deployment → Web app · Execute as Me · Access: Anyone",
                        "5. Paste the /exec URL and the token above → Test connection",
                    ).forEach { Text(it, color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp) }
                }
            }
        }
    }
}
