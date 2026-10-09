package com.eko.ledger

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Local source of truth. Everything lands here first (instant), then the sync
 * worker copies it to the Sheet whenever there's network.
 */
object Store {
    private const val PREFS = "ledger"
    private const val KEY_TXS = "txs"
    private const val KEY_DELETES = "pending_deletes"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- settings ----
    fun url(ctx: Context) = prefs(ctx).getString("url", "")!!.trim()
    fun token(ctx: Context) = prefs(ctx).getString("token", "")!!.trim()
    fun name(ctx: Context) = prefs(ctx).getString("name", "")!!.trim()
    fun saveSettings(ctx: Context, url: String, token: String, name: String) =
        prefs(ctx).edit().putString("url", url.trim()).putString("token", token.trim())
            .putString("name", name.trim()).apply()
    fun lastSync(ctx: Context) = prefs(ctx).getLong("last_sync", 0L)
    fun lastError(ctx: Context) = prefs(ctx).getString("last_error", "")!!
    fun setSyncResult(ctx: Context, error: String?) {
        val e = prefs(ctx).edit().putString("last_error", error ?: "")
        if (error == null) e.putLong("last_sync", System.currentTimeMillis())
        e.apply()
    }

    // ---- transactions ----
    @Synchronized
    fun all(ctx: Context): List<Tx> {
        val raw = prefs(ctx).getString(KEY_TXS, "[]")!!
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(Tx::fromJson) }
                .filter { it.id.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    private fun write(ctx: Context, txs: List<Tx>) {
        val arr = JSONArray()
        txs.sortedByDescending { it.ts }.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_TXS, arr.toString()).apply()
    }

    /** Adds new transactions, skipping ids already known. Returns the ones actually added. */
    @Synchronized
    fun add(ctx: Context, incoming: List<Tx>): List<Tx> {
        val current = all(ctx)
        val known = HashSet<String>().apply { current.forEach { add(it.id) }; addAll(deletedIds(ctx)) }
        val fresh = incoming.filter { known.add(it.id) }
        if (fresh.isNotEmpty()) write(ctx, current + fresh)
        return fresh
    }

    @Synchronized
    fun update(ctx: Context, tx: Tx) {
        write(ctx, all(ctx).map {
            if (it.id == tx.id) tx.copy(synced = false, dirty = it.synced || it.dirty) else it
        })
    }

    @Synchronized
    fun delete(ctx: Context, id: String) {
        val tx = all(ctx).firstOrNull { it.id == id } ?: return
        write(ctx, all(ctx).filter { it.id != id })
        // Remember the id so an inbox re-scan doesn't bring it back, and so the Sheet row gets removed.
        val dels = deletedIds(ctx) + id
        val pending = if (tx.synced || tx.dirty) pendingDeletes(ctx) + id else pendingDeletes(ctx)
        prefs(ctx).edit()
            .putStringSet("deleted_ids", dels)
            .putStringSet(KEY_DELETES, pending)
            .apply()
    }

    fun deletedIds(ctx: Context): Set<String> = prefs(ctx).getStringSet("deleted_ids", emptySet())!!.toSet()
    fun pendingDeletes(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_DELETES, emptySet())!!.toSet()

    @Synchronized
    fun markSynced(ctx: Context, ids: Set<String>, deleted: Set<String>, sent: Map<String, Tx>) {
        // Only clear the flag if the row wasn't edited again while the request was in flight.
        write(ctx, all(ctx).map {
            if (it.id in ids && sent[it.id] == it) it.copy(synced = true, dirty = false) else it
        })
        if (deleted.isNotEmpty()) {
            prefs(ctx).edit().putStringSet(KEY_DELETES, pendingDeletes(ctx) - deleted).apply()
        }
    }
}
