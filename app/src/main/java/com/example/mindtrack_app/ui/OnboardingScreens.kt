package com.example.mindtrack_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(onStart: () -> Unit, onRestore: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HeroBrush)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(MtIcons.Heart, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.height(28.dp))
            Text("MindTrack", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "A short, private check-in on your phone habits and how you've been feeling.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                WelcomePoint(MtIcons.Timer, "About 4 minutes", "41 quick taps, one question at a time")
                WelcomePoint(MtIcons.Lock, "Private by design", "Your answers stay on your phone")
                WelcomePoint(MtIcons.Heart, "Supportive, not a diagnosis", "A nudge to check in with yourself")
            }
            Spacer(Modifier.weight(1.2f))
            Surface(onClick = onStart, shape = MaterialTheme.shapes.large, color = Color.White, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Get started", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = onRestore, modifier = Modifier.padding(top = 8.dp)) {
                Text("I already have a backup. Restore it", color = Color.White)
            }
        }
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun NameScreen(name: String, onNameChange: (String) -> Unit, onContinue: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        TopBar("", onBack = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Text("Step 1 of 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("What should we call you?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Just a first name or nickname. It only appears on this phone.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) onNameChange(it) },
                label = { Text("First name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onContinue() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(Modifier.padding(24.dp)) {
            PrimaryButton(if (name.isBlank()) "Skip for now" else "Continue", onClick = onContinue)
        }
    }
}

@Composable
fun ConsentScreen(onAgree: () -> Unit, onBack: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Step 2 of 2", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            Spacer(Modifier.height(8.dp))
            Text("Before you begin", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsentPoint(MtIcons.Sparkle, "What MindTrack does",
                    "It compares your answers with a study of 1,220 university students and estimates whether your answers look like those of students with low or raised depression-questionnaire scores.")
                ConsentPoint(MtIcons.Info, "What it isn't",
                    "It is not a diagnosis and can be wrong. Only a qualified professional can assess your mental health.")
                ConsentPoint(MtIcons.Lock, "Your privacy",
                    "Your answers stay on this phone. You can optionally turn on cloud backup of your results in Profile, and delete everything at any time.")
                ConsentPoint(MtIcons.Heart, "If you're struggling",
                    "Please don't wait for an app. Call Kenya Red Cross on 1199 (free, 24 hours) or talk to someone you trust.")
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Surface(
                onClick = { agreed = !agreed },
                shape = MaterialTheme.shapes.medium,
                color = if (agreed) cs.primaryContainer else cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text("I understand this is a screening tool, not a diagnosis.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Agree and continue", onClick = onAgree, enabled = agreed)
        }
    }
}

@Composable
private fun ConsentPoint(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row {
            IconBadge(icon, tint = cs.primary, background = cs.primaryContainer, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ErrorScreen(message: String) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {}) { Text("Check that assets/mindtrack_model.json is in the app.") }
    }
}
