package com.morphlabs.morph.core.academy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AcademyTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test fun reviewMovesBoxes() {
        var p = Progress("x")
        p = Academy.review(p, Outcome.KNEW, today)
        assertEquals(1, p.box); assertEquals(today.plusDays(1), p.due)
        p = Academy.review(p.copy(box = 4), Outcome.KNEW, today)
        assertEquals(5, p.box); assertEquals(today.plusDays(60), p.due)
        p = Academy.review(p, Outcome.UNSURE, today)
        assertEquals(2, p.box)
        p = Academy.review(p, Outcome.NO_IDEA, today)
        assertEquals(0, p.box); assertEquals(today.plusDays(1), p.due); assertEquals(1, p.wrong); assertEquals(4, p.seen)
    }

    @Test fun deckIsWellFormed() {
        val ids = Deck.cards.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Deck.cards.filter { it.kind == Kind.QUIZ }.forEach { assertTrue(it.id, it.correct in it.options.indices) }
        Track.entries.forEach { t -> assertTrue(t.name, Deck.cards.any { it.track == t }) }
    }

    @Test fun tracksUnlockInOrder() {
        assertEquals(setOf(Track.FOUNDATIONS), Academy.unlocked(Deck.cards, emptyMap()))
        val half = Deck.cards.filter { it.track == Track.FOUNDATIONS }.let { it.take((it.size + 1) / 2) }
        val p = half.associate { it.id to Progress(it.id, seen = 1) }
        assertTrue(Track.DEBT in Academy.unlocked(Deck.cards, p))
    }

    @Test fun sessionPutsSignalFirstThenDueThenNew() {
        val due = Deck.cards.first { it.track == Track.FOUNDATIONS && it.kind != Kind.YOU }
        val p = mapOf(due.id to Progress(due.id, box = 1, due = today.minusDays(2), seen = 1))
        val s = Academy.session(Deck.cards, p, today, setOf(Signal.FULIZA_RECENT), canUse = { false })
        assertEquals(5, s.size)
        assertTrue(Signal.FULIZA_RECENT in s[0].signals)
        assertEquals(due, s[1])
        assertTrue(s.none { it.kind == Kind.YOU })
        assertEquals(s.size, s.toSet().size)
    }

    @Test fun masteryAndIq() {
        assertEquals(0, Academy.moneyIq(Deck.cards, emptyMap()))
        val all = Deck.cards.associate { it.id to Progress(it.id, box = 5, seen = 3) }
        assertEquals(100, Academy.moneyIq(Deck.cards, all))
        assertEquals(1f, Academy.mastery(Deck.cards, all)[Track.MIND]!!, 0.001f)
    }

    @Test fun streakCountsBackFromYesterday() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, Academy.streak(days, today))
        assertEquals(3, Academy.streak(days + today, today))
    }

    @Test fun fillNeedsEveryPlaceholder() {
        assertEquals("You paid Ksh 300", Academy.fill("You paid Ksh {fees_month}", mapOf("fees_month" to "300")))
        assertNull(Academy.fill("{a} and {b}", mapOf("a" to "1")))
    }
}

class MoneyFactsTest {
    private val ksh = { s: Long -> s * 100 }

    @Test fun factsAndSignals() {
        val n = MoneyNumbers(feesMonth = ksh(500), dayOfMonth = 10, fixedMonth = ksh(20_000), savings = ksh(10_000),
            spendMonthAvg = ksh(30_000), netWorth = ksh(108_000), inLastMonth = ksh(40_000), outLastMonth = ksh(38_000))
        val f = MoneyFacts.facts(n)
        assertEquals("Ksh 18,250", f["fees_year"])
        assertEquals("Ksh 60,000", f["emergency_target"])
        assertEquals("15 days", f["runway"])
        assertEquals("Ksh 10,800,000", f["freedom_number"])
        assertEquals("1%", f["freedom_pct"])
        assertEquals("5%", f["savings_rate"])
        assertNull(f["debts"])
        assertTrue(f["stage"]!!.startsWith("1"))
        assertEquals(setOf(Signal.HIGH_FEES, Signal.NO_EMERGENCY_FUND, Signal.SAVING_LOW), MoneyFacts.signals(n))
        // A YOU card needing debts can't be filled.
        assertNull(Academy.fill(Deck.byId("d-you-debts")!!.question, f))
    }

    @Test fun stagesClimb() {
        val base = MoneyNumbers(fixedMonth = ksh(10_000), spendMonthAvg = ksh(10_000))
        assertTrue(MoneyFacts.stage(base.copy(savings = ksh(5_000))).startsWith("1"))
        assertTrue(MoneyFacts.stage(base.copy(savings = ksh(15_000), debts = 1)).startsWith("2"))
        assertTrue(MoneyFacts.stage(base.copy(savings = ksh(15_000))).startsWith("3"))
        assertTrue(MoneyFacts.stage(base.copy(savings = ksh(50_000), netWorth = ksh(50_000))).startsWith("4"))
        assertTrue(MoneyFacts.stage(base.copy(savings = ksh(50_000), netWorth = ksh(4_000_000))).startsWith("6"))
    }
}
