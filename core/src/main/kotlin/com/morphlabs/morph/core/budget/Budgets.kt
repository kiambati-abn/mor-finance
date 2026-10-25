package com.morphlabs.morph.core.budget

import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Moving budget between envelopes. Real money doesn't move; only the plan for [periodKey] changes. */
data class BudgetMove(
    val id: Long = 0,
    /** "2026-10" for a monthly envelope, "2026-10-24" for a daily one. */
    val periodKey: String,
    val fromId: Long,
    val toId: Long,
    val amount: Long,
    val timestamp: Long,
)

data class Envelope(
    val account: Account,
    val period: BudgetPeriod,
    val planned: Long,
    val moved: Long,
    /** Spent (expenses), saved (savings), repaid (debts) or received (income) in the period. */
    val used: Long,
    /** A per-day cap on a monthly budget (0 = none), and what's been used of it today. */
    val dayLimit: Long = 0,
    val usedToday: Long = 0,
) {
    /** What today's cap still allows, for monthly budgets that have one. */
    val todayLeft: Long? get() = if (dayLimit > 0 && period == BudgetPeriod.MONTHLY) dayLimit - usedToday else null
    val available: Long get() = planned + moved
    val left: Long get() = available - used
    val progress: Float get() = if (available <= 0) (if (used > 0) 1f else 0f) else used.toFloat() / available
    /** For expenses going over is bad; for savings, repayments and income reaching it is good. */
    val isLimit: Boolean get() = account.type == AccountType.EXPENSE
    val over: Boolean get() = isLimit && used > available
    val reached: Boolean get() = !isLimit && available > 0 && used >= available
}

class Budgets(private val zone: ZoneId) {

    fun monthKey(date: LocalDate): String = YearMonth.from(date).toString()
    fun dayKey(date: LocalDate): String = date.toString()

    /** Envelopes for every account with a budget, for the day/month containing [date]. */
    fun envelopes(accounts: List<Account>, entries: List<Entry>, moves: List<BudgetMove>, date: LocalDate): List<Envelope> {
        val month = YearMonth.from(date)
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return accounts.filter { !it.archived && it.period != BudgetPeriod.NONE && it.budget > 0 }.map { a ->
            val (start, end, key) = when (a.period) {
                BudgetPeriod.DAILY -> Triple(dayStart, dayEnd, dayKey(date))
                else -> Triple(monthStart, monthEnd, monthKey(date))
            }
            val moved = moves.filter { it.periodKey == key }.sumOf { m ->
                (if (m.toId == a.id) m.amount else 0L) - (if (m.fromId == a.id) m.amount else 0L)
            }
            val cap = if (a.period == BudgetPeriod.MONTHLY) a.dailyCap else 0L
            Envelope(a, a.period, a.budget, moved, used(a, entries, start, end), cap, if (cap > 0) used(a, entries, dayStart, dayEnd) else 0L)
        }
    }

    /**
     * How much you can spend today and stay on plan: what's left in each daily envelope today,
     * plus what's left in each monthly spending envelope spread over the remaining days.
     */
    fun safeToSpendToday(envelopes: List<Envelope>, date: LocalDate, spentToday: Map<Long, Long>): Long {
        val daysLeft = (YearMonth.from(date).lengthOfMonth() - date.dayOfMonth + 1).toLong()
        return envelopes.filter { it.isLimit }.sumOf { e ->
            when (e.period) {
                BudgetPeriod.DAILY -> maxOf(0L, e.left)
                else -> {
                    // Spread what was left at the start of today, then subtract what's already gone today.
                    val today = spentToday[e.account.id] ?: 0L
                    val leftAtDawn = e.left + today
                    val spread = maxOf(0L, leftAtDawn / daysLeft - today)
                    e.todayLeft?.let { minOf(spread, maxOf(0L, it)) } ?: spread
                }
            }
        }
    }

    companion object {
        /** How much moved through an account in [start, end), the way its envelope cares about. */
        fun used(a: Account, entries: List<Entry>, start: Long, end: Long): Long {
            var net = 0L
            var repaid = 0L
            for (e in entries) {
                if (e.timestamp < start || e.timestamp >= end) continue
                if (e.source == EntrySource.SYSTEM) continue // opening balances aren't activity
                for (l in e.lines) if (l.accountId == a.id) {
                    net += l.amount
                    if (l.amount > 0) repaid += l.amount
                }
            }
            return when (a.type) {
                AccountType.EXPENSE, AccountType.ASSET -> net   // spent / saved (net of refunds and withdrawals)
                AccountType.LIABILITY -> repaid                 // repaid; new borrowing doesn't count as progress
                AccountType.INCOME, AccountType.EQUITY -> -net  // received
            }
        }
    }
}
