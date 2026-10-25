package com.morphlabs.morph.core.academy

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The twelve areas. Each day has a theme area (they rotate), and each area has five levels.
 * [funTitle] is what the app shows when fun names are on.
 */
enum class Track(val plainTitle: String, val emoji: String, val funTitle: String) {
    FOUNDATIONS("Money basics", "🧱", "Pesa 101"),
    DEBT("Debt", "👹", "Madeni Monsters"),
    KENYA_TOOLS("Kenyan money tools", "🇰🇪", "Kenyan Toolbox"),
    INVESTING("Investing", "📈", "Make Money Work"),
    FREEDOM("Financial freedom", "🦋", "Freedom Road"),
    FOUNDER("Building a business", "🚀", "Boss Moves"),
    DEALS("Deals & valuation", "🦈", "Shark Tank"),
    MIND("Money psychology", "🧠", "Mind Games"),
    ECONOMY("Economy & markets", "🌍", "Big Picture"),
    PROTECTION("Insurance, scams & safety", "🛡️", "Shield Up"),
    EARNING("Earning more", "💼", "Hustle Up"),
    PROPERTY("Land, homes & big buys", "🏠", "Shamba & Keys"),
    ;

    val title: String get() = com.morphlabs.morph.core.model.Naming.pick(funTitle, plainTitle)
}

/** How deep a card goes. You climb an area's levels by actually knowing the cards. */
enum class Level(val title: String, val emoji: String) {
    ROOKIE("Rookie", "🐣"), HUSTLER("Hustler", "🔧"), PRO("Pro", "🎯"), SHARK("Shark", "🦈"), LEGEND("Legend", "👑");

    val number: Int get() = ordinal + 1

    companion object {
        fun of(n: Int): Level = entries[(n - 1).coerceIn(0, entries.size - 1)]
    }
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
    /** 1 (Rookie) .. 5 (Legend). */
    val level: Int = 1,
    /** Where the fact comes from, short ("CBK, 2026"). */
    val source: String? = null,
)

/** Your history with one card. Boxes 0..5; higher = better known, seen less often. */
data class Progress(val cardId: String, val box: Int = 0, val due: LocalDate? = null, val seen: Int = 0, val right: Int = 0, val wrong: Int = 0)

enum class Outcome { KNEW, UNSURE, NO_IDEA }

object Academy {

    /** Days until the next review for each box (spaced retrieval). Long gaps at the top make it a lifetime habit. */
    val intervals = listOf(1L, 1L, 3L, 7L, 21L, 60L, 120L, 240L)
    private val topBox = intervals.size - 1

    /** Share of a level's cards you must know (seen right twice) before the next level opens. */
    const val LEVEL_UP = 0.6

    fun review(p: Progress, outcome: Outcome, today: LocalDate): Progress {
        val box = when (outcome) {
            Outcome.KNEW -> (p.box + 1).coerceAtMost(topBox)
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

    /** Today's theme area: they take turns, one a day, so every area comes round about once every 12 days. */
    fun themeOf(day: LocalDate): Track = Track.entries[Math.floorMod(day.toEpochDay(), Track.entries.size.toLong()).toInt()]

    fun known(p: Progress?): Boolean = (p?.box ?: 0) >= 2

    /** The highest level open in [track]: level n+1 opens once you know 60% of level n. */
    fun openLevel(track: Track, deck: List<Card>, progress: Map<String, Progress>): Int {
        var level = 1
        while (level < Level.entries.size) {
            val cards = deck.filter { it.track == track && it.level == level }
            if (cards.isNotEmpty() && cards.count { known(progress[it.id]) } < cards.size * LEVEL_UP) break
            level++
        }
        return level
    }

    /** (known, total) at a level of an area. */
    fun levelCount(track: Track, level: Int, deck: List<Card>, progress: Map<String, Progress>): Pair<Int, Int> {
        val cards = deck.filter { it.track == track && it.level == level }
        return cards.count { known(progress[it.id]) } to cards.size
    }

    /**
     * Today's cards, at most [size]:
     * 1. a new card about something happening in your money right now (just in time),
     * 2. up to 3 reviews you're about to forget,
     * 3. new cards from [focus] (today's theme), easiest open level first, then from the next areas.
     * Only 2–3 new cards a day, so the deck lasts and sticks. [extra] rounds are all new cards.
     */
    fun session(
        deck: List<Card>, progress: Map<String, Progress>, today: LocalDate, signals: Set<Signal>,
        canUse: (Card) -> Boolean = { true }, focus: Track = themeOf(today), extra: Boolean = false, size: Int = 5,
    ): List<Card> {
        val open = Track.entries.associateWith { openLevel(it, deck, progress) }
        val fresh = deck.filter { c ->
            (progress[c.id]?.seen ?: 0) == 0 && c.level <= open.getValue(c.track) && (c.kind != Kind.YOU || canUse(c))
        }
        val out = mutableListOf<Card>()
        fresh.firstOrNull { c -> c.signals.any { it in signals } }?.let { out += it }
        val reviews = if (extra) emptyList() else deck.filter { c ->
            c !in out && (c.kind != Kind.YOU || canUse(c)) &&
                progress[c.id]?.let { p -> p.seen > 0 && p.due != null && !p.due.isAfter(today) } == true
        }.sortedBy { progress[it.id]!!.due }.take(3)
        out += reviews
        val newLimit = when {
            extra -> size
            reviews.size < 2 -> 3
            else -> 2
        }
        val order = Track.entries.let { t -> (t.indexOf(focus) until t.indexOf(focus) + t.size).map { t[it % t.size] } }
        for (track in order) {
            for (c in fresh.filter { it.track == track }.sortedBy { it.level }) {
                if (out.size >= size || out.count { it !in reviews } >= newLimit) return out
                if (c !in out) out += c
            }
        }
        return out
    }

    /** 0..1 per area: how well you know it (a card counts fully once known three times). */
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
