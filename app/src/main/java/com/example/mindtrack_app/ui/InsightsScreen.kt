package com.example.mindtrack_app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindtrack_app.MindTrackViewModel
import com.example.mindtrack_app.data.MoodEntry
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.Moods
import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.data.ScreenTimeSummary
import com.example.mindtrack_app.data.formatDuration
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun InsightsScreen(vm: MindTrackViewModel, contentPadding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your daily mood and how you actually use your phone. Everything here stays on this phone.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoodCard(vm)
        MoodHistoryCard(vm.moods)
        ScreenTimeCard(vm)
    }
}

// ======================= MOOD =======================

/** Five tappable faces. Used on Home and Insights. */
@Composable
fun MoodPicker(selected: Int?, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..5).forEach { m ->
            val isSel = selected == m
            val bg by animateColorAsState(if (isSel) cs.primaryContainer else cs.surfaceContainer, label = "mood")
            Surface(
                onClick = { onPick(m) },
                shape = CircleShape,
                color = bg,
                border = if (isSel) BorderStroke(2.dp, cs.primary) else null,
                modifier = Modifier.size(54.dp).semantics { contentDescription = "Mood: ${Moods.label(m)}" + if (isSel) ", selected" else "" },
            ) {
                Box(contentAlignment = Alignment.Center) { Text(Moods.face(m), fontSize = 26.sp) }
            }
        }
    }
}

@Composable
private fun MoodCard(vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    val today = vm.todayMood
    var note by rememberSaveable(today?.date) { mutableStateOf(today?.note.orEmpty()) }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("How are you feeling today?", style = MaterialTheme.typography.titleMedium)
            MoodPicker(today?.mood) { vm.logMood(it, note) }
            if (today != null) {
                Text(
                    "Logged: ${Moods.label(today.mood)} ${Moods.face(today.mood)}. Tap another face to change it.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.secondary,
                )
                OutlinedTextField(
                    value = note, onValueChange = { if (it.length <= 200) note = it },
                    label = { Text("Add a note (optional)") },
                    placeholder = { Text("What's on your mind?") },
                    shape = MaterialTheme.shapes.medium, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                if (note.trim() != today.note) {
                    TextButton(onClick = { vm.logMood(today.mood, note) }) { Text("Save note") }
                }
            } else {
                Text("One tap a day builds a picture of how you're doing.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MoodHistoryCard(moods: List<MoodEntry>) {
    val cs = MaterialTheme.colorScheme
    val days = MoodRepository.lastDays(14)
    val byDate = moods.associateBy { it.date }
    val logged = days.mapNotNull { byDate[it] }
    AppCard {
        Column {
            Text("Last 14 days", style = MaterialTheme.typography.titleMedium)
            Text(
                if (logged.isEmpty()) "No moods logged yet."
                else "Logged on ${logged.size} of 14 days · average: ${Moods.label(Math.round(logged.map { it.mood }.average()).toInt())}",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("EEEEE", Locale.getDefault())
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { d ->
                        val e = byDate[d]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(if (e != null) cs.primaryContainer else cs.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) { Text(e?.let { Moods.face(it.mood) } ?: "·", fontSize = if (e != null) 18.sp else 14.sp) }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                runCatching { outFmt.format(inFmt.parse(d)!!) }.getOrDefault(""),
                                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ======================= SCREEN TIME =======================

@Composable
private fun ScreenTimeCard(vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Phone, tint = cs.secondary, background = cs.secondaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Your real screen time", style = MaterialTheme.typography.titleMedium)
                    Text("Measured by Android, not self-reported", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            when {
                !vm.screenTimeAllowed -> {
                    Text(
                        "The check-in asks how you feel about your phone use. This shows what actually happens: daily screen time, unlocks and your most-used apps. " +
                            "To see it, allow MindTrack \"Usage access\" in Settings. The data is read on this phone only and is never uploaded.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    PrimaryButton("Allow usage access", onClick = {
                        runCatching { context.startActivity(vm.screenTimePermissionIntent()) }
                    })
                    Text(
                        "In the list, tap MindTrack, switch on \"Permit usage access\", then come back.",
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    )
                }
                vm.screenTime == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> ScreenTimeContent(vm.screenTime!!, vm)
            }
        }
    }
}

@Composable
private fun ScreenTimeContent(st: ScreenTimeSummary, vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Today", formatDuration(st.today.totalMillis), Modifier.weight(1f))
        StatTile("Unlocks today", st.today.unlocks?.toString() ?: "n/a", Modifier.weight(1f))
        StatTile("7-day average", formatDuration(st.weekAverageMillis), Modifier.weight(1f))
    }

    Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
    WeekBars(st)

    if (st.topAppsToday.isNotEmpty()) {
        Text("Most used today", style = MaterialTheme.typography.titleMedium)
        val max = st.topAppsToday.first().millis.coerceAtLeast(1)
        st.topAppsToday.forEach { app ->
            Column {
                Row {
                    Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatDuration(app.millis), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { app.millis.toFloat() / max },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = cs.secondary, trackColor = cs.surfaceVariant, strokeCap = StrokeCap.Round,
                )
            }
        }
    }

    vm.checkIns.firstOrNull()?.let { last ->
        Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(
                "In your last check-in, your answers put your phone dependence at \"${QuestionBank.tmdBand(last.tmdTotal)}\" (${last.tmdTotal}/88). " +
                    "Does your real screen time match how it feels?",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WeekBars(st: ScreenTimeSummary) {
    val cs = MaterialTheme.colorScheme
    val maxMs = st.week.maxOf { it.totalMillis }.coerceAtLeast(60_000L)
    val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val barColor = cs.secondary
    val todayColor = cs.primary
    val track = cs.surfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().aspectRatio(2.6f)) {
            val n = st.week.size
            val slot = size.width / n
            val barW = slot * 0.5f
            st.week.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                val h = size.height * (d.totalMillis.toFloat() / maxMs)
                if (h > 0f) {
                    drawRoundRect(
                        if (i == n - 1) todayColor else barColor,
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            st.week.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayFmt.format(d.dayStart), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(formatDuration(d.totalMillis), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
