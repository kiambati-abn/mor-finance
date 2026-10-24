package com.morphlabs.morph.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.sms.InboxImporter
import com.morphlabs.morph.ui.components.Gap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tracking start date: Morph ignores (and doesn't ask about) anything earlier. Move it later to
 * declutter; pull earlier history whenever you want it, which moves it back.
 */
@Composable
fun StartDateDialog(repo: MorphRepo, today: LocalDate, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val current = repo.startDate?.let { java.time.Instant.ofEpochMilli(it).atZone(repo.zone).toLocalDate() }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var pulled by remember { mutableStateOf<InboxImporter.Result?>(null) }
    var picking by remember { mutableStateOf(false) }
    fun ms(d: LocalDate) = d.atStartOfDay(repo.zone).toInstant().toEpochMilli()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Tracking starts") },
        text = {
            Column {
                Text(current?.let { fmt.format(it) } ?: "From the beginning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Nothing before this date is imported or put in Sort. Earlier waiting items are hidden, not deleted.",
                    style = MaterialTheme.typography.bodySmall)
                Gap()
                Text("Start from…", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((d, l) in listOf(today to "Today", today.withDayOfMonth(1) to "This month", today.minusMonths(1).withDayOfMonth(1) to "Last month")) {
                        FilterChip(selected = current == d, onClick = { repo.startDate = ms(d) }, label = { Text(l) })
                    }
                    FilterChip(selected = false, onClick = { picking = true }, label = { Text("📅 Pick a date") })
                }
                if (current != null) {
                    Gap()
                    Text("Pull earlier history", style = MaterialTheme.typography.labelLarge)
                    Text("Imports M-Pesa SMS from before ${fmt.format(current)} and moves the start back.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((months, l) in listOf(1L to "1 month", 3L to "3 months", 6L to "6 months", 12L to "1 year", 0L to "Everything")) {
                            FilterChip(selected = false, enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    val from = if (months == 0L) 0L else ms(current.minusMonths(months))
                                    val r = withContext(Dispatchers.IO) { InboxImporter.pullEarlier(ctx, from) { progress = it } }
                                    busy = false
                                    pulled = r
                                }
                            }, label = { Text(l) })
                        }
                    }
                }
                if (busy) { Gap(8); Row { CircularProgressIndicator(); Text("  Read $progress messages…") } }
                pulled?.let { r ->
                    Gap(8)
                    Text(r.summary(), fontWeight = FontWeight.SemiBold)
                    if (r.known > 0) Text("They're shown again now that the start date is earlier.", style = MaterialTheme.typography.bodySmall)
                    if (r.added > 0 || r.known > 0) TextButton(onClick = {
                        scope.launch {
                            val (filed, unsure) = withContext(Dispatchers.IO) { repo.sortEverything() }
                            Toast.makeText(ctx, "Sorted $filed · $unsure flagged 🤔 to check", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }) { Text("🪄 Sort them for me") }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") } },
    )
    // Picking a later date just hides older items; picking an earlier one pulls that history in.
    if (picking) com.morphlabs.morph.ui.components.PickDateDialog(current ?: today, today, onDismiss = { picking = false }) { d ->
        if (current != null && d.isBefore(current)) {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { InboxImporter.pullEarlier(ctx, ms(d)) { progress = it } }
                busy = false; pulled = r
            }
        } else repo.startDate = ms(d)
    }
}
