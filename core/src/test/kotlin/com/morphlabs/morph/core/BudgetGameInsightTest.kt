package com.morphlabs.morph.core

import com.morphlabs.morph.core.TestBook.hint
import com.morphlabs.morph.core.TestBook.role
import com.morphlabs.morph.core.TestBook.ts
import com.morphlabs.morph.core.budget.BudgetMove
import com.morphlabs.morph.core.budget.Budgets
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.insight.Insights
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BudgetGameInsightTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val mpesa = role(SystemRole.MPESA)
    private fun spend(day: LocalDate, hour: Int, amount: Long, account: Long, what: String = "x", status: EntryStatus = EntryStatus.CONFIRMED) =
        Posting.manual(ts(LocalDateTime.of(day, java.time.LocalTime.of(hour, 0))), what, amount, mpesa, account).copy(status = status)

    private val accounts = TestBook.accounts.map {
        when (it.hint) {
            "food" -> it.copy(budget = 300_00, period = BudgetPeriod.DAILY)
            "transport" -> it.copy(budget = 3_100_00, period = BudgetPeriod.MONTHLY)
            "fun" -> it.copy(budget = 1_000_00, period = BudgetPeriod.MONTHLY)
            "chama" -> it.copy(budget = 500_00, period = BudgetPeriod.MONTHLY)
            else -> it
        }
    }

    @Test fun dailyAndMonthlyEnvelopesWithAMove() {
        val entries = listOf(
            spend(today, 13, 200_00, hint("food")),
            spend(today.minusDays(1), 13, 999_00, hint("food")), // yesterday doesn't count for a daily envelope
            spend(today, 8, 100_00, hint("transport")),
            spend(today.minusDays(3), 8, 1_000_00, hint("transport")),
            spend(today.minusDays(2), 9, 500_00, hint("chama")),
        )
        val moves = listOf(BudgetMove(periodKey = "2026-10", fromId = hint("fun"), toId = hint("transport"), amount = 200_00, timestamp = 0))
        val env = Budgets(TestBook.zone).envelopes(accounts, entries, moves, today).associateBy { it.account.hint }

        assertEquals(200_00L, env["food"]!!.used)
        assertEquals(100_00L, env["food"]!!.left)
        assertEquals(3_300_00L, env["transport"]!!.available)
        assertEquals(1_100_00L, env["transport"]!!.used)
        assertEquals(800_00L, env["fun"]!!.available)
        assert(env["chama"]!!.reached) // savings target met: good, not "over"

        // Food: 100 left today. Transport: (3300 - 1000 spent before today) / 8 days left = 287.50, minus 100 spent today.
        val safe = Budgets(TestBook.zone).safeToSpendToday(env.values.toList(), today, mapOf(hint("transport") to 100_00))
        assertEquals(100_00L + 187_50L + 800_00L / 8, safe)
    }

    @Test fun streakSkipsQuietDaysAndForgivesToday() {
        val entries = listOf(
            spend(today, 9, 10_00, hint("food"), status = EntryStatus.NEEDS_REVIEW), // today not done yet
            spend(today.minusDays(1), 9, 10_00, hint("food")),
            // two days ago: no transactions at all
            spend(today.minusDays(3), 9, 10_00, hint("food"), status = EntryStatus.AUTO),
            spend(today.minusDays(4), 9, 10_00, hint("food"), status = EntryStatus.NEEDS_REVIEW), // breaks here
            spend(today.minusDays(5), 9, 10_00, hint("food")),
        )
        assertEquals(2, Game.streak(entries, today, TestBook.zone))
    }

    @Test fun starsAndLevels() {
        val fresh = Game.rewardForSorting(txTime = 0, sortedAt = 60_000, inboxNowEmpty = true, alreadyGotInboxBonusToday = false)
        assertEquals(8, fresh.stars)
        assertEquals(1, Game.rewardForSorting(0, 3_600_000, inboxNowEmpty = true, alreadyGotInboxBonusToday = true).stars)
        val day = Game.rewardForSorting(0, 3_600_000, inboxNowEmpty = false, alreadyGotInboxBonusToday = false, dayNowComplete = true)
        assertEquals(4, day.stars)
        assert(Game.DAY_COMPLETE in day.reasons)
        assertEquals(1, Game.rewardForSorting(0, 3_600_000, false, false, dayNowComplete = true, alreadyGotDayBonus = true).stars)
        assertEquals("Egg", Game.level(0).name)
        assertEquals("Butterfly", Game.level(300).name)
        assertEquals(0.5f, Game.levelProgress(62), 0.01f)
    }

    @Test fun insightsSpotSmallRepeatedPaymentsAndFees() {
        val entries = mutableListOf<Entry>(Posting.opening(ts(LocalDateTime.of(2026, 9, 1, 0, 0)), mpesa, true, 20_000_00, ::role))
        for (i in 0..5) entries += spend(today.minusDays(i.toLong()), 21, 23_00, hint("fun"), "Paid Direct Pay")
            .copy(counterpartyKey = "paybill:DIRECT PAY", counterparty = "DIRECT PAY", kind = com.morphlabs.morph.core.model.TxKind.PAYBILL)
        for (i in 0..15) entries += spend(today.minusDays(i.toLong()), 9, 7_00, role(SystemRole.TRANSACTION_COSTS))
        for (i in 0..20) entries += spend(today.minusDays(i.toLong()), 13, 250_00, hint("food"))
        val insights = Insights(accounts, entries, TestBook.zone).all(today).associateBy { it.id }

        assert(insights.getValue("small").title.startsWith("6 small payments to DIRECT PAY")) { insights.getValue("small").title }
        assertEquals("${Money.format(16 * 7_00)} in M-Pesa fees this month", insights.getValue("fees").title)
        assert(insights.containsKey("runway"))
    }

    @Test fun trafficLight() {
        val l = com.morphlabs.morph.core.budget.Health.Companion
        assertEquals(com.morphlabs.morph.core.budget.Level.OK, l.level(1_000_00, 2_000_00))
        assertEquals(com.morphlabs.morph.core.budget.Level.LOW, l.level(500_00, 2_000_00))
        assertEquals(com.morphlabs.morph.core.budget.Level.CRITICAL, l.level(100_00, 2_000_00))
        assertEquals(com.morphlabs.morph.core.budget.Level.OVER, l.level(-1, 2_000_00))
    }
}
