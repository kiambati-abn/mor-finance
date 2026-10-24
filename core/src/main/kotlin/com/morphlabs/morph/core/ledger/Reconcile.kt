package com.morphlabs.morph.core.ledger

import com.morphlabs.morph.core.model.Entry

/**
 * Keeps Morph's M-Pesa balance equal to what M-Pesa itself says. Every M-Pesa SMS states the
 * balance after it, so walking them in time order shows exactly where something is missing:
 *  - the starting balance is whatever makes the earliest SMS match,
 *  - any later jump between two SMS that Morph's entries don't explain is a gap (a deleted or
 *    unreadable message), placed just before the SMS where it shows up.
 */
object Reconcile {

    data class Gap(
        /** The SMS entry where the difference first shows. */
        val atEntryId: Long,
        val timestamp: Long,
        /** Positive: money arrived that Morph didn't see. Negative: money left that Morph didn't see. */
        val amount: Long,
    )

    data class Result(val opening: Long, val openingTimestamp: Long, val gaps: List<Gap>)

    /**
     * @param entries everything except the M-Pesa starting balance and unsorted gap entries
     *   (they're what this works out), in any order.
     * @param settleBefore gaps that show up at or after this time are left alone for now: the
     *   message that explains them (e.g. a Fuliza SMS) may simply not have arrived yet.
     * @return null when there's no M-Pesa SMS with a balance yet.
     */
    fun mpesa(entries: List<Entry>, mpesaId: Long, settleBefore: Long = Long.MAX_VALUE): Result? {
        fun effect(e: Entry) = e.lines.filter { it.accountId == mpesaId }.sumOf { it.amount }
        var running = 0L
        var offset: Long? = null
        var opening = 0L
        var openingTs = 0L
        val gaps = mutableListOf<Gap>()
        fun visit(e: Entry) {
            running += effect(e)
            val stated = e.smsBalance ?: return
            val diff = stated - running
            val o = offset
            if (o == null) {
                offset = diff; opening = diff; openingTs = e.timestamp - 1
            } else if (diff != o && e.timestamp < settleBefore) {
                gaps += Gap(e.id, e.timestamp - 1, diff - o)
                offset = diff
            }
        }
        // SMS times are to the minute, so several can share one. Within a minute, follow the
        // balances: next is the message whose stated balance continues the chain.
        for ((_, group) in entries.groupBy { it.timestamp }.toSortedMap()) {
            val left = group.sortedBy { it.id }.toMutableList()
            left.filter { it.smsBalance == null }.forEach { visit(it); left.remove(it) }
            while (left.isNotEmpty()) {
                val o = offset
                val next = if (o == null) {
                    // First ever: the one whose balance others continue from.
                    // (nobody else's closing balance is its opening balance).
                    left.firstOrNull { a -> left.none { b -> b !== a && b.smsBalance == a.smsBalance!! - effect(a) } } ?: left.first()
                } else left.firstOrNull { it.smsBalance == running + effect(it) + o } ?: left.first()
                visit(next)
                left.remove(next)
            }
        }
        return offset?.let { Result(opening, openingTs, gaps) }
    }
}
