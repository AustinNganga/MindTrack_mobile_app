package com.example.mindtrack_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.MindTrackViewModel
import com.example.mindtrack_app.data.CheckIn
import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.data.RiskLevel
import com.example.mindtrack_app.ui.theme.RiskElevated
import com.example.mindtrack_app.ui.theme.RiskLow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Score on a 0–100 scale, as shown to the student. */
private fun score100(p: Double) = (p * 100).roundToInt()
private fun points(c: Double) = String.format(Locale.US, "%+.1f", c * 100)

// ======================= WHY THIS RESULT? =======================

/**
 * Explainable AI: lists the answers that pushed this result up or down, with how many points each
 * contributed (tree-path contributions computed by MindTrackModel.explain).
 */
@Composable
fun WhyThisResultCard(vm: MindTrackViewModel, checkIn: CheckIn) {
    val cs = MaterialTheme.colorScheme
    val explanation = remember(checkIn.id) { vm.explain(checkIn) }
    val inputs = remember(checkIn.id) { vm.inputsOf(checkIn) }
    var showAll by rememberSaveable(checkIn.id) { mutableStateOf(false) }

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Sparkle, tint = cs.primary, background = cs.primaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Why this result?", style = MaterialTheme.typography.titleMedium)
                    Text("How the AI model reached it", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }

            if (explanation == null || inputs == null) {
                Text(
                    "A detailed explanation is only available on the phone where this check-in was taken, " +
                        "because your individual answers are never uploaded to the cloud.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            } else {
                Text(
                    "MindTrack's model is 300 decision trees that learned patterns from 1,220 university students. " +
                        "Each of your answers nudged your score up or down. These had the biggest effect:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val sorted = explanation.contributions.entries.sortedByDescending { abs(it.value) }
                val maxAbs = sorted.firstOrNull()?.value?.let { abs(it) }?.coerceAtLeast(1e-6) ?: 1.0
                val up = sorted.filter { it.value > 0.002 }.take(3)
                val down = sorted.filter { it.value < -0.002 }.take(3)

                if (up.isNotEmpty()) {
                    Text("⬆  Pushed your score up", style = MaterialTheme.typography.titleSmall, color = RiskElevated)
                    up.forEach { FactorRow(it.key, inputs.getValue(it.key), it.value, maxAbs) }
                }
                if (down.isNotEmpty()) {
                    Text("⬇  Pulled your score down", style = MaterialTheme.typography.titleSmall, color = RiskLow)
                    down.forEach { FactorRow(it.key, inputs.getValue(it.key), it.value, maxAbs) }
                }

                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(start = 0.dp)) {
                    Text(if (showAll) "Show fewer" else "Show all ${sorted.size} factors")
                }
                if (showAll) {
                    sorted.forEach { FactorRow(it.key, inputs.getValue(it.key), it.value, maxAbs) }
                }

                Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "How to read this: the model starts every student at ${score100(explanation.baseline)} points and " +
                            "each answer adds or removes points. Yours ended at ${score100(explanation.probability)}; " +
                            "${score100(vm.threshold)} or more counts as Elevated. These are patterns from the study data, " +
                            "not causes, and not a diagnosis.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FactorRow(feature: String, value: Double, contribution: Double, maxAbs: Double) {
    val cs = MaterialTheme.colorScheme
    val color: Color = if (contribution >= 0) RiskElevated else RiskLow
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(QuestionBank.questionFor(feature), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${points(contribution)} pts", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
        }
        Text("Your answer: ${QuestionBank.answerText(feature, value)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth((abs(contribution) / maxAbs).toFloat().coerceIn(0.03f, 1f))
                    .height(6.dp).clip(CircleShape).background(color),
            )
        }
    }
}

// ======================= WHAT IF…? =======================

/**
 * Lets the student change their most influential answers (and their phone-dependence score) and
 * see the model's result update instantly. Nothing is saved.
 */
@Composable
fun WhatIfCard(vm: MindTrackViewModel, checkIn: CheckIn) {
    val cs = MaterialTheme.colorScheme
    val explanation = remember(checkIn.id) { vm.explain(checkIn) }
    val base = remember(checkIn.id) { vm.inputsOf(checkIn) }
    if (explanation == null || base == null) return

    // The 4 most influential multiple-choice answers for this student.
    val choiceFeatures = remember(checkIn.id) {
        explanation.contributions.entries.sortedByDescending { abs(it.value) }
            .map { it.key }.filter { QuestionBank.choiceQuestion(it) != null }.take(4)
    }
    var overrides by remember(checkIn.id) { mutableStateOf(mapOf<String, Double>()) }
    val score = remember(overrides) { vm.scoreWith(checkIn, overrides) ?: checkIn.riskScore }
    val level = if (score >= vm.threshold) RiskLevel.ELEVATED else RiskLevel.LOW
    val diff = score100(score) - score100(checkIn.riskScore)

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Insights, tint = cs.secondary, background = cs.secondaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("What if…?", style = MaterialTheme.typography.titleMedium)
                    Text("Change an answer and watch the result update", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }

            RiskGauge(score, vm.threshold, level)
            Text(
                "Score: ${score100(checkIn.riskScore)} → ${score100(score)}" +
                    when {
                        diff > 0 -> "  (+$diff)"
                        diff < 0 -> "  ($diff)"
                        else -> ""
                    },
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            // Phone dependence (0-88)
            val tmd = overrides["TMD_Total"] ?: base.getValue("TMD_Total")
            Text("Phone-dependence score", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = tmd.toFloat(),
                onValueChange = { overrides = overrides + ("TMD_Total" to it.roundToInt().toDouble()) },
                valueRange = 0f..88f,
            )
            Text(QuestionBank.answerText("TMD_Total", tmd), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)

            // Most influential answers
            choiceFeatures.forEach { f ->
                val q = QuestionBank.choiceQuestion(f) ?: return@forEach
                val current = overrides[f] ?: base.getValue(f)
                Text(q.prompt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.options.forEach { o ->
                        FilterChip(
                            selected = current == o.code.toDouble(),
                            onClick = { overrides = overrides + (f to o.code.toDouble()) },
                            label = { Text(o.label) },
                        )
                    }
                }
            }

            if (overrides.isNotEmpty()) {
                TextButton(onClick = { overrides = emptyMap() }) { Text("Reset to my real answers") }
            }
            Text(
                "Trying answers here doesn't change your saved result.",
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            )
        }
    }
}
