package com.morphlabs.morph.core.academy

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The eight learning tracks, in the order they unlock. */
enum class Track(val title: String, val emoji: String) {
    FOUNDATIONS("Foundations", "🧱"),
    DEBT("Debt", "⛓️"),
    KENYA_TOOLS("Kenyan money tools", "🇰🇪"),
    INVESTING("Investing", "📈"),
    FREEDOM("Financial freedom", "🦋"),
    FOUNDER("Founder", "🚀"),
    DEALS("Deals (Shark Tank)", "🦈"),
    MIND("Money mind", "🧠"),
}

enum class Kind { CONCEPT, QUIZ, YOU }

/** Moments in your own money that make a card worth seeing today (just-in-time learning). */
enum class Signal { FULIZA_RECENT, MONEY_IN_RECENT, HIGH_FEES, HAS_DEBT, NO_EMERGENCY_FUND, OVER_BUDGET, SAVING_LOW, BIG_SPEND }

/** Where a "Try this" button takes you in Morph. */
enum class Action { BUDGETS, ACCOUNTS, PATTERNS, AUTOSORT, NONE }

/**
 * One card. QUIZ cards have [options] and the index of the [correct] one; every card has an
 * [answer] (the explanation shown after). YOU cards use {placeholders} filled from your own data.
 */
data class Card(
    val id: String,
    val track: Track,
    val kind: Kind,
    val question: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val correct: Int = -1,
    /** One small real-world step. */
    val tryThis: String? = null,
    val action: Action = Action.NONE,
    val signals: Set<Signal> = emptySet(),
    /** Figures that change over time: the app shows "check current". */
    val timeSensitive: Boolean = false,
)

/** Your history with one card. Boxes 0..5; higher = better known, seen less often. */
data class Progress(val cardId: String, val box: Int = 0, val due: LocalDate? = null, val seen: Int = 0, val right: Int = 0, val wrong: Int = 0)

enum class Outcome { KNEW, UNSURE, NO_IDEA }

object Academy {

    /** Days until the next review for each box (spaced retrieval; fixed gaps work about as well as fancy ones). */
    val intervals = listOf(1L, 1L, 3L, 7L, 21L, 60L)

    fun review(p: Progress, outcome: Outcome, today: LocalDate): Progress {
        val box = when (outcome) {
            Outcome.KNEW -> (p.box + 1).coerceAtMost(5)
            Outcome.UNSURE -> p.box.coerceIn(1, 2)
            Outcome.NO_IDEA -> 0
        }
        return p.copy(
            box = box,
            due = today.plusDays(if (outcome == Outcome.NO_IDEA) 1 else intervals[box]),
            seen = p.seen + 1,
            right = p.right + if (outcome == Outcome.KNEW) 1 else 0,
            wrong = p.wrong + if (outcome == Outcome.NO_IDEA) 1 else 0,
        )
    }

    /** A track opens once you've seen half of the one before it (Foundations is always open). */
    fun unlocked(deck: List<Card>, progress: Map<String, Progress>): Set<Track> {
        val open = mutableSetOf(Track.FOUNDATIONS)
        for (i in 1 until Track.entries.size) {
            val prev = deck.filter { it.track == Track.entries[i - 1] }
            val seen = prev.count { (progress[it.id]?.seen ?: 0) > 0 }
            if (prev.isEmpty() || seen * 2 >= prev.size) open += Track.entries[i] else break
        }
        return open
    }

    /**
     * Today's cards: first a new card that fits something happening in your money right now,
     * then reviews that are due, then new cards in curriculum order. [canUse] says whether a YOU
     * card can be filled with your data.
     */
    fun session(
        deck: List<Card>, progress: Map<String, Progress>, today: LocalDate, signals: Set<Signal>,
        canUse: (Card) -> Boolean = { true }, size: Int = 5,
    ): List<Card> {
        val open = unlocked(deck, progress)
        val usable = deck.filter { (it.kind != Kind.YOU || canUse(it)) }
        val fresh = usable.filter { (progress[it.id]?.seen ?: 0) == 0 }
        val out = mutableListOf<Card>()
        // 1. Just in time: a card about what's happening in your money, even from a later track.
        fresh.firstOrNull { c -> c.signals.any { it in signals } }?.let { out += it }
        // 2. Reviews that are due, most overdue first (at most 3, so there's always something new).
        out += usable.filter { c -> progress[c.id]?.let { p -> p.seen > 0 && p.due != null && !p.due.isAfter(today) } == true && c !in out }
            .sortedBy { progress[it.id]!!.due }.take(3)
        // 3. New cards from open tracks, in order.
        out += fresh.filter { it.track in open && it !in out }.take(size - out.size)
        return out.take(size)
    }

    /** 0..1 per track: how well you know it (a card counts fully once it reaches box 3). */
    fun mastery(deck: List<Card>, progress: Map<String, Progress>): Map<Track, Float> =
        Track.entries.associateWith { t ->
            val cards = deck.filter { it.track == t }
            if (cards.isEmpty()) 0f else cards.sumOf { (minOf(progress[it.id]?.box ?: 0, 3) / 3.0) }.toFloat() / cards.size
        }

    /** Money IQ: overall mastery as 0..100. */
    fun moneyIq(deck: List<Card>, progress: Map<String, Progress>): Int {
        if (deck.isEmpty()) return 0
        val total = deck.sumOf { minOf(progress[it.id]?.box ?: 0, 3) / 3.0 }
        return (total * 100 / deck.size).toInt()
    }

    /** Consecutive days (ending today or yesterday) with a finished lesson. */
    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    /** Fills {placeholders} from your numbers; null when a needed number is missing. */
    fun fill(text: String, facts: Map<String, String>): String? {
        var out = text
        for (m in placeholder.findAll(text)) {
            val v = facts[m.groupValues[1]] ?: return null
            out = out.replace(m.value, v)
        }
        return out
    }

    // Both braces escaped: Android's regex engine rejects a bare "}" (the JVM doesn't, so tests can't catch it).
    private val placeholder = Regex("""\{(\w+)\}""")

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
