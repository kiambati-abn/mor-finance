package com.morphlabs.morph.core.insight

import com.morphlabs.morph.core.learn.Categorizer
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.SystemRole
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The deeper look: where money goes over months, when you spend, what repeats, what's unusual.
 * Each finding is plain numbers; the app adds one sentence of meaning.
 */
class Patterns(private val accounts: List<Account>, private val entries: List<Entry>, private val zone: ZoneId) {

    private val byId = accounts.associateBy { it.id }
    private val expenseIds = accounts.filter { it.type == AccountType.EXPENSE }.map { it.id }.toSet()
    private val feeIds = accounts.filter {
        it.role == SystemRole.TRANSACTION_COSTS || it.role == SystemRole.WITHDRAWAL_CHARGES || it.role == SystemRole.FULIZA_CHARGES
    }.map { it.id }.toSet()
    private val incomeIds = accounts.filter { it.type == AccountType.INCOME }.map { it.id }.toSet()

    private fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    private fun hour(ts: Long): Int = Instant.ofEpochMilli(ts).atZone(zone).hour
    private fun start(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private val real = entries.filter { it.source != EntrySource.SYSTEM }

    /** Expense amount of one entry, optionally only for one account. Fees count as spending but not as "places". */
    private fun spend(e: Entry, accountId: Long? = null, withFees: Boolean = true): Long =
        e.lines.filter { it.accountId in expenseIds && (accountId == null || it.accountId == accountId) && (withFees || it.accountId !in feeIds) }
            .sumOf { it.amount }

    data class Period(val label: String, val total: Long)

    /** Total spending per calendar month, oldest first, ending with the current month. */
    fun monthly(today: LocalDate, months: Int = 6, accountId: Long? = null): List<Period> {
        val now = YearMonth.from(today)
        return (months - 1 downTo 0).map { back ->
            val m = now.minusMonths(back.toLong())
            val from = start(m.atDay(1)); val to = start(m.plusMonths(1).atDay(1))
            Period(m.month.name.take(3).lowercase().replaceFirstChar(Char::uppercaseChar), real.filter { it.timestamp in from until to }.sumOf { spend(it, accountId) })
        }
    }

    data class Trend(val account: Account, val thisMonth: Long, val usual: Long) {
        /** +0.32 = 32% more than usual. Null when there's no "usual" yet. */
        val change: Double? get() = if (usual <= 0) null else (thisMonth - usual).toDouble() / usual
    }

    /**
     * This month so far vs your usual (average of the previous 3 months), scaled to the same
     * number of days so the comparison is fair mid-month.
     */
    fun trends(today: LocalDate): List<Trend> {
        val m = YearMonth.from(today)
        val from = start(m.atDay(1)); val to = start(today.plusDays(1))
        val share = today.dayOfMonth.toDouble() / m.lengthOfMonth()
        val prevFrom = start(m.minusMonths(3).atDay(1))
        val prevEntries = real.filter { it.timestamp in prevFrom until from }
        val monthsWithData = (1..3).count { back ->
            val pm = m.minusMonths(back.toLong())
            prevEntries.any { YearMonth.from(day(it.timestamp)) == pm }
        }.coerceAtLeast(1)
        return expenseIds.mapNotNull { id ->
            val a = byId[id] ?: return@mapNotNull null
            val now = real.filter { it.timestamp in from until to }.sumOf { spend(it, id) }
            val prev = prevEntries.sumOf { spend(it, id) }
            val usual = (prev.toDouble() / monthsWithData * share).toLong()
            if (now == 0L && usual == 0L) null else Trend(a, now, usual)
        }.sortedByDescending { it.thisMonth }
    }

    /** Average spending per weekday (Mon..Sun) over the last [days] days. */
    fun byWeekday(today: LocalDate, days: Int): List<Period> {
        val totals = LongArray(7); val counts = IntArray(7)
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            counts[d.dayOfWeek.value - 1]++
        }
        val from = start(today.minusDays(days - 1L))
        for (e in real) if (e.timestamp >= from) totals[day(e.timestamp).dayOfWeek.value - 1] += spend(e)
        return DayOfWeek.entries.map { dw ->
            Period(dw.name.take(3).lowercase().replaceFirstChar(Char::uppercaseChar), if (counts[dw.value - 1] == 0) 0 else totals[dw.value - 1] / counts[dw.value - 1])
        }
    }

    /** Total spending per time of day over the last [days] days. */
    fun byTimeOfDay(today: LocalDate, days: Int): List<Period> {
        val totals = LongArray(5)
        val from = start(today.minusDays(days - 1L))
        for (e in real) if (e.timestamp >= from) totals[Categorizer.bucket(hour(e.timestamp))] += spend(e, withFees = false)
        return listOf("Morning", "Midday", "Afternoon", "Evening", "Night").mapIndexed { i, l -> Period(l, totals[i]) }
    }

    data class Place(val key: String, val name: String, val count: Int, val total: Long, val accountId: Long?)

    /** Where (and to whom) your money goes most, by total. */
    fun topPlaces(today: LocalDate, days: Int, n: Int = 8, accountId: Long? = null): List<Place> {
        val from = start(today.minusDays(days - 1L))
        return real.filter { it.timestamp >= from && it.counterpartyKey != null && !it.counterpartyKey!!.startsWith("manual:") }
            .filter { accountId == null || it.categoryId == accountId }
            .mapNotNull { e -> spend(e, withFees = false).takeIf { it > 0 }?.let { e to it } }
            .groupBy { it.first.counterpartyKey!! }
            .map { (k, list) ->
                Place(k, list.last().first.counterparty ?: k.substringAfter(':'), list.size, list.sumOf { it.second },
                    list.groupingBy { it.first.categoryId }.eachCount().maxByOrNull { it.value }?.key)
            }
            .sortedByDescending { it.total }.take(n)
    }

    data class Regular(val name: String, val accountId: Long?, val count: Int, val average: Long, val everyDays: Int, val monthlyCost: Long, val next: LocalDate)

    /**
     * Payments that repeat on a rhythm: daily bets, weekly chama, monthly rent. Needs at least 3
     * payments, a steady gap between them and similar amounts.
     */
    fun regulars(today: LocalDate, lookbackDays: Int = 120): List<Regular> {
        val from = start(today.minusDays(lookbackDays.toLong()))
        return real.filter { it.timestamp >= from && it.counterpartyKey != null && it.kind?.outgoing == true }
            .mapNotNull { e -> spend(e, withFees = false).takeIf { it > 0 }?.let { e to it } }
            .groupBy { it.first.counterpartyKey!! }
            .mapNotNull { (_, list) ->
                if (list.size < 3) return@mapNotNull null
                val days = list.map { day(it.first.timestamp) }.distinct().sorted()
                if (days.size < 3) return@mapNotNull null
                val gaps = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toDouble() }
                val mean = gaps.average()
                if (mean < 1 || mean > 40) return@mapNotNull null
                val sd = sqrt(gaps.sumOf { (it - mean) * (it - mean) } / gaps.size)
                if (sd / mean > 0.5) return@mapNotNull null
                val amounts = list.map { it.second }
                if (amounts.max().toDouble() / amounts.min() > 2.0) return@mapNotNull null
                // Still active: the last one isn't long overdue.
                if (ChronoUnit.DAYS.between(days.last(), today) > mean * 2 + 2) return@mapNotNull null
                val avg = amounts.sum() / amounts.size
                val perDay = list.size.toDouble() / days.size // several a day counts
                Regular(
                    name = list.last().first.counterparty ?: "?", accountId = list.last().first.categoryId,
                    count = list.size, average = avg, everyDays = mean.toInt().coerceAtLeast(1),
                    monthlyCost = (avg * perDay * 30 / mean).toLong(),
                    next = days.last().plusDays(Math.round(mean)),
                )
            }
            .sortedByDescending { it.monthlyCost }
    }

    data class Unusual(val entry: Entry, val accountId: Long, val amount: Long, val timesUsual: Double)

    /** Spends far bigger than what's normal for their category (3× the median, with enough history to judge). */
    fun unusual(today: LocalDate, days: Int): List<Unusual> {
        val from = start(today.minusDays(days - 1L))
        val samples = HashMap<Long, MutableList<Long>>()
        for (e in real) {
            val id = e.categoryId ?: continue
            if (id !in expenseIds) continue
            val s = spend(e, id)
            if (s > 0) samples.getOrPut(id) { mutableListOf() } += s
        }
        val medians = samples.filterValues { it.size >= 5 }.mapValues { (_, v) -> v.sorted()[v.size / 2] }
        return real.filter { it.timestamp >= from }.mapNotNull { e ->
            val id = e.categoryId ?: return@mapNotNull null
            val med = medians[id] ?: return@mapNotNull null
            val s = spend(e, id)
            if (med <= 0 || s < 500 * 100 || s < med * 3) null else Unusual(e, id, s, s.toDouble() / med)
        }.sortedByDescending { it.amount }
    }

    data class AfterIncome(val incomes: Int, val received: Long, val spentIn3Days: Long) {
        val share: Double get() = if (received == 0L) 0.0 else spentIn3Days.toDouble() / received
    }

    /** How fast money goes after it comes in: spending in the 3 days after each big income (Ksh 1,000+). */
    fun afterIncome(today: LocalDate, days: Int): AfterIncome? {
        val from = start(today.minusDays(days - 1L))
        val big = real.filter { it.timestamp >= from }.mapNotNull { e ->
            val got = e.lines.filter { it.accountId in incomeIds }.sumOf { -it.amount }
            if (got >= 1000 * 100) e.timestamp to got else null
        }
        if (big.isEmpty()) return null
        val window = 3L * 24 * 3600 * 1000
        val spent = real.filter { e -> big.any { (t, _) -> e.timestamp >= t && e.timestamp < t + window } }.sumOf { spend(it) }
        return AfterIncome(big.size, big.sumOf { it.second }, spent)
    }

    companion object {
        fun pct(x: Double): String = "${(abs(x) * 100).toInt()}%"
    }
}
