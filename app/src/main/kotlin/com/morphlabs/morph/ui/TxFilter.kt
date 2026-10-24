package com.morphlabs.morph.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.data.Snapshot

/**
 * "Show me the transactions behind this number." Every tappable total, bar, row and insight
 * opens the transaction list with one of these.
 */
data class TxFilter(
    val title: String,
    /** Only transactions touching one of these accounts. Empty = any. */
    val accountIds: Set<Long> = emptySet(),
    val from: Long? = null,
    val to: Long? = null,
    val counterpartyKey: String? = null,
    /** Only spending (a line on an expense account). */
    val spendingOnly: Boolean = false,
) {
    fun matches(e: Entry, s: Snapshot): Boolean {
        if (e.source == EntrySource.SYSTEM) return false
        if (from != null && e.timestamp < from) return false
        if (to != null && e.timestamp >= to) return false
        if (counterpartyKey != null && e.counterpartyKey != counterpartyKey) return false
        if (accountIds.isNotEmpty() && e.lines.none { it.accountId in accountIds }) return false
        if (spendingOnly && e.lines.none { it.amount > 0 && s.byId[it.accountId]?.type == AccountType.EXPENSE }) return false
        return true
    }

    /** The total this list stands for: spending on the chosen accounts (or all spending). */
    fun total(list: List<Entry>, s: Snapshot): Long = list.sumOf { e ->
        e.lines.filter { l ->
            if (accountIds.isNotEmpty()) l.accountId in accountIds
            else s.byId[l.accountId]?.type == AccountType.EXPENSE
        }.sumOf { it.amount }
    }.let { if (accountIds.isNotEmpty() && accountIds.all { s.byId[it]?.type?.debitNormal == false }) -it else it }
}

/** Opens the transaction list for a filter, from anywhere in the app. */
val LocalOpenList = staticCompositionLocalOf<(TxFilter) -> Unit> { {} }
