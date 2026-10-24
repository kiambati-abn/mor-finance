package com.morphlabs.morph.core.model

/**
 * One side of a journal entry. Positive [amount] is a debit, negative is a credit (cents).
 * Every [Entry]'s lines sum to zero; that's what keeps every balance honest.
 */
data class Line(val accountId: Long, val amount: Long)

enum class EntrySource { SMS, MANUAL, SYSTEM }

enum class EntryStatus {
    /** Waiting for the user to say what it was. Money sits in Uncategorized meanwhile. */
    NEEDS_REVIEW,
    /** Morph filed it on its own. Still counts as done, but the user can change it. */
    AUTO,
    /** The user filed or confirmed it. */
    CONFIRMED,
}

data class Entry(
    val id: Long = 0,
    /** Epoch millis. */
    val timestamp: Long,
    val description: String,
    val lines: List<Line>,
    val status: EntryStatus = EntryStatus.CONFIRMED,
    val source: EntrySource = EntrySource.MANUAL,
    /** Display name of the other side, e.g. "LAKEWOOD HOTEL". */
    val counterparty: String? = null,
    /** Normalized key the learner uses to recognise the same counterparty next time. */
    val counterpartyKey: String? = null,
    val kind: TxKind? = null,
    /** M-Pesa transaction code, e.g. "UJMJ0BKZN2". */
    val code: String? = null,
    val smsBody: String? = null,
    /** The "what was it" account: a category, savings, debt... (Uncategorized while pending). */
    val categoryId: Long? = null,
    /** M-Pesa balance the SMS reported after this transaction. */
    val smsBalance: Long? = null,
    /** Your own words about it ("lunch with Ann"). */
    val note: String? = null,
) {
    init {
        require(lines.sumOf { it.amount } == 0L) { "Entry '$description' is unbalanced: $lines" }
    }

    /** Total that left or entered the wallet side (absolute, cents). */
    fun size(): Long = lines.filter { it.amount > 0 }.sumOf { it.amount }
}

/** What kind of M-Pesa event an SMS described. */
enum class TxKind(val outgoing: Boolean) {
    SEND_MONEY(true),
    PAYBILL(true),
    BUY_GOODS(true),
    WITHDRAW(true),
    AIRTIME(true),
    TO_MSHWARI(true),
    FULIZA_REPAY(true),
    RECEIVED(false),
    DEPOSIT(false),
    FROM_MSHWARI(false),
    FULIZA_DRAW(false),
    REVERSAL(false),
    /** Recognised as M-Pesa but not a known format; direction comes from the balance change. */
    UNKNOWN(false),
}
