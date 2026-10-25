package com.morphlabs.morph.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.core.academy.Academy
import com.morphlabs.morph.core.academy.Action
import com.morphlabs.morph.core.academy.Card
import com.morphlabs.morph.core.academy.Deck
import com.morphlabs.morph.core.academy.Kind
import com.morphlabs.morph.core.academy.Outcome
import com.morphlabs.morph.core.academy.Track
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.data.academyData
import com.morphlabs.morph.ui.components.Celebration
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.Pill
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.theme.LocalMorph
import com.morphlabs.morph.ui.today

/**
 * Morph Academy: five cards a day, about two minutes. Cards about what's happening in your money
 * come first, cards you're forgetting come back, and every card ends with one small thing to try.
 */
@Composable
fun LearnScreen(repo: MorphRepo, s: Snapshot, onBack: () -> Unit, go: (Action) -> Unit) {
    val day = today(repo)
    var progress by remember { mutableStateOf(repo.academyProgress()) }
    // If your numbers can't be worked out, the Academy still opens: it just skips the 📊 cards.
    val data = remember(s) {
        runCatching { s.academyData(day) }.getOrElse {
            com.morphlabs.morph.data.AcademyData(com.morphlabs.morph.core.academy.MoneyNumbers(), emptyMap(), emptySet())
        }
    }
    var lesson by remember { mutableStateOf<List<Card>?>(null) }
    var earned by remember { mutableIntStateOf(0) }
    var celebrate by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { repo.academyClosed() } }
    fun canUse(c: Card) = Academy.fill(c.question, data.facts) != null

    val current = lesson
    if (current != null) {
        LessonView(
            cards = current, facts = data.facts,
            onAnswer = { c, outcome, right -> earned += repo.recordCard(c.id, outcome, right); progress = repo.academyProgress() },
            onTry = { a -> go(a) },
            onFinish = { earned += repo.finishLesson(); lesson = null; celebrate = true },
            onQuit = { lesson = null },
        )
    } else Column(Modifier.fillMaxSize()) {
        BackHeader("📚 Morph Academy", onBack)
        val open = Academy.unlocked(Deck.cards, progress)
        val mastery = Academy.mastery(Deck.cards, progress)
        val streak = Academy.streak(s.learnDays, day)
        val doneToday = day in s.learnDays
        val next = Academy.session(Deck.cards, progress, day, data.signals, ::canUse)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                MorphCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Money IQ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${Academy.moneyIq(Deck.cards, progress)}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("🔥 $streak-day streak", fontWeight = FontWeight.SemiBold)
                                Text("${progress.values.count { it.seen > 0 }} of ${Deck.cards.size} cards seen", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        data.facts["stage"]?.let {
                            Gap(8)
                            Text("🦋 Your freedom stage: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Gap()
                Button(onClick = { earned = 0; lesson = next }, enabled = next.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        when {
                            next.isEmpty() -> "All caught up. Come back tomorrow 🌙"
                            doneToday -> "Extra round · ${next.size} cards"
                            else -> "Start today's lesson · ${next.size} cards · 2 min"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (doneToday) Text("✓ Today's lesson done. Extra rounds earn stars but don't change the streak.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                SectionTitle("Tracks")
            }
            items(Track.entries) { t ->
                val cards = Deck.cards.filter { it.track == t }
                val seen = cards.count { (progress[it.id]?.seen ?: 0) > 0 }
                val unlocked = t in open
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (unlocked) t.emoji else "🔒", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(t.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$seen/${cards.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    Gap(4)
                    LinearProgressIndicator(progress = { mastery[t] ?: 0f }, modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                        color = LocalMorph.current.gold, trackColor = LocalMorph.current.track)
                    if (!unlocked) Text("Opens when you've seen half of the track before.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 30.dp, top = 2.dp))
                }
            }
            item {
                Gap()
                Text(
                    "How it works: each card comes back just before you'd forget it (1, 3, 7, 21, then 60 days). " +
                        "Cards about your own money jump the queue. Figures marked ⏳ change over time, so check today's rates before acting.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(88)
            }
        }
    }
    if (celebrate) Celebration(earned, Academy.streak(s.learnDays + day, day), "Lesson done · Money IQ ${Academy.moneyIq(Deck.cards, progress)}") { celebrate = false }
}

@Composable
private fun LessonView(
    cards: List<Card>, facts: Map<String, String>,
    onAnswer: (Card, Outcome, Boolean) -> Unit, onTry: (Action) -> Unit, onFinish: () -> Unit, onQuit: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler(onBack = onQuit)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onQuit) { Text("✕ Stop") }
            LinearProgressIndicator(progress = { index / cards.size.toFloat() }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = LocalMorph.current.gold, trackColor = LocalMorph.current.track)
            Text("${index + 1}/${cards.size}", style = MaterialTheme.typography.labelLarge)
        }
        Gap()
        AnimatedContent(targetState = index, label = "card") { i ->
            CardView(cards[i], facts, onAnswer = { o, r -> onAnswer(cards[i], o, r) }, onTry = onTry,
                onNext = { if (i + 1 < cards.size) index = i + 1 else onFinish() }, last = i + 1 == cards.size)
        }
    }
}

@Composable
private fun CardView(card: Card, facts: Map<String, String>, onAnswer: (Outcome, Boolean) -> Unit, onTry: (Action) -> Unit, onNext: () -> Unit, last: Boolean) {
    // For a quiz: the option picked. For a concept: whether the answer is showing. YOU cards show everything at once.
    var picked by remember(card.id) { mutableStateOf<Int?>(null) }
    var revealed by remember(card.id) { mutableStateOf(card.kind == Kind.YOU) }
    var answered by remember(card.id) { mutableStateOf(false) }
    val good = LocalMorph.current.good
    val bad = LocalMorph.current.bad
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${card.track.emoji} ${card.track.title}")
            if (card.kind == Kind.YOU) Pill("📊 Your numbers")
            if (card.timeSensitive) Pill("⏳ Check current rates")
        }
        Gap()
        Text(Academy.fill(card.question, facts) ?: card.question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Gap(16)
        when (card.kind) {
            Kind.QUIZ -> card.options.forEachIndexed { i, opt ->
                val color = when {
                    picked == null -> null
                    i == card.correct -> good
                    i == picked -> bad
                    else -> null
                }
                OutlinedButton(
                    onClick = {
                        if (picked == null) {
                            picked = i; revealed = true; answered = true
                            onAnswer(if (i == card.correct) Outcome.KNEW else Outcome.NO_IDEA, i == card.correct)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    border = BorderStroke(if (color != null) 2.dp else 1.dp, color ?: MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        (if (color == good) "✓ " else if (color == bad) "✗ " else "") + opt,
                        Modifier.fillMaxWidth().padding(vertical = 6.dp), color = color ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Kind.CONCEPT -> if (!revealed) Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Show answer") }
            Kind.YOU -> Unit
        }
        if (revealed) {
            Gap()
            if (card.kind == Kind.QUIZ) Text(if (picked == card.correct) "Right! +2 ⭐" else "Not quite. Here's why:", fontWeight = FontWeight.SemiBold,
                color = if (picked == card.correct) good else bad)
            MorphCard { Text(card.answer, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
            card.tryThis?.let { t ->
                Gap(8)
                MorphCard(onClick = if (card.action != Action.NONE) ({ onTry(card.action) }) else null) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👉", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Try this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(t, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (card.action != Action.NONE) Text("Open →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Gap(16)
            if (!answered) {
                // How well did you know it? This decides when the card comes back.
                val choices = if (card.kind == Kind.YOU) listOf(Outcome.KNEW to "Got it ✓")
                else listOf(Outcome.NO_IDEA to "😕 No idea", Outcome.UNSURE to "🤔 Sort of", Outcome.KNEW to "😎 Knew it")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((o, label) in choices) Button(onClick = { answered = true; onAnswer(o, false); onNext() }, modifier = Modifier.weight(1f)) {
                        Text(label, maxLines = 1)
                    }
                }
            } else Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (last) "Finish 🎉" else "Next →") }
        }
        Box(Modifier.height(40.dp))
    }
}
