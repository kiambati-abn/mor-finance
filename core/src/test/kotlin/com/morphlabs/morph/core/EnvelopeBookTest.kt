package com.morphlabs.morph.core

import com.morphlabs.morph.core.TestBook.hint
import com.morphlabs.morph.core.TestBook.role
import com.morphlabs.morph.core.TestBook.ts
import com.morphlabs.morph.core.budget.Allocation
import com.morphlabs.morph.core.budget.EnvelopeBook
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.SystemRole
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EnvelopeBookTest {

    private val today = LocalDate.of(2026, 10, 24) // 8 days left in October, including today
    private val start = ts(LocalDateTime.of(2026, 10, 1, 0, 0))
    private val bank = hint("bank")
    private val accounts = TestBook.accounts.map {
        when (it.hint) {
            "food" -> it.copy(budget = 300_00, period = BudgetPeriod.DAILY)
            "friends" -> it.copy(budget = 2_000_00, period = BudgetPeriod.MONTHLY)
            "rent" -> it.copy(budget = 5_000_00, period = BudgetPeriod.MONTHLY)
            "chama" -> it.copy(budget = 1_000_00, period = BudgetPeriod.MONTHLY)
            else -> it
        }
    }
    private fun at(d: LocalDate, h: Int) = ts(LocalDateTime.of(d, LocalTime.of(h, 0)))
    private fun spend(d: LocalDate, amount: Long, on: Long) = Posting.manual(at(d, 12), "x", amount, bank, on)
    private val opening = Posting.opening(start - 1, bank, true, 10_000_00, ::role)

    private fun book(entries: List<Entry>, allocs: List<Allocation>) = EnvelopeBook(accounts, entries, allocs, start, TestBook.zone)

    @Test fun theFriendsExample() {
        // 10,000 in the bank; 2,000 given to Friends; 1,900 already spent on friends.
        val allocs = listOf(Allocation(timestamp = start, accountId = hint("friends"), amount = 2_000_00))
        val entries = listOf(opening, spend(today.minusDays(3), 1_000_00, hint("friends")), spend(today, 900_00, hint("friends")))
        val b = book(entries, allocs)
        val friends = b.envelopes(today).first { it.account.hint == "friends" }
        assertEquals(100_00L, friends.left)          // "you have Ksh 100 for friends: say no"
        assertEquals(8_100_00L, b.walletTotal)        // the real bank balance, hidden in budget view
        assertEquals(8_000_00L, b.toAssign(today))    // spending from the envelope didn't touch To assign
    }

    @Test fun fillTopsUpFixedFirstAndNeverOverAssigns() {
        val small = Posting.opening(start - 1, bank, true, 6_000_00, ::role)
        val b = book(listOf(small), emptyList())
        val plan = b.fillPlan(today).toMap()
        assertEquals(5_000_00L, plan[hint("rent")])          // fixed first
        assertEquals(1_000_00L, plan.values.sum() - 5_000_00L) // only 1,000 left for the rest
        assertEquals(6_000_00L, plan.values.sum())
    }

    @Test fun dailyEnvelopeHoldsTheRestOfTheMonthAndTracksToday() {
        val allocs = listOf(Allocation(timestamp = start, accountId = hint("food"), amount = 2_400_00)) // 8 days × 300
        val entries = listOf(opening, spend(today, 120_00, hint("food")))
        val food = book(entries, allocs).envelopes(today).first { it.account.hint == "food" }
        assertEquals(2_400_00L, food.target)
        assertEquals(180_00L, food.todayLeft)
        assertEquals(2_280_00L, food.left)
    }

    @Test fun overspendingAndCarryOver() {
        val allocs = listOf(Allocation(timestamp = start, accountId = hint("friends"), amount = 500_00))
        val sept = Posting.manual(ts(LocalDateTime.of(2026, 9, 20, 12, 0)), "before envelopes", 3_000_00, bank, hint("friends"))
        val entries = listOf(opening, sept, spend(today, 700_00, hint("friends")))
        val friends = book(entries, allocs).envelopes(today).first { it.account.hint == "friends" }
        assertEquals(-200_00L, friends.left) // spending before envelopes were on doesn't count
        assert(friends.overspent)
        // Money moved into the Chama savings uses up the Chama envelope.
        val chamaAlloc = allocs + Allocation(timestamp = start, accountId = hint("chama"), amount = 1_000_00)
        val toChama = Posting.manual(at(today, 9), "chama", 1_000_00, bank, hint("chama"))
        val chama = book(entries + toChama, chamaAlloc).envelopes(today).first { it.account.hint == "chama" }
        assertEquals(0L, chama.left)
    }
}
