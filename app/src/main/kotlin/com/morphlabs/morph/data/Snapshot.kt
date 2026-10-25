package com.morphlabs.morph.data

import com.morphlabs.morph.core.budget.Allocation
import com.morphlabs.morph.core.budget.BudgetMove
import com.morphlabs.morph.core.budget.EnvelopeBook
import com.morphlabs.morph.core.learn.Categorizer
import com.morphlabs.morph.core.learn.Guess
import com.morphlabs.morph.core.learn.Observation
import com.morphlabs.morph.core.learn.Query
import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.ledger.Posting
import com.morphlabs.morph.core.ledger.Reconcile
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.sms.Counterparty
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** An immutable, in-memory copy of everything in the database. Screens read from this. */
class Snapshot(
    val accounts: List<Account>,
    /** Oldest first. */
    val entries: List<Entry>,
    val moves: List<BudgetMove>,
    val allocations: List<Allocation> = emptyList(),
    /** When envelopes were switched on, or null while they're off. */
    val envelopesStart: Long? = null,
    /** Budget view: hide real balances, show what's left in envelopes. */
    val budgetView: Boolean = false,
    /** Withdrawals you said to keep as plain cash. */
    val cashDone: Set<Long> = emptySet(),
    /** Tracking start: nothing earlier is imported or asked about. Null = no limit. */
    val startDate: Long? = null,
    val stars: Int,
    val bonusDays: Set<String>,
    val zone: ZoneId,
    /** Days with a finished Academy lesson. */
    val learnDays: Set<java.time.LocalDate> = emptySet(),
) {
    val byId: Map<Long, Account> = accounts.associateBy { it.id }
    val raw: Map<Long, Long> by lazy { Balances.raw(entries) }

    fun role(r: SystemRole): Long = accounts.first { it.role == r }.id
    fun account(r: SystemRole): Account = accounts.first { it.role == r }
    fun balance(a: Account): Long = Balances.natural(a, raw)

    val pending: List<Entry> by lazy {
        entries.filter { it.status == EntryStatus.NEEDS_REVIEW && (startDate == null || it.timestamp >= startDate) }.sortedByDescending { it.timestamp }
    }
    val newestFirst: List<Entry> by lazy { entries.sortedByDescending { it.timestamp } }

    /** Wallet and fee accounts: never the "what was it" side of a transaction. */
    val structural: Set<Long> by lazy {
        accounts.filter {
            it.role == SystemRole.MPESA || it.role == SystemRole.TRANSACTION_COSTS ||
                it.role == SystemRole.WITHDRAWAL_CHARGES || it.role == SystemRole.FULIZA_CHARGES
        }.map { it.id }.toSet()
    }

    fun categoryLineAmount(e: Entry): Long = e.lines.firstOrNull { it.accountId == e.categoryId }?.amount ?: 0L

    /** All accounts on the category side (more than one when split). */
    fun categories(e: Entry): List<Account> = Posting.categorySide(e, structural).mapNotNull { byId[it.accountId] }

    /** Money went *to* the category side, e.g. a payment or a transfer into savings. */
    fun outgoing(e: Entry): Boolean = categoryLineAmount(e) > 0 || (e.categoryId == null && e.kind?.outgoing == true)

    fun amount(e: Entry): Long = Posting.categoryAmount(e, structural).takeIf { it > 0 } ?: e.size()

    fun hour(e: Entry): Int = Instant.ofEpochMilli(e.timestamp).atZone(zone).hour

    private fun minute(e: Entry): Int = Instant.ofEpochMilli(e.timestamp).atZone(zone).let { it.hour * 60 + it.minute }

    private fun weekend(e: Entry): Boolean = Instant.ofEpochMilli(e.timestamp).atZone(zone).dayOfWeek.value >= 6

    fun query(e: Entry): Query = Query(e.counterpartyKey, e.counterparty, e.kind, outgoing(e), amount(e), hour(e), minute(e), weekend(e))

    /** What the learner studies: every filed transaction you didn't object to. */
    fun observations(): List<Observation> {
        val unc = role(SystemRole.UNCATEGORIZED)
        return entries.filter {
            it.source != EntrySource.SYSTEM && it.status != EntryStatus.NEEDS_REVIEW && it.categoryId != null && it.categoryId != unc
        }.map { e ->
            Observation(
                key = e.counterpartyKey, tokens = Counterparty.tokens(e.counterparty), kind = e.kind,
                outgoing = outgoing(e), amount = amount(e), hour = hour(e), accountId = e.categoryId!!,
                timestamp = e.timestamp,
                // Your choices count fully; Morph's own filings less, and its forced low-confidence guesses barely.
                weight = when {
                    e.status == EntryStatus.CONFIRMED -> 1.0
                    (e.confidence ?: 1.0) < 0.55 -> 0.25
                    else -> 0.6
                },
                minute = minute(e), weekend = weekend(e), entryId = e.id,
            )
        }
    }

    private val allObservations: List<com.morphlabs.morph.core.learn.Observation> by lazy { observations() }

    fun categorizer(): Categorizer = Categorizer(accounts, allObservations)

    /** A fresh opinion on an entry Morph filed itself: what would it say without that entry's own vote? */
    fun reconsider(e: Entry): Guess =
        Categorizer(accounts, allObservations.filter { it.entryId != e.id }).guess(query(e), 3)

    fun guess(e: Entry, limit: Int = 3, autopilot: Double? = null): Guess = categorizer().guess(query(e), limit, autopilot)

    /** Auto-filed entries Morph wasn't very sure about: worth a quick look. */
    val unsure: List<Entry> by lazy {
        entries.filter { it.status == EntryStatus.AUTO && (it.confidence ?: 1.0) < UNSURE }.sortedBy { it.confidence ?: 1.0 }
    }

    companion object {
        const val UNSURE = 0.75
    }

    /** The newest SMS that stated a balance. */
    val lastSms: Entry? by lazy { entries.filter { it.smsBalance != null }.maxWithOrNull(compareBy({ it.timestamp }, { it.id })) }

    /**
     * Difference between what M-Pesa says you have (latest SMS) and what Morph's ledger says,
     * at that moment. Non-zero means an SMS was missed or deleted.
     */
    val mpesaGap: Long by lazy {
        val r = Reconcile.mpesa(entries, role(SystemRole.MPESA)) ?: return@lazy 0L
        r.opening + r.gaps.sumOf { it.amount }
    }

    fun with(entry: Entry): Snapshot = Snapshot(accounts, entries + entry, moves, allocations, envelopesStart, budgetView, cashDone, startDate, stars, bonusDays, zone, learnDays)

    /** Recent cash withdrawals with money not yet said what it's for: (withdrawal, still unspread). */
    val cashToSpread: List<Pair<Entry, Long>> by lazy {
        val cash = role(SystemRole.CASH)
        val weekAgo = (entries.maxOfOrNull { it.timestamp } ?: 0L) - 14L * 24 * 3600 * 1000
        entries.filter { it.kind == com.morphlabs.morph.core.model.TxKind.WITHDRAW && it.timestamp >= weekAgo && it.id !in cashDone }
            .mapNotNull { w ->
                val got = w.lines.firstOrNull { it.accountId == cash }?.amount ?: return@mapNotNull null
                val spread = entries.filter { it.counterpartyKey == "cash:${w.id}" }.sumOf { e -> e.lines.filter { it.accountId == cash && it.amount < 0 }.sumOf { -it.amount } }
                (w to (got - spread)).takeIf { it.second > 0 }
            }.sortedByDescending { it.first.timestamp }
    }

    /** Envelopes, when they're on. */
    val book: EnvelopeBook? by lazy { envelopesStart?.let { EnvelopeBook(accounts, entries, allocations, it, zone) } }

    /**
     * How an account's spending budget is doing right now (envelope, or plain budget), or null when
     * it has none. Savings and debt targets aren't warnings, so only spending accounts get one.
     */
    fun health(accountId: Long, today: java.time.LocalDate): com.morphlabs.morph.core.budget.Health? {
        val a = byId[accountId] ?: return null
        if (a.type != com.morphlabs.morph.core.model.AccountType.EXPENSE) return null
        val month = java.time.YearMonth.from(today).toString()
        book?.let { _ ->
            val e = envelopesOn(today).firstOrNull { it.account.id == accountId } ?: return null
            return if (e.todayLeft != null) com.morphlabs.morph.core.budget.Health(accountId, minOf(e.todayLeft!!, e.left), a.budget, today.toString())
            else com.morphlabs.morph.core.budget.Health(accountId, e.left, maxOf(e.assigned, e.target), month)
        }
        val e = budgetsOn(today).firstOrNull { it.account.id == accountId && it.isLimit } ?: return null
        return com.morphlabs.morph.core.budget.Health(accountId, e.left, e.available, if (e.period == com.morphlabs.morph.core.model.BudgetPeriod.DAILY) today.toString() else month)
    }

    // Worked out once per day per snapshot: health() is asked for every account on every screen.
    private val envelopeCache = HashMap<java.time.LocalDate, List<EnvelopeBook.Envelope>>()
    private val budgetCache = HashMap<java.time.LocalDate, List<com.morphlabs.morph.core.budget.Envelope>>()

    @Synchronized
    fun envelopesOn(today: java.time.LocalDate): List<EnvelopeBook.Envelope> =
        envelopeCache.getOrPut(today) { book?.envelopes(today).orEmpty() }

    @Synchronized
    fun budgetsOn(today: java.time.LocalDate): List<com.morphlabs.morph.core.budget.Envelope> =
        budgetCache.getOrPut(today) { com.morphlabs.morph.core.budget.Budgets(zone).envelopes(accounts, entries, moves, today) }

    /** Every spending budget that's getting low, almost out or over. */
    fun warnings(today: java.time.LocalDate): List<com.morphlabs.morph.core.budget.Health> =
        accounts.mapNotNull { health(it.id, today) }.filter { it.level.warns }.sortedBy { it.level.ordinal * -1 }

    /** A few lines to jog your memory about a transaction (Sort card). */
    fun context(e: Entry): List<String> =
        runCatching { com.morphlabs.morph.core.insight.TxContext.explain(e, entries, byId, structural, zone) }.getOrDefault(emptyList())

    /** "Ksh 1,240 left" for an account's envelope, if it has one. */
    fun envelopeLeft(accountId: Long, today: java.time.LocalDate): Long? =
        envelopesOn(today).firstOrNull { it.account.id == accountId }?.let { it.todayLeft?.let { t -> minOf(t, it.left) } ?: it.left }
}
