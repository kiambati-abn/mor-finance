package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountGroup
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.SectionTitle

@Composable
fun BackHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Every account in one place: search, filter by type, and see at a glance what each budget has
 * left this month (or today). Tap an account for its transactions; Edit changes it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(repo: MorphRepo, s: Snapshot, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Account?>(null) }
    val openList = com.morphlabs.morph.ui.LocalOpenList.current
    var creating by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("all") }
    val today = remember(s) { repo.dayOf(System.currentTimeMillis()) }
    val monthStart = today.withDayOfMonth(1).atStartOfDay(s.zone).toInstant().toEpochMilli()
    val tomorrow = today.plusDays(1).atStartOfDay(s.zone).toInstant().toEpochMilli()
    // This month's activity per account, worked out once.
    val month = remember(s) {
        val out = HashMap<Long, Long>()
        for (e in s.entries) if (e.timestamp in monthStart until tomorrow && e.source != com.morphlabs.morph.core.model.EntrySource.SYSTEM)
            for (l in e.lines) out[l.accountId] = (out[l.accountId] ?: 0L) + l.amount
        out
    }
    val types = listOf("all" to "All", "spend" to "Spending", "save" to "Savings", "debt" to "Debts", "wallet" to "Wallets", "income" to "Income", "budget" to "With budget")
    val needle = query.trim().lowercase()
    val shown = s.accounts.filter { a ->
        a.group != AccountGroup.SYSTEM && (showArchived || !a.archived) &&
            a.matches(needle) &&
            when (type) {
                "spend" -> a.type == com.morphlabs.morph.core.model.AccountType.EXPENSE
                "save" -> a.group == AccountGroup.SAVINGS || a.group == AccountGroup.OWED_TO_ME
                "debt" -> a.group == AccountGroup.DEBT
                "wallet" -> a.group == AccountGroup.WALLET
                "income" -> a.group == AccountGroup.INCOME
                "budget" -> a.period != BudgetPeriod.NONE && a.budget > 0
                else -> true
            }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackHeader("Accounts", onBack) {
            com.morphlabs.morph.ui.EyeToggle(s)
            TextButton(onClick = { creating = true }) { Text("+ Add") }
        }
        val budgetMode = com.morphlabs.morph.ui.budgetMode(s)
        if (budgetMode) Text("Budget view: showing what's left in each budget. Tap 👁 for real balances.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            androidx.compose.material3.OutlinedTextField(
                query, { query = it }, singleLine = true, label = { Text("🔍 Find an account") }, keyboardOptions = com.morphlabs.morph.ui.components.DoneKeys,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                for ((k, l) in types) androidx.compose.material3.FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) })
            }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            // While searching, one flat list; otherwise grouped by type.
            val sections = if (needle.isNotEmpty()) listOf("Results" to shown)
            else AccountGroup.entries.filter { it != AccountGroup.SYSTEM }.flatMap { g -> shown.filter { it.group == g }.groupBy { it.typeLabel }.toList() }
            if (shown.isEmpty()) item { Text("Nothing matches.", modifier = Modifier.padding(16.dp)) }
            for ((label, list) in sections) {
                item(key = "h$label") { SectionTitle(label) }
                items(list, key = { it.id }) { a ->
                    AccountRow(s, a, today, month[a.id] ?: 0L, budgetMode,
                        onOpen = { openList(com.morphlabs.morph.ui.TxFilter(a.label, accountIds = setOf(a.id))) },
                        onEdit = { editing = a })
                }
            }
            item {
                TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Hide archived" else "Show archived") }
            }
        }
    }
    editing?.let { a -> AccountEditDialog(repo, s, a) { editing = null } }
    if (creating) AccountEditDialog(repo, s, null) { creating = false }
}

/**
 * One account. With a spending budget: "Ksh 700 left" in its traffic-light colour, a thin bar and
 * "Spent Ksh 1,300 of 2,000 · 65%". Otherwise its balance, or what moved this month.
 */
@Composable
private fun AccountRow(s: Snapshot, a: Account, today: java.time.LocalDate, monthNet: Long, budgetMode: Boolean, onOpen: () -> Unit, onEdit: () -> Unit) {
    // Budget view shows budget amounts; real view shows real money (spent this month, balances).
    val h = if (budgetMode || !s.budgetView) s.health(a.id, today) else null
    val envelope = if (budgetMode) s.envelopesOn(today).firstOrNull { it.account.id == a.id } else null
    val m = com.morphlabs.morph.ui.theme.LocalMorph.current
    MorphCard(Modifier.padding(bottom = 6.dp), onClick = onOpen) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label + if (a.archived) " (archived)" else "", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val (big, small) = when {
                    // Anything with a budget shows its two numbers (spent | left) below instead.
                    h != null || (envelope != null && envelope.target > 0) || (a.period != BudgetPeriod.NONE && a.budget > 0) -> "" to ""
                    envelope != null -> Money.format(envelope.left) to "in envelope"
                    budgetMode && a.group == AccountGroup.WALLET -> "••••" to "in envelopes"
                    a.type == com.morphlabs.morph.core.model.AccountType.EXPENSE -> Money.format(monthNet) to "spent this month"
                    a.type == com.morphlabs.morph.core.model.AccountType.INCOME -> Money.format(-monthNet) to "in this month"
                    else -> Money.format(s.balance(a)) to (if (a.group == AccountGroup.DEBT) "owed" else "balance")
                }
                if (big.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
                    Text(big, fontWeight = FontWeight.Bold)
                    Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
            if (h != null && h.available > 0) {
                Gap(6)
                com.morphlabs.morph.ui.components.SpentLeft(h.available - h.left, h.left, h.available, h.level,
                    period = if (a.period == BudgetPeriod.DAILY) "today" else "this month")
            } else if (envelope != null && envelope.target > 0) {
                Gap(6)
                val saving = a.type != com.morphlabs.morph.core.model.AccountType.EXPENSE
                com.morphlabs.morph.ui.components.SpentLeft(envelope.used, envelope.left, maxOf(envelope.assigned, envelope.target),
                    com.morphlabs.morph.core.budget.Level.OK,
                    spentWord = if (a.group == AccountGroup.DEBT) "Repaid" else if (saving) "Saved" else "Spent", leftWord = if (saving) "In envelope" else "Left")
            } else if (a.period != BudgetPeriod.NONE && a.budget > 0) {
                val expense = a.type == com.morphlabs.morph.core.model.AccountType.EXPENSE
                val used = (if (a.type == com.morphlabs.morph.core.model.AccountType.INCOME) -monthNet else monthNet).coerceAtLeast(0)
                Gap(6)
                com.morphlabs.morph.ui.components.SpentLeft(used, a.budget - used, a.budget,
                    if (expense) com.morphlabs.morph.core.budget.Health.level(a.budget - used, a.budget) else com.morphlabs.morph.core.budget.Level.OK,
                    spentWord = when { expense -> "Spent"; a.group == AccountGroup.DEBT -> "Repaid"; a.group == AccountGroup.INCOME -> "Received"; else -> "Saved" },
                    leftWord = if (expense) "Left" else "To go",
                    period = "this month")
            }
        }
    }
}
