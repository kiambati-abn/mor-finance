package com.morphlabs.morph.core.budget

import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.ledger.Balances
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Real money given a job: positive puts money in an envelope, negative takes it back out. */
data class Allocation(val id: Long = 0, val timestamp: Long, val accountId: Long, val amount: Long)

/**
 * Envelope budgeting: every shilling in your wallets (M-Pesa, cash, bank…) is either in an
 * envelope or waiting in "To assign".
 *
 *  - Money in → To assign goes up.
 *  - Filling an envelope moves money from To assign into it.
 *  - Spending on a category takes it out of that category's envelope (To assign doesn't change:
 *    the wallet and the envelope both drop). Spending on something without an envelope comes
 *    out of To assign.
 *  - Whatever is left in an envelope carries over to the next month.
 */
class EnvelopeBook(
    private val accounts: List<Account>,
    private val entries: List<Entry>,
    private val allocations: List<Allocation>,
    /** When envelopes were switched on; spending before this doesn't touch them. */
    private val start: Long,
    private val zone: ZoneId,
) {
    data class Envelope(
        val account: Account,
        val assigned: Long,
        val used: Long,
        /** What Fill tops it up to right now (from the account's daily or monthly amount). */
        val target: Long,
        /** For daily envelopes: what's still allowed today. */
        val todayLeft: Long?,
    ) {
        val left: Long get() = assigned - used
        val overspent: Boolean get() = left < 0
        val progressLeft: Float get() = if (target <= 0) (if (left > 0) 1f else 0f) else (left.toFloat() / target).coerceIn(0f, 1f)
    }

    private val wallets = accounts.filter { it.group == AccountGroup.WALLET && !it.archived }
    private val raw = Balances.raw(entries)

    /** Total real money you can spend right now. */
    val walletTotal: Long = wallets.sumOf { Balances.natural(it, raw) }

    /** Accounts that get an envelope: anything with a daily/monthly amount, or money already in it. */
    private fun envelopeAccounts(): List<Account> {
        val withMoney = allocations.map { it.accountId }.toSet()
        return accounts.filter { a ->
            !a.archived && a.pickable && a.group != AccountGroup.WALLET && a.type != AccountType.INCOME &&
                ((a.period != BudgetPeriod.NONE && a.budget > 0) || a.id in withMoney)
        }
    }

    /** How much of an envelope's purpose has happened since [start]: spent, saved into it, or repaid. */
    private fun used(a: Account): Long {
        var net = 0L
        var plus = 0L
        for (e in entries) {
            if (e.timestamp < start || e.source == EntrySource.SYSTEM) continue
            for (l in e.lines) if (l.accountId == a.id) { net += l.amount; if (l.amount > 0) plus += l.amount }
        }
        return when (a.type) {
            AccountType.EXPENSE -> net    // refunds give money back to the envelope
            AccountType.ASSET -> plus     // money moved into savings / lent out
            AccountType.LIABILITY -> plus // repayments
            else -> 0
        }
    }

    fun target(a: Account, today: LocalDate): Long = when (a.period) {
        BudgetPeriod.MONTHLY -> a.budget
        // A daily envelope holds enough for the rest of this month.
        BudgetPeriod.DAILY -> a.budget * (YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1)
        BudgetPeriod.NONE -> 0
    }

    fun envelopes(today: LocalDate): List<Envelope> {
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        return envelopeAccounts().map { a ->
            val assigned = allocations.filter { it.accountId == a.id }.sumOf { it.amount }
            val used = used(a)
            val todayLeft = if (a.period == BudgetPeriod.DAILY) {
                val spentToday = entries.filter { it.timestamp >= maxOf(dayStart, start) && it.source != EntrySource.SYSTEM }
                    .sumOf { e -> e.lines.filter { it.accountId == a.id }.sumOf { it.amount } }
                minOf(a.budget - spentToday, assigned - used)
            } else null
            Envelope(a, assigned, used, target(a, today), todayLeft)
        }.sortedWith(compareBy({ it.account.group.ordinal }, { it.account.sortOrder }))
    }

    /** Money in your wallets that has no job yet. Overspent envelopes don't count against it until you cover them. */
    fun toAssign(today: LocalDate): Long = walletTotal - envelopes(today).sumOf { maxOf(0L, it.left) }

    /**
     * What "Fill my envelopes" would do: top each envelope up to its target, in your account
     * order (fixed costs first), until To assign runs out. Never assigns money you don't have.
     */
    fun fillPlan(today: LocalDate): List<Pair<Long, Long>> {
        var free = toAssign(today)
        val plan = mutableListOf<Pair<Long, Long>>()
        for (e in envelopes(today).sortedBy { if (it.account.group == AccountGroup.FIXED) 0 else 1 }) {
            if (free <= 0) break
            val need = e.target - e.left
            if (need <= 0) continue
            val give = minOf(need, free)
            plan += e.account.id to give
            free -= give
        }
        return plan
    }
}
