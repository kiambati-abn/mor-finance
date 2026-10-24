package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.insight.Patterns
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.BarChart
import com.morphlabs.morph.ui.components.EntryRow
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.HBar
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.theme.LocalMorph
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** The deep look. Every section is one chart or list plus one sentence saying what it means. */
@Composable
fun PatternsScreen(repo: MorphRepo, s: Snapshot, onBack: () -> Unit, startAccount: Account? = null) {
    val today = repo.dayOf(System.currentTimeMillis())
    var days by rememberSaveable { mutableIntStateOf(90) }
    var drill by remember { mutableStateOf(startAccount) }
    var open by remember { mutableStateOf<com.morphlabs.morph.core.model.Entry?>(null) }
    val p = remember(s) { Patterns(s.accounts, s.entries, s.zone) }
    val monthly = remember(p) { p.monthly(today) }
    val trends = remember(p) { p.trends(today) }
    val week = remember(p, days) { p.byWeekday(today, days) }
    val tod = remember(p, days) { p.byTimeOfDay(today, days) }
    val places = remember(p, days) { p.topPlaces(today, days) }
    val regulars = remember(p) { p.regulars(today) }
    val unusual = remember(p, days) { p.unusual(today, days) }
    val after = remember(p, days) { p.afterIncome(today, days) }
    val openList = com.morphlabs.morph.ui.LocalOpenList.current
    val from = today.minusDays(days - 1L).atStartOfDay(s.zone).toInstant().toEpochMilli()
    fun who(key: String, name: String, since: Long? = null) = openList(com.morphlabs.morph.ui.TxFilter(name, counterpartyKey = key, from = since))

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackHeader("Patterns", onBack)
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((d, l) in listOf(30 to "30 days", 90 to "3 months", 180 to "6 months")) {
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                    }
                }
                MorphCard { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val window = remember(p, days) { p.byWeekday(today, days).sumOf { it.total } / 7 }
                    for ((l, v) in listOf("Avg / day" to window, "Avg / week" to window * 7, "Avg / month" to (window * 30.44).toLong())) {
                        Column(Modifier.weight(1f)) {
                            Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(v), fontWeight = FontWeight.Bold)
                        }
                    }
                } }
                Text("Averages over the period above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionTitle("Spending by month")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    BarChart(monthly.map { it.label to it.total }) { i ->
                        val month = java.time.YearMonth.from(today).minusMonths((monthly.lastIndex - i).toLong())
                        TextButton(onClick = {
                            openList(com.morphlabs.morph.ui.TxFilter("Spending · ${monthly[i].label}", spendingOnly = true,
                                from = month.atDay(1).atStartOfDay(s.zone).toInstant().toEpochMilli(),
                                to = month.plusMonths(1).atDay(1).atStartOfDay(s.zone).toInstant().toEpochMilli()))
                        }) { Text("See ${monthly[i].label} transactions →") }
                        val why = remember(p, i) { p.whyMonth(month, today) }
                        if (why.isNotEmpty()) Text(
                            "vs your usual: " + why.joinToString(" · ") { dr ->
                                "${if (dr.change > 0) "▲" else "▼"} ${dr.account.emoji} ${dr.account.name} ${if (dr.change > 0) "+" else "−"}${Money.format(kotlin.math.abs(dr.change))}" +
                                    (dr.biggest?.takeIf { dr.change > 0 }?.counterparty?.let { " (mostly $it)" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Gap(8)
                    val prev = monthly.dropLast(1).filter { it.total > 0 }
                    if (prev.isNotEmpty()) {
                        val avg = prev.sumOf { it.total } / prev.size
                        Text("Your average month: ${Money.format(avg)}. This month so far: ${Money.format(monthly.last().total)}.", style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
            if (trends.isNotEmpty()) item {
                SectionTitle("This month vs your usual")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    Text("Compared fairly: the same number of days from your last 3 months. Tap one to dig in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(4)
                    val max = trends.maxOf { maxOf(it.thisMonth, it.usual) }
                    for (t in trends.take(8)) {
                        val c = t.change
                        val sub = when {
                            c == null -> "New this month"
                            c > 0.15 -> "▲ ${Patterns.pct(c)} more than usual (${Money.format(t.usual)})"
                            c < -0.15 -> "▼ ${Patterns.pct(c)} less than usual (${Money.format(t.usual)})"
                            else -> "About usual"
                        }
                        HBar(t.account.label, t.thisMonth, max, sub) { drill = t.account }
                    }
                } }
            }
            item {
                SectionTitle("When you spend")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    val peak = week.maxByOrNull { it.total }
                    BarChart(week.map { it.label to it.total }, initial = week.indexOf(peak).coerceAtLeast(0))
                    Text("Average per day of the week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(16)
                    val peakT = tod.maxByOrNull { it.total }
                    BarChart(tod.map { it.label to it.total }, height = 90, initial = tod.indexOf(peakT).coerceAtLeast(0))
                    Text("Total by time of day. ${peakT?.takeIf { it.total > 0 }?.let { "Most of your money moves in the ${it.label.lowercase()}." } ?: ""}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
            if (regulars.isNotEmpty()) item {
                SectionTitle("Things you pay regularly")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    val total = regulars.sumOf { it.monthlyCost }
                    Text("About ${Money.format(total)} a month goes to these on autopilot. Amounts below are per month.", style = MaterialTheme.typography.bodySmall)
                    Gap(4)
                    val max = regulars.maxOf { it.monthlyCost }
                    for (r in regulars.take(8)) {
                        val every = when (r.everyDays) { 1 -> "every day"; in 6..8 -> "every week"; in 13..16 -> "every 2 weeks"; in 27..32 -> "every month"; else -> "every ${r.everyDays} days" }
                        HBar("${r.accountId?.let { s.byId[it]?.emoji } ?: "🔁"} ${r.name}", r.monthlyCost, max,
                            "~${Money.format(r.average)} $every · next ~${dayFmt.format(r.next)}") { who(r.key, r.name) }
                    }
                } }
            }
            if (places.isNotEmpty()) item {
                SectionTitle("Top people & places")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    val max = places.first().total
                    for (pl in places) HBar(
                        "${pl.accountId?.let { s.byId[it]?.emoji } ?: "❔"} ${pl.name}", pl.total, max,
                        "${pl.count} payment${if (pl.count == 1) "" else "s"} · average ${Money.format(pl.total / pl.count)}",
                    ) { who(pl.key, pl.name, from) }
                } }
            }
            if (unusual.isNotEmpty()) item {
                SectionTitle("Unusual spends")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    Text("Much bigger than what's normal for their category. Worth a second look.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (u in unusual.take(5)) {
                        EntryRow(u.entry, s) { open = u.entry }
                        Text("${"%.1f".format(u.timesUsual)}× your usual ${s.byId[u.accountId]?.name}", style = MaterialTheme.typography.bodySmall, color = LocalMorph.current.gold)
                    }
                } }
            }
            after?.let { a ->
                item {
                    SectionTitle("After money comes in")
                    MorphCard { Column(Modifier.padding(16.dp)) {
                        val pct = (a.share * 100).roundToInt()
                        Text("$pct% spent within 3 days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Across ${a.incomes} time${if (a.incomes == 1) "" else "s"} you received ${Money.format(a.received)}, " +
                                "${Money.format(a.spentIn3Days)} was spent in the next 3 days. " +
                                if (pct >= 40) "Try moving savings out the moment money lands, before spending starts." else "That's a calm pace. Nice.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } }
                }
            }
            item { Gap(40) }
        }
    }
    drill?.let { a -> DrillDialog(p, s, a, today, { k, n -> drill = null; who(k, n) }, { drill = null; openList(com.morphlabs.morph.ui.TxFilter(a.label, accountIds = setOf(a.id))) }) { drill = null } }
    open?.let { e -> EntryDialog(repo, s, e) { open = null } }
}

@Composable
private fun DrillDialog(p: Patterns, s: Snapshot, a: Account, today: java.time.LocalDate, onPlace: (String, String) -> Unit, onAll: () -> Unit, onDismiss: () -> Unit) {
    val months = remember(p, a) { p.monthly(today, 6, a.id) }
    val places = remember(p, a) { p.topPlaces(today, 180, 6, a.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.label) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                BarChart(months.map { it.label to it.total })
                if (places.isNotEmpty()) {
                    Gap(12)
                    Text("Where it goes (6 months)", style = MaterialTheme.typography.labelLarge)
                    val max = places.first().total
                    for (pl in places) HBar(pl.name, pl.total, max, "${pl.count}× · average ${Money.format(pl.total / pl.count)}") { onPlace(pl.key, pl.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAll) { Text("All transactions") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
