package com.eko.ledger

import java.security.MessageDigest

/**
 * Turns an Indian bank SMS into a transaction, or returns null if it isn't one.
 * Pure Kotlin (no Android APIs) so it can be unit tested on the JVM.
 */
object SmsParser {

    data class Parsed(
        val type: String,        // "debit" | "credit"
        val amount: Double,
        val merchant: String,
        val account: String,
        val ref: String,
        val mode: String,        // upi | card | atm | neft | imps | rtgs | bank
    )

    // Messages that mention money but are not a completed transaction.
    private val REJECT = listOf(
        "otp", "one time password", "verification code",
        "will be debited", "will be credited", "to be debited", "scheduled",
        "is due", "due on", "due date", "min amt due", "minimum amount due", "total amt due",
        "has requested", "requested money", "collect request", "payment request",
        "declined", "failed", "unsuccessful", "could not be", "insufficient",
        "pre-approved", "preapproved", "offer", "apply now", "eligible for", "cashback of upto",
        "emi conversion", "reward points",
    )

    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    // SBI-style "debited by 120.0" with no currency marker
    private val BARE_AMOUNT = Regex("""\b(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val NOT_AMOUNT_BEFORE = Regex("""(?:bal(?:ance)?|limit|avl|avbl|available)[\s.:a-z]{0,12}$""", RegexOption.IGNORE_CASE)

    private val DEBIT = Regex("""\b(debited|debit|spent|sent|paid|withdrawn|withdrawal|purchase|purchased|dr\b|transferred to)""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(credited|credit|received|deposited|refund(?:ed)?|reversed|reversal|cr\b)""", RegexOption.IGNORE_CASE)

    private val BANK_CONTEXT = Regex("""\b(a/?c|acct|account|card|upi|vpa|bank|neft|imps|rtgs|atm|wallet)\b""", RegexOption.IGNORE_CASE)

    private val VPA = Regex("""\b([a-z0-9.\-_]{2,}@[a-z]{2,})\b""", RegexOption.IGNORE_CASE)
    private val STOP = """(?=\s+(?:on|ref\w*|upi|via|avl|avbl|using|thru|through|for|dated|date|txn|transaction|bal|info|not\s+you|if\s+not)\b|[.,;(]|\s*$)"""
    private val TO_MERCHANT = Regex("""\b(?:to|at|towards|in favour of|for)\s+(?:vpa\s+)?([a-z0-9&@'*_\-/. ]{2,40}?)$STOP""", RegexOption.IGNORE_CASE)
    private val FROM_MERCHANT = Regex("""\b(?:from|by)\s+(?:vpa\s+)?([a-z0-9&@'*_\-/. ]{2,40}?)$STOP""", RegexOption.IGNORE_CASE)
    private val INFO = Regex("""\binfo[:\s-]+([a-z0-9&@'*_\-/. ]{2,40}?)(?=[.,;]|\s{2}|\s*$)""", RegexOption.IGNORE_CASE)

    private val ACCOUNT = listOf(
        Regex("""(?:a/?c|acct|account|card)(?:\s*no\.?)?[\s:.#-]*(?:ending\s*(?:with|in)?\s*)?[x*.]+\s*(\d{3,6})""", RegexOption.IGNORE_CASE),
        Regex("""ending\s*(?:with|in)?\s*[x*]*\s*(\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""(?:a/?c|acct|account)\s*(?:no\.?)?\s*(\d{4})\b""", RegexOption.IGNORE_CASE),
    )
    private val REF = Regex("""(?:upi\s*ref(?:erence)?(?:\s*no)?|ref(?:erence)?\s*(?:no|number|#|id)?|utr(?:\s*no)?|txn\s*(?:id|no)|rrn)[\s.:#-]*([a-z0-9]{6,})""", RegexOption.IGNORE_CASE)

    private val JUNK_MERCHANT = Regex("""^(your|the|a/?c|ac|acct|account|card|bank|you|self|xx|\*|ending|upi|rs|inr)\b""", RegexOption.IGNORE_CASE)

    fun parse(body: String): Parsed? {
        val text = body.replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase()
        if (REJECT.any { lower.contains(it) }) return null
        if (!BANK_CONTEXT.containsMatchIn(text)) return null

        // "credit card"/"debit card" are nouns, not directions.
        val scan = text.replace(Regex("""(credit|debit)\s+card""", RegexOption.IGNORE_CASE), "card")

        val d = DEBIT.find(scan)
        val c = CREDIT.find(scan)
        val type = when {
            d != null && c != null -> if (d.range.first <= c.range.first) "debit" else "credit"
            d != null -> "debit"
            c != null -> "credit"
            else -> return null
        }

        val amountStr = AMOUNT.findAll(scan).firstOrNull { m ->
            val before = scan.substring(maxOf(0, m.range.first - 20), m.range.first)
            !NOT_AMOUNT_BEFORE.containsMatchIn(before)
        }?.groupValues?.get(1)
            ?: BARE_AMOUNT.find(scan)?.groupValues?.get(1)
        val amount = amountStr?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        return Parsed(
            type = type,
            amount = amount,
            merchant = merchant(scan, type).ifEmpty { if (mode(lower) == "atm") "ATM cash" else "" },
            account = ACCOUNT.firstNotNullOfOrNull { it.find(scan)?.groupValues?.get(1) }?.let { "XX$it" } ?: "",
            ref = REF.find(scan)?.groupValues?.get(1) ?: "",
            mode = mode(lower),
        )
    }

    private fun merchant(text: String, type: String): String {
        val vpa = VPA.find(text)?.groupValues?.get(1)
        val dirWord = if (type == "debit") "to" else "(?:from|by)"
        Regex("""\b$dirWord\s+(?:vpa\s+)?([a-z0-9.\-_]{2,}@[a-z]{2,})""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return it.groupValues[1] }
        val primary = if (type == "debit") TO_MERCHANT else FROM_MERCHANT
        val candidates = sequenceOf(
            primary.findAll(text).map { it.groupValues[1] },
            INFO.findAll(text).map { it.groupValues[1] },
        ).flatten()
        val named = candidates.map { clean(it) }.firstOrNull { it.length >= 2 && !JUNK_MERCHANT.containsMatchIn(it) && it.count(Char::isDigit) <= 4 }
        return named ?: vpa ?: ""
    }

    private fun clean(s: String): String =
        s.trim().trimEnd('.', '-', '/').replace(Regex("""\s+"""), " ")
            .removePrefix("VPA ").removePrefix("vpa ").trim()

    private fun mode(lower: String): String = when {
        "upi" in lower || "vpa" in lower -> "upi"
        "atm" in lower || "withdrawn" in lower -> "atm"
        "card" in lower -> "card"
        "neft" in lower -> "neft"
        "imps" in lower -> "imps"
        "rtgs" in lower -> "rtgs"
        else -> "bank"
    }

    /** Same SMS always gets the same id, so the live receiver and inbox scans never double-log. */
    fun idFor(body: String): String {
        val norm = body.replace(Regex("""\s+"""), " ").trim()
        val bytes = MessageDigest.getInstance("SHA-1").digest(norm.toByteArray())
        return "sms_" + bytes.take(10).joinToString("") { "%02x".format(it) }
    }
}
