package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.EntryRow

@Composable
fun TransactionsScreen(repo: MorphRepo, s: Snapshot, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Entry?>(null) }
    val list = remember(s, q) {
        val needle = q.trim().lowercase()
        s.newestFirst.filter { e ->
            needle.isEmpty() || e.description.lowercase().contains(needle) ||
                (e.counterparty?.lowercase()?.contains(needle) == true) ||
                (e.categoryId?.let { s.byId[it]?.name?.lowercase()?.contains(needle) } == true)
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackHeader("All transactions", onBack)
        OutlinedTextField(q, { q = it }, label = { Text("Search name, place or category") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { e -> EntryRow(e, s) { open = e } }
        }
    }
    open?.let { e -> EntryDialog(repo, s, e) { open = null } }
}
