package com.example.mindtrack_app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.MindTrackViewModel
import com.example.mindtrack_app.data.formatDuration
import com.example.mindtrack_app.reminders.Reminders
import com.example.mindtrack_app.ui.theme.RiskElevated
import java.util.Locale
import kotlin.math.roundToInt

// ======================= HOME: GOAL RING =======================

/** Today's screen time against the goal, as an animated ring. Tapping opens the goal settings. */
@Composable
fun ScreenGoalHomeCard(vm: MindTrackViewModel, onOpenSettings: () -> Unit) {
    if (!vm.screenTimeAllowed) return
    val cs = MaterialTheme.colorScheme
    val goalMin = vm.screenGoalMinutes

    if (goalMin <= 0) {
        AppCard(onClick = onOpenSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Phone, tint = cs.secondary, background = cs.secondaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Set a daily screen-time goal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        vm.suggestedGoalMinutes?.let { "Based on your week, try ${formatDuration(it * 60_000L)} a day." }
                            ?: "Small, steady goals work best.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
                Icon(MtIcons.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
            }
        }
        return
    }

    val used = vm.screenTime?.today?.totalMillis ?: 0L
    val goalMs = goalMin * 60_000L
    val frac = used.toFloat() / goalMs
    val ringColor = when {
        frac >= 1f -> RiskElevated
        frac >= 0.8f -> cs.tertiary
        else -> cs.secondary
    }
    val track = cs.surfaceVariant
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(frac) { sweep.animateTo(frac.coerceAtMost(1f), tween(1000, easing = FastOutSlowInEasing)) }

    val status = when {
        frac < 0.8f -> "${formatDuration(goalMs - used)} left today. Nice pace 🌿"
        frac < 1f -> "Almost there: ${formatDuration(goalMs - used)} left."
        else -> "Over by ${formatDuration(used - goalMs)}. Maybe a short break? 🚶"
    }

    AppCard(onClick = onOpenSettings) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val stroke = 10.dp.toPx()
                    val tl = Offset(stroke / 2, stroke / 2)
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (sweep.value > 0f) {
                        drawArc(ringColor, -90f, 360f * sweep.value, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(used), style = MaterialTheme.typography.titleMedium)
                    Text("of ${formatDuration(goalMs)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Screen-time goal", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (frac >= 1f) RiskElevated else cs.onSurfaceVariant)
                vm.goalDaysMet?.let { (met, total) ->
                    Text(
                        when {
                            met == total -> "🎉 Goal met on all of the last $total days!"
                            met > 0 -> "🎉 Goal met on $met of the last $total days"
                            else -> "Tomorrow is a fresh start 🌱"
                        },
                        style = MaterialTheme.typography.labelLarge, color = cs.primary,
                    )
                }
            }
        }
    }
}

// ======================= PROFILE: GOAL SETTINGS =======================

@Composable
fun ScreenGoalCard(vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAction?.invoke()
        else note = "Notifications are blocked. You can allow them in Settings → Apps → MindTrack → Notifications."
        pendingAction = null
    }
    fun withNotifications(action: () -> Unit) {
        if (Reminders.canPostNotifications(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) action()
        else {
            pendingAction = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val startValue = (vm.screenGoalMinutes.takeIf { it > 0 } ?: vm.suggestedGoalMinutes ?: 180).toFloat()
    var slider by remember(vm.screenGoalMinutes) { mutableFloatStateOf(startValue) }

    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Phone, tint = cs.secondary, background = cs.secondaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Screen-time goal", style = MaterialTheme.typography.titleMedium)
                    Text("A daily target, with gentle nudges", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (!vm.screenTimeAllowed) {
                Text(
                    "To set a goal, first allow \"Usage access\" on the Insights tab, so MindTrack can measure your screen time.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            } else {
                SettingRow("Daily goal", "Track your screen time against a target", vm.screenGoalMinutes > 0) { on ->
                    if (on) withNotifications { vm.setScreenGoal(slider.roundToInt()) } else vm.setScreenGoal(0)
                }
                if (vm.screenGoalMinutes > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Goal: ${formatDuration(slider.roundToInt() * 60_000L)} a day", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                    Slider(
                        value = slider,
                        onValueChange = { slider = ((it / 15f).roundToInt() * 15).toFloat() },
                        onValueChangeFinished = { vm.setScreenGoal(slider.roundToInt()) },
                        valueRange = 15f..480f,
                        // No tick marks (30 dots looked cluttered); onValueChange already snaps to 15 minutes.
                    )
                    vm.suggestedGoalMinutes?.let { s ->
                        if (s != vm.screenGoalMinutes) {
                            TextButton(onClick = { slider = s.toFloat(); vm.setScreenGoal(s) }) {
                                Text("Use suggestion: ${formatDuration(s * 60_000L)} (15% under your 7-day average)")
                            }
                        }
                    }
                    HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                    SettingRow("Goal nudges", "At 80% and 100% of your goal, once a day", vm.goalNudgesOn) { on ->
                        if (on) withNotifications { vm.setGoalNudges(true) } else vm.setGoalNudges(false)
                    }
                }
                HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                SettingRow("Bedtime wind-down", "A nudge if you're still on your phone late at night", vm.bedtimeNudgeOn) { on ->
                    if (on) withNotifications { vm.setBedtimeNudge(true) } else vm.setBedtimeNudge(false)
                }
                if (vm.bedtimeNudgeOn) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Bedtime", style = MaterialTheme.typography.bodyLarge)
                            Text("Nudges only if you're actively using the phone after this", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            TimePickerDialog(
                                context, { _, h, m -> vm.setBedtime(h, m) },
                                vm.bedtimeHour, vm.bedtimeMinute, DateFormat.is24HourFormat(context),
                            ).show()
                        }) {
                            Text(String.format(Locale.getDefault(), "%02d:%02d", vm.bedtimeHour, vm.bedtimeMinute), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if ((vm.screenGoalMinutes > 0 && vm.goalNudgesOn) || vm.bedtimeNudgeOn) {
                    TextButton(onClick = {
                        withNotifications {
                            vm.sendTestNudge()
                            note = "Test nudge sent with today's real numbers. Pull down the notification shade to see it."
                        }
                    }) { Text("Send a test nudge now") }
                }
            }
            note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.secondary)
            }
        }
    }
}

// ======================= HOME: SAFETY NET =======================

/** A caring card shown after several low days or an Elevated result. */
@Composable
fun SafetyNetCard(reason: String, onBreathe: () -> Unit, onHide: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    AppCard(container = cs.tertiaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Heart, tint = cs.tertiary, background = cs.surface, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("We're here for you", style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer)
                    Text(
                        "$reason Would talking to someone help?",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer,
                    )
                }
            }
            PrimaryButton("Call Kenya Red Cross · 1199 (free, 24h)", onClick = { dial(context, "1199") })
            SecondaryButton("Befrienders Kenya · +254 722 178 177", onClick = { dial(context, "+254722178177") })
            Text(
                "Your university counselling centre is also a good place to start. It's free and confidential.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBreathe) { Text("Try the 1-minute breather") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onHide) { Text("Not now") }
            }
        }
    }
}
