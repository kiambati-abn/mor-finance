package com.morphlabs.morph.data

import com.morphlabs.morph.core.academy.MoneyFacts
import com.morphlabs.morph.core.academy.MoneyNumbers
import com.morphlabs.morph.core.academy.Signal
import com.morphlabs.morph.core.budget.Level
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the Academy knows about your money today: numbers for YOU cards, and signals that pick cards. */
class AcademyData(val numbers: MoneyNumbers, val facts: Map<String, String>, val signals: Set<Signal>)

fun Snapshot.academyData(today: LocalDate): AcademyData {
    fun day(ts: Long) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val live = accounts.filter { !it.archived }
    val real = entries.filter { it.source != EntrySource.SYSTEM }
    fun typeOf(id: Long) = byId[id]?.type
    fun since(d: LocalDate) = real.filter { !day(it.timestamp).isBefore(d) }
    /** Money that went into accounts of [ids] (debits), over [list]. */
    fun into(list: List<com.morphlabs.morph.core.model.Entry>, test: (Long) -> Boolean) =
        list.sumOf { e -> e.lines.filter { it.amount > 0 && test(it.accountId) }.sumOf { it.amount } }

    val feeIds = live.filter { it.role in setOf(SystemRole.TRANSACTION_COSTS, SystemRole.WITHDRAWAL_CHARGES, SystemRole.FULIZA_CHARGES) }.map { it.id }.toSet()
    val fixedIds = live.filter { it.group == AccountGroup.FIXED }.map { it.id }.toSet()
    val fixedPlan = live.filter { it.group == AccountGroup.FIXED }.sumOf {
        when (it.period) { BudgetPeriod.MONTHLY -> it.budget; BudgetPeriod.DAILY -> it.budget * 30; BudgetPeriod.NONE -> 0L }
    }
    val last30 = since(today.minusDays(29))
    val isExpense = { id: Long -> typeOf(id) == AccountType.EXPENSE }
    val firstDay = real.minOfOrNull { it.timestamp }?.let(::day) ?: today
    val months = (ChronoUnit.DAYS.between(firstDay, today) + 1).coerceAtLeast(30) / 30.0
    val spendAll = into(real, isExpense)
    val savings = live.filter { it.group == AccountGroup.SAVINGS }.sumOf { balance(it) }
    val debts = live.filter { it.group == AccountGroup.DEBT }.sumOf { maxOf(balance(it), 0L) }
    val assets = live.filter { it.type == AccountType.ASSET }.sumOf { balance(it) }
    val incomeIn = last30.sumOf { e -> e.lines.filter { it.amount < 0 && byId[it.accountId]?.group == AccountGroup.INCOME }.sumOf { -it.amount } }

    val n = MoneyNumbers(
        feesMonth = into(since(today.withDayOfMonth(1)), { it in feeIds }),
        dayOfMonth = today.dayOfMonth,
        fixedMonth = maxOf(fixedPlan, into(last30, { it in fixedIds })),
        savings = savings,
        debts = debts,
        spendMonthAvg = (spendAll / months).toLong(),
        netWorth = assets - debts,
        inLastMonth = incomeIn,
        outLastMonth = into(last30, isExpense),
    )

    // Signals from what just happened.
    val recent = buildSet {
        addAll(MoneyFacts.signals(n))
        val fuliza = live.firstOrNull { it.role == SystemRole.FULIZA }?.id
        if (fuliza != null && since(today.minusDays(7)).any { e -> e.lines.any { it.accountId == fuliza } }) add(Signal.FULIZA_RECENT)
        if (since(today.minusDays(2)).any { e -> e.lines.any { it.amount < 0 && byId[it.accountId]?.group == AccountGroup.INCOME } }) add(Signal.MONEY_IN_RECENT)
        val spends = real.flatMap { e -> e.lines.filter { it.amount > 0 && isExpense(it.accountId) && it.accountId !in feeIds } }
        val avg = if (spends.isEmpty()) 0L else spends.sumOf { it.amount } / spends.size
        val big = maxOf(Money.ofShillings(2_000), avg * 3)
        if (since(today.minusDays(3)).any { e -> e.lines.any { it.amount >= big && isExpense(it.accountId) && it.accountId !in feeIds } }) add(Signal.BIG_SPEND)
        if (warnings(today).any { it.level == Level.OVER }) add(Signal.OVER_BUDGET)
    }
    return AcademyData(n, MoneyFacts.facts(n), recent)
}
