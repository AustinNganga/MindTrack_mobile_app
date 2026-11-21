package com.example.mindtrack_app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.data.DailyScreenTime
import com.example.mindtrack_app.data.MoodEntry
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.MoodScreenAnalysis
import com.example.mindtrack_app.data.MoodScreenPattern
import com.example.mindtrack_app.data.Moods
import com.example.mindtrack_app.data.formatDuration
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mood (dots and line, 1–5) drawn over daily screen time (bars) for the last 14 days,
 * plus a plain-language summary of the student's own pattern. This links the app's
 * objective phone data to the research question about phone use and low mood.
 */
@Composable
fun MoodVsScreenCard(moods: List<MoodEntry>, history: List<DailyScreenTime>) {
    val cs = MaterialTheme.colorScheme
    val days = MoodRepository.lastDays(14)
    val screenByDate = history.associateBy { it.date }
    val moodByDate = moods.associate { it.date to it.mood }
    val paired = days.count { it in screenByDate && it in moodByDate }
    val pattern = remember(moods, history) { MoodScreenAnalysis.pattern(moods, history) }

    val barColor = cs.secondary.copy(alpha = 0.45f)
    val lineColor = cs.primary
    val grid = cs.outlineVariant.copy(alpha = 0.5f)
    val dotCenter = cs.surface
    val maxMs = (days.mapNotNull { screenByDate[it]?.totalMillis }.maxOrNull() ?: 0L).coerceAtLeast(60 * 60_000L)

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Insights, tint = cs.primary, background = cs.primaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Mood vs screen time", style = MaterialTheme.typography.titleMedium)
                    Text("Last 14 days, measured on this phone", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendKey(barColor, square = true, label = "Screen time")
                LegendKey(lineColor, square = false, label = "Mood")
            }

            Canvas(
                Modifier.fillMaxWidth().height(170.dp).semantics {
                    contentDescription = "Chart of mood and screen time for the last 14 days. " +
                        "$paired days have both a mood and screen time."
                },
            ) {
                val top = 10.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                val plotH = bottom - top
                fun moodY(m: Int) = bottom - plotH * (m - 1) / 4f
                val slot = size.width / days.size
                val barW = slot * 0.55f

                for (m in 1..5) drawLine(grid, Offset(0f, moodY(m)), Offset(size.width, moodY(m)), strokeWidth = 1f)

                days.forEachIndexed { i, d ->
                    val ms = screenByDate[d]?.totalMillis ?: return@forEachIndexed
                    val h = plotH * (ms.toFloat() / maxMs)
                    if (h > 0f) {
                        drawRoundRect(barColor, Offset(i * slot + (slot - barW) / 2, bottom - h), Size(barW, h), CornerRadius(barW / 3))
                    }
                }

                val points = days.mapIndexedNotNull { i, d -> moodByDate[d]?.let { Offset(i * slot + slot / 2, moodY(it)) } }
                points.zipWithNext { a, b -> drawLine(lineColor, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round) }
                points.forEach {
                    drawCircle(lineColor, radius = 5.dp.toPx(), center = it)
                    drawCircle(dotCenter, radius = 2.dp.toPx(), center = it)
                }
            }

            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("EEEEE", Locale.getDefault())
            Row(Modifier.fillMaxWidth()) {
                days.forEach { d ->
                    Text(
                        runCatching { outFmt.format(inFmt.parse(d)!!) }.getOrDefault(""),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Bars: screen time (tallest = ${formatDuration(maxMs)}). Dots: mood, from ${Moods.face(1)} at the bottom to ${Moods.face(5)} at the top.",
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            )

            Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(
                    summaryText(pattern, paired),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private fun summaryText(p: MoodScreenPattern?, paired: Int): String {
    if (p == null) {
        return "Log your mood on at least ${MoodScreenAnalysis.MIN_DAYS} days to see your own pattern " +
            "($paired so far). Your screen time is saved automatically each time you open MindTrack."
    }
    fun mood(x: Double) = "${Moods.label(x.roundToInt())} (${"%.1f".format(Locale.US, x)}/5)"
    val sb = StringBuilder()
    sb.append("On your higher screen-time days (${formatDuration(p.medianMillis)} or more), your average mood was ${mood(p.moodOnHigherDays)}. ")
    sb.append("On lower days it was ${mood(p.moodOnLowerDays)}.")
    p.correlation?.let { r ->
        val strength = when {
            abs(r) < 0.1 -> "no clear link"
            abs(r) < 0.3 -> "a weak link"
            abs(r) < 0.5 -> "a moderate link"
            else -> "a strong link"
        }
        val direction = if (abs(r) < 0.1) "" else if (r < 0) ": more screen time went with lower mood" else ": more screen time went with better mood"
        sb.append("\n\nOver ${p.pairedDays} days, r = ${"%.2f".format(Locale.US, r)}, $strength$direction.")
    }
    sb.append("\n\nThis is a pattern in your own data, not proof that one causes the other.")
    return sb.toString()
}

@Composable
private fun LegendKey(color: Color, square: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(if (square) RoundedCornerShape(3.dp) else CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
