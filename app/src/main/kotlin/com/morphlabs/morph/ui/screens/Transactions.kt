package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntrySource
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.EntryRow

@Composable
fun TransactionsScreen(repo: MorphRepo, s: Snapshot, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var open by remember { mutableStateOf<Entry?>(null) }
    val list = remember(s, q, filter) {
        val needle = q.trim().lowercase()
        val monthStart = repo.dayOf(System.currentTimeMillis()).withDayOfMonth(1).atStartOfDay(s.zone).toInstant().toEpochMilli()
        s.newestFirst.filter { e ->
            when (filter) {
                "sort" -> e.status == EntryStatus.NEEDS_REVIEW
                "auto" -> e.status == EntryStatus.AUTO
                "month" -> e.timestamp >= monthStart
                "cash" -> e.source == EntrySource.MANUAL
                else -> true
            }
        }.filter { e ->
            needle.isEmpty() || e.description.lowercase().contains(needle) ||
                (e.counterparty?.lowercase()?.contains(needle) == true) ||
                (e.categoryId?.let { s.byId[it]?.name?.lowercase()?.contains(needle) } == true) ||
                (e.note?.lowercase()?.contains(needle) == true) ||
                (Money.format(s.amount(e), withCurrency = false).replace(",", "").startsWith(needle.replace(",", "")))
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackHeader("All transactions", onBack)
        OutlinedTextField(q, { q = it }, label = { Text("Search name, place or category") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((k, l) in listOf("all" to "All", "month" to "This month", "sort" to "To sort", "auto" to "Auto", "cash" to "By hand")) {
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
            }
        }
        Text("${list.size} transactions · ${Money.format(list.filter { s.outgoing(it) }.sumOf { s.amount(it) })} out · ${Money.format(list.filterNot { s.outgoing(it) }.sumOf { s.amount(it) })} in",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { e -> EntryRow(e, s) { open = e } }
        }
    }
    open?.let { e -> EntryDialog(repo, s, e) { open = null } }
}
