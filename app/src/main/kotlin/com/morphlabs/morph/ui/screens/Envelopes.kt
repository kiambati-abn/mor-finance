package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.budget.Envelope
import com.morphlabs.morph.core.model.AccountType
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.ui.Dashboard
import com.morphlabs.morph.ui.components.EnvelopeBar
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.theme.LocalMorph

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EnvelopesScreen(repo: MorphRepo, d: Dashboard, goAccounts: () -> Unit, startFilter: String = "all") {
    val s = d.s
    s.book?.let { book -> EnvelopeModeScreen(repo, d, book, goAccounts, startFilter); return }
    var daily by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Envelope?>(null) }
    var moving by remember { mutableStateOf<Envelope?>(null) }
    val period = if (daily) BudgetPeriod.DAILY else BudgetPeriod.MONTHLY
    val find = remember { FindState().also { it.filter = startFilter } }
    val all = d.envelopes.filter { it.period == period }
    val list = all.filter { e -> find.matches(e.account, e.isLimit && com.morphlabs.morph.core.budget.Health.level(e.left, e.available).warns) }
        .let { l -> find.affordCents?.let { amt -> l.sortedByDescending { it.left - amt } } ?: l }
    val limits = list.filter { it.isLimit }
    val targets = list.filter { !it.isLimit }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = goAccounts) { Text("Set up") }
            }
            EnvelopesPitch(repo)
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !daily, onClick = { daily = false }, label = { Text("This month") })
                FilterChip(selected = daily, onClick = { daily = true }, label = { Text("Today") })
            }
            if (limits.isNotEmpty()) {
                val used = limits.sumOf { it.used }
                val avail = limits.sumOf { it.available }
                Gap()
                MorphCard {
                    Column(Modifier.padding(16.dp)) {
                        val level = com.morphlabs.morph.core.budget.Health.level(avail - used, avail)
                        val pct = if (avail > 0) (used * 100 / avail) else 0
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (used <= avail) Money.format(avail - used) else "−" + Money.format(used - avail),
                                fontSize = 28.sp, fontWeight = FontWeight.Black,
                                color = com.morphlabs.morph.ui.components.levelColor(level),
                            )
                            Text(if (used <= avail) "  left ${if (daily) "today" else "this month"}" else "  over", style = MaterialTheme.typography.bodyMedium)
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (avail > 0) (used.toFloat() / avail).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                            color = com.morphlabs.morph.ui.components.levelColor(level), trackColor = LocalMorph.current.track, drawStopIndicator = {},
                        )
                        Text("Spent ${Money.format(used)} of ${Money.format(avail)} · $pct% used", style = MaterialTheme.typography.bodySmall)
                        d.safeToSpend?.let { Text("Safe to spend today: ${Money.format(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (all.isNotEmpty()) stickyHeader(key = "find") { FindBar(find) }
        if (all.isNotEmpty() && list.isEmpty()) item { Text("Nothing matches.", modifier = Modifier.padding(16.dp)) }
        if (all.isEmpty()) item {
            Gap(24)
            MorphCard(onClick = goAccounts) {
                Column(Modifier.padding(20.dp)) {
                    Text("No ${if (daily) "daily" else "monthly"} budgets yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Gap(4)
                    Text("Tap to give accounts an amount: a limit for spending (Food Ksh 300 a day), a target for savings (Chama Ksh 2,000 a month) or a repayment plan for debts (HELB).")
                }
            }
        }
        if (limits.isNotEmpty()) item { SectionTitle("Spending limits") }
        items(limits, key = { "l" + it.account.id }) { e -> EnvelopeRow(e, { editing = e }, { moving = e }, find.affordCents) }
        if (targets.isNotEmpty()) item { SectionTitle("Savings, debts & income targets") }
        items(targets, key = { "t" + it.account.id }) { e -> EnvelopeRow(e, { editing = e }, null) }
        item { Gap(40) }
    }
    editing?.let { e -> AccountEditDialog(repo, s, e.account) { editing = null } }
    moving?.let { e -> MoveBudgetDialog(repo, s, e.account, if (daily) d.dayKey else d.monthKey, d.envelopes) { moving = null } }
}

@Composable
private fun EnvelopeRow(e: Envelope, onEdit: () -> Unit, onMove: (() -> Unit)?, afford: Long? = null) {
    val m = LocalMorph.current
    MorphCard(Modifier.padding(bottom = 8.dp), onClick = onEdit) {
        Column(Modifier.padding(14.dp)) {
            Text(e.account.label, fontWeight = FontWeight.SemiBold)
            if (e.isLimit) AffordLine(e.left, afford)
            Gap(6)
            // Spent | Left, the same two numbers everywhere a budget shows.
            com.morphlabs.morph.ui.components.SpentLeft(
                e.used, e.left, e.available,
                if (e.isLimit) com.morphlabs.morph.core.budget.Health.level(e.left, e.available) else com.morphlabs.morph.core.budget.Level.OK,
                spentWord = when (e.account.type) { AccountType.EXPENSE -> "Spent"; AccountType.LIABILITY -> "Repaid"; AccountType.INCOME -> "Received"; else -> "Saved" },
                leftWord = if (e.isLimit) "Left" else "To go",
                period = if (e.period == com.morphlabs.morph.core.model.BudgetPeriod.DAILY) "today" else "this month",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (e.reached) "Target reached 🎉" else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = m.good,
                )
                if (e.moved != 0L) Text("moved ${if (e.moved > 0) "+" else ""}${Money.format(e.moved, withCurrency = false)}  ", style = MaterialTheme.typography.bodySmall)
                val openList = com.morphlabs.morph.ui.LocalOpenList.current
                TextButton(onClick = { openList(com.morphlabs.morph.ui.TxFilter(e.account.label, accountIds = setOf(e.account.id))) }) { Text("History") }
                if (onMove != null) TextButton(onClick = onMove) { Text("Move in") }
            }
        }
    }
}
