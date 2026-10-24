package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Column
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

/** Every account, its balance and its budget. Tap to edit; everything is yours to define. */
@Composable
fun AccountsScreen(repo: MorphRepo, s: Snapshot, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Account?>(null) }
    val openList = com.morphlabs.morph.ui.LocalOpenList.current
    var creating by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackHeader("Accounts & budgets", onBack) { TextButton(onClick = { creating = true }) { Text("+ Add") } }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            val sections = AccountGroup.entries.filter { it != AccountGroup.SYSTEM }.flatMap { g ->
                s.accounts.filter { it.group == g && (showArchived || !it.archived) }.groupBy { it.typeLabel }.toList()
            }
            for ((label, list) in sections) {
                item(key = "h$label") { SectionTitle(label) }
                items(list, key = { it.id }) { a ->
                    MorphCard(Modifier.padding(bottom = 6.dp), onClick = { openList(com.morphlabs.morph.ui.TxFilter(a.label, accountIds = setOf(a.id))) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.label + if (a.archived) " (archived)" else "", fontWeight = FontWeight.SemiBold)
                                if (a.period != BudgetPeriod.NONE && a.budget > 0) Text(
                                    "${Money.format(a.budget)} ${if (a.period == BudgetPeriod.DAILY) "a day" else "a month"}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                s.health(a.id, java.time.LocalDate.now(s.zone))?.let { h ->
                                    com.morphlabs.morph.ui.components.LevelPill(h.level)
                                }
                            }
                            Text(
                                if (s.budgetView && a.group == AccountGroup.WALLET) "••••" else Money.format(s.balance(a)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { editing = a }) { Text("Edit") }
                        }
                    }
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
