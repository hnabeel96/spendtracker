package com.eko.ledger

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Copies unsynced rows to the Sheet. WorkManager retries with backoff until there's network. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val url = Store.url(ctx)
        val token = Store.token(ctx)
        if (url.isEmpty() || token.isEmpty()) return Result.success()   // not set up yet; stays queued locally

        return try {
            val pending = Store.all(ctx).filter { !it.synced }
            val deletes = Store.pendingDeletes(ctx)
            if (pending.isEmpty() && deletes.isEmpty()) { Store.setSyncResult(ctx, null); return Result.success() }

            val batches = pending.chunked(50).ifEmpty { listOf(emptyList()) }
            batches.forEachIndexed { i, batch ->
                val dels = if (i == 0) deletes else emptySet()
                val body = JSONObject()
                    .put("token", token)
                    .put("txs", JSONArray().apply { batch.forEach { put(it.toSheetJson()) } })
                    .put("deletes", JSONArray(dels.toList()))
                val res = SheetApi.post(url, body)
                if (!res.optBoolean("ok")) error(res.optString("error", "Sheet rejected the request"))
                val acc = res.optJSONArray("accepted") ?: JSONArray()
                val ids = (0 until acc.length()).map { acc.getString(it) }.toSet()
                Store.markSynced(ctx, ids, dels, batch.associateBy { it.id })
            }
            Store.setSyncResult(ctx, null)
            Result.success()
        } catch (e: Exception) {
            Store.setSyncResult(ctx, e.message ?: e.javaClass.simpleName)
            if (runAttemptCount < 8) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("sync", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
