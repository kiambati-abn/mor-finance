package com.morphlabs.morph.ui

import com.morphlabs.morph.core.budget.Budgets
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.insight.Insights
import com.morphlabs.morph.core.insight.Spending
import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.data.Snapshot
import java.time.LocalDate

/** Everything the screens show, worked out once per change. */
class Dashboard(val s: Snapshot, val today: LocalDate) {
    private val spending = Spending(s.accounts, s.entries, s.zone)
    private fun start(d: LocalDate) = spending.startOf(d)

    val spentToday = spending.total(start(today), start(today.plusDays(1)))
    val spentYesterday = spending.total(start(today.minusDays(1)), start(today))
    val spent7 = spending.total(start(today.minusDays(6)), start(today.plusDays(1)))
    val spent30 = spending.total(start(today.minusDays(29)), start(today.plusDays(1)))
    /** Average spending, measured over the last 90 days (or since your first transaction, if sooner). */
    private val avgWindow: Long = run {
        val first = s.entries.filter { it.source != com.morphlabs.morph.core.model.EntrySource.SYSTEM }.minOfOrNull { it.timestamp }
        val days = first?.let { java.time.temporal.ChronoUnit.DAYS.between(s.zone.let { z -> java.time.Instant.ofEpochMilli(it).atZone(z).toLocalDate() }, today) + 1 } ?: 1
        days.coerceIn(1, 90)
    }
    val avgDay: Long = spending.total(start(today.minusDays(avgWindow - 1)), start(today.plusDays(1))) / avgWindow
    val avgWeek: Long = avgDay * 7
    val avgMonth: Long = (avgDay * 30.44).toLong()
    val avgBasis: Long get() = avgWindow

    val receivedThisMonth = spending.income(start(today.withDayOfMonth(1)), start(today.plusDays(1)))

    private val budgets = Budgets(s.zone)
    val envelopes = budgets.envelopes(s.accounts, s.entries, s.moves, today)
    val monthKey = budgets.monthKey(today)
    val dayKey = budgets.dayKey(today)

    /** Null until you've set at least one spending limit. */
    val safeToSpend: Long? = envelopes.filter { it.isLimit }.takeIf { it.isNotEmpty() }?.let {
        budgets.safeToSpendToday(envelopes, today, spending.byAccount(start(today), start(today.plusDays(1))))
    }

    fun topSpending(days: Long, n: Int = 5): List<Pair<Account, Long>> =
        spending.byAccount(start(today.minusDays(days - 1)), start(today.plusDays(1)))
            .mapNotNull { (id, v) -> s.byId[id]?.let { it to v } }
            .sortedByDescending { it.second }.take(n)

    fun monthByAccount(): Map<Long, Long> = spending.byAccount(start(today.withDayOfMonth(1)), start(today.plusDays(1)))

    val insights = Insights(s.accounts, s.entries, s.zone).all(today, envelopes)
    val streak = Game.streak(s.entries, today, s.zone)
    val stars = s.stars
    val level = Game.level(stars)
    val nextLevel = Game.nextLevel(stars)
    val levelProgress = Game.levelProgress(stars)

    val mpesaBalance = s.balance(s.account(SystemRole.MPESA))

    // Envelopes (when on).
    val envList = s.book?.envelopes(today)
    val toAssign: Long? = s.book?.toAssign(today)
    val envelopeLeft: Long? = envList?.sumOf { maxOf(0L, it.left) }
    /** What envelopes allow today: daily envelopes' today, plus monthly spending envelopes spread over the days left. */
    val envelopeToday: Long? = envList?.let { list ->
        val daysLeft = (java.time.YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1).toLong()
        list.filter { it.account.type == com.morphlabs.morph.core.model.AccountType.EXPENSE }.sumOf { e ->
            if (e.todayLeft != null) maxOf(0L, minOf(e.todayLeft!!, e.left)) else maxOf(0L, e.left) / daysLeft
        }
    }
    val netWorth = Balances.netWorth(s.accounts.filter { !it.archived }, s.raw)
}
