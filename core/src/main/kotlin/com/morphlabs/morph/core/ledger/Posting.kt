package com.morphlabs.morph.core.ledger

import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.Line
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.model.TxKind
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.core.sms.MpesaSms

/**
 * Turns an M-Pesa SMS into a balanced journal entry. The M-Pesa wallet side and fees are always
 * known; the "what was it" side is either a fixed account (withdrawals go to Cash), the account
 * the learner picked, or Uncategorized until the user taps a choice.
 */
object Posting {

    /** Kinds where Morph already knows the other side, so the user is never asked. */
    fun fixedRole(kind: TxKind): SystemRole? = when (kind) {
        TxKind.WITHDRAW, TxKind.DEPOSIT -> SystemRole.CASH
        TxKind.TO_MSHWARI, TxKind.FROM_MSHWARI -> SystemRole.MSHWARI
        TxKind.FULIZA_REPAY, TxKind.FULIZA_DRAW -> SystemRole.FULIZA
        else -> null
    }

    fun needsCategory(kind: TxKind): Boolean = fixedRole(kind) == null

    /**
     * @param categoryId account chosen for the "what was it" side; null leaves it in Uncategorized.
     * @param outgoing only used for [TxKind.UNKNOWN], where direction comes from the balance change.
     */
    fun fromSms(
        sms: MpesaSms,
        timestamp: Long,
        role: (SystemRole) -> Long,
        categoryId: Long?,
        status: EntryStatus,
        outgoing: Boolean = sms.outgoing,
    ): Entry {
        val mpesa = role(SystemRole.MPESA)
        val other = fixedRole(sms.kind)?.let(role) ?: categoryId ?: role(SystemRole.UNCATEGORIZED)
        val finalStatus = when {
            fixedRole(sms.kind) != null -> EntryStatus.CONFIRMED
            categoryId == null -> EntryStatus.NEEDS_REVIEW
            else -> status
        }
        val lines = when {
            sms.kind == TxKind.FULIZA_DRAW -> listOfNotNull(
                Line(mpesa, sms.amount),
                sms.fee.takeIf { it > 0 }?.let { Line(role(SystemRole.FULIZA_CHARGES), it) },
                Line(other, -(sms.amount + sms.fee)),
            )
            outgoing -> {
                val feeRole = if (sms.kind == TxKind.WITHDRAW) SystemRole.WITHDRAWAL_CHARGES else SystemRole.TRANSACTION_COSTS
                listOfNotNull(
                    Line(other, sms.amount),
                    sms.fee.takeIf { it > 0 }?.let { Line(role(feeRole), it) },
                    Line(mpesa, -(sms.amount + sms.fee)),
                )
            }
            else -> listOfNotNull(
                Line(mpesa, sms.amount - sms.fee),
                sms.fee.takeIf { it > 0 }?.let { Line(role(SystemRole.TRANSACTION_COSTS), it) },
                Line(other, -sms.amount),
            )
        }
        return Entry(
            timestamp = timestamp,
            description = describe(sms, outgoing),
            lines = lines.filter { it.amount != 0L }.ifEmpty { lines },
            status = finalStatus,
            source = EntrySource.SMS,
            counterparty = sms.counterparty,
            counterpartyKey = sms.counterpartyKey,
            kind = sms.kind,
            code = sms.code,
            smsBody = sms.raw,
        )
    }

    /** A simple two-line entry, e.g. cash spent on lunch: money leaves [fromId] and lands in [toId]. */
    fun manual(timestamp: Long, description: String, amount: Long, fromId: Long, toId: Long): Entry =
        Entry(
            timestamp = timestamp,
            description = description,
            lines = listOf(Line(toId, amount), Line(fromId, -amount)),
            status = EntryStatus.CONFIRMED,
            source = EntrySource.MANUAL,
            counterparty = description,
            counterpartyKey = "manual:" + description.trim().uppercase(),
        )

    /** Sets an account's starting balance (positive = you have it / you owe it). */
    fun opening(timestamp: Long, accountId: Long, debitNormal: Boolean, amount: Long, role: (SystemRole) -> Long): Entry {
        val signed = if (debitNormal) amount else -amount
        return Entry(
            timestamp = timestamp,
            description = "Opening balance",
            lines = listOf(Line(accountId, signed), Line(role(SystemRole.OPENING_BALANCE), -signed)),
            status = EntryStatus.CONFIRMED,
            source = EntrySource.SYSTEM,
        )
    }

    /** Re-points the Uncategorized (or previous category) line to [newAccountId]. Wallet and fee lines stay as they are. */
    fun recategorize(entry: Entry, oldAccountId: Long, newAccountId: Long, status: EntryStatus = EntryStatus.CONFIRMED): Entry =
        entry.copy(
            lines = entry.lines.map { if (it.accountId == oldAccountId) it.copy(accountId = newAccountId) else it },
            status = status,
        )

    fun describe(sms: MpesaSms, outgoing: Boolean = sms.outgoing): String {
        val who = sms.counterparty?.let(::titleCase)
        return when (sms.kind) {
            TxKind.SEND_MONEY -> "Sent to $who"
            TxKind.PAYBILL -> "Paid $who" + (sms.accountRef?.let { " · acc $it" } ?: "")
            TxKind.BUY_GOODS -> "Paid $who"
            TxKind.WITHDRAW -> "Cash withdrawal" + (who?.let { " · $it" } ?: "")
            TxKind.AIRTIME -> "Airtime" + (sms.accountRef?.let { " for $it" } ?: "")
            TxKind.TO_MSHWARI -> "Moved to M-Shwari"
            TxKind.FROM_MSHWARI -> "Moved from M-Shwari"
            TxKind.FULIZA_REPAY -> "Fuliza repaid"
            TxKind.FULIZA_DRAW -> "Fuliza used · fee ${Money.format(sms.fee)}"
            TxKind.RECEIVED -> "Received from $who"
            TxKind.DEPOSIT -> "Cash deposit" + (who?.let { " · $it" } ?: "")
            TxKind.REVERSAL -> "Reversal of ${sms.reversedCode}"
            TxKind.UNKNOWN -> if (outgoing) "M-Pesa payment" else "M-Pesa money in"
        }
    }

    fun titleCase(s: String): String =
        if (s.any(Char::isLowerCase) && s.any(Char::isUpperCase)) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercaseChar) }
}
