package com.morphlabs.morph.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphlabs.morph.core.budget.Envelope
import com.morphlabs.morph.core.model.Entry
import com.morphlabs.morph.core.model.EntryStatus
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.data.Snapshot
import com.morphlabs.morph.ui.theme.LocalMorph
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) Text(
            action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
        )
    }
}

@Composable
fun MorphCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = colors) { content() }
    else Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = colors) { content() }
}

@Composable
fun StatTile(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.compact(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Thin progress bar coloured by meaning: spending limits turn gold then red; targets turn green when reached. */
@Composable
fun EnvelopeBar(e: Envelope, modifier: Modifier = Modifier) {
    val m = LocalMorph.current
    val color = when {
        e.over -> m.bad
        e.reached -> m.good
        e.isLimit && e.progress > 0.85f -> m.gold
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { e.progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = m.track,
        drawStopIndicator = {},
    )
}

private val timeFmt = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)

fun whenText(ts: Long, zone: ZoneId): String = timeFmt.format(Instant.ofEpochMilli(ts).atZone(zone))

@Composable
fun EntryRow(e: Entry, s: Snapshot, onClick: () -> Unit) {
    val a = e.categoryId?.let { s.byId[it] }
    val out = s.outgoing(e)
    val split = s.categories(e).size > 1
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { Text(a?.emoji ?: "❔", fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                (if (e.status == EntryStatus.NEEDS_REVIEW) "To sort · " else if (split) "Split · " else (a?.name?.plus(" · ") ?: "")) +
                    (e.note?.let { "“$it” · " } ?: "") + whenText(e.timestamp, s.zone),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (out) "−" else "+") + Money.format(s.amount(e), withCurrency = false),
            fontWeight = FontWeight.SemiBold,
            color = if (out) MaterialTheme.colorScheme.onSurface else LocalMorph.current.good,
        )
    }
}

/** The reward moment: stars fly out from the middle and a "+3 ⭐" pops. Calls [onDone] when finished. */
@Composable
fun StarBurst(stars: Int, label: String, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        onDone()
    }
    val v = p.value
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val count = 10
        for (i in 0 until count) {
            val angle = (i * 360.0 / count + 18) * Math.PI / 180
            val r = 40 + 220 * v
            Text(
                "⭐",
                fontSize = (18 + (i % 3) * 8).sp,
                modifier = Modifier
                    .offset { IntOffset((cos(angle) * r).roundToInt(), (sin(angle) * r).roundToInt()) }
                    .graphicsLayer { alpha = (1f - v).coerceIn(0f, 1f); rotationZ = 180 * v },
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = if (v < 0.25f) 0.5f + 2f * v else 1f
                scaleX = s; scaleY = s; alpha = if (v > 0.8f) (1f - v) * 5f else 1f
            }
        ) {
            Text("+$stars ⭐", fontSize = 44.sp, fontWeight = FontWeight.Black, color = LocalMorph.current.gold)
            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xAA2E1260)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text, color = content, style = MaterialTheme.typography.labelLarge,
        modifier = modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

val RowGap = Arrangement.spacedBy(10.dp)
