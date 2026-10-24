package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.horizontalScroll
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
    // The period every section follows: a preset, or a custom from–to range.
    var preset by rememberSaveable { mutableStateOf("90") }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(0) } // 1 = from, 2 = to
    var drill by remember { mutableStateOf(startAccount) }
    var open by remember { mutableStateOf<com.morphlabs.morph.core.model.Entry?>(null) }
    val firstDay = remember(s) {
        s.entries.filter { it.source != com.morphlabs.morph.core.model.EntrySource.SYSTEM }.minOfOrNull { it.timestamp }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(s.zone).toLocalDate() } ?: today
    }
    val rangeEnd = if (preset == "custom") customTo?.let { java.time.LocalDate.parse(it) } ?: today else today
    val rangeStart = when (preset) {
        "30" -> today.minusDays(29)
        "180" -> today.minusDays(179)
        "365" -> today.minusDays(364)
        "all" -> firstDay
        "custom" -> customFrom?.let { java.time.LocalDate.parse(it) } ?: today.minusDays(29)
        else -> today.minusDays(89)
    }.let { if (it.isAfter(rangeEnd)) rangeEnd else it }
    val days = (java.time.temporal.ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1).toInt()
    val p = remember(s) { Patterns(s.accounts, s.entries, s.zone) }
    val key = "$rangeStart..$rangeEnd"
    val monthCount = (java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(rangeStart), java.time.YearMonth.from(rangeEnd)) + 1).toInt().coerceIn(2, 24)
    val monthly = remember(p, key) { p.monthly(rangeEnd, monthCount) }
    val trends = remember(p, key) { p.versusBefore(rangeStart, rangeEnd) }
    val week = remember(p, key) { p.byWeekday(rangeEnd, days) }
    val tod = remember(p, key) { p.byTimeOfDay(rangeEnd, days) }
    val places = remember(p, key) { p.topPlaces(rangeEnd, days) }
    val regulars = remember(p, key) { p.regulars(rangeEnd, maxOf(days, 30)) }
    val unusual = remember(p, key) { p.unusual(rangeEnd, days) }
    val after = remember(p, key) { p.afterIncome(rangeEnd, days) }
    val spentInRange = remember(s, key) {
        com.morphlabs.morph.core.insight.Spending(s.accounts, s.entries, s.zone).total(
            rangeStart.atStartOfDay(s.zone).toInstant().toEpochMilli(), rangeEnd.plusDays(1).atStartOfDay(s.zone).toInstant().toEpochMilli())
    }
    val openList = com.morphlabs.morph.ui.LocalOpenList.current
    val from = rangeStart.atStartOfDay(s.zone).toInstant().toEpochMilli()
    val to = rangeEnd.plusDays(1).atStartOfDay(s.zone).toInstant().toEpochMilli()
    val rangeFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
    fun who(key: String, name: String, since: Long? = null) = openList(com.morphlabs.morph.ui.TxFilter(name, counterpartyKey = key, from = since, to = to))

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackHeader("Patterns", onBack)
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, l) in listOf("30" to "30d", "90" to "3m", "180" to "6m", "365" to "Year", "all" to "All")) {
                        FilterChip(selected = preset == k, onClick = { preset = k }, label = { Text(l) })
                    }
                    FilterChip(selected = preset == "custom", onClick = { picking = 1 }, label = { Text("📅 Range") })
                }
                Text(
                    "${rangeFmt.format(rangeStart)} – ${rangeFmt.format(rangeEnd)} · $days days · ${Money.format(spentInRange)} spent",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp),
                )
                MorphCard { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val window = spentInRange / days.coerceAtLeast(1)
                    for ((l, v) in listOf("Avg / day" to window, "Avg / week" to window * 7, "Avg / month" to (window * 30.44).toLong())) {
                        Column(Modifier.weight(1f)) {
                            Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(v), fontWeight = FontWeight.Bold)
                        }
                    }
                } }
                Text("Averages over the period above. Every section below follows it too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionTitle("Spending by month")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    BarChart(monthly.map { it.label to it.total }) { i ->
                        val month = java.time.YearMonth.from(rangeEnd).minusMonths((monthly.lastIndex - i).toLong())
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
                        Text("Average month: ${Money.format(avg)}. Last month shown: ${Money.format(monthly.last().total)}.", style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
            if (trends.isNotEmpty()) item {
                SectionTitle("This period vs the one before")
                MorphCard { Column(Modifier.padding(16.dp)) {
                    Text("Compared with the $days days just before. Tap one to dig in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(4)
                    val max = trends.maxOf { maxOf(it.thisMonth, it.usual) }
                    for (t in trends.take(8)) {
                        val c = t.change
                        val sub = when {
                            c == null -> "New in this period"
                            c > 0.15 -> "▲ ${Patterns.pct(c)} more than before (${Money.format(t.usual)})"
                            c < -0.15 -> "▼ ${Patterns.pct(c)} less than before (${Money.format(t.usual)})"
                            else -> "About the same"
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
    if (picking == 1) com.morphlabs.morph.ui.components.PickDateDialog(rangeStart, today, onDismiss = { picking = 0 }) { d ->
        customFrom = d.toString(); picking = 2
    }
    if (picking == 2) com.morphlabs.morph.ui.components.PickDateDialog(rangeEnd.coerceAtLeast(rangeStart), today, onDismiss = { picking = 0 }) { d ->
        customTo = d.toString(); preset = "custom"; picking = 0
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
