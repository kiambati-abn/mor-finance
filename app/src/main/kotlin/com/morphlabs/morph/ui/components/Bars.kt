package com.morphlabs.morph.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morphlabs.morph.core.money.Money
import com.morphlabs.morph.ui.theme.LocalMorph

/**
 * One series of vertical bars in the brand purple. The selected bar (last by default, tap to
 * change) is solid and its value is shown above the chart; the others are lighter.
 */
@Composable
fun BarChart(items: List<Pair<String, Long>>, modifier: Modifier = Modifier, height: Int = 120, initial: Int = items.lastIndex) {
    if (items.isEmpty()) return
    var selected by remember(items) { mutableIntStateOf(initial.coerceIn(0, items.lastIndex)) }
    val max = items.maxOf { it.second }.coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        val (label, value) = items[selected]
        Text("$label: ${Money.format(value)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(height.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEachIndexed { i, (_, v) ->
                val frac = (v.toFloat() / max).coerceIn(0.02f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selected = i },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(frac)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (i == selected) color else color.copy(alpha = 0.35f))
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalMorph.current.track))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, (l, _) ->
                Text(
                    l, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A labelled horizontal bar: name on the left, amount on the right, bar underneath. */
@Composable
fun HBar(label: String, value: Long, max: Long, sub: String? = null, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier
    Column(m.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(Money.format(value), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(LocalMorph.current.track)) {
            Box(Modifier.fillMaxWidth((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.width(8.dp))
