package com.example.mindtrack_app.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mindtrack_app.MindTrackViewModel
import com.example.mindtrack_app.report.SummaryReport
import kotlinx.coroutines.launch
import java.io.File

/** Profile card that opens the summary screen. */
@Composable
fun ShareSummaryCard(onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(MtIcons.History, tint = cs.primary, background = cs.primaryContainer, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Share a summary with a counsellor", style = MaterialTheme.typography.titleMedium)
                Text("A PDF of your last 30 days, created on your phone", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Icon(MtIcons.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}

/**
 * Two steps: choose what to include, then preview the real PDF before sharing or saving it.
 * Nothing is uploaded; the student decides who receives the file.
 */
@Composable
fun ShareSummaryDialog(vm: MindTrackViewModel, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val cs = MaterialTheme.colorScheme
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var includeNotes by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var file by remember { mutableStateOf<File?>(null) }
        var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
        var message by remember { mutableStateOf<String?>(null) }

        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                TopBar(
                    if (file == null) "Share a summary" else "Preview",
                    onBack = { if (file != null) { file = null; pages = emptyList(); message = null } else onClose() },
                    backIcon = if (file == null) MtIcons.Close else MtIcons.ArrowBack,
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val current = file
                    if (current == null) {
                        Text(
                            "Create a PDF you can show or send to a counsellor or doctor. It can make it easier to start the conversation.",
                            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                        )
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("What's included (last 30 days)", style = MaterialTheme.typography.titleMedium)
                                listOf(
                                    "Your daily moods, with a chart",
                                    "Your screen time and unlocks, with a chart",
                                    "Your check-in results (Low / Elevated)",
                                    "How mood and screen time moved together",
                                    "Support lines in Kenya",
                                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                                Text(
                                    "Your individual questionnaire answers are never included.",
                                    style = MaterialTheme.typography.bodyMedium, color = cs.secondary,
                                )
                            }
                        }
                        AppCard(onClick = { includeNotes = !includeNotes }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Include my mood notes", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Notes can be personal. Only include them if you're comfortable sharing them.",
                                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            "🔒 The PDF is made on your phone. Nothing is uploaded, and you choose who to send it to.",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "This is exactly what the other person will see. ${pages.size} page${if (pages.size == 1) "" else "s"}.",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        )
                        pages.forEachIndexed { i, img ->
                            Image(
                                bitmap = img, contentDescription = "Summary page ${i + 1}",
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                    .border(1.dp, cs.outlineVariant, MaterialTheme.shapes.medium),
                            )
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.secondary) }
                }

                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val current = file
                    if (current == null) {
                        PrimaryButton("Create PDF summary", loading = busy, onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                runCatching {
                                    val f = SummaryReport.create(
                                        context, vm.profile?.name, vm.checkIns, includeNotes, vm.screenGoalMinutes,
                                    )
                                    pages = SummaryReport.renderPreview(f).map { it.asImageBitmap() }
                                    file = f
                                }.onFailure { message = "Sorry, the summary couldn't be created (${it.message})." }
                                busy = false
                            }
                        })
                    } else {
                        PrimaryButton("Share…", onClick = {
                            runCatching { context.startActivity(SummaryReport.shareIntent(context, current)) }
                                .onFailure { message = "No app on this phone can share files." }
                        })
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            SecondaryButton("Save to Downloads", onClick = {
                                scope.launch {
                                    message = if (SummaryReport.saveToDownloads(context, current)) "Saved to Downloads as ${current.name}"
                                    else "Sorry, the file couldn't be saved."
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
