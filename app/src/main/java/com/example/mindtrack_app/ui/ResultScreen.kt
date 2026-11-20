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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.data.CheckIn
import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.data.RiskLevel

@Composable
fun ResultScreen(checkIn: CheckIn, threshold: Double, isNew: Boolean, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val elevated = checkIn.level == RiskLevel.ELEVATED
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar(if (isNew) "Your result" else formatDate(checkIn.timestamp, "d MMM yyyy"), onBack = onDone, backIcon = if (isNew) MtIcons.Close else MtIcons.ArrowBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    RiskGauge(checkIn.riskScore, threshold, checkIn.level)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (elevated) "Your answers suggest you may be going through a tough time"
                        else "Your answers look similar to students who were doing okay",
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (elevated) "In the study, students who answered like this were several times more likely to report mild or stronger low mood. It's worth paying attention to, and talking to someone can really help."
                        else "In the study, students who answered like this mostly reported normal ups and downs. Keep looking after yourself, and check in again whenever you like.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(formatDate(checkIn.timestamp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }

            if (elevated) SupportCard(prominent = true)

            PhoneDependenceCard(checkIn.tmdTotal)

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Small things that help", style = MaterialTheme.typography.titleMedium)
                    Tip(MtIcons.Moon, "Protect your sleep", "Put your phone away 30 minutes before bed, ideally outside arm's reach.")
                    Tip(MtIcons.Person, "Stay connected", "Spend time face-to-face with a friend or family member this week.")
                    Tip(MtIcons.Timer, "Take phone-free breaks", "Try short no-phone blocks while studying, eating or walking.")
                    Tip(MtIcons.Heart, "Be kind to yourself", "Low moods are common and they pass, especially when shared.")
                }
            }

            if (!elevated) SupportCard(prominent = false)

            Text(
                "MindTrack is a screening aid built from a research dataset. It is not a diagnosis and can be wrong. If you're worried about how you feel, please talk to a professional.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            PrimaryButton(if (isNew) "Back to home" else "Done", onClick = onDone)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PhoneDependenceCard(total: Int) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Phone, tint = cs.secondary, background = cs.secondaryContainer, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Phone dependence", style = MaterialTheme.typography.titleMedium)
                    Text("From your 22 phone-habit answers", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Text(QuestionBank.tmdBand(total), style = MaterialTheme.typography.titleMedium, color = cs.secondary)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { total / 88f },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = cs.secondary,
                trackColor = cs.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
            Text("$total out of 88", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Row {
        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}
