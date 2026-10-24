package com.morphlabs.morph.core.game

import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stars, streaks and the butterfly's life cycle. Kept small on purpose: reward the habit, nothing else. */
object Game {

    data class Level(val name: String, val emoji: String, val minStars: Int)

    val levels = listOf(
        Level("Egg", "🥚", 0),
        Level("Caterpillar", "🐛", 25),
        Level("Cocoon", "🌀", 100),
        Level("Butterfly", "🦋", 300),
        Level("Monarch", "👑", 1000),
    )

    fun level(stars: Int): Level = levels.last { stars >= it.minStars }

    fun nextLevel(stars: Int): Level? = levels.firstOrNull { it.minStars > stars }

    /** 0..1 progress from the current level to the next one. */
    fun levelProgress(stars: Int): Float {
        val cur = level(stars)
        val next = nextLevel(stars) ?: return 1f
        return (stars - cur.minStars).toFloat() / (next.minStars - cur.minStars)
    }

    /** Fresh sorting (within 15 minutes of paying) is worth more: that's when you remember best. */
    const val FRESH_WINDOW_MS = 15 * 60 * 1000L

    data class Reward(val stars: Int, val reasons: List<String>)

    fun rewardForSorting(txTime: Long, sortedAt: Long, inboxNowEmpty: Boolean, alreadyGotInboxBonusToday: Boolean): Reward {
        var stars = 1
        val reasons = mutableListOf("Sorted")
        if (sortedAt - txTime in 0..FRESH_WINDOW_MS) {
            stars += 2; reasons += "Fresh"
        }
        if (inboxNowEmpty && !alreadyGotInboxBonusToday) {
            stars += 5; reasons += "Inbox zero"
        }
        return Reward(stars, reasons)
    }

    /**
     * Consecutive days, ending today, on which nothing was left unsorted. Days without any
     * transactions don't break the streak. If today still has unsorted items the streak counts
     * up to yesterday, so you're not punished before the day is over.
     */
    fun streak(entries: List<Entry>, today: LocalDate, zone: ZoneId, maxDays: Int = 365): Int {
        val byDay = entries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        fun clean(d: LocalDate) = byDay[d].orEmpty().none { it.status == EntryStatus.NEEDS_REVIEW }
        fun active(d: LocalDate) = byDay[d].orEmpty().isNotEmpty()
        val firstDay = byDay.keys.minOrNull() ?: return 0

        var day = if (clean(today)) today else today.minusDays(1)
        var count = 0
        var steps = 0
        while (!day.isBefore(firstDay) && steps < maxDays) {
            if (!clean(day)) break
            if (active(day)) count++
            day = day.minusDays(1)
            steps++
        }
        return count
    }
}
