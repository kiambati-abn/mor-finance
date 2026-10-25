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
        assertEquals(7, Academy.review(p.copy(box = 7), Outcome.KNEW, today).box)
        p = Academy.review(p, Outcome.UNSURE, today)
        assertEquals(2, p.box)
        p = Academy.review(p, Outcome.NO_IDEA, today)
        assertEquals(0, p.box); assertEquals(today.plusDays(1), p.due); assertEquals(1, p.wrong); assertEquals(4, p.seen)
    }

    @Test fun deckIsWellFormed() {
        val ids = Deck.cards.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Deck.cards.filter { it.kind == Kind.QUIZ }.forEach { assertTrue(it.id, it.correct in it.options.indices) }
        // A year of new cards at 2 a day, with every level of every area filled.
        assertTrue("${Deck.cards.size} cards", Deck.cards.size >= 700)
        Track.entries.forEach { t -> (1..5).forEach { l -> assertTrue("$t L$l", Deck.cards.count { it.track == t && it.level == l } >= 8) } }
        Deck.cards.filter { it.kind == Kind.YOU }.forEach { c ->
            assertTrue(c.id, Regex("""\{(\w+)\}""").findAll(c.question).all { it.groupValues[1] in MoneyFacts.facts(MoneyNumbers(1, 1, 1, 1, 1, 1, 1, 1, 1)).keys })
        }
    }

    private fun card(id: String, t: Track, level: Int) = Card(id, t, Kind.CONCEPT, "q", "a", level = level)

    @Test fun levelsOpenWhenYouKnowTheOneBelow() {
        val deck = (1..5).map { card("r$it", Track.DEBT, 1) } + (1..5).map { card("h$it", Track.DEBT, 2) } + card("p1", Track.DEBT, 3)
        assertEquals(1, Academy.openLevel(Track.DEBT, deck, emptyMap()))
        val knowThree = (1..3).associate { "r$it" to Progress("r$it", box = 2, seen = 2) }
        assertEquals(2, Academy.openLevel(Track.DEBT, deck, knowThree))
        // Seen but not yet known (box 1) doesn't count.
        assertEquals(1, Academy.openLevel(Track.DEBT, deck, (1..5).associate { "r$it" to Progress("r$it", box = 1, seen = 1) }))
        // An area with no cards at a level skips it; with nothing left it tops out at Legend.
        assertEquals(5, Academy.openLevel(Track.MIND, deck, emptyMap()))
        assertEquals(2 to 5, Academy.levelCount(Track.DEBT, 1, deck, knowThree.filterKeys { it != "r3" }))
    }

    @Test fun themeRotatesDaily() {
        val today = LocalDate.of(2026, 10, 25)
        val week = (0L until 12L).map { Academy.themeOf(today.plusDays(it)) }
        assertEquals(Track.entries.toSet(), week.toSet())
        assertTrue(Academy.themeOf(today) != Academy.themeOf(today.plusDays(1)))
    }

    @Test fun sessionPacesNewCardsAndFollowsTheme() {
        val deck = Track.entries.flatMap { t -> (1..6).map { card("${t.name}-$it", t, if (it <= 3) 1 else 2) } }
        val s = Academy.session(deck, emptyMap(), today, emptySet(), focus = Track.ECONOMY)
        assertEquals(3, s.size) // no reviews yet: 3 new
        assertTrue(s.all { it.track == Track.ECONOMY && it.level == 1 })
        val extra = Academy.session(deck, emptyMap(), today, emptySet(), focus = Track.ECONOMY, extra = true)
        assertEquals(5, extra.size)
        // Theme exhausted at its open level: the next area fills in.
        val seenEconomyL1 = (1..3).associate { "ECONOMY-$it" to Progress("ECONOMY-$it", box = 1, due = today.plusDays(1), seen = 1) }
        val next = Academy.session(deck, seenEconomyL1, today, emptySet(), focus = Track.ECONOMY)
        assertTrue(next.all { it.track == Track.PROTECTION })
        // A card about what just happened in your money comes first, from any area; due reviews follow.
        val fuliza = Card("fz", Track.DEBT, Kind.QUIZ, "q", "a", listOf("x", "y"), 0, signals = setOf(Signal.FULIZA_RECENT))
        val due = deck.first { it.track == Track.MIND }
        val p = mapOf(due.id to Progress(due.id, box = 1, due = today.minusDays(1), seen = 1))
        val withSignal = Academy.session(deck + fuliza, p, today, setOf(Signal.FULIZA_RECENT), focus = Track.ECONOMY)
        assertEquals(fuliza, withSignal[0])
        assertEquals(due, withSignal[1])
        assertEquals(4, withSignal.size) // signal + 1 review + 2 more new (3 new while reviews are few)
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
