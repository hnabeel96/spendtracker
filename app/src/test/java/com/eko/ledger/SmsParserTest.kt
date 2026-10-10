package com.eko.ledger

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {
    private fun p(s: String) = SmsParser.parse(s)

    @Test fun hdfcUpiSent() {
        val r = p("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 09/10/26 Ref 628212345678 Not You? Call 18002586161")!!
        assertEquals("debit", r.type); assertEquals(250.0, r.amount, 0.001)
        assertEquals("SWIGGY", r.merchant); assertEquals("XX1234", r.account)
    }

    @Test fun axisVpa() {
        val r = p("Rs.450.00 debited from A/c XX5678 on 09-10-26 to VPA swiggy.stores@icici. UPI Ref No 528212345678. -Axis Bank")!!
        assertEquals("swiggy.stores@icici", r.merchant); assertEquals("528212345678", r.ref)
    }

    @Test fun iciciCredit() {
        val r = p("Dear Customer, INR 1,200.00 credited to your A/c No XX1234 on 09-Oct-26 by UPI from rahul.k@okaxis. Ref 628299991111 -ICICI Bank")!!
        assertEquals("credit", r.type); assertEquals(1200.0, r.amount, 0.001); assertEquals("rahul.k@okaxis", r.merchant)
    }

    @Test fun sbiBareAmount() {
        val r = p("Dear UPI user A/C X9876 debited by 120.0 on date 09Oct26 trf to ZOMATO LTD Refno 628211112222. -SBI")!!
        assertEquals(120.0, r.amount, 0.001); assertEquals("ZOMATO LTD", r.merchant)
    }

    @Test fun creditCardSpendIgnoresLimit() {
        val r = p("INR 2,499.00 spent on ICICI Bank Credit Card XX4321 on 09-Oct-26 at AMAZON PAY IN. Avl Limit: INR 1,52,345.10.")!!
        assertEquals("debit", r.type); assertEquals(2499.0, r.amount, 0.001); assertEquals("card", r.mode)
    }

    @Test fun balanceNotTakenAsAmount() {
        val r = p("A/c *1234 Debited for Rs:75.00 on 09-10-2026 by Mob Bk ref no 628200001111 Avl Bal Rs:8,123.40 -Canara Bank")!!
        assertEquals(75.0, r.amount, 0.001)
    }

    @Test fun rejectsNonTransactions() {
        assertNull(p("123456 is your OTP for txn of Rs 450.00 at SWIGGY. Do not share. -HDFC"))
        assertNull(p("Your HDFC Bank Credit Card bill of Rs.12,340 is due on 15-10-26."))
        assertNull(p("Rs.199.00 will be debited from your account on 12-10-26 for NETFLIX e-mandate"))
        assertNull(p("Get flat 10% cashback on your next UPI payment! Offer valid till 31 Oct"))
    }

    @Test fun iciciSemicolonCredited() {
        val r = p("ICICI Bank Acct XX281 debited for Rs 10.00 on 10-Oct-26; SWIGGY credited. UPI:528212345678. Call 18002662 for dispute. SMS BLOCK 281 to 9215676766.")!!
        assertEquals("debit", r.type); assertEquals(10.0, r.amount, 0.001); assertEquals("SWIGGY", r.merchant)
    }

    @Test fun iciciPersonCredited() {
        val r = p("ICICI Bank Acct XX281 debited for Rs 740.00 on 09-Oct-26; RAMESH KUMAR credited. UPI:628212345678. Call 18002662 for dispute.")!!
        assertEquals("RAMESH KUMAR", r.merchant)
    }

    @Test fun footerNeverMerchant() {
        val r = p("Rs 200.00 debited from A/c XX1234 on 09-10-26. Call 18001234 to dispute. -Bank")!!
        assertFalse(r.merchant.equals("dispute", true))
        assertTrue(SmsParser.isJunkMerchant("dispute"))
    }

    @Test fun sameSmsSameId() {
        assertEquals(SmsParser.idFor("Rs 5 debited  from a/c"), SmsParser.idFor("Rs 5 debited from a/c"))
    }
}
