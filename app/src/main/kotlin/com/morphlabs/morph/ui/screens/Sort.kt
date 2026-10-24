package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.model.SystemRole
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.components.EntryRow
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.Pill
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.components.StarBurst
import com.morphlabs.morph.ui.components.whenText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SortScreen(repo: MorphRepo, s: Snapshot) {
    var burst by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    val queue = s.pending.filter { it.id !in skipped }.ifEmpty { s.pending }
    val current = queue.firstOrNull()
    val today = repo.dayOf(System.currentTimeMillis())
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (current == null) "All sorted" else "${s.pending.size} to sort",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                val streak = Game.streak(s.entries, today, s.zone)
                if (streak > 0) Pill("🔥 $streak")
                Text("  ")
                Pill("${Game.level(s.stars).emoji} ${s.stars}⭐")
            }
            Gap()
            if (current == null) AllDone(repo, s)
            else SortPanel(
                repo, s, current,
                onSorted = { reward, label -> burst = reward.stars to label },
                onSkip = if (s.pending.size > 1) ({ skipped = skipped + current.id }) else null,
            )
        }
        burst?.let { (stars, label) -> StarBurst(stars, label) { burst = null } }
    }
}

/** One transaction, the best guesses as big buttons, and everything else one tap away. */
@Composable
fun SortPanel(repo: MorphRepo, s: Snapshot, e: Entry, onSorted: (Game.Reward, String) -> Unit, onSkip: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val guess = remember(e.id, s) { s.guess(e, limit = 4) }
    var showAll by remember(e.id) { mutableStateOf(false) }
    val out = s.outgoing(e)

    fun pick(a: Account) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val r = withContext(Dispatchers.IO) { repo.categorize(e.id, a.id) }
            onSorted(r, a.label)
        }
    }

    MorphCard {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (out) "Money out" else "Money in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(s.amount(e)), fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text(e.counterparty ?: e.description, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(whenText(e.timestamp, s.zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fee = e.lines.filter { it.accountId != e.categoryId && it.amount > 0 && s.byId[it.accountId]?.role in feeRoles }.sumOf { it.amount }
            if (fee > 0) Text("+ ${Money.format(fee)} M-Pesa fee (already filed)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fresh = System.currentTimeMillis() - e.timestamp in 0..Game.FRESH_WINDOW_MS
            if (fresh) { Gap(6); Pill("⚡ Fresh: +2 bonus stars") }
        }
    }
    Gap(16)
    Text("What was it?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    guess.suggestions.firstOrNull()?.reason?.takeIf { it.isNotEmpty() }?.let {
        Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Gap(8)
    guess.suggestions.forEachIndexed { i, sug ->
        val a = s.byId[sug.accountId] ?: return@forEachIndexed
        if (i == 0) Button(onClick = { pick(a) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text(a.label, fontSize = 18.sp)
        } else FilledTonalButton(onClick = { pick(a) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(a.label, fontSize = 16.sp)
        }
        Gap(8)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showAll = !showAll }, modifier = Modifier.weight(1f)) { Text(if (showAll) "Hide" else "Something else…") }
        if (onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip for now") }
    }
    if (showAll) {
        Gap(8)
        AccountGrid(s.accounts, out, exclude = guess.suggestions.map { it.accountId }.toSet()) { pick(it) }
    }
    e.smsBody?.let {
        Gap(16)
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AllDone(repo: MorphRepo, s: Snapshot) {
    var open by remember { mutableStateOf<Entry?>(null) }
    val scope = rememberCoroutineScope()
    val level = Game.level(s.stars)
    val next = Game.nextLevel(s.stars)
    MorphCard {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🦋", fontSize = 56.sp)
            Text("Inbox zero", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (next != null) "You're a ${level.name} ${level.emoji}. ${next.minStars - s.stars} ⭐ to ${next.name} ${next.emoji}."
                else "You're a ${level.name} ${level.emoji}. Top level!",
                textAlign = TextAlign.Center,
            )
        }
    }
    val autos = s.newestFirst.filter { it.status == EntryStatus.AUTO }.take(10)
    if (autos.isNotEmpty()) {
        SectionTitle("Did Morph get these right?")
        Text("Confirm or fix what Morph filed for you. Each one teaches it (+1⭐).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (e in autos) {
            EntryRow(e, s) { open = e }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { repo.confirm(e.id) } } }) { Text("👍 Right") }
                TextButton(onClick = { open = e }) { Text("Change") }
            }
        }
    }
    open?.let { e -> EntryDialog(repo, s, e) { open = null } }
}

private val feeRoles = setOf<SystemRole?>(SystemRole.TRANSACTION_COSTS, SystemRole.WITHDRAWAL_CHARGES, SystemRole.FULIZA_CHARGES)
