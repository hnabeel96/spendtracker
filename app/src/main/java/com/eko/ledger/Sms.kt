package com.eko.ledger

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Ingest {
    fun toTx(ctx: Context, body: String, ts: Long, source: String): Tx? {
        val p = SmsParser.parse(body) ?: return null
        return Tx(
            id = SmsParser.idFor(body), ts = ts, amount = p.amount, type = p.type,
            merchant = p.merchant, account = p.account, ref = p.ref, mode = p.mode,
            paidBy = Store.name(ctx), source = source, sms = body,
        )
    }

    /** Safety net for SMS the receiver missed (phone off, app killed by the OEM). */
    fun scanInbox(ctx: Context, days: Int): Int {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return 0
        val since = System.currentTimeMillis() - days * 86_400_000L
        val found = mutableListOf<Tx>()
        ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("body", "date"),
            "date > ?", arrayOf(since.toString()), "date DESC"
        )?.use { c ->
            val b = c.getColumnIndex("body"); val d = c.getColumnIndex("date")
            while (c.moveToNext()) {
                val body = c.getString(b) ?: continue
                toTx(ctx, body, c.getLong(d), "scan")?.let(found::add)
            }
        }
        val added = Store.add(ctx, found)
        if (added.isNotEmpty()) SyncWorker.enqueue(ctx)
        return added.size
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Long SMS arrive as several parts; stitch them per sender.
        val bySender = parts.groupBy { it.originatingAddress ?: "" }
        val txs = bySender.values.mapNotNull { msgs ->
            val body = msgs.joinToString("") { it.messageBody ?: "" }
            Ingest.toTx(ctx, body, msgs.first().timestampMillis, "sms")
        }
        val added = Store.add(ctx, txs)
        if (added.isEmpty()) return
        SyncWorker.enqueue(ctx)
        added.forEach { Notify.logged(ctx, it) }
    }
}

object Notify {
    private const val CHANNEL = "auto_logged"

    fun logged(ctx: Context, tx: Tx) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Auto-logged transactions", NotificationManager.IMPORTANCE_LOW))
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val verb = if (tx.isDebit) "spent" else "received"
        val who = tx.merchant.ifEmpty { tx.mode.uppercase() }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Money.fmt(tx.amount)} $verb")
            .setContentText(if (who.isNotEmpty()) "$who · logged to your sheet" else "Logged to your sheet")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(tx.id.hashCode(), n) } catch (_: SecurityException) {}
    }
}
