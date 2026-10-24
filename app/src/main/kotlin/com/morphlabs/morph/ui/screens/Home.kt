package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.R
import com.morphlabs.morph.core.insight.Insight
import com.morphlabs.morph.core.insight.Tone
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.ui.Dashboard
import com.morphlabs.morph.ui.LocalOpenList
import com.morphlabs.morph.ui.TxFilter
import com.morphlabs.morph.sms.InboxImporter
import com.morphlabs.morph.ui.components.BarChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.morphlabs.morph.ui.components.EntryRow
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.Pill
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.components.StatTile
import com.morphlabs.morph.ui.theme.LocalMorph

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repo: MorphRepo, d: Dashboard, goSort: () -> Unit, goMe: () -> Unit, goAll: () -> Unit, goPatterns: () -> Unit, goBudgets: () -> Unit, goLowBudgets: () -> Unit = goBudgets, goAccounts: () -> Unit = {}) {
    val s = d.s
    var open by remember { mutableStateOf<Entry?>(null) }
    val openList = LocalOpenList.current
    fun days(title: String, fromDaysBack: Long, toDaysBack: Long = -1, accounts: Set<Long> = emptySet()) =
        openList(TxFilter(title, accountIds = accounts, spendingOnly = accounts.isEmpty(),
            from = d.today.minusDays(fromDaysBack).atStartOfDay(s.zone).toInstant().toEpochMilli(),
            to = d.today.minusDays(toDaysBack).atStartOfDay(s.zone).toInstant().toEpochMilli()))
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { InboxImporter.refresh(ctx) }
            refreshing = false
            android.widget.Toast.makeText(ctx, if (r.added > 0) "Found ${r.added} new" else "Up to date ✓ (${r.known} already in Morph)", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh() }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Header(d, refreshing) { refresh() } }
        item { Hero(d) { days("Spent today", 0) } }
        item {
            // One tap to any account and what its budget has left.
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = goAccounts)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍  Find an account…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("🗂️ Accounts", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        val warnings = s.warnings(d.today)
        if (warnings.isNotEmpty()) item {
            Gap()
            MorphCard(onClick = goLowBudgets) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(warnings.first().level.icon, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${warnings.size} budget${if (warnings.size > 1) "s" else ""} running low", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            warnings.take(3).joinToString(" · ") { w -> "${s.byId[w.accountId]?.label} ${if (w.left < 0) "over ${Money.format(-w.left)}" else "${Money.format(w.left)} left"}" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("See →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if ((d.toAssign ?: 0) > 0) item {
            Gap()
            MorphCard(onClick = goBudgets) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✉️", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${Money.format(d.toAssign ?: 0)} to assign", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Give it a job before it disappears.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Fill →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (s.pending.isNotEmpty()) item {
            Gap()
            MorphCard(onClick = goSort) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.pending.size} to sort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("One tap each. Fresh ones earn bonus stars.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Go →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (s.mpesaGap != 0L) item {
            Gap()
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Text("⚠️ M-Pesa doesn't match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Off by ${Money.format(kotlin.math.abs(s.mpesaGap))}. Usually a message that hasn't arrived yet, or one that was deleted. " +
                            "Morph fixes this on its own after 30 minutes; tap to do it now.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { Thread { repo.fixMpesaGap() }.start() }) { Text("Match M-Pesa now") }
                }
            }
        }
        item {
            SectionTitle("Spending")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Today", d.spentToday, Modifier.weight(1f)) { days("Spent today", 0) }
                StatTile("Yesterday", d.spentYesterday, Modifier.weight(1f)) { days("Spent yesterday", 1, 0) }
                StatTile("7 days", d.spent7, Modifier.weight(1f)) { days("Last 7 days", 6) }
                StatTile("30 days", d.spent30, Modifier.weight(1f)) { days("Last 30 days", 29) }
            }
            if (d.avgDay > 0) {
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Avg / day", d.avgDay, Modifier.weight(1f)) { days("Last ${d.avgBasis} days", d.avgBasis - 1) }
                    StatTile("Avg / week", d.avgWeek, Modifier.weight(1f)) { days("Last ${d.avgBasis} days", d.avgBasis - 1) }
                    StatTile("Avg / month", d.avgMonth, Modifier.weight(1f)) { days("Last ${d.avgBasis} days", d.avgBasis - 1) }
                }
                Text(
                    "Averages over the last ${d.avgBasis} days. " + when {
                        d.spentToday > d.avgDay * 3 / 2 -> "Today is well above your usual day."
                        d.spentToday in 1..d.avgDay -> "Today is below your usual day. 👍"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            val bars = remember(s) { com.morphlabs.morph.core.insight.Patterns(s.accounts, s.entries, s.zone).daily(d.today) }
            if (bars.any { it.total > 0 }) {
                SectionTitle("Last 14 days", action = "Patterns", onAction = goPatterns)
                MorphCard {
                    Column(Modifier.padding(16.dp)) {
                        BarChart(
                            items = bars.map { (if (it.date == d.today) "Today" else it.date.dayOfWeek.name.take(1)) to it.total },
                            marks = bars.indices.filter { bars[it].spike }.toSet(),
                            height = 100,
                            showLabel = { i -> i == bars.lastIndex || i % 2 == 1 },
                        ) { i ->
                            DayWhy(bars[i])
                            if (bars[i].total > 0) androidx.compose.material3.TextButton(onClick = {
                                val back = java.time.temporal.ChronoUnit.DAYS.between(bars[i].date, d.today)
                                days(dayFmt.format(bars[i].date), back, back - 1)
                            }) { Text("See that day's transactions →") }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Where it went", action = "Patterns", onAction = goPatterns)
            MorphCard { Column(Modifier.padding(16.dp)) { com.morphlabs.morph.ui.components.SpendingExplorer(s, d.today) } }
        }
        if (repo.backupUri == null && s.entries.size > 20) item {
            Gap()
            MorphCard(onClick = goMe) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("☁️", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Turn on automatic backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("If you lose this phone, your history comes back with one file.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        d.insights.firstOrNull()?.let { ins ->
            item {
                SectionTitle("Today's insight", action = "All", onAction = goMe)
                InsightCard(ins, s)
            }
        }
        item { SectionTitle("Recent", action = "All", onAction = goAll) }
        items(s.newestFirst.filter { it.source != com.morphlabs.morph.core.model.EntrySource.SYSTEM }.take(8), key = { it.id }) { e ->
            EntryRow(e, s) { open = e }
        }
        item { Gap(88) }
    }
    }
    open?.let { e -> EntryDialog(repo, s, e) { open = null } }
}

@Composable
private fun Header(d: Dashboard, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.morph_wing), contentDescription = null, modifier = Modifier.height(36.dp))
        Spacer(Modifier.width(8.dp))
        Text("Morph", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(
            if (refreshing) "⏳" else "↻", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onRefresh).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        if (d.streak > 0) Pill("🔥 ${d.streak}")
        Spacer(Modifier.width(6.dp))
        Pill("${d.level.emoji} ${d.stars}⭐")
    }
}

@Composable
private fun Hero(d: Dashboard, onToday: () -> Unit) {
    val m = LocalMorph.current
    val revealState = com.morphlabs.morph.ui.LocalReveal.current
    val reveal = revealState.value
    val hidden = com.morphlabs.morph.ui.budgetMode(d.s)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(m.hero).clickable(onClick = onToday).padding(20.dp)
    ) {
        Text("Spent today ›", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        Text(Money.format(d.spentToday), color = m.onHero, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Gap(4)
        val safe = d.envelopeToday ?: d.safeToSpend
        Text(
            if (safe != null) "Safe to spend today: ${Money.format(safe)}" else "Set budgets to see what's safe to spend",
            color = m.gold, fontWeight = FontWeight.SemiBold,
        )
        Gap(16)
        Row(verticalAlignment = Alignment.Bottom) {
            if (hidden) {
                Column(Modifier.weight(1f)) {
                    Text("✉️ In envelopes", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(d.envelopeLeft ?: 0), color = m.onHero, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("To assign", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(d.toAssign ?: 0), color = m.onHero, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("📱 M-Pesa", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(d.mpesaBalance), color = m.onHero, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Net worth", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(d.netWorth.net), color = m.onHero, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("In this month", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(if (hidden) "••••" else Money.format(d.receivedThisMonth), color = m.onHero, fontWeight = FontWeight.Bold)
            }
            if (d.s.budgetView) Text(
                if (reveal) "🙈" else "👁", fontSize = 22.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { revealState.value = !revealState.value }.padding(6.dp),
            )
        }
    }
}

private val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH)

/** The "why" under a day's bar: what that day's money went on. */
@Composable
private fun DayWhy(b: com.morphlabs.morph.core.insight.Patterns.DayBar) {
    Text(
        (if (b.spike) "● Big day · " else "") + dayFmt.format(b.date),
        style = MaterialTheme.typography.labelMedium,
        color = if (b.spike) LocalMorph.current.gold else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    if (b.drivers.isEmpty()) Text("No spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Text(
        "Why: " + b.drivers.joinToString(" · ") { dr ->
            "${dr.account.emoji} ${dr.account.name} ${Money.format(dr.amount)}" +
                (dr.biggest?.let { e -> (e.counterparty ?: e.note)?.let { " ($it)" } } ?: "")
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

/** An insight; when it's about specific transactions, tapping shows them. */
@Composable
fun InsightCard(i: Insight, s: com.morphlabs.morph.data.Snapshot? = null) {
    val m = LocalMorph.current
    val accent = when (i.tone) { Tone.GOOD -> m.good; Tone.WATCH -> m.gold; Tone.INFO -> MaterialTheme.colorScheme.tertiary }
    val openList = LocalOpenList.current
    val drill = (i.accountIds.isNotEmpty() || i.counterpartyKey != null)
    MorphCard(onClick = if (drill) ({ openList(TxFilter(i.title, accountIds = i.accountIds, counterpartyKey = i.counterpartyKey, from = i.since)) }) else null) {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${i.emoji}  ${i.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Gap(4)
                Text(i.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors()) { Text(text) }
}
