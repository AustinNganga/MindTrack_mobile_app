package com.example.mindtrack_app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.data.CheckIn
import com.example.mindtrack_app.data.Profile
import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.ui.theme.RiskElevated
import com.example.mindtrack_app.ui.theme.RiskLow

// ======================= HOME =======================

@Composable
fun HomeScreen(
    profile: Profile?,
    checkIns: List<CheckIn>,
    questionCount: Int,
    contentPadding: PaddingValues,
    onStart: () -> Unit,
    onOpen: (CheckIn) -> Unit,
    onSeeHistory: () -> Unit,
    todayMood: Int?,
    onLogMood: (Int) -> Unit,
    onOpenInsights: () -> Unit,
    moodStreak: Int = 0,
    onBreathe: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val latest = checkIns.firstOrNull()
    // A friendly wave when Home opens.
    val wave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(450)
        repeat(2) { wave.animateTo(18f, tween(150)); wave.animateTo(-10f, tween(150)) }
        wave.animateTo(0f, tween(150))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Column(Modifier.staggerIn()) {
            val name = profile?.name.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (name.isBlank()) greeting() else "${greeting()}, $name",
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " 👋", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = wave.value
                        transformOrigin = TransformOrigin(0.7f, 0.9f)
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(System.currentTimeMillis(), "EEEE, d MMMM"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                if (moodStreak > 0) {
                    Spacer(Modifier.width(10.dp))
                    Surface(color = cs.tertiaryContainer, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥 ", style = MaterialTheme.typography.labelLarge)
                            AnimatedContent(
                                targetState = moodStreak,
                                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                                label = "streak",
                            ) { n -> Text("$n", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer) }
                            Text("-day streak", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
                        }
                    }
                }
            }
        }

        // Hero call to action
        Box(Modifier.staggerIn().fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(HeroBrush).floatingBubbles()) {
            Column(Modifier.padding(24.dp)) {
                Text("How are you, really?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Take a $questionCount-question check-in on your phone habits and how you've been feeling. About 4 minutes.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(20.dp))
                Surface(onClick = onStart, shape = MaterialTheme.shapes.large, color = Color.White, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (latest == null) "Start my first check-in" else "Start a new check-in", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    }
                }
            }
        }

        // Daily one-tap mood
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (todayMood == null) "How are you feeling today?" else "Today: ${com.example.mindtrack_app.data.Moods.label(todayMood)}",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenInsights) { Text("Insights") }
                }
                MoodPicker(todayMood, onBreathe = onBreathe, onPick = onLogMood)
            }
        }

        // Calm-down shortcut
        AppCard(onClick = onBreathe) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Moon, tint = cs.primary, background = cs.primaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Need a moment?", style = MaterialTheme.typography.titleMedium)
                    Text("Try a calming 1-minute breather", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Icon(MtIcons.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
            }
        }

        if (latest != null) {
            AppCard(onClick = { onOpen(latest) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Latest check-in", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        LevelChip(latest.level)
                        Spacer(Modifier.height(8.dp))
                        Text(formatDate(latest.timestamp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Text("Phone dependence: ${QuestionBank.tmdBand(latest.tmdTotal)} (${latest.tmdTotal}/88)", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Icon(MtIcons.ChevronRight, contentDescription = "Open", tint = cs.onSurfaceVariant)
                }
            }
            if (checkIns.size > 1) {
                AppCard(onClick = onSeeHistory) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your recent check-ins", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("See all", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            checkIns.take(7).reversed().forEach { c ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(c.level.container()), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(c.level.color()))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatDate(c.timestamp, "d/M"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(MtIcons.Sparkle, tint = cs.secondary, background = cs.secondaryContainer)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Check in every week or two to notice how your mood and phone habits change over time.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        SupportCard(prominent = false)
        Spacer(Modifier.height(8.dp))
    }
}

// ======================= HISTORY =======================

@Composable
fun HistoryScreen(checkIns: List<CheckIn>, threshold: Double, contentPadding: PaddingValues, onOpen: (CheckIn) -> Unit, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (checkIns.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(MtIcons.History, tint = cs.primary, background = cs.primaryContainer, size = 80.dp)
            Spacer(Modifier.height(20.dp))
            Text("No check-ins yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Your results will appear here so you can see how things change over time.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Start a check-in", onClick = onStart)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("${checkIns.size} check-in${if (checkIns.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
        if (checkIns.size >= 2) item { TrendCard(checkIns, threshold) }
        items(checkIns, key = { it.id }) { c ->
            AppCard(onClick = { onOpen(c) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(c.timestamp, "EEE d MMM yyyy"), style = MaterialTheme.typography.titleMedium)
                        Text("${formatDate(c.timestamp, "HH:mm")} · Phone dependence ${QuestionBank.tmdBand(c.tmdTotal).lowercase()}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    LevelChip(c.level)
                    Spacer(Modifier.width(4.dp))
                    Icon(MtIcons.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrendCard(checkIns: List<CheckIn>, threshold: Double) {
    val cs = MaterialTheme.colorScheme
    val points = checkIns.take(12).reversed()
    AppCard {
        Column {
            Text("Trend", style = MaterialTheme.typography.titleMedium)
            Text("Higher = closer to the elevated range. Dashed line = screening cut-off.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            val line = cs.primary
            val grid = cs.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
                fun y(v: Double) = size.height * (1f - v.coerceIn(0.0, 1.0).toFloat())
                drawLine(grid, Offset(0f, y(threshold)), Offset(size.width, y(threshold)), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                for (i in 1 until points.size) {
                    drawLine(line, Offset((i - 1) * stepX, y(points[i - 1].riskScore)), Offset(i * stepX, y(points[i].riskScore)), strokeWidth = 5f)
                }
                points.forEachIndexed { i, c ->
                    val color = if (c.riskScore >= threshold) RiskElevated else RiskLow
                    drawCircle(Color.White, radius = 11f, center = Offset(i * stepX, y(c.riskScore)))
                    drawCircle(color, radius = 8f, center = Offset(i * stepX, y(c.riskScore)))
                }
            }
        }
    }
}

// ======================= ABOUT =======================

@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("How MindTrack works", style = MaterialTheme.typography.headlineMedium)
        AboutBlock("The research behind it",
            "MindTrack was trained on survey answers from 1,220 university students, who completed questionnaires on phone dependence (TMD), anxiety (STAI) and depression (Beck Depression Inventory).")
        AboutBlock("What it estimates",
            "Whether your answers look more like students who scored in the normal range on the Beck questionnaire (0–10) or those who scored 11 or above (mild low mood or stronger).")
        AboutBlock("How accurate is it?",
            "On students it had never seen, it ranked a struggling student above a non-struggling one about 3 times out of 4, and flagged about 8 in 10 students who scored 11+. It also flags some students who are fine, so treat an \"Elevated\" result as a prompt to reflect and talk to someone, not as a verdict.")
        AboutBlock("Runs on your phone",
            "The prediction model (a random forest of 300 decision trees) runs entirely on your device, even offline. Results are only copied to the cloud if you turn on backup in Profile, and your answers are never uploaded.")
        AboutBlock("Limitations",
            "The data comes from one university at one point in time, so results may differ for other students. MindTrack shows patterns, not causes: it does not show that phone use causes low mood. It is not a medical device or a diagnosis.")
        Text("MindTrack · student research prototype", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AboutBlock(title: String, body: String) {
    AppCard {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ======================= PROFILE =======================

@Composable
fun ProfileScreen(
    profile: Profile?,
    checkInCount: Int,
    contentPadding: PaddingValues,
    cloudEmail: String?,
    cloudBusy: Boolean,
    cloudMessage: String?,
    onSaveName: (String) -> Unit,
    onTurnOnBackup: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAll: () -> Unit,
    settingsContent: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(HeroBrush), contentAlignment = Alignment.Center) {
                Text(profile?.name?.firstOrNull()?.uppercase() ?: "☺", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(profile?.name?.ifBlank { null } ?: "Your profile", style = MaterialTheme.typography.headlineSmall)
                Text("$checkInCount check-in${if (checkInCount == 1) "" else "s"} saved on this phone", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text(if (cloudEmail != null) "Cloud backup: on" else "Cloud backup: off", style = MaterialTheme.typography.labelMedium, color = if (cloudEmail != null) cs.secondary else cs.onSurfaceVariant)
            }
        }
        AppCard {
            Column {
                Text("Display name", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 30) name = it },
                    singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Save", onClick = { onSaveName(name) }, enabled = name.trim() != profile?.name)
            }
        }
        AppCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(MtIcons.History, tint = cs.secondary, background = cs.secondaryContainer, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Cloud backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (cloudEmail != null) "Signed in as $cloudEmail" else "Off. Your check-ins are only on this phone.",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Backup saves your results (date, Low/Elevated, phone-dependence score) to your private Firebase account so you can restore them on a new phone. Your individual answers are never uploaded.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                cloudMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = cs.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (cloudEmail == null) {
                    PrimaryButton("Turn on cloud backup", onClick = onTurnOnBackup)
                } else {
                    PrimaryButton("Sync now", onClick = onSyncNow, loading = cloudBusy)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Turn off backup (sign out)", onClick = onSignOut)
                }
            }
        }
        settingsContent()
        AppCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(MtIcons.Lock, tint = cs.primary, background = cs.primaryContainer, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Privacy", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your check-ins are stored on this phone. If you turn on cloud backup, only your results are copied to your own private account, which nobody else can read.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = cs.outlineVariant)
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(MtIcons.Delete, contentDescription = null, tint = cs.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete all my data", color = cs.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    if (cloudEmail != null) "This permanently removes your name and all $checkInCount check-ins from this phone AND deletes your cloud backup, then signs you out."
                    else "This permanently removes your name and all $checkInCount check-ins from this phone.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAll() }) { Text("Delete", color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
