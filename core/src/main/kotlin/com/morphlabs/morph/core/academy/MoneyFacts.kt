package com.morphlabs.morph.core.academy

import com.morphlabs.morph.core.money.Money

/** Your numbers, in cents, as the Academy needs them. Zero means "unknown / none". */
data class MoneyNumbers(
    val feesMonth: Long = 0,
    /** Day of the month today (1..31), to project this month's fees over a year. */
    val dayOfMonth: Int = 1,
    val fixedMonth: Long = 0,
    val savings: Long = 0,
    val debts: Long = 0,
    /** Average monthly spending over the months Morph has seen. */
    val spendMonthAvg: Long = 0,
    val netWorth: Long = 0,
    val inLastMonth: Long = 0,
    val outLastMonth: Long = 0,
)

/** Turns your numbers into the {placeholders} YOU cards use, your freedom stage and the signals that pick cards. */
object MoneyFacts {

    /** Freedom stages, from the research in docs/ACADEMY.md. */
    fun stage(n: MoneyNumbers): String = when {
        n.fixedMonth > 0 && n.savings < n.fixedMonth -> "1 · Steady (build a 1-month buffer)"
        n.debts > 0 -> "2 · Debt-free (clear costly debt)"
        n.fixedMonth > 0 && n.savings < 3 * n.fixedMonth -> "3 · Safety net (3–6 months saved)"
        freedomPct(n) in 0 until 25 -> "4 · Growing (invest every month)"
        freedomPct(n) in 25 until 100 -> "5 · Choice (work becomes optional bit by bit)"
        freedomPct(n) >= 100 -> "6 · Free"
        else -> "1 · Steady (build a 1-month buffer)"
    }

    fun freedomNumber(n: MoneyNumbers): Long = n.spendMonthAvg * 12 * 30

    /** Percent of the freedom number your net worth covers, or -1 when unknown. */
    fun freedomPct(n: MoneyNumbers): Int {
        val f = freedomNumber(n)
        return if (f <= 0) -1 else (maxOf(n.netWorth, 0) * 100 / f).toInt()
    }

    fun savingsRate(n: MoneyNumbers): Int? =
        if (n.inLastMonth <= 0) null else ((n.inLastMonth - n.outLastMonth) * 100 / n.inLastMonth).toInt()

    fun facts(n: MoneyNumbers): Map<String, String> = buildMap {
        val k = Money::format
        if (n.feesMonth > 0) {
            put("fees_month", k(n.feesMonth, true))
            put("fees_year", k(n.feesMonth * 365 / n.dayOfMonth.coerceAtLeast(1) / 100 * 100, true))
        }
        if (n.fixedMonth > 0) {
            put("fixed_month", k(n.fixedMonth, true))
            put("emergency_target", k(3 * n.fixedMonth, true))
            val months = n.savings.toDouble() / n.fixedMonth
            put("runway", if (months < 1) "${(months * 30).toInt()} days" else "%.1f months".format(months).replace(".0 ", " "))
        }
        put("savings", k(maxOf(n.savings, 0), true))
        if (n.debts > 0) put("debts", k(n.debts, true))
        if (n.spendMonthAvg > 0) {
            put("spend_year", k(n.spendMonthAvg * 12, true))
            put("freedom_number", k(freedomNumber(n), true))
            put("net_worth", k(n.netWorth, true))
            put("freedom_pct", "${freedomPct(n)}%")
        }
        savingsRate(n)?.let { put("savings_rate", "$it%") }
        put("stage", stage(n))
    }

    /** Signals that come from totals (the app adds the ones that come from recent transactions). */
    fun signals(n: MoneyNumbers): Set<Signal> = buildSet {
        if (n.feesMonth >= Money.ofShillings(300)) add(Signal.HIGH_FEES)
        if (n.debts > 0) add(Signal.HAS_DEBT)
        if (n.fixedMonth > 0 && n.savings < 3 * n.fixedMonth) add(Signal.NO_EMERGENCY_FUND)
        savingsRate(n)?.let { if (it < 10) add(Signal.SAVING_LOW) }
    }
}
