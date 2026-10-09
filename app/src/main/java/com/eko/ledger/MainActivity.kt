package com.eko.ledger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.eko.ledger.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.YearMonth
import java.util.Date

class MainActivity : ComponentActivity() {

    private var version by mutableIntStateOf(0)          // bumps whenever stored data changes
    private var resumed by mutableIntStateOf(0)          // bumps on every resume (re-check permissions)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        setContent { LedgerTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        resumed++
        // Catch anything the receiver missed (phone off, app killed), then push to the Sheet.
        lifecycleScope.launch(Dispatchers.IO) { Ingest.scanInbox(this@MainActivity, 3) }
        SyncWorker.enqueue(this)
    }

    override fun onDestroy() {
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("BatteryLife")
    @Composable
    private fun App() {
        val ctx = this
        @Suppress("UNUSED_VARIABLE") val v = version
        @Suppress("UNUSED_VARIABLE") val r = resumed

        var screen by remember { mutableStateOf("home") }
        var month by remember { mutableStateOf(YearMonth.now()) }
        var editing by remember { mutableStateOf<Tx?>(null) }
        var adding by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            resumed++
            lifecycleScope.launch(Dispatchers.IO) { Ingest.scanInbox(ctx, 30) }
        }
        val smsPerms = buildList {
            add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

        // Ask once on first launch.
        LaunchedEffect(Unit) {
            if (!granted(Manifest.permission.RECEIVE_SMS)) perms.launch(smsPerms)
        }

        val txs = remember(version) { Store.all(ctx) }
        val connected = Store.url(ctx).isNotEmpty() && Store.token(ctx).isNotEmpty()
        val pending = txs.count { !it.synced } + Store.pendingDeletes(ctx).size

        val fixes = buildList {
            if (!granted(Manifest.permission.RECEIVE_SMS) || !granted(Manifest.permission.READ_SMS)) add(Fix(
                "Allow SMS access", "So bank debits and credits log themselves. Messages never leave the phone except the parsed rows.",
                "Allow") { perms.launch(smsPerms) })
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) add(Fix(
                "Keep auto-logging alive", "Some phones stop apps in the background. Exempt Ledger from battery optimisation.",
                "Fix") {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            })
        }

        BackHandler(enabled = screen != "home") { screen = "home" }

        when (screen) {
            "settings" -> SettingsScreen(
                url0 = Store.url(ctx), token0 = Store.token(ctx), name0 = Store.name(ctx),
                lastSync = Store.lastSync(ctx).let { if (it == 0L) "never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
                status = status, busy = busy,
                onSave = { u, t, n -> Store.saveSettings(ctx, u, t, n); status = "Saved."; SyncWorker.enqueue(ctx) },
                onTest = { u, t ->
                    busy = true; status = "Testing…"
                    lifecycleScope.launch {
                        status = withContext(Dispatchers.IO) {
                            try {
                                val res = SheetApi.ping(u.trim(), t.trim())
                                if (res.optBoolean("ok")) "Connected ✓ to “${res.optString("sheet")}” — ${res.optInt("rows")} rows"
                                else "Sheet said: ${res.optString("error")}"
                            } catch (e: Exception) { "Failed: ${e.message}" }
                        }
                        busy = false
                    }
                },
                onImport = {
                    busy = true; status = "Reading inbox…"
                    lifecycleScope.launch {
                        val n = withContext(Dispatchers.IO) { Ingest.scanInbox(ctx, 30) }
                        status = if (!granted(Manifest.permission.READ_SMS)) "SMS permission is off — allow it on the home screen."
                                 else "Imported $n new transaction${if (n == 1) "" else "s"}."
                        busy = false
                    }
                },
                onSync = { SyncWorker.enqueue(ctx); status = "Sync queued." },
                onBack = { screen = "home" },
            )
            else -> HomeScreen(
                txs = txs, month = month, onMonth = { month = it }, fixes = fixes,
                pending = pending, syncError = Store.lastError(ctx), connected = connected,
                onSync = { SyncWorker.enqueue(ctx) },
                onSettings = { status = ""; screen = "settings" },
                onAdd = { adding = true },
                onOpen = { editing = it },
            )
        }

        if (adding || editing != null) {
            TxDialog(
                initial = editing,
                paidBy = Store.name(ctx),
                onSave = { tx ->
                    if (editing == null) Store.add(ctx, listOf(tx)) else Store.update(ctx, tx)
                    SyncWorker.enqueue(ctx); adding = false; editing = null
                },
                onDelete = { tx -> Store.delete(ctx, tx.id); SyncWorker.enqueue(ctx); editing = null },
                onDismiss = { adding = false; editing = null },
            )
        }
    }
}
