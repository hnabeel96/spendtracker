package com.eko.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    url0: String, token0: String, name0: String,
    lastSync: String, status: String, busy: Boolean,
    onSave: (String, String, String) -> Unit,
    onTest: (String, String) -> Unit,
    onImport: () -> Unit,
    onSync: () -> Unit,
    onBack: () -> Unit,
) {
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
                    Text("Sync", color = C.Text, fontSize = 16.sp)
                    Text("Last successful sync: $lastSync", color = C.Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onSync) { Text("Sync now") }
                        OutlinedButton(enabled = !busy, onClick = onImport) { Text("Import last 30 days") }
                    }
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
