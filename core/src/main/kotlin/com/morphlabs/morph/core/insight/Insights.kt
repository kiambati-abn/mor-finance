package com.morphlabs.morph.core.insight

import com.morphlabs.morph.core.budget.Envelope
import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.money.Money
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

enum class Tone { GOOD, WATCH, INFO }

data class Insight(val id: String, val emoji: String, val title: String, val body: String, val tone: Tone)

/** Spending totals per account and period, shared by the home screen and the insights. */
class Spending(private val accounts: List<Account>, private val entries: List<Entry>, private val zone: ZoneId) {

    private val expenseIds = accounts.filter { it.type == AccountType.EXPENSE }.map { it.id }.toSet()

    fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    fun startOf(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Expense debits (net of refunds) per expense account in [from, to). */
    fun byAccount(from: Long, to: Long): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (e in entries) {
            if (e.timestamp < from || e.timestamp >= to || e.source == EntrySource.SYSTEM) continue
            for (l in e.lines) if (l.accountId in expenseIds) out[l.accountId] = (out[l.accountId] ?: 0L) + l.amount
        }
        return out.filterValues { it != 0L }
    }

    fun total(from: Long, to: Long): Long = byAccount(from, to).values.sum()

    fun totalOn(d: LocalDate): Long = total(startOf(d), startOf(d.plusDays(1)))

    /** Money received as income in [from, to). */
    fun income(from: Long, to: Long): Long {
        val incomeIds = accounts.filter { it.type == AccountType.INCOME }.map { it.id }.toSet()
        return entries.filter { it.timestamp in from until to && it.source != EntrySource.SYSTEM }
            .sumOf { e -> e.lines.filter { it.accountId in incomeIds }.sumOf { -it.amount } }
    }
}

/**
 * The feedback part of Morph: short, personal observations with one lesson each.
 * Every insight uses your own numbers, so you learn about money by learning about yourself.
 */
class Insights(private val accounts: List<Account>, private val entries: List<Entry>, private val zone: ZoneId) {

    private val byId = accounts.associateBy { it.id }
    private val spending = Spending(accounts, entries, zone)
    private fun name(id: Long) = byId[id]?.let { "${it.emoji} ${it.name}" } ?: "?"
    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    fun all(today: LocalDate, envelopes: List<Envelope> = emptyList()): List<Insight> = listOfNotNull(
        weekOverWeek(today),
        fees(today),
        repeatedSmall(today),
        runway(today),
        weekendHabit(today),
        budgetPace(today, envelopes),
        biggestThisWeek(today),
        split503020(today),
        debts(),
        emergencyFund(today),
    )

    fun weekOverWeek(today: LocalDate): Insight? {
        val end = spending.startOf(today.plusDays(1))
        val mid = spending.startOf(today.minusDays(6))
        val start = spending.startOf(today.minusDays(13))
        val thisWeek = spending.byAccount(mid, end)
        val lastWeek = spending.byAccount(start, mid)
        val a = thisWeek.values.sum()
        val b = lastWeek.values.sum()
        if (a == 0L || b == 0L) return null
        val change = (a - b).toDouble() / b
        val mover = (thisWeek.keys + lastWeek.keys).maxByOrNull { kotlin.math.abs((thisWeek[it] ?: 0) - (lastWeek[it] ?: 0)) }
        val moverText = mover?.let {
            val d = (thisWeek[it] ?: 0) - (lastWeek[it] ?: 0)
            " Biggest change: ${name(it)} (${if (d > 0) "+" else ""}${Money.format(d)})."
        } ?: ""
        return if (change > 0.1) Insight("wow", "📈", "Spending up ${pct(change)} this week",
            "${Money.format(a)} in the last 7 days vs ${Money.format(b)} the week before.$moverText", Tone.WATCH)
        else if (change < -0.1) Insight("wow", "📉", "Spending down ${pct(-change)} this week",
            "${Money.format(a)} in the last 7 days vs ${Money.format(b)} the week before.$moverText Keep it up.", Tone.GOOD)
        else Insight("wow", "⚖️", "A steady week", "You spent ${Money.format(a)}, about the same as last week.$moverText", Tone.INFO)
    }

    fun fees(today: LocalDate): Insight? {
        val feeIds = accounts.filter {
            it.role == SystemRole.TRANSACTION_COSTS || it.role == SystemRole.WITHDRAWAL_CHARGES || it.role == SystemRole.FULIZA_CHARGES
        }.map { it.id }.toSet()
        val start = spending.startOf(YearMonth.from(today).atDay(1))
        val fees = spending.byAccount(start, spending.startOf(today.plusDays(1))).filterKeys { it in feeIds }.values.sum()
        if (fees < 100 * 100) return null
        return Insight("fees", "🧾", "${Money.format(fees)} in M-Pesa fees this month",
            "Paying a till (Buy Goods) is free for you; sending to people, paybills and withdrawals cost. " +
                "Fewer, bigger withdrawals and paying tills directly cut this down.", Tone.WATCH)
    }

    fun repeatedSmall(today: LocalDate): Insight? {
        val since = spending.startOf(today.minusDays(6))
        val groups = entries.filter {
            it.timestamp >= since && it.kind?.outgoing == true && it.counterpartyKey != null && it.size() <= 300 * 100
        }.groupBy { it.counterpartyKey }
        val (_, list) = groups.filter { it.value.size >= 5 }.maxByOrNull { (_, l) -> l.sumOf { it.size() } } ?: return null
        val total = list.sumOf { it.size() }
        val who = list.first().counterparty ?: "the same place"
        return Insight("small", "🔁", "${list.size} small payments to $who this week",
            "They add up to ${Money.format(total)}. Small, frequent payments are the easiest leak to miss. " +
                "At this rate that's about ${Money.format(total * 30 / 7)} a month.", Tone.WATCH)
    }

    fun runway(today: LocalDate): Insight? {
        val raw = Balances.raw(entries)
        val cash = accounts.filter { it.group == AccountGroup.WALLET }.sumOf { Balances.natural(it, raw) }
        val from = spending.startOf(today.minusDays(13))
        val daily = spending.total(from, spending.startOf(today.plusDays(1))) / 14
        if (daily <= 0 || cash <= 0) return null
        val days = (cash / daily).toInt()
        val tone = if (days < 7) Tone.WATCH else Tone.GOOD
        return Insight("runway", "⏳", "Your wallets last about $days days",
            "You have ${Money.format(cash)} across your wallets and spend about ${Money.format(daily)} a day on average.", tone)
    }

    fun weekendHabit(today: LocalDate): Insight? {
        var weekend = 0L; var weekendDays = 0
        var weekday = 0L; var weekdayDays = 0
        for (i in 1..28) {
            val d = today.minusDays(i.toLong())
            val s = spending.totalOn(d)
            if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) { weekend += s; weekendDays++ } else { weekday += s; weekdayDays++ }
        }
        if (weekend == 0L || weekday == 0L) return null
        val ratio = (weekend.toDouble() / weekendDays) / (weekday.toDouble() / weekdayDays)
        return when {
            ratio > 1.3 -> Insight("weekend", "🎉", "Weekends cost you ${pct(ratio - 1)} more per day",
                "Plan a weekend envelope on Friday so the fun is budgeted, not accidental.", Tone.WATCH)
            ratio < 0.77 -> Insight("weekend", "🗓️", "Weekdays are your expensive days",
                "You spend ${pct(1 / ratio - 1)} more per day on weekdays. Workday lunches and transport are usually why.", Tone.INFO)
            else -> null
        }
    }

    fun budgetPace(today: LocalDate, envelopes: List<Envelope>): Insight? {
        val month = YearMonth.from(today)
        val elapsed = today.dayOfMonth.toDouble() / month.lengthOfMonth()
        val monthly = envelopes.filter { it.isLimit && it.period == com.morphlabs.morph.core.model.BudgetPeriod.MONTHLY && it.available > 0 }
        val ahead = monthly.filter { it.progress > elapsed + 0.15 && !it.over }.maxByOrNull { it.progress - elapsed }
        val over = monthly.filter { it.over }.maxByOrNull { it.used - it.available }
        val wins = monthly.filter { it.progress < elapsed - 0.1 }
        return when {
            over != null -> Insight("pace", "🚨", "${over.account.name} is over budget",
                "Over by ${Money.format(over.used - over.available)}. Move budget from another envelope to cover it, so the plan stays honest.", Tone.WATCH)
            ahead != null -> Insight("pace", "🏃", "${ahead.account.name} is running ahead",
                "${pct(ahead.progress.toDouble())} used with ${pct(elapsed)} of the month gone. " +
                    "${Money.format(ahead.left)} left for ${month.lengthOfMonth() - today.dayOfMonth} days.", Tone.WATCH)
            wins.isNotEmpty() -> Insight("pace", "🌟", "${wins.size} envelope${if (wins.size > 1) "s" else ""} on track",
                wins.take(3).joinToString(", ") { it.account.name } + " are below pace for this point in the month.", Tone.GOOD)
            else -> null
        }
    }

    fun biggestThisWeek(today: LocalDate): Insight? {
        val since = spending.startOf(today.minusDays(6))
        val expenseIds = accounts.filter { it.type == AccountType.EXPENSE }.map { it.id }.toSet()
        val e = entries.filter { it.timestamp >= since && it.source != EntrySource.SYSTEM && it.lines.any { l -> l.accountId in expenseIds && l.amount > 0 } }
            .maxByOrNull { it.size() } ?: return null
        if (e.size() < 500 * 100) return null
        return Insight("big", "🔎", "Biggest spend this week: ${Money.format(e.size())}",
            "${e.description}. Big one-offs shape a month more than daily coffee; worth a second look before the next one.", Tone.INFO)
    }

    /** The 50/30/20 rule, measured on your own month: needs / wants / savings and debt payments. */
    fun split503020(today: LocalDate): Insight? {
        val start = spending.startOf(YearMonth.from(today).atDay(1))
        val end = spending.startOf(today.plusDays(1))
        val by = spending.byAccount(start, end)
        val needs = by.filterKeys { byId[it]?.group == AccountGroup.FIXED }.values.sum()
        val wants = by.filterKeys { byId[it]?.group == AccountGroup.DISCRETIONARY }.values.sum()
        val saveIds = accounts.filter { it.group == AccountGroup.SAVINGS || it.group == AccountGroup.DEBT }.map { it.id }.toSet()
        val saved = entries.filter { it.timestamp in start until end && it.source != EntrySource.SYSTEM && it.kind != TxKind.FULIZA_DRAW }
            .sumOf { e -> e.lines.filter { it.accountId in saveIds && it.amount > 0 }.sumOf { it.amount } }
        val total = needs + wants + saved
        if (total < 1000 * 100) return null
        fun p(x: Long) = (x * 100.0 / total).roundToInt()
        return Insight("503020", "🧮", "Your split: ${p(needs)} / ${p(wants)} / ${p(saved)}",
            "Needs / wants / saving & debt this month. A common guide is 50 / 30 / 20: " +
                "pay yourself first by moving savings out on payday, before spending starts.",
            if (p(saved) >= 20) Tone.GOOD else Tone.INFO)
    }

    fun debts(): Insight? {
        val raw = Balances.raw(entries)
        val debts = accounts.filter { it.type == AccountType.LIABILITY }.map { it to Balances.natural(it, raw) }.filter { it.second > 0 }
        if (debts.isEmpty()) return null
        val total = debts.sumOf { it.second }
        val biggest = debts.maxBy { it.second }
        return Insight("debts", "⛓️", "You owe ${Money.format(total)} in total",
            "Biggest: ${biggest.first.name} (${Money.format(biggest.second)}). Clear the most expensive debt first " +
                "(Fuliza charges daily), and give every debt a monthly repayment target in its envelope.", Tone.INFO)
    }

    fun emergencyFund(today: LocalDate): Insight? {
        val start = spending.startOf(today.minusDays(29))
        val fixed = spending.byAccount(start, spending.startOf(today.plusDays(1)))
            .filterKeys { byId[it]?.group == AccountGroup.FIXED }.values.sum()
        if (fixed < 2000 * 100) return null
        val raw = Balances.raw(entries)
        val savings = accounts.filter { it.group == AccountGroup.SAVINGS }.sumOf { Balances.natural(it, raw) }
        val months = savings.toDouble() / fixed
        return Insight("emergency", "🛟", "Emergency fund: ${"%.1f".format(months)} months",
            "Your fixed costs are about ${Money.format(fixed)} a month and your savings are ${Money.format(savings)}. " +
                "A safety net of 3 months (${Money.format(fixed * 3)}) means one bad month can't knock you over.",
            if (months >= 3) Tone.GOOD else Tone.INFO)
    }
}
