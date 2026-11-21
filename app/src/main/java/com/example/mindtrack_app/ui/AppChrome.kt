package com.example.mindtrack_app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top app bar shown on the main screens. It keeps content clear of the status bar and holds
 * the app menu (⋮) so every main action is one tap away from anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    title: String,
    onBack: (() -> Unit)?,
    lockEnabled: Boolean,
    onLockNow: () -> Unit,
    onNewCheckIn: () -> Unit,
    onLogMood: () -> Unit,
    onSettings: () -> Unit,
    onSupport: () -> Unit,
    onAbout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(MtIcons.ArrowBack, contentDescription = "Back") }
        },
        actions = {
            if (lockEnabled) {
                IconButton(onClick = onLockNow) { Icon(MtIcons.Lock, contentDescription = "Lock MindTrack now") }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(MtIcons.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuRow("New check-in", MtIcons.Sparkle) { menuOpen = false; onNewCheckIn() }
                    MenuRow("Log today's mood", MtIcons.Heart) { menuOpen = false; onLogMood() }
                    MenuRow("Reminders & app lock", MtIcons.Timer) { menuOpen = false; onSettings() }
                    HorizontalDivider()
                    MenuRow("Get support now", MtIcons.Call) { menuOpen = false; onSupport() }
                    MenuRow("About MindTrack", MtIcons.Info) { menuOpen = false; onAbout() }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
    )
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    )
}

/** The support lines in a pop-up, reachable from the menu on every main screen. */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = { SupportCard(prominent = false) },
    )
}
