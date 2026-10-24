package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.core.game.Game
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.AccountType
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What just happened, so it can be undone from the bar at the bottom. */
private data class LastSort(val entryId: Long, val label: String, val count: Int, val at: Long = System.currentTimeMillis())

@Composable
fun SortScreen(repo: MorphRepo, s: Snapshot) {
    var burst by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var party by remember { mutableStateOf<Int?>(null) }
    var skipped by remember { mutableStateOf(listOf<Long>()) }
    // Everything sorted while this screen is open, oldest first: lets you step back.
    var history by remember { mutableStateOf(listOf<Long>()) }
    var backIndex by remember { mutableStateOf<Int?>(null) }
    var last by remember { mutableStateOf<LastSort?>(null) }
    var cashOpen by remember { mutableStateOf<Pair<Entry, Long>?>(null) }
    val scope = rememberCoroutineScope()

    // Skipped ones go to the end of the line instead of disappearing.
    val queue = s.pending.filter { it.id !in skipped } + s.pending.filter { it.id in skipped }.sortedBy { skipped.indexOf(it.id) }
    val revisiting = backIndex?.let { i -> history.getOrNull(i) }?.let { id -> s.entries.firstOrNull { it.id == id } }
    val current = revisiting ?: queue.firstOrNull()
    val today = repo.dayOf(System.currentTimeMillis())

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.pending.isEmpty()) "All sorted" else "${s.pending.size} to sort",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    if (history.isNotEmpty()) Text("${history.size} sorted just now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val streak = Game.streak(s.entries, today, s.zone)
                if (streak > 0) Pill("🔥 $streak")
                Text("  ")
                Pill("${Game.level(s.stars).emoji} ${s.stars}⭐")
            }
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canBack = history.isNotEmpty() && (backIndex ?: history.size) > 0
                OutlinedButton(onClick = { backIndex = (backIndex ?: history.size) - 1 }, enabled = canBack) { Text("← Previous") }
                if (backIndex != null) FilledTonalButton(onClick = {
                    backIndex = backIndex!!.plus(1).takeIf { it < history.size }
                }) { Text(if (backIndex!! + 1 < history.size) "Next →" else "Back to sorting") }
            }
            Gap()
            if (revisiting != null) {
                Text("Changing an earlier one. Pick again, or Undo to send it back to the pile.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    val id = revisiting.id
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.undoSort(id) }
                        history = history - id; backIndex = null
                    }
                }) { Text("↩ Undo this one") }
            }
            // Cash withdrawals waiting to be spread come first: one card each.
            if (backIndex == null) for ((w, left) in s.cashToSpread.take(3)) {
                MorphCard(Modifier.padding(bottom = 8.dp), onClick = { cashOpen = w to left }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💵", fontSize = 26.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("${Money.format(left)} cash to spread", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("From ${w.counterparty ?: "a withdrawal"} · ${whenText(w.timestamp, s.zone)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Spread →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (current == null) AllDone(repo, s)
            else SwipeArea(
                onRight = { if (history.isNotEmpty() && (backIndex ?: history.size) > 0) backIndex = (backIndex ?: history.size) - 1 },
                onLeft = {
                    val bi = backIndex
                    if (bi != null) backIndex = (bi + 1).takeIf { it < history.size }
                    else if (s.pending.size > 1) skipped = (skipped - current.id) + current.id
                },
            ) { SortPanel(
                repo, s, current,
                onSorted = { reward, label, count ->
                    if (Game.DAY_COMPLETE in reward.reasons) party = reward.stars
                    else if (reward.stars > 0) burst = reward.stars to label
                    last = LastSort(current.id, label, count)
                    if (revisiting == null) history = (history - current.id) + current.id
                    backIndex = null
                },
                onSkip = if (revisiting == null && s.pending.size > 1) ({ skipped = (skipped - current.id) + current.id }) else null,
            ) }
            if (current != null) Text("Swipe → to go back · ← to skip or go forward", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Gap(72)
        }
        burst?.let { (stars, label) -> StarBurst(stars, label) { burst = null } }
        party?.let { stars ->
            val next = Game.nextLevel(s.stars)
            com.morphlabs.morph.ui.components.Celebration(
                stars, Game.streak(s.entries, today, s.zone),
                if (next != null) "${Game.level(s.stars).emoji} ${s.stars} ⭐ · ${next.minStars - s.stars} to ${next.name} ${next.emoji}" else "👑 Top level",
            ) { party = null }
        }
        cashOpen?.let { (w, left) -> CashSpreadDialog(repo, s, w, left) { cashOpen = null } }
        last?.let { l ->
            LaunchedEffect(l) { delay(6_000); if (last == l) last = null }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (l.count > 1) "${l.count} filed under ${l.label}" else "Filed under ${l.label}",
                    color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.weight(1f), maxLines = 1,
                )
                TextButton(onClick = {
                    last = null
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.undoSort(l.entryId) }
                        history = history - l.entryId
                    }
                }) { Text("Undo", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}

/** Swipe right to go back, left to skip / go forward. The card follows your finger a little. */
@Composable
private fun SwipeArea(onRight: () -> Unit, onLeft: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // The gesture handler lives across recompositions; always call the latest callbacks.
    val right by androidx.compose.runtime.rememberUpdatedState(onRight)
    val left by androidx.compose.runtime.rememberUpdatedState(onLeft)
    Column(
        Modifier
            .graphicsLayer { translationX = offset.value; alpha = 1f - (kotlin.math.abs(offset.value) / 900f).coerceIn(0f, 0.5f) }
            .pointerInput(Unit) {
                val threshold = 140.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = offset.value
                        scope.launch {
                            if (v > threshold) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); right() }
                            else if (v < -threshold) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); left() }
                            offset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx * 0.6f) }
                }
            }
    ) { content() }
}

/** One transaction, the best guesses as big buttons, and everything else one tap away. */
@Composable
fun SortPanel(repo: MorphRepo, s: Snapshot, e: Entry, onSorted: (Game.Reward, String, Int) -> Unit, onSkip: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val guess = remember(e.id, s) { s.guess(e, limit = 4) }
    var showAll by remember(e.id) { mutableStateOf(false) }
    val similar = remember(e.id, s) { if (e.status == EntryStatus.NEEDS_REVIEW) repo.similarPending(e.id).size else 0 }
    var allSimilar by remember(e.id) { mutableStateOf(false) }
    var note by remember(e.id) { mutableStateOf(e.note ?: "") }
    val out = s.outgoing(e)

    fun pick(a: Account) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val withNote = note.trim().takeIf { it != (e.note ?: "") }
        val all = allSimilar && similar > 0
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                if (withNote != null) repo.setNote(e.id, withNote)
                if (all) repo.categorizeAllSimilar(e.id, a.id) else repo.categorize(e.id, a.id)
            }
            onSorted(r, a.label, if (all) similar + 1 else 1)
        }
    }

    MorphCard {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (out) "Money out" else "Money in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(s.amount(e)), fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text(e.counterparty ?: e.description, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(whenText(e.timestamp, s.zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fee = e.lines.filter { it.amount > 0 && s.byId[it.accountId]?.role in feeRoles }.sumOf { it.amount }
            if (fee > 0) Text("+ ${Money.format(fee)} M-Pesa fee (already filed)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.status != EntryStatus.NEEDS_REVIEW) e.categoryId?.let { s.byId[it] }?.let { Gap(6); Pill("Now: ${it.label}") }
            val fresh = e.status == EntryStatus.NEEDS_REVIEW && System.currentTimeMillis() - e.timestamp in 0..Game.FRESH_WINDOW_MS
            if (fresh) { Gap(6); Pill("⚡ Fresh: +2 bonus stars") }
        }
    }
    Gap(16)
    Text("What was it?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    guess.suggestions.firstOrNull()?.reason?.takeIf { it.isNotEmpty() }?.let {
        Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (similar > 0) {
        Gap(6)
        FilterChip(
            selected = allSimilar, onClick = { allSimilar = !allSimilar },
            label = { Text("Same for all ${similar + 1} from ${e.counterparty ?: "here"}") },
        )
    }
    Gap(8)
    val today = remember { repo.dayOf(System.currentTimeMillis()) }
    guess.suggestions.forEachIndexed { i, sug ->
        val a = s.byId[sug.accountId] ?: return@forEachIndexed
        // With envelopes on, show what's left, so the choice also tells you how you're doing.
        val left = if (out) s.envelopeLeft(a.id, today)?.let { "  ·  ${Money.format(it)} left" } ?: "" else ""
        if (i == 0) Button(onClick = { pick(a) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text(a.label + left, fontSize = 18.sp)
        } else FilledTonalButton(onClick = { pick(a) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(a.label + left, fontSize = 16.sp)
        }
        Gap(8)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showAll = !showAll }, modifier = Modifier.weight(1f)) { Text(if (showAll) "Hide" else "Something else…") }
        if (onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip for now") }
    }
    if (showAll) {
        Gap(8)
        SearchableAccounts(s, out, exclude = guess.suggestions.map { it.accountId }.toSet()) { pick(it) }
    }
    Gap(12)
    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    e.smsBody?.let {
        Gap(12)
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** All accounts with a search box and your most recent choices first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchableAccounts(s: Snapshot, outgoing: Boolean?, exclude: Set<Long> = emptySet(), onPick: (Account) -> Unit) {
    var q by remember { mutableStateOf("") }
    val recent = remember(s, outgoing) {
        s.newestFirst.asSequence()
            .filter { it.status != EntryStatus.NEEDS_REVIEW && (outgoing == null || s.outgoing(it) == outgoing) }
            .mapNotNull { it.categoryId?.let { id -> s.byId[id] } }
            .filter { it.pickable && it.id !in exclude && (outgoing != true || it.type != AccountType.INCOME) && (outgoing != false || it.type != AccountType.EXPENSE) }
            .distinctBy { it.id }.take(6).toList()
    }
    OutlinedTextField(q, { q = it }, label = { Text("Search accounts") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val needle = q.trim().lowercase()
    if (needle.isEmpty() && recent.isNotEmpty()) {
        Text("Recent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in recent) OutlinedButton(onClick = { onPick(a) }, shape = RoundedCornerShape(14.dp)) { Text(a.label) }
        }
    }
    val matching = if (needle.isEmpty()) s.accounts else s.accounts.filter { it.name.lowercase().contains(needle) || it.typeLabel.lowercase().contains(needle) }
    AccountGrid(matching, outgoing, exclude, onPick)
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
    val autos = (s.unsure + s.newestFirst.filter { it.status == EntryStatus.AUTO }).distinctBy { it.id }.take(15)
    if (autos.isNotEmpty()) {
        SectionTitle(if (s.unsure.isNotEmpty()) "🤔 ${s.unsure.size} Morph wasn't sure about" else "Did Morph get these right?")
        Text("Confirm or fix what Morph filed for you. Each one teaches it (+1⭐).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (e in autos) {
            if ((e.confidence ?: 1.0) < Snapshot.UNSURE) Text("🤔 ${((e.confidence ?: 0.0) * 100).toInt()}% sure", style = MaterialTheme.typography.labelMedium, color = com.morphlabs.morph.ui.theme.LocalMorph.current.gold)
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
