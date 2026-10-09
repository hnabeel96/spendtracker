package com.eko.ledger

import java.text.NumberFormat
import java.util.Locale

object Money {
    private val inr = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2; minimumFractionDigits = 0
    }
    fun fmt(v: Double) = "₹" + inr.format(v)
}
