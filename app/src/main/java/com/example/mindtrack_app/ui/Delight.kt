package com.example.mindtrack_app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ======================= ENTRANCE: cards rise in one after another =======================

/** One counter per screen visit, so the cards on that screen enter in order (a "stagger"). */
class StaggerCounter {
    private val createdAt = SystemClock.uptimeMillis()
    private var next = 0

    /** Index for the next card, or -1 once the screen has settled (cards scrolled in later don't animate). */
    fun take(): Int = if (SystemClock.uptimeMillis() - createdAt > 700) -1 else next++
}

/** Provided per screen in MainActivity. When absent (check-in, result), nothing animates. */
val LocalStagger = staticCompositionLocalOf<StaggerCounter?> { null }

/** Fades and lifts the element into place, slightly after the one before it. */
@Composable
fun Modifier.staggerIn(): Modifier {
    val counter = LocalStagger.current ?: return this
    val index = remember { counter.take() }
    if (index < 0) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(6) * 70L)
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

// ======================= PRESS: tappable cards sink slightly =======================

@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

// ======================= HERO: slow floating light bubbles =======================

@Composable
fun Modifier.floatingBubbles(): Modifier {
    val t = rememberInfiniteTransition(label = "bubbles")
    val a = t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = t.animateFloat(0f, 1f, infiniteRepeatable(tween(9500, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    // Values are read inside drawBehind, so only drawing repeats each frame (no recomposition).
    return this.drawBehind {
        drawCircle(
            Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.55f,
            center = Offset(size.width * (0.88f - 0.18f * a.value), size.height * (0.05f + 0.3f * b.value)),
        )
        drawCircle(
            Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.35f,
            center = Offset(size.width * (0.08f + 0.2f * b.value), size.height * (0.98f - 0.25f * a.value)),
        )
    }
}

// ======================= CELEBRATION: a burst of sparkles =======================

/** Dots fly out from the centre and fade. Change [trigger] (to a value above 0) to fire it again. */
@Composable
fun SparkleBurst(
    trigger: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    particles: Int = 14,
    reach: Dp = 46.dp,
) {
    val progress = remember { Animatable(1f) }
    val seeds = remember(trigger) { List(particles) { Random.nextFloat() } }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val maxR = reach.toPx()
        seeds.forEachIndexed { i, s ->
            val angle = (i.toFloat() / particles) * 2f * PI.toFloat() + s * 0.6f
            val dist = maxR * (0.55f + 0.45f * s) * p
            val pos = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
            val r = (2.5.dp.toPx() + s * 3.dp.toPx()) * (1f - p * 0.6f)
            drawCircle(colors[i % colors.size].copy(alpha = 1f - p), radius = r, center = pos)
        }
    }
}

// ======================= 1-MINUTE BREATHER =======================

private const val BREATH_ROUNDS = 5 // 5 x (4 s in + 2 s hold + 6 s out) = 60 s

/**
 * A guided breathing exercise: the circle grows as you breathe in and shrinks as you breathe out.
 * Slow breathing with a longer out-breath is a common, simple way to calm down.
 */
@Composable
fun BreatherDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val cs = MaterialTheme.colorScheme
        val haptic = LocalHapticFeedback.current
        val scale = remember { Animatable(0.55f) }
        var phase by remember { mutableStateOf("Get comfortable") }
        var round by remember { mutableIntStateOf(0) }
        var done by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(1500)
            repeat(BREATH_ROUNDS) { i ->
                round = i + 1
                phase = "Breathe in…"
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                phase = "Hold"
                delay(2000)
                phase = "Breathe out…"
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scale.animateTo(0.55f, tween(6000, easing = FastOutSlowInEasing))
            }
            phase = "Well done"
            done = true
        }

        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.background))),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Text("1-minute breather", style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        done -> "Notice how you feel now."
                        round == 0 -> "Follow the circle"
                        else -> "Round $round of $BREATH_ROUNDS"
                    },
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(36.dp))
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(260.dp)
                            .graphicsLayer { scaleX = scale.value * 1.1f; scaleY = scale.value * 1.1f; alpha = 0.22f }
                            .clip(CircleShape).background(cs.primary),
                    )
                    Box(
                        Modifier.size(220.dp)
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                            .clip(CircleShape).background(HeroBrush),
                    )
                    if (done) SparkleBurst(trigger = 1, colors = listOf(cs.primary, cs.secondary, cs.tertiary), modifier = Modifier.size(120.dp), particles = 18, reach = 120.dp)
                }
                Spacer(Modifier.height(36.dp))
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                    label = "phase",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(40.dp))
                PrimaryButton(if (done) "Done" else "Stop", onClick = onClose)
            }
        }
    }
}
