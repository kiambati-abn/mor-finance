package com.morphlabs.morph.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.core.budget.EnvelopeBook
import com.morphlabs.morph.core.model.BudgetPeriod
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.Dashboard
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.theme.LocalMorph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Envelopes: every shilling has a job, and each envelope shows what's left. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EnvelopeModeScreen(repo: MorphRepo, d: Dashboard, book: EnvelopeBook, goAccounts: () -> Unit, startFilter: String = "all") {
    val s = d.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = remember(s) { book.envelopes(d.today) }
    val toAssign = remember(s) { book.toAssign(d.today) }
    var open by remember { mutableStateOf<EnvelopeBook.Envelope?>(null) }
    val find = remember { FindState().also { it.filter = startFilter } }
    val shown = list.filter { e -> find.matches(e.account, s.health(e.account.id, d.today)?.level?.warns == true || e.overspent) }
    fun spendable(e: EnvelopeBook.Envelope) = if (e.todayLeft != null) minOf(e.todayLeft!!, e.left) else e.left

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Envelopes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = goAccounts) { Text("Amounts") }
            }
            Gap(4)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp)) {
                val spend = list.filter { it.account.type == com.morphlabs.morph.core.model.AccountType.EXPENSE }
                val inEnv = spend.sumOf { maxOf(0L, it.left) }
                val put = spend.sumOf { maxOf(it.assigned, it.target) }
                if (put > 0) {
                    val pct = ((put - inEnv) * 100 / put).coerceIn(0, 100)
                    Text("${Money.format(inEnv)} left in spending envelopes · $pct% used", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Gap(8)
                }
                Text(if (toAssign >= 0) "To assign" else "Assigned more than you have", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(Money.format(toAssign), fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    if (toAssign > 0) "Money with no job yet. Give it one so it doesn't quietly disappear."
                    else if (toAssign == 0L) "Every shilling has a job. 🦋"
                    else "Take some back from an envelope, or wait for money to come in.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (toAssign > 0) {
                    Gap(8)
                    Button(onClick = {
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { repo.fillEnvelopes() }
                            Toast.makeText(ctx, if (n > 0) "Filled ${Money.format(n)} into your envelopes" else "Your envelopes are already full. Set amounts under Amounts.", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Fill my envelopes") }
                }
            }
        }
        if (list.isEmpty()) item {
            Gap(16)
            MorphCard(onClick = goAccounts) {
                Column(Modifier.padding(20.dp)) {
                    Text("No envelopes yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Give accounts a daily or monthly amount (Food 300 a day, Friends 2,000 a month), then tap Fill my envelopes.")
                }
            }
        }
        if (list.isNotEmpty()) stickyHeader(key = "find") { FindBar(find) }
        if (list.isNotEmpty() && shown.isEmpty()) item { Text("Nothing matches.", modifier = Modifier.padding(16.dp)) }
        // While checking an amount, the envelopes that can afford it come first.
        val ordered = find.affordCents?.let { amt -> shown.sortedByDescending { spendable(it) - amt } } ?: shown
        val groups = if (find.affordCents != null) mapOf("Can it cover ${Money.format(find.affordCents!!)}?" to ordered) else ordered.groupBy { it.account.typeLabel }
        for ((label, envs) in groups) {
            item(key = "h$label") { SectionTitle(label) }
            items(envs, key = { it.account.id }) { e -> EnvelopeCard(e, find.affordCents?.let { spendable(e) to it }) { open = e } }
        }
        item { Gap(40) }
    }
    open?.let { e -> EnvelopeDialog(repo, s, e, list, toAssign) { open = null } }
}

@Composable
private fun EnvelopeCard(e: EnvelopeBook.Envelope, afford: Pair<Long, Long>? = null, onClick: () -> Unit) {
    val daily = e.account.period == BudgetPeriod.DAILY
    val level = if (e.account.type == com.morphlabs.morph.core.model.AccountType.EXPENSE) {
        if (daily) com.morphlabs.morph.core.budget.Health.level(minOf(e.todayLeft ?: e.left, e.left), e.account.budget)
        else com.morphlabs.morph.core.budget.Health.level(e.left, maxOf(e.assigned, e.target))
    } else com.morphlabs.morph.core.budget.Level.OK
    MorphCard(Modifier.padding(bottom = 8.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Text(e.account.label, fontWeight = FontWeight.SemiBold)
            afford?.let { (left, amt) -> AffordLine(left, amt) }
            Gap(6)
            val spending = e.account.type == com.morphlabs.morph.core.model.AccountType.EXPENSE
            if (daily && e.todayLeft != null) {
                val todayLeft = minOf(e.todayLeft!!, e.left)
                com.morphlabs.morph.ui.components.SpentLeft(e.account.budget - todayLeft, todayLeft, e.account.budget, level, period = "today")
                Text("${Money.format(e.left)} in envelope for the rest of the month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else com.morphlabs.morph.ui.components.SpentLeft(
                e.used, e.left, maxOf(e.assigned, e.target), level,
                spentWord = if (spending) "Spent" else if (e.account.group == com.morphlabs.morph.core.model.AccountGroup.DEBT) "Repaid" else "Saved",
                leftWord = if (spending) "Left" else "In envelope",
            )
        }
    }
}

@Composable
private fun EnvelopeDialog(repo: MorphRepo, s: Snapshot, e: EnvelopeBook.Envelope, all: List<EnvelopeBook.Envelope>, toAssign: Long, onDismiss: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0 add, 1 take back, 2 move to another
    var amount by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<EnvelopeBook.Envelope?>(null) }
    var editing by remember { mutableStateOf(false) }
    val openList = com.morphlabs.morph.ui.LocalOpenList.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.account.label) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("${Money.format(e.left)} left", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Put in ${Money.format(e.assigned)} · used ${Money.format(e.used)}", style = MaterialTheme.typography.bodySmall)
                Gap()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Add") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Take back") })
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Move") })
                }
                Gap(8)
                AmountField(amount, { amount = it }, when (mode) { 0 -> "From To assign (${Money.format(toAssign)})"; 1 -> "Back to To assign"; else -> "Move to another envelope" })
                if (e.overspent && mode == 0) TextButton(onClick = { amount = Money.format(-e.left, withCurrency = false).replace(",", "") }) { Text("Cover the ${Money.format(-e.left)} overspent") }
                if (mode == 2) {
                    Gap(6)
                    for (o in all.filter { it.account.id != e.account.id }) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { target = o }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text((if (target?.account?.id == o.account.id) "● " else "○ ") + o.account.label, modifier = Modifier.weight(1f))
                            Text("${Money.format(o.left)} left", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Gap(8)
                Row {
                    TextButton(onClick = { onDismiss(); openList(com.morphlabs.morph.ui.TxFilter(e.account.label, accountIds = setOf(e.account.id))) }) { Text("See transactions") }
                    TextButton(onClick = { editing = true }) { Text("Change amount") }
                }
            }
        },
        confirmButton = {
            val cents = Money.parse(amount)
            TextButton(enabled = cents != null && cents > 0 && (mode != 2 || target != null), onClick = {
                val id = e.account.id
                val to = target?.account?.id
                Thread {
                    when (mode) {
                        0 -> repo.assign(id, cents!!)
                        1 -> repo.assign(id, -cents!!)
                        else -> repo.moveBetweenEnvelopes(id, to!!, cents!!)
                    }
                }.start()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (editing) AccountEditDialog(repo, s, e.account) { editing = false }
}

/** Shown on the Budgets tab while envelopes are off. */
@Composable
fun EnvelopesPitch(repo: MorphRepo) {
    MorphCard(Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("✉️ Try envelopes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Give every shilling a job. Put real money into envelopes (Friends 2,000) and Morph shows what's left as you spend: " +
                    "\"Friends: Ksh 100 left\". Leftovers carry over. Your daily/monthly amounts below become the fill amounts.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { Thread { repo.setEnvelopes(true) }.start() }) { Text("Turn on envelopes") }
        }
    }
}
