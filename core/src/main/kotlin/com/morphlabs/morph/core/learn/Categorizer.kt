package com.morphlabs.morph.core.learn

import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.core.sms.Counterparty
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/** One thing Morph has seen you file. Built from your confirmed (and accepted auto) entries. */
data class Observation(
    val key: String?,
    val tokens: Set<String>,
    val kind: TxKind?,
    val outgoing: Boolean,
    val amount: Long,
    val hour: Int,
    val accountId: Long,
    val timestamp: Long,
    /** 1.0 when you chose it, lower when Morph auto-filed it and you just didn't object. */
    val weight: Double = 1.0,
    /** Minute of the day (0..1439), for routines like "the 7:30 AM bus". */
    val minute: Int = hour * 60,
    val weekend: Boolean? = null,
)

/** What Morph wants to know about a new transaction. */
data class Query(
    val key: String?,
    val name: String?,
    val kind: TxKind?,
    val outgoing: Boolean,
    val amount: Long,
    val hour: Int,
    val minute: Int = hour * 60,
    val weekend: Boolean? = null,
)

data class Suggestion(val accountId: Long, val score: Double, val reason: String)

data class Guess(
    /** Set when Morph is confident enough to file it without asking. */
    val autoAccountId: Long?,
    val suggestions: List<Suggestion>,
    /** 0..1: how sure Morph is about the first suggestion. */
    val confidence: Double = 0.0,
) {
    val reason: String? get() = suggestions.firstOrNull { it.accountId == autoAccountId }?.reason
}

/**
 * Learns how you file transactions and predicts the next one. Deliberately simple and explainable:
 *  1. the same person/till/paybill you've filed before (strongest),
 *  2. names that share words with ones you've filed ("LAKEWOOD HOTEL" vs "LAKEWOOD CAFE"),
 *  3. routines: about the same amount at about the same time of day, even to different people
 *     (the morning bus fare paid to a different conductor every day),
 *  4. Morph's starter knowledge of Kenyan merchants.
 */
class Categorizer(
    private val accounts: List<Account>,
    private val history: List<Observation>,
) {
    private val byId = accounts.associateBy { it.id }
    private val byHint = accounts.filter { it.pickable && it.hint != null }.groupBy { it.hint!! }

    /**
     * @param autopilot when set (0..1), also files on its own anything at least this confident,
     *   not only the rock-solid cases. That's the "Auto-sort" setting.
     */
    fun guess(q: Query, limit: Int = 3, autopilot: Double? = null): Guess {
        val scores = HashMap<Long, Double>()
        val reasons = HashMap<Long, String>()
        fun add(id: Long, s: Double, reason: String) {
            val a = byId[id] ?: return
            if (!allowed(a, q.outgoing)) return
            scores[id] = (scores[id] ?: 0.0) + s
            if (reason.isNotEmpty()) reasons.putIfAbsent(id, reason)
        }

        // 1. Exact counterparty.
        val same = if (q.key != null) history.filter { it.key == q.key } else emptyList()
        val sameByAccount = same.groupBy { it.accountId }
        val label = q.name?.let { Counterparty.normalize(it) } ?: "this"
        for ((id, obs) in sameByAccount) {
            add(id, 6.0 * obs.sumOf { it.weight }, "You filed $label as ${byId[id]?.name} ${times(obs.size)}")
        }

        // 2. Shared words.
        val tokens = Counterparty.tokens(q.name)
        if (tokens.isNotEmpty()) {
            for (o in history) {
                if (o.key == q.key || o.tokens.isEmpty()) continue
                val overlap = tokens.intersect(o.tokens).size
                if (overlap == 0) continue
                add(o.accountId, 1.5 * o.weight * overlap / min(tokens.size, o.tokens.size), "Similar to ${o.tokens.joinToString(" ")}")
            }
        }

        // 3. Routines: similar amount, similar time of day, same kind of payment.
        val routine = HashMap<Long, Double>()
        val routineCount = HashMap<Long, Int>()
        for (o in history) {
            if (o.outgoing != q.outgoing || !sameFamily(o.kind, q.kind)) continue
            val close = closeness(o.amount, q.amount)
            if (close == 0.0) continue
            val gap = minuteGap(o.minute, q.minute)
            if (gap > 120) continue
            val timeFactor = 1.0 - gap / 150.0
            val dayFactor = when {
                o.weekend == null || q.weekend == null -> 1.0
                o.weekend == q.weekend -> 1.15
                else -> 0.7
            }
            routine[o.accountId] = (routine[o.accountId] ?: 0.0) + 1.5 * close * timeFactor * dayFactor * o.weight
            routineCount[o.accountId] = (routineCount[o.accountId] ?: 0) + 1
        }
        for ((id, s) in routine) {
            val n = routineCount[id] ?: 0
            add(id, min(s, 8.0), "You usually file ~${Money.format(q.amount)} around ${clock(q.minute)} as ${byId[id]?.name} (${times(n)})")
        }

        // 4. Starter knowledge.
        var strongPrior: Pair<Long, Double>? = null
        val priorHints = mutableListOf<Pair<String, Double>>()
        if (q.kind == TxKind.AIRTIME) priorHints += "airtime" to StarterKnowledge.AUTO_STRENGTH
        if (q.name != null) priorHints += StarterKnowledge.match(Counterparty.normalize(q.name))
        for ((hint, strength) in priorHints) {
            val target = byHint[hint]?.firstOrNull() ?: continue
            add(target.id, strength, "Looks like ${target.name}")
            if (strength >= StarterKnowledge.AUTO_STRENGTH && (strongPrior == null || strength > strongPrior.second)) strongPrior = target.id to strength
        }

        val ranked = scores.entries.sortedByDescending { it.value }
            .map { Suggestion(it.key, it.value, reasons[it.key] ?: "") }
            .toMutableList()
        val confidence = confidenceOf(ranked)
        // Always offer a few choices: fill with what you use most for this direction.
        if (ranked.size < limit) {
            val popular = history.filter { it.outgoing == q.outgoing }
                .groupingBy { it.accountId }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
            for (id in popular + defaultChoices(q.outgoing)) {
                if (ranked.size >= limit) break
                val a = byId[id] ?: continue
                if (!allowed(a, q.outgoing) || ranked.any { it.accountId == id }) continue
                ranked += Suggestion(id, 0.0, "")
            }
        }

        var auto = autoPick(sameByAccount, q.key, strongPrior)
        if (auto == null && autopilot != null && confidence >= autopilot) auto = ranked.firstOrNull()?.accountId
        return Guess(autoAccountId = auto, suggestions = ranked.take(limit), confidence = confidence)
    }

    /**
     * How sure: the top choice's share of all the evidence, times how much evidence there is
     * (a little evidence can't make Morph very sure, however one-sided it is).
     */
    private fun confidenceOf(ranked: List<Suggestion>): Double {
        val top = ranked.firstOrNull()?.score ?: return 0.0
        val total = ranked.sumOf { it.score }
        if (top <= 0 || total <= 0) return 0.0
        val share = top / total
        val evidence = 1 - exp(-top / 6.0)
        return share * evidence
    }

    private fun autoPick(sameByAccount: Map<Long, List<Observation>>, key: String?, strongPrior: Pair<Long, Double>?): Long? {
        if (key != null && sameByAccount.isNotEmpty()) {
            val total = sameByAccount.values.sumOf { obs -> obs.sumOf { it.weight } }
            val (topId, topObs) = sameByAccount.maxBy { (_, obs) -> obs.sumOf { it.weight } }
            val topWeight = topObs.sumOf { it.weight }
            val latest = sameByAccount.values.flatten().maxBy { it.timestamp }
            val confident = topObs.count { it.weight >= 1.0 } >= 2 && topWeight / total >= 0.8 && latest.accountId == topId
            // You've filed this counterparty, but not consistently: ask rather than guess.
            return if (confident && byId[topId]?.pickable == true) topId else null
        }
        return strongPrior?.first
    }

    /** Money out can go to an expense, savings, a debt you're paying, or someone who owes you; never to income. */
    private fun allowed(a: Account, outgoing: Boolean): Boolean {
        if (!a.pickable) return false
        return if (outgoing) a.type != AccountType.INCOME else a.type != AccountType.EXPENSE
    }

    private fun defaultChoices(outgoing: Boolean): List<Long> {
        val hints = if (outgoing) listOf("food", "transport", "family", "friends", "groceries", "airtime")
        else listOf("income_other", "family", "owed_to_me", "salary", "business")
        return hints.mapNotNull { byHint[it]?.firstOrNull()?.id }
    }

    companion object {
        fun similar(a: Long, b: Long): Boolean {
            val hi = maxOf(abs(a), abs(b)).toDouble()
            return hi == 0.0 || abs(a - b) / hi <= 0.3
        }

        /** 1.0 for the same amount, less as amounts drift apart, 0 beyond 30%. */
        fun closeness(a: Long, b: Long): Double {
            val hi = maxOf(abs(a), abs(b)).toDouble()
            if (hi == 0.0) return 1.0
            val d = abs(a - b) / hi
            return when {
                d == 0.0 -> 1.0
                d <= 0.1 -> 0.7
                d <= 0.3 -> 0.3
                else -> 0.0
            }
        }

        /** Paying a conductor by Send Money or a till by Buy Goods is the same kind of thing. */
        fun sameFamily(a: TxKind?, b: TxKind?): Boolean {
            if (a == b) return true
            val everyday = setOf(TxKind.SEND_MONEY, TxKind.BUY_GOODS)
            return a in everyday && b in everyday
        }

        /** Minutes between two times of day, the short way round midnight. */
        fun minuteGap(a: Int, b: Int): Int {
            val d = abs(a - b) % 1440
            return min(d, 1440 - d)
        }

        fun clock(minute: Int): String {
            val h = minute / 60 % 24
            val m = minute % 60
            val h12 = if (h % 12 == 0) 12 else h % 12
            return "$h12:${"%02d".format(m)} ${if (h < 12) "AM" else "PM"}"
        }

        fun bucket(hour: Int): Int = when (hour) {
            in 5..10 -> 0
            in 11..14 -> 1
            in 15..17 -> 2
            in 18..21 -> 3
            else -> 4
        }

        fun bucketName(hour: Int): String = listOf("morning", "midday", "afternoon", "evening", "late-night")[bucket(hour)]

        private fun times(n: Int) = if (n == 1) "once" else if (n == 2) "twice" else "$n times"
    }
}
