package com.morphlabs.morph.core.budget

/** One traffic light for every budget and envelope. */
enum class Level(val label: String, val icon: String) {
    OK("On track", "🟢"),
    LOW("Getting low", "🟡"),
    CRITICAL("Almost out", "🔴"),
    OVER("Over", "⛔");

    val warns: Boolean get() = this != OK
}

/** How a budget is doing: [left] out of [available] for its current day or month. */
data class Health(val accountId: Long, val left: Long, val available: Long, val periodKey: String) {
    val level: Level get() = Health.level(left, available)
    val usedShare: Float get() = if (available <= 0) 1f else ((available - left).toFloat() / available).coerceAtLeast(0f)

    companion object {
        /** Over below zero; Almost out at 10% or less left; Getting low at 25% or less. */
        fun level(left: Long, available: Long): Level = when {
            left < 0 -> Level.OVER
            available <= 0 -> if (left == 0L) Level.CRITICAL else Level.OK
            left * 10 <= available -> Level.CRITICAL
            left * 4 <= available -> Level.LOW
            else -> Level.OK
        }
    }
}
