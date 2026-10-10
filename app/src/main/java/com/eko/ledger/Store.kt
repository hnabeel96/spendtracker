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

    // ---- categories (user-defined, any name) ----
    private val DEFAULT_CATEGORIES = listOf(
        "Food", "Groceries", "Transport", "Shopping", "Bills", "Rent",
        "Health", "Entertainment", "Travel", "Salary", "Transfer", "Other",
    )

    fun categories(ctx: Context): List<String> {
        val raw = prefs(ctx).getString("categories", null) ?: return DEFAULT_CATEGORIES
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) { DEFAULT_CATEGORIES }
    }

    private fun saveCategories(ctx: Context, list: List<String>) =
        prefs(ctx).edit().putString("categories", JSONArray(list).toString()).apply()

    /** Adds a category (case-insensitive de-dupe). Returns the stored name. */
    @Synchronized
    fun addCategory(ctx: Context, name: String): String {
        val n = name.trim().replace(Regex("""\s+"""), " ")
        if (n.isEmpty()) return n
        val cur = categories(ctx)
        cur.firstOrNull { it.equals(n, ignoreCase = true) }?.let { return it }
        saveCategories(ctx, cur + n)
        return n
    }

    /** Removes from the picker only — past entries keep their category. */
    @Synchronized
    fun removeCategory(ctx: Context, name: String) = saveCategories(ctx, categories(ctx) - name)

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

    /**
     * One-time repair: re-read the merchant for SMS entries the old parser got wrong
     * (e.g. "dispute"). Changed rows are re-sent to the Sheet as updates.
     */
    @Synchronized
    fun repairMerchants(ctx: Context) {
        val key = "repair_merchant_v1"
        if (prefs(ctx).getBoolean(key, false)) return
        var changed = false
        val fixed = all(ctx).map { t ->
            if (t.sms.isBlank() || !(t.merchant.isBlank() || SmsParser.isJunkMerchant(t.merchant))) return@map t
            val p = SmsParser.parse(t.sms) ?: return@map t
            if (p.merchant == t.merchant) return@map t
            changed = true
            t.copy(merchant = p.merchant, ref = t.ref.ifBlank { p.ref }, synced = false, dirty = t.synced || t.dirty)
        }
        if (changed) write(ctx, fixed)
        prefs(ctx).edit().putBoolean(key, true).apply()
    }

    /** Undo a delete: bring the row back and make sure the Sheet has it again. */
    @Synchronized
    fun restore(ctx: Context, tx: Tx) {
        prefs(ctx).edit()
            .putStringSet("deleted_ids", deletedIds(ctx) - tx.id)
            .putStringSet(KEY_DELETES, pendingDeletes(ctx) - tx.id)
            .apply()
        // dirty=true → sent as an "update": overwrites the row if it still exists, re-inserts it if not.
        write(ctx, all(ctx).filter { it.id != tx.id } + tx.copy(synced = false, dirty = true))
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
