package com.morphlabs.morph.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.R
import com.morphlabs.morph.core.insight.Insight
import com.morphlabs.morph.core.insight.Tone
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.ui.Dashboard
import com.morphlabs.morph.ui.components.EntryRow
import com.morphlabs.morph.ui.components.Gap
import com.morphlabs.morph.ui.components.MorphCard
import com.morphlabs.morph.ui.components.Pill
import com.morphlabs.morph.ui.components.SectionTitle
import com.morphlabs.morph.ui.components.StatTile
import com.morphlabs.morph.ui.theme.LocalMorph

@Composable
fun HomeScreen(repo: MorphRepo, d: Dashboard, goSort: () -> Unit, goMe: () -> Unit, goAll: () -> Unit) {
    val s = d.s
    var open by remember { mutableStateOf<Entry?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Header(d) }
        item { Hero(d) }
        if (s.pending.isNotEmpty()) item {
            Gap()
            MorphCard(onClick = goSort) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.pending.size} to sort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("One tap each. Fresh ones earn bonus stars.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Go →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (s.mpesaGap != 0L) item {
            Gap()
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    Text("⚠️ M-Pesa doesn't match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your latest SMS says ${Money.format(s.lastSms?.smsBalance ?: 0)}, Morph has ${Money.format((s.lastSms?.smsBalance ?: 0) - s.mpesaGap)} at that time. " +
                            "A message was probably deleted or missed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { Thread { repo.fixMpesaGap() }.start() }) { Text("Add the difference to sort") }
                }
            }
        }
        item {
            SectionTitle("Spending")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Today", d.spentToday, Modifier.weight(1f))
                StatTile("Yesterday", d.spentYesterday, Modifier.weight(1f))
                StatTile("7 days", d.spent7, Modifier.weight(1f))
                StatTile("30 days", d.spent30, Modifier.weight(1f))
            }
        }
        val top = d.topSpending(30)
        if (top.isNotEmpty()) item {
            SectionTitle("Where it went · 30 days")
            MorphCard {
                Column(Modifier.padding(16.dp)) {
                    val max = top.first().second.coerceAtLeast(1)
                    for ((a, v) in top) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(v), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Box(
                            Modifier.padding(top = 4.dp, bottom = 10.dp).fillMaxWidth(v.toFloat() / max).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        d.insights.firstOrNull()?.let { ins ->
            item {
                SectionTitle("Today's insight", action = "All", onAction = goMe)
                InsightCard(ins)
            }
        }
        item { SectionTitle("Recent", action = "All", onAction = goAll) }
        items(s.newestFirst.filter { it.source != com.morphlabs.morph.core.model.EntrySource.SYSTEM }.take(8), key = { it.id }) { e ->
            EntryRow(e, s) { open = e }
        }
        item { Gap(88) }
    }
    open?.let { e -> EntryDialog(repo, s, e) { open = null } }
}

@Composable
private fun Header(d: Dashboard) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.morph_wing), contentDescription = null, modifier = Modifier.height(36.dp))
        Spacer(Modifier.width(8.dp))
        Text("Morph", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (d.streak > 0) Pill("🔥 ${d.streak}")
        Spacer(Modifier.width(6.dp))
        Pill("${d.level.emoji} ${d.stars}⭐")
    }
}

@Composable
private fun Hero(d: Dashboard) {
    val m = LocalMorph.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(m.hero).padding(20.dp)
    ) {
        Text("Spent today", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        Text(Money.format(d.spentToday), color = m.onHero, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Gap(4)
        val safe = d.safeToSpend
        Text(
            if (safe != null) "Safe to spend today: ${Money.format(safe)}" else "Set budgets to see what's safe to spend",
            color = m.gold, fontWeight = FontWeight.SemiBold,
        )
        Gap(16)
        Row {
            Column(Modifier.weight(1f)) {
                Text("📱 M-Pesa", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(d.mpesaBalance), color = m.onHero, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Net worth", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(d.netWorth.net), color = m.onHero, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("In this month", color = m.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(d.receivedThisMonth), color = m.onHero, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InsightCard(i: Insight) {
    val m = LocalMorph.current
    val accent = when (i.tone) { Tone.GOOD -> m.good; Tone.WATCH -> m.gold; Tone.INFO -> MaterialTheme.colorScheme.tertiary }
    MorphCard {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${i.emoji}  ${i.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Gap(4)
                Text(i.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors()) { Text(text) }
}
