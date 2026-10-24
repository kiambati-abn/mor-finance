package com.morphlabs.morph.core.ledger

import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.Entry

object Balances {

    /** Raw debit-minus-credit per account. */
    fun raw(entries: Iterable<Entry>): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (e in entries) for (l in e.lines) out[l.accountId] = (out[l.accountId] ?: 0L) + l.amount
        return out
    }

    /** Balance the way a person reads it: what you have (assets), owe (debts), spent (expenses), earned (income). */
    fun natural(account: Account, raw: Map<Long, Long>): Long {
        val r = raw[account.id] ?: 0L
        return if (account.type.debitNormal) r else -r
    }

    data class NetWorth(val assets: Long, val liabilities: Long) {
        val net: Long get() = assets - liabilities
    }

    fun netWorth(accounts: List<Account>, raw: Map<Long, Long>): NetWorth = NetWorth(
        assets = accounts.filter { it.type == AccountType.ASSET }.sumOf { natural(it, raw) },
        liabilities = accounts.filter { it.type == AccountType.LIABILITY }.sumOf { natural(it, raw) },
    )
}
