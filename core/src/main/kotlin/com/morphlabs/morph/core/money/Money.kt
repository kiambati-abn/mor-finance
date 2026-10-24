package com.morphlabs.morph.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * All money in Morph is stored as whole cents in a [Long] so sums never drift.
 * Ksh 1,234.50 is stored as 123450.
 */
object Money {

    /** Parses "1,234.50", "Ksh1,234.50", "Ksh 50" or "50.5" into cents, or null if it isn't money. */
    fun parse(text: String): Long? {
        val cleaned = text.trim()
            .replace(Regex("""^(?i)(ksh|kes)\.?\s*"""), "")
            .replace(",", "")
            .trimEnd('.')
        if (cleaned.isEmpty() || !cleaned.matches(Regex("""\d+(\.\d{1,2})?"""))) return null
        return BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /** "Ksh 1,234" for whole amounts, "Ksh 1,234.50" otherwise. Negative amounts get a leading minus. */
    fun format(cents: Long, withCurrency: Boolean = true): String {
        val sign = if (cents < 0) "-" else ""
        val a = abs(cents)
        val whole = a / 100
        val frac = a % 100
        val wholeText = "%,d".format(whole)
        val body = if (frac == 0L) wholeText else "$wholeText.${"%02d".format(frac)}"
        return if (withCurrency) "${sign}Ksh $body" else "$sign$body"
    }

    /** Short form for tight spaces: 950, 1.2K, 45K, 1.3M. */
    fun compact(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val shillings = abs(cents) / 100.0
        return sign + when {
            shillings >= 1_000_000 -> trim("%.1f".format(shillings / 1_000_000)) + "M"
            shillings >= 10_000 -> "%.0f".format(shillings / 1_000) + "K"
            shillings >= 1_000 -> trim("%.1f".format(shillings / 1_000)) + "K"
            else -> "%.0f".format(shillings)
        }
    }

    fun ofShillings(shillings: Long): Long = shillings * 100

    private fun trim(s: String) = s.removeSuffix(".0")
}
