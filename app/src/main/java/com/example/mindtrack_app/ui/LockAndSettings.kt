package com.example.mindtrack_app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.MindTrackViewModel
import com.example.mindtrack_app.reminders.Reminders
import java.util.Locale

// ======================= LOCK SCREEN =======================

/** Covers the whole app until the student unlocks with fingerprint, face, PIN, pattern or password. */
@Composable
fun LockScreen(message: String?, onUnlock: () -> Unit) {
    // Ask for authentication straight away; the button lets them try again after cancelling.
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(HeroBrush)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(MtIcons.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(24.dp))
            Text("MindTrack is locked", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use your PIN, fingerprint or face to continue.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
            )
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1.2f))
            Surface(onClick = onUnlock, shape = MaterialTheme.shapes.large, color = Color.White, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Unlock", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ======================= PROFILE: APP LOCK CARD =======================

/**
 * @param canUseLock returns true if the phone has a screen lock (PIN/pattern/password) or biometrics set up.
 * @param authenticate shows the system prompt and calls back only on success.
 */
@Composable
fun AppLockCard(vm: MindTrackViewModel, canUseLock: () -> Boolean, authenticate: (onSuccess: () -> Unit) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var note by remember { mutableStateOf<String?>(null) }
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Lock, tint = cs.primary, background = cs.primaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App lock", style = MaterialTheme.typography.titleMedium)
                    Text("Ask for PIN or fingerprint when MindTrack opens", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Switch(
                    checked = vm.lockEnabled,
                    onCheckedChange = { turnOn ->
                        if (!canUseLock()) {
                            note = "Set up a screen lock (PIN, pattern or password) in your phone's Settings first, then try again."
                        } else {
                            // Confirm it's really the owner before changing the lock either way.
                            authenticate {
                                vm.changeAppLock(turnOn)
                                note = if (turnOn) "App lock is on. MindTrack will ask for your PIN or fingerprint when it opens."
                                else "App lock is off."
                            }
                        }
                    },
                )
            }
            note?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.secondary)
            }
        }
    }
}

// ======================= PROFILE: REMINDERS CARD =======================

@Composable
fun ReminderSettingsCard(vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    // Which switch asked for notification permission, so we can turn it on after the user allows it.
    var pending by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pending) {
                Reminders.OPEN_MOOD -> vm.setMoodReminder(true)
                Reminders.OPEN_CHECKIN -> vm.setCheckInReminder(true)
            }
            note = null
        } else {
            note = "Notifications are blocked. You can allow them in Settings → Apps → MindTrack → Notifications."
        }
        pending = null
    }

    fun turnOn(type: String) {
        if (Reminders.canPostNotifications(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (type == Reminders.OPEN_MOOD) vm.setMoodReminder(true) else vm.setCheckInReminder(true)
        } else {
            pending = type
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val timeText = String.format(Locale.getDefault(), "%02d:%02d", vm.reminderHour, vm.reminderMinute)

    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Timer, tint = cs.tertiary, background = cs.tertiaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Reminders", style = MaterialTheme.typography.titleMedium)
                    Text("Gentle nudges, skipped if you've already done it", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            SettingRow("Daily mood reminder", "Only if you haven't logged today's mood", vm.moodReminderOn) {
                if (it) turnOn(Reminders.OPEN_MOOD) else vm.setMoodReminder(false)
            }
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
            SettingRow("Check-in reminder", "Only if you haven't done a check-in recently", vm.checkInReminderOn) {
                if (it) turnOn(Reminders.OPEN_CHECKIN) else vm.setCheckInReminder(false)
            }
            if (vm.checkInReminderOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilterChip(selected = vm.checkInIntervalDays == 7, onClick = { vm.setCheckInInterval(7) }, label = { Text("Weekly") })
                    FilterChip(selected = vm.checkInIntervalDays == 14, onClick = { vm.setCheckInInterval(14) }, label = { Text("Every 2 weeks") })
                }
            }
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reminder time", style = MaterialTheme.typography.bodyLarge)
                    Text("Used for both reminders", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                TextButton(onClick = {
                    TimePickerDialog(
                        context, { _, h, m -> vm.setReminderTime(h, m) },
                        vm.reminderHour, vm.reminderMinute, DateFormat.is24HourFormat(context),
                    ).show()
                }) { Text(timeText, style = MaterialTheme.typography.titleMedium) }
            }

            if (vm.moodReminderOn || vm.checkInReminderOn) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    vm.sendTestReminder(if (vm.checkInReminderOn) Reminders.OPEN_CHECKIN else Reminders.OPEN_MOOD)
                    note = "Test reminder sent. Pull down the notification shade to see it."
                }) { Text("Send a test reminder now") }
            }
            note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.secondary)
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
