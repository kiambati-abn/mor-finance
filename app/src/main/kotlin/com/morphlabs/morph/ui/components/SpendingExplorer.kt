package com.morphlabs.morph.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.insight.Patterns
import com.morphlabs.morph.core.insight.Spending
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.LocalOpenList
import com.morphlabs.morph.ui.TxFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Where it went", with a little control: Where (by category / type / people & places) or
 * When (over time). Every bar opens its transactions. On Home it has its own period chips; in
 * Patterns it follows the screen's start and end dates ([range]).
 */
@Composable
fun SpendingExplorer(s: Snapshot, today: LocalDate, range: Pair<LocalDate, LocalDate>? = null, rangeName: String? = null) {
    var view by rememberSaveable { mutableStateOf("where") }
    var period by rememberSaveable { mutableStateOf("30") }
    var group by rememberSaveable { mutableStateOf("category") }
    val openList = LocalOpenList.current

    val firstDay = remember(s) {
        s.entries.filter { it.source != com.morphlabs.morph.core.model.EntrySource.SYSTEM }.minOfOrNull { it.timestamp }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(s.zone).toLocalDate() } ?: today
    }
    val fromDate = range?.first ?: when (period) {
        "7" -> today.minusDays(6)
        "month" -> today.withDayOfMonth(1)
        "90" -> today.minusDays(89)
        "year" -> today.withDayOfYear(1)
        "all" -> firstDay
        else -> today.minusDays(29)
    }
    val endDate = range?.second ?: today
    val periodName = rangeName ?: mapOf("7" to "7 days", "30" to "30 days", "month" to "this month", "90" to "3 months", "year" to "this year", "all" to "all time")[period]!!
    fun ms(d: LocalDate) = d.atStartOfDay(s.zone).toInstant().toEpochMilli()
    val from = ms(fromDate); val to = ms(endDate.plusDays(1))
    val key = "$fromDate..$endDate"
    val spanDays = java.time.temporal.ChronoUnit.DAYS.between(fromDate, endDate).toInt() + 1
    val spending = remember(s) { Spending(s.accounts, s.entries, s.zone) }
    val total = remember(s, key) { spending.total(from, to) }

    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = view == "where", onClick = { view = "where" }, label = { Text("Where") })
            FilterChip(selected = view == "when", onClick = { view = "when" }, label = { Text("When") })
            if (range == null) {
                Text("  ")
                for ((k, l) in listOf("7" to "7d", "30" to "30d", "month" to "Month", "90" to "3m", "year" to "Year", "all" to "All")) {
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
        }
        if (view == "where") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((k, l) in listOf("category" to "Category", "type" to "Type", "places" to "People & places")) {
                FilterChip(selected = group == k, onClick = { group = k }, label = { Text(l) })
            }
        }
        Text("${Money.format(total)} spent · $periodName", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 6.dp))

        if (view == "where") {
            val rows: List<Triple<String, Long, TxFilter>> = remember(s, key, group) {
                when (group) {
                    "type" -> spending.byAccount(from, to).entries
                        .groupBy { s.byId[it.key]?.typeLabel ?: "Other" }
                        .map { (label, es) -> Triple(label, es.sumOf { it.value }, TxFilter("$label · $periodName", accountIds = es.map { it.key }.toSet(), from = from, to = to)) }
                    "places" -> {
                        Patterns(s.accounts, s.entries, s.zone).topPlaces(endDate, spanDays, n = 12)
                            .map { Triple("${it.accountId?.let { id -> s.byId[id]?.emoji } ?: "❔"} ${it.name}", it.total, TxFilter("${it.name} · $periodName", counterpartyKey = it.key, from = from, to = to)) }
                    }
                    else -> spending.byAccount(from, to).mapNotNull { (id, v) ->
                        s.byId[id]?.let { a -> Triple(a.label, v, TxFilter("${a.label} · $periodName", accountIds = setOf(id), from = from, to = to)) }
                    }
                }.filter { it.second > 0 }.sortedByDescending { it.second }
            }
            if (rows.isEmpty()) Text("No spending in this period.", style = MaterialTheme.typography.bodySmall)
            var showAll by rememberSaveable(key, group) { mutableStateOf(false) }
            val max = rows.firstOrNull()?.second ?: 1
            for ((label, v, f) in if (showAll) rows else rows.take(6)) {
                val share = if (total > 0) (v * 100 / total) else 0
                HBar(label, v, max, "$share% of spending ›") { openList(f) }
            }
            if (rows.size > 6) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show all ${rows.size}") }
        } else {
            // Days for up to a month, weeks for up to 4 months, months for anything longer.
            val buckets: List<Triple<String, Long, Pair<LocalDate, LocalDate>>> = remember(s, key) {
                if (spanDays > 120) {
                    val first = java.time.YearMonth.from(fromDate)
                    val last = java.time.YearMonth.from(endDate)
                    generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList().takeLast(24).map { m ->
                        val a = maxOf(m.atDay(1), fromDate); val b = minOf(m.atEndOfMonth(), endDate)
                        Triple(m.month.name.take(3).lowercase().replaceFirstChar(Char::uppercaseChar), spending.total(ms(a), ms(b.plusDays(1))), a to b)
                    }
                } else if (spanDays > 31) {
                    // Weeks ending on the end date, back to the start date.
                    generateSequence(endDate) { it.minusWeeks(1) }.takeWhile { !it.isBefore(fromDate) }.toList().reversed().map { end ->
                        val start = maxOf(end.minusDays(6), fromDate)
                        Triple(DateTimeFormatter.ofPattern("d/M", Locale.ENGLISH).format(start), spending.total(ms(start), ms(end.plusDays(1))), start to end)
                    }
                } else {
                    generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.map { d ->
                        Triple(if (d == today) "Today" else d.dayOfMonth.toString(), spending.totalOn(d), d to d)
                    }.toList()
                }
            }
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
            BarChart(
                buckets.map { it.first to it.second },
                height = 110,
                showLabel = { i -> buckets.size <= 8 || i % (buckets.size / 6 + 1) == 0 || i == buckets.lastIndex },
            ) { i ->
                val (a, b) = buckets[i].third
                Text(if (a == b) fmt.format(a) else "${fmt.format(a)} – ${fmt.format(b)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (buckets[i].second > 0) TextButton(onClick = {
                    val title = when {
                        a == b -> fmt.format(a)
                        spanDays > 120 -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).format(a)
                        else -> "Week of ${fmt.format(a)}"
                    }
                    openList(TxFilter(title, spendingOnly = true, from = ms(a), to = ms(b.plusDays(1))))
                }) { Text("See transactions →") }
            }
        }
    }
}
