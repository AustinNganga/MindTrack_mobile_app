package com.example.mindtrack_app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.mindtrack_app.data.RiskLevel
import com.example.mindtrack_app.ui.theme.RiskElevated
import com.example.mindtrack_app.ui.theme.RiskElevatedContainer
import com.example.mindtrack_app.ui.theme.RiskLow
import com.example.mindtrack_app.ui.theme.RiskLowContainer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- helpers ----------

fun RiskLevel.color(): Color = if (this == RiskLevel.LOW) RiskLow else RiskElevated
fun RiskLevel.container(): Color = if (this == RiskLevel.LOW) RiskLowContainer else RiskElevatedContainer

fun formatDate(millis: Long, pattern: String = "EEE d MMM yyyy, HH:mm"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
    } catch (_: ActivityNotFoundException) { /* no dialer (e.g. some tablets) */ }
}

val HeroBrush: Brush
    @Composable get() = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )

// ---------- building blocks ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(if (loading) "Working…" else text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, backIcon: ImageVector = MtIcons.ArrowBack, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(backIcon, contentDescription = "Back") }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Box(Modifier.padding(20.dp)) { content() } }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun LevelChip(level: RiskLevel) {
    Surface(color = level.container(), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(level.color()))
            Spacer(Modifier.width(6.dp))
            Text(level.label, style = MaterialTheme.typography.labelMedium, color = level.color(), fontWeight = FontWeight.Bold)
        }
    }
}

/** Large tappable answer card with a radio indicator. */
@Composable
fun OptionCard(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, centered: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) cs.primary else cs.outlineVariant, label = "border")
    val bg by animateColorAsState(if (selected) cs.primaryContainer else cs.surface, label = "bg")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, selected" else label },
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        if (centered) {
            Box(Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    color = if (selected) cs.onPrimaryContainer else cs.onSurface, modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                RadioDot(selected)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(if (selected) cs.primary else cs.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(MtIcons.Check, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Three-part progress bar: one segment per questionnaire section. */
@Composable
fun SegmentedProgress(fractions: List<Float>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        fractions.forEach { f ->
            val animated by animateFloatAsState(f, animationSpec = tween(350), label = "seg")
            Box(
                Modifier.weight(1f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * Semicircle gauge. The arc is split at the model's screening threshold:
 * left part = Low (green), right part = Elevated (orange). The marker shows where this check-in falls.
 */
@Composable
fun RiskGauge(score: Double, threshold: Double, level: RiskLevel, modifier: Modifier = Modifier) {
    val target = score.coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(target, animationSpec = tween(1100), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markerRing = MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxWidth().aspectRatio(1.7f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f)) {
            val stroke = size.width * 0.075f
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            val split = (threshold.toFloat().coerceIn(0.05f, 0.95f)) * 180f
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(RiskLow.copy(alpha = 0.85f), 180f, split - 2f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(RiskElevated.copy(alpha = 0.85f), 180f + split + 2f, 180f - split - 2f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val angle = Math.toRadians((180.0 + animated * 180.0))
            val r = diameter / 2
            val cx = topLeft.x + r
            val cy = topLeft.y + r
            val marker = Offset(cx + (r * kotlin.math.cos(angle)).toFloat(), cy + (r * kotlin.math.sin(angle)).toFloat())
            drawCircle(markerRing, radius = stroke * 0.95f, center = marker)
            drawCircle(level.color(), radius = stroke * 0.65f, center = marker)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 20.dp)) {
            Text(level.label, style = MaterialTheme.typography.displaySmall, color = level.color())
            Text("indicator", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Kenyan support lines (verified Nov 2026). Shown on consent, home and result screens. */
@Composable
fun SupportCard(prominent: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    AppCard(modifier, container = if (prominent) cs.tertiaryContainer else cs.surface) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Heart, tint = cs.tertiary, background = cs.surface, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Need to talk to someone?", style = MaterialTheme.typography.titleMedium)
                    Text("Free and confidential", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            HelpLine("Kenya Red Cross", "Toll-free, 24 hours", "1199") { dial(context, "1199") }
            HelpLine("Befrienders Kenya", "Call, SMS or WhatsApp", "+254 722 178 177") { dial(context, "+254722178177") }
            Text(
                "Your university counselling centre is also a great place to start.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HelpLine(name: String, detail: String, number: String, onCall: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onCall,
        shape = MaterialTheme.shapes.medium,
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$number · $detail", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Icon(MtIcons.Call, contentDescription = "Call $name", tint = cs.primary)
        }
    }
}
