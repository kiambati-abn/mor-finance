package com.morphlabs.morph.core

import com.morphlabs.morph.core.budget.Budgets
import com.morphlabs.morph.core.budget.EnvelopeBook
import com.morphlabs.morph.core.budget.Health
import com.morphlabs.morph.core.budget.Level
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.budget.Allocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayAndMonthBudgetTest {
    private val ksh = { s: Long -> s * 100 }
    private val today = LocalDate.of(2026, 10, 25)
    private val foodId = TestBook.hint("food")
    // Food: Ksh 12,000 a month, and at most Ksh 500 a day.
    private val accounts = TestBook.accounts.map { if (it.id == foodId) it.withBudget(ksh(12_000), ksh(500)) else it }
    private val food = accounts.first { it.id == foodId }
    private val mpesa = TestBook.role(SystemRole.MPESA)

    private fun spend(day: Int, amount: Long, id: Long) =
        Posting.manual(TestBook.ts(LocalDateTime.of(2026, 10, day, 13, 0)), "lunch", ksh(amount), mpesa, foodId).copy(id = id)

    @Test fun withBudgetPicksTheRightShape() {
        assertEquals(BudgetPeriod.MONTHLY, food.period)
        assertEquals(ksh(12_000), food.perMonth); assertEquals(ksh(500), food.perDay)
        val dailyOnly = food.withBudget(0, ksh(300))
        assertEquals(BudgetPeriod.DAILY, dailyOnly.period); assertEquals(ksh(300), dailyOnly.perDay); assertEquals(0L, dailyOnly.perMonth)
        assertEquals(BudgetPeriod.NONE, food.withBudget(0, 0).period)
    }

    @Test fun bothLimitsAreTracked() {
        val entries = listOf(spend(3, 9_000, 1), spend(25, 400, 2))
        val e = Budgets(TestBook.zone).envelopes(accounts, entries, emptyList(), today).first { it.account.id == foodId }
        assertEquals(ksh(2_600), e.left)          // month: 12,000 − 9,400
        assertEquals(ksh(100), e.todayLeft)       // today: 500 − 400
        assertEquals(Level.LOW, Health.level(e.todayLeft!!, ksh(500))) // 20% left today
        // Safe to spend today is the tighter of the month spread over the days left and today's cap.
        // Month: 3,000 at dawn over 7 days ≈ 428.57 − 400 spent = 28.57, tighter than the cap's 100.
        assertEquals(2_857L, Budgets(TestBook.zone).safeToSpendToday(listOf(e), today, mapOf(foodId to ksh(400))))
        // Early in the month the cap is the tighter one.
        val early = Budgets(TestBook.zone).envelopes(accounts, listOf(spend(10, 400, 3)), emptyList(), LocalDate.of(2026, 10, 10)).first { it.account.id == foodId }
        assertEquals(ksh(100), Budgets(TestBook.zone).safeToSpendToday(listOf(early), LocalDate.of(2026, 10, 10), mapOf(foodId to ksh(400))))
        // A monthly budget without a cap has no "today" part.
        val plain = Budgets(TestBook.zone).envelopes(accounts.map { if (it.id == foodId) it.withBudget(ksh(12_000), 0) else it }, entries, emptyList(), today)
            .first { it.account.id == foodId }
        assertNull(plain.todayLeft)
    }

    @Test fun envelopesRespectTheDailyCapToo() {
        val start = TestBook.ts(LocalDateTime.of(2026, 10, 1, 0, 0))
        val book = EnvelopeBook(accounts, listOf(spend(25, 450, 1)), listOf(Allocation(1, start, foodId, ksh(12_000))), start, TestBook.zone)
        val e = book.envelopes(today).first { it.account.id == foodId }
        assertEquals(ksh(11_550), e.left)
        assertEquals(ksh(50), e.todayLeft)
        assertEquals(ksh(12_000), e.target)
    }
}
