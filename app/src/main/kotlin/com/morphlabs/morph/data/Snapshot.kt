package com.morphlabs.morph.data

import com.morphlabs.morph.core.budget.BudgetMove
import com.morphlabs.morph.core.learn.Categorizer
import com.morphlabs.morph.core.learn.Guess
import com.morphlabs.morph.core.learn.Observation
import com.morphlabs.morph.core.learn.Query
import com.morphlabs.morph.core.ledger.Balances
import com.morphlabs.morph.core.ledger.Posting
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
    val stars: Int,
    val bonusDays: Set<String>,
    val zone: ZoneId,
) {
    val byId: Map<Long, Account> = accounts.associateBy { it.id }
    val raw: Map<Long, Long> by lazy { Balances.raw(entries) }

    fun role(r: SystemRole): Long = accounts.first { it.role == r }.id
    fun account(r: SystemRole): Account = accounts.first { it.role == r }
    fun balance(a: Account): Long = Balances.natural(a, raw)

    val pending: List<Entry> by lazy { entries.filter { it.status == EntryStatus.NEEDS_REVIEW }.sortedByDescending { it.timestamp } }
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

    fun query(e: Entry): Query = Query(e.counterpartyKey, e.counterparty, e.kind, outgoing(e), amount(e), hour(e))

    /** What the learner studies: every filed transaction you didn't object to. */
    fun observations(): List<Observation> {
        val unc = role(SystemRole.UNCATEGORIZED)
        return entries.filter {
            it.source != EntrySource.SYSTEM && it.status != EntryStatus.NEEDS_REVIEW && it.categoryId != null && it.categoryId != unc
        }.map { e ->
            Observation(
                key = e.counterpartyKey, tokens = Counterparty.tokens(e.counterparty), kind = e.kind,
                outgoing = outgoing(e), amount = amount(e), hour = hour(e), accountId = e.categoryId!!,
                timestamp = e.timestamp, weight = if (e.status == EntryStatus.CONFIRMED) 1.0 else 0.6,
            )
        }
    }

    fun categorizer(): Categorizer = Categorizer(accounts, observations())

    fun guess(e: Entry, limit: Int = 3): Guess = categorizer().guess(query(e), limit)

    /** The newest SMS that stated a balance. */
    val lastSms: Entry? by lazy { entries.filter { it.smsBalance != null }.maxWithOrNull(compareBy({ it.timestamp }, { it.id })) }

    /**
     * Difference between what M-Pesa says you have (latest SMS) and what Morph's ledger says,
     * at that moment. Non-zero means an SMS was missed or deleted.
     */
    val mpesaGap: Long by lazy {
        val last = lastSms ?: return@lazy 0L
        val mpesa = role(SystemRole.MPESA)
        val ledger = entries.filter { it.timestamp <= last.timestamp }.sumOf { e -> e.lines.filter { it.accountId == mpesa }.sumOf { it.amount } }
        last.smsBalance!! - ledger
    }

    fun with(entry: Entry): Snapshot = Snapshot(accounts, entries + entry, moves, stars, bonusDays, zone)
}
