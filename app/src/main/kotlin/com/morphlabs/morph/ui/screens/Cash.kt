package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.whenText
import com.morphlabs.morph.ui.theme.LocalMorph

/**
 * "Ksh 2,000 cash: what's it for?" Tap categories, type amounts, done. Your usual cash
 * categories are one tap away; whatever you don't spread stays in Cash.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashSpreadPanel(repo: MorphRepo, s: Snapshot, withdrawal: Entry, remaining: Long, onDone: (stars: Int) -> Unit) {
    val parts = remember(withdrawal.id) { mutableStateListOf<Pair<Account, String>>() }
    var more by remember(withdrawal.id) { mutableStateOf(false) }
    val cashId = remember(s) { s.role(com.morphlabs.morph.core.model.SystemRole.CASH) }
    // Categories you usually pay cash for, most used first.
    val usual = remember(s) {
        s.newestFirst.asSequence()
            .filter { e -> e.status != EntryStatus.NEEDS_REVIEW && e.lines.any { it.accountId == cashId && it.amount < 0 } }
            .mapNotNull { it.categoryId?.let { id -> s.byId[id] } }
            .filter { it.pickable && it.type == AccountType.EXPENSE }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            .take(6).toList()
            .ifEmpty { s.accounts.filter { it.hint in setOf("food", "transport", "groceries", "family") } }
    }
    val sum = parts.sumOf { Money.parse(it.second) ?: 0L }
    val left = remaining - sum

    Text("💵 ${Money.format(remaining)} cash", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    Text("Withdrawn ${whenText(withdrawal.timestamp, s.zone)}. What's it for? Whatever you leave stays in Cash.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Gap(10)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (a in usual.filter { u -> parts.none { it.first.id == u.id } }) {
            OutlinedButton(onClick = { parts.add(a to "") }, shape = RoundedCornerShape(14.dp)) { Text("+ ${a.label}") }
        }
        TextButton(onClick = { more = !more }) { Text(if (more) "Hide" else "Other…") }
    }
    if (more) SearchableAccounts(s, outgoing = true, exclude = parts.map { it.first.id }.toSet()) { a -> parts.add(a to ""); more = false }
    Gap(8)
    parts.forEachIndexed { i, (a, v) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(a.label, modifier = Modifier.weight(1f))
            AmountField(v, { parts[i] = a to it }, "", modifier = Modifier.weight(1f))
            TextButton(onClick = { parts.removeAt(i) }) { Text("✕") }
        }
    }
    if (parts.isNotEmpty()) {
        Text(
            if (left >= 0) "Stays in Cash: ${Money.format(left)}" else "${Money.format(-left)} more than the cash",
            color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else LocalMorph.current.bad,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (left > 0) TextButton(onClick = {
            val (a, v) = parts.last()
            parts[parts.lastIndex] = a to Money.format((Money.parse(v) ?: 0L) + left, withCurrency = false).replace(",", "")
        }) { Text("Put the rest on ${parts.last().first.name}") }
    }
    Gap(8)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = parts.isNotEmpty() && sum > 0 && left >= 0,
            onClick = {
                val list = parts.map { it.first.id to (Money.parse(it.second) ?: 0L) }.filter { it.second > 0 }
                Thread { repo.spreadCash(withdrawal.id, list) }.start()
                onDone(list.size)
            },
        ) { Text("Save +${parts.count { (Money.parse(it.second) ?: 0L) > 0 }}⭐") }
        TextButton(onClick = { Thread { repo.keepAsCash(withdrawal.id) }.start(); onDone(0) }) { Text("Keep as cash") }
    }
}

/** The cash panel as a dialog, for the transaction details and the Sort tab. */
@Composable
fun CashSpreadDialog(repo: MorphRepo, s: Snapshot, withdrawal: Entry, remaining: Long, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                CashSpreadPanel(repo, s, withdrawal, remaining) { onDismiss() }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
