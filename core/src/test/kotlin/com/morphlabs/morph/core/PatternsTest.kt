package com.morphlabs.morph.core

import com.morphlabs.morph.core.TestBook.hint
import com.morphlabs.morph.core.TestBook.role
import com.morphlabs.morph.core.TestBook.ts
import com.morphlabs.morph.core.insight.Patterns
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.Line
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.model.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PatternsTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val mpesa = role(SystemRole.MPESA)

    private fun pay(d: LocalDate, hour: Int, amount: Long, account: Long, who: String, kind: TxKind = TxKind.BUY_GOODS): Entry =
        Posting.manual(ts(LocalDateTime.of(d, LocalTime.of(hour, 0))), "Paid $who", amount, mpesa, account)
            .copy(counterparty = who, counterpartyKey = "till:$who", kind = kind)

    private fun p(entries: List<Entry>) = Patterns(TestBook.accounts, entries, TestBook.zone)

    @Test fun findsDailyAndMonthlyRegularsButNotRandomOnes() {
        val entries = mutableListOf<Entry>()
        for (i in 0..20) entries += pay(today.minusDays(i.toLong()), 21, 23_00, hint("fun"), "DIRECT PAY", TxKind.PAYBILL)
        for (m in 0..3) entries += pay(today.minusMonths(m.toLong()).withDayOfMonth(1), 9, 8_000_00, hint("rent"), "LANDLORD", TxKind.PAYBILL)
        entries += pay(today.minusDays(3), 13, 200_00, hint("food"), "RANDOM CAFE")
        entries += pay(today.minusDays(40), 13, 2_500_00, hint("food"), "RANDOM CAFE")
        entries += pay(today.minusDays(41), 13, 90_00, hint("food"), "RANDOM CAFE")

        val regs = p(entries).regulars(today).associateBy { it.name }
        assertEquals(1, regs.getValue("DIRECT PAY").everyDays)
        assertEquals(690_00L, regs.getValue("DIRECT PAY").monthlyCost)
        assert(regs.getValue("LANDLORD").everyDays in 28..31)
        assertNull(regs["RANDOM CAFE"])
    }

    @Test fun trendsCompareWithAFairUsual() {
        val entries = mutableListOf<Entry>()
        // Usual: Ksh 3,100 of food per month in the 3 previous months (100 a day).
        for (m in 1..3) {
            val month = java.time.YearMonth.from(today).minusMonths(m.toLong())
            entries += pay(month.atDay(10), 13, 3_100_00, hint("food"), "CAFE")
        }
        // This month so far (24 of 31 days): 4,800 → usual for 24 days is 2,400 → +100%.
        entries += pay(today.minusDays(1), 13, 4_800_00, hint("food"), "CAFE")
        val t = p(entries).trends(today).first { it.account.id == hint("food") }
        assertEquals(4_800_00L, t.thisMonth)
        assertEquals(2_400_00L, t.usual)
        assertEquals(1.0, t.change!!, 0.001)
    }

    @Test fun weekdayTimeOfDayPlacesAndUnusual() {
        val entries = mutableListOf<Entry>()
        for (i in 0..27) entries += pay(today.minusDays(i.toLong()), 13, 100_00, hint("food"), "LAKEWOOD HOTEL")
        entries += pay(today, 19, 2_000_00, hint("food"), "FANCY PLACE")
        val pat = p(entries)
        val week = pat.byWeekday(today, 28)
        assertEquals(7, week.size)
        assert(week.all { it.total >= 100_00 })
        val tod = pat.byTimeOfDay(today, 28)
        assertEquals(28 * 100_00L, tod[1].total) // midday
        assertEquals(2_000_00L, tod[3].total)    // evening
        assertEquals("LAKEWOOD HOTEL", pat.topPlaces(today, 30).first().name)
        val odd = pat.unusual(today, 7)
        assertEquals(1, odd.size)
        assertEquals(20.0, odd.first().timesUsual, 0.01)
    }

    @Test fun moneyGoesFastAfterPayday() {
        val got = Entry(timestamp = ts(LocalDateTime.of(today.minusDays(5), LocalTime.of(9, 0))), description = "Salary",
            lines = listOf(Line(mpesa, 10_000_00), Line(hint("salary"), -10_000_00)), categoryId = hint("salary"))
        val entries = listOf(got,
            pay(today.minusDays(5), 12, 3_000_00, hint("fun"), "CLUB"),
            pay(today.minusDays(4), 12, 1_000_00, hint("food"), "CAFE"),
            pay(today.minusDays(1), 12, 500_00, hint("food"), "CAFE"))
        val a = p(entries).afterIncome(today, 30)!!
        assertEquals(4_000_00L, a.spentIn3Days)
        assertEquals(0.4, a.share, 0.001)
    }
}
