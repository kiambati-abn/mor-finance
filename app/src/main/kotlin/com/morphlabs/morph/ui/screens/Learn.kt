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
import com.morphlabs.morph.core.academy.Level
import com.morphlabs.morph.core.model.Naming
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
    var lessonIsDaily by remember { mutableStateOf(true) }
    var earned by remember { mutableIntStateOf(0) }
    var celebrate by remember { mutableStateOf(false) }
    var levelUp by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { repo.academyClosed() } }
    fun canUse(c: Card) = Academy.fill(c.question, data.facts) != null
    fun levels(p: Map<String, com.morphlabs.morph.core.academy.Progress>) = Track.entries.associateWith { Academy.openLevel(it, Deck.cards, p) }

    val current = lesson
    if (current != null) {
        val before = remember(current) { levels(progress) }
        LessonView(
            cards = current, facts = data.facts, day = day,
            onAnswer = { c, outcome, right -> earned += repo.recordCard(c.id, outcome, right); progress = repo.academyProgress() },
            onTry = { a -> go(a) },
            onFinish = {
                if (lessonIsDaily) earned += repo.finishLesson()
                // Did any area climb a level during this round?
                val after = levels(progress)
                levelUp = Track.entries.firstOrNull { after.getValue(it) > before.getValue(it) }?.let { t ->
                    val l = Level.of(after.getValue(t)); "${t.emoji} ${t.title}: ${l.emoji} ${l.title} unlocked!"
                }
                lesson = null; celebrate = true
            },
            onQuit = { lesson = null },
        )
    } else Column(Modifier.fillMaxSize()) {
        BackHeader(Naming.pick("🥋 Money Dojo", "📚 Morph Academy"), onBack)
        val open = levels(progress)
        val streak = Academy.streak(s.learnDays, day)
        val doneToday = day in s.learnDays
        val theme = Academy.themeOf(day)
        val next = Academy.session(Deck.cards, progress, day, data.signals, ::canUse, extra = doneToday)
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
                Text("Today is ${theme.emoji} ${theme.title} day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("A new area every day. Cards about your own money jump the queue.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(8)
                Button(onClick = { earned = 0; lessonIsDaily = true; lesson = next }, enabled = next.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        when {
                            next.isEmpty() -> "All caught up. Come back tomorrow 🌙"
                            doneToday -> Naming.pick("💣 One more bomb · ${next.size} cards", "Extra round · ${next.size} cards")
                            else -> Naming.pick("💣 Today's Bomb · ${next.size} cards · 2 min", "Start today's lesson · ${next.size} cards · 2 min")
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (doneToday) Text(Naming.pick("💥 Today's bomb defused. Extra rounds earn stars; the streak is safe.", "✓ Today's lesson done. Extra rounds earn stars but don't change the streak."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                SectionTitle("Areas · tap one to play it now")
            }
            items(Track.entries) { t ->
                val level = Level.of(open.getValue(t))
                val (known, total) = Academy.levelCount(t, level.number, Deck.cards, progress)
                val leftHere = Deck.cards.count { it.track == t && it.level <= level.number && (progress[it.id]?.seen ?: 0) == 0 && (it.kind != com.morphlabs.morph.core.academy.Kind.YOU || canUse(it)) }
                Column(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable(enabled = leftHere > 0) {
                            earned = 0; lessonIsDaily = false
                            lesson = Academy.session(Deck.cards, progress, day, emptySet(), ::canUse, focus = t, extra = true).filter { it.track == t }
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title + if (t == theme) "  · today" else "", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${level.emoji} ${level.title}" + (if (total > 0) " · $known/$total known" else "") +
                                    if (level.number < Level.entries.size) " · next level at ${kotlin.math.ceil(total * Academy.LEVEL_UP).toInt()}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(if (leftHere > 0) "Play →" else "✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Gap(4)
                    LinearProgressIndicator(progress = { if (total == 0) 1f else known / total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                        color = LocalMorph.current.gold, trackColor = LocalMorph.current.track)
                }
            }
            item {
                Gap()
                Text(
                    "How it works: 2–3 new cards a day plus reviews. Each card comes back just before you'd forget it " +
                        "(1, 3, 7, 21, 60, 120, then 240 days). Each area has 5 levels: 🐣 Rookie → 🔧 Hustler → 🎯 Pro → 🦈 Shark → 👑 Legend. " +
                        "A level opens once you know 60% of the one below (a card counts as known after you get it right twice). " +
                        "Figures marked ⏳ change over time, so check today's rates before acting.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(88)
            }
        }
    }
    if (celebrate) Celebration(earned, Academy.streak(s.learnDays + day, day), levelUp ?: "Money IQ ${Academy.moneyIq(Deck.cards, progress)}") { celebrate = false; levelUp = null }
}

@Composable
private fun LessonView(
    cards: List<Card>, facts: Map<String, String>, day: java.time.LocalDate,
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
            CardView(cards[i], facts, day, onAnswer = { o, r -> onAnswer(cards[i], o, r) }, onTry = onTry,
                onNext = { if (i + 1 < cards.size) index = i + 1 else onFinish() }, last = i + 1 == cards.size)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CardView(card: Card, facts: Map<String, String>, day: java.time.LocalDate, onAnswer: (Outcome, Boolean) -> Unit, onTry: (Action) -> Unit, onNext: () -> Unit, last: Boolean) {
    // For a quiz: the option picked. For a concept: whether the answer is showing. YOU cards show everything at once.
    var picked by remember(card.id) { mutableStateOf<Int?>(null) }
    var revealed by remember(card.id) { mutableStateOf(card.kind == Kind.YOU) }
    var answered by remember(card.id) { mutableStateOf(false) }
    // Options in a fresh order each day, so you learn the answer, not its position.
    val order = remember(card.id, day) { card.options.indices.shuffled(kotlin.random.Random(card.id.hashCode() * 31 + day.toEpochDay().toInt())) }
    val good = LocalMorph.current.good
    val bad = LocalMorph.current.bad
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("${card.track.emoji} ${card.track.title}")
            Level.of(card.level).let { Pill("${it.emoji} ${it.title}") }
            if (card.kind == Kind.YOU) Pill("📊 Your numbers")
            if (card.timeSensitive) Pill("⏳ Check current rates")
        }
        Gap()
        Text(Academy.fill(card.question, facts) ?: card.question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Gap(16)
        when (card.kind) {
            Kind.QUIZ -> order.forEach { i ->
                val opt = card.options[i]
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
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Text(card.answer, style = MaterialTheme.typography.bodyLarge)
                    card.source?.let {
                        Gap(6)
                        Text("📖 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
