package com.eko.ledger

import org.json.JSONObject

/** One money movement. Field names match the Sheet's column headers. */
data class Tx(
    val id: String,
    val ts: Long,
    val amount: Double,
    val type: String,            // "debit" | "credit"
    val category: String = "",   // left blank for now — to be filled later
    val merchant: String = "",
    val account: String = "",
    val ref: String = "",
    val note: String = "",
    val mode: String = "",
    val paidBy: String = "",
    val source: String = "sms",  // sms | scan | manual
    val sms: String = "",
    val synced: Boolean = false,
    val dirty: Boolean = false,  // edited after it was synced → send as an update
) {
    val isDebit get() = type == "debit"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("ts", ts).put("amount", amount).put("type", type)
        .put("category", category).put("merchant", merchant).put("account", account)
        .put("ref", ref).put("note", note).put("mode", mode).put("paid_by", paidBy)
        .put("source", source).put("sms", sms).put("synced", synced).put("dirty", dirty)

    /** Payload for the Sheet (no local-only flags). */
    fun toSheetJson(): JSONObject = toJson().apply {
        remove("synced"); remove("dirty")
        if (dirty) put("op", "update")
    }

    companion object {
        /** Tolerant: every field optional with a default, so older saved data always loads. */
        fun fromJson(o: JSONObject) = Tx(
            id = o.optString("id"),
            ts = o.optLong("ts", System.currentTimeMillis()),
            amount = o.optDouble("amount", 0.0),
            type = o.optString("type", "debit"),
            category = o.optString("category"),
            merchant = o.optString("merchant"),
            account = o.optString("account"),
            ref = o.optString("ref"),
            note = o.optString("note"),
            mode = o.optString("mode"),
            paidBy = o.optString("paid_by"),
            source = o.optString("source", "sms"),
            sms = o.optString("sms"),
            synced = o.optBoolean("synced", false),
            dirty = o.optBoolean("dirty", false),
        )
    }
}
