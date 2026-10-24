package com.morphlabs.morph.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The big one: today's transactions are all sorted. Butterflies and stars rain down, the card
 * pops in, the phone gives a little double buzz. Tap anywhere (or wait) to carry on.
 */
@Composable
fun Celebration(stars: Int, streak: Int, levelLine: String, onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val fall = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val pieces = remember {
        val r = Random(System.nanoTime())
        List(36) {
            Piece(
                emoji = listOf("🦋", "⭐", "✨", "🦋", "🎉", "⭐", "💜", "💛")[it % 8],
                x = r.nextFloat(), delay = r.nextFloat() * 0.35f, sway = 20f + r.nextFloat() * 50f,
                size = 18 + r.nextInt(18), spin = (r.nextFloat() - 0.5f) * 540f,
            )
        }
    }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { delay(180); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        fall.animateTo(1f, tween(3200, easing = LinearEasing))
        onDone()
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0x99120B1F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        for (p in pieces) {
            val t = ((fall.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            Text(
                p.emoji, fontSize = p.size.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset((p.x * wPx + sin(t * 9f + p.x * 6f) * p.sway).roundToInt(), (-80 + t * (hPx + 160)).roundToInt()) }
                    .graphicsLayer { rotationZ = p.spin * t; alpha = if (t <= 0f) 0f else 1f - (t - 0.85f).coerceAtLeast(0f) * 6f },
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer { scaleX = 0.6f + 0.4f * pop.value; scaleY = 0.6f + 0.4f * pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text("🦋", fontSize = 64.sp)
            Text("Day complete!", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("Every one of today's transactions is sorted.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Gap(10)
            Text("+$stars ⭐", fontSize = 36.sp, fontWeight = FontWeight.Black, color = com.morphlabs.morph.ui.theme.LocalMorph.current.gold)
            if (streak > 0) Text("🔥 $streak-day streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(levelLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private data class Piece(val emoji: String, val x: Float, val delay: Float, val sway: Float, val size: Int, val spin: Float)
