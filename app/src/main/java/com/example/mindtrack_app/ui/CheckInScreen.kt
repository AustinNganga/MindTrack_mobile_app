package com.example.mindtrack_app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.MindTrackViewModel
import com.example.mindtrack_app.Step
import com.example.mindtrack_app.data.ChoiceQuestion
import com.example.mindtrack_app.data.NumberQuestion
import com.example.mindtrack_app.data.Section
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CheckInScreen(vm: MindTrackViewModel) {
    val step = vm.steps[vm.stepIndex]
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var lastIndex by remember { mutableStateOf(vm.stepIndex) }
    val forward = vm.stepIndex >= lastIndex
    LaunchedEffect(vm.stepIndex) { lastIndex = vm.stepIndex }

    val currentSection = when (step) {
        is Step.Intro -> step.section
        is Step.Ask -> step.question.section
        Step.Finish -> Section.FEELINGS
    }
    val fractions = Section.entries.map { s ->
        val qs = vm.steps.filterIsInstance<Step.Ask>().filter { it.question.section == s }
        qs.count { it.question.id in vm.answers }.toFloat() / qs.size
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.previousStep() }) { Icon(MtIcons.ArrowBack, contentDescription = "Previous question") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Part ${currentSection.number} of 3 · ${currentSection.title}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { if (vm.answeredCount == 0) vm.abandonCheckIn() else confirmExit = true }) {
                Icon(MtIcons.Close, contentDescription = "Exit check-in")
            }
        }
        SegmentedProgress(fractions, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = vm.stepIndex,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith (slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
            },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { index ->
            when (val s = vm.steps[index]) {
                is Step.Intro -> SectionIntro(s.section, onStart = { vm.nextStep() })
                is Step.Ask -> QuestionPage(vm, s)
                Step.Finish -> FinishPage(vm)
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave this check-in?") },
            text = { Text("Your answers so far won't be saved.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.abandonCheckIn() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun SectionIntro(section: Section, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val icon = when (section) {
        Section.ABOUT -> MtIcons.Person
        Section.PHONE -> MtIcons.Phone
        Section.FEELINGS -> MtIcons.Heart
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        IconBadge(icon, tint = cs.primary, background = cs.primaryContainer, size = 88.dp)
        Spacer(Modifier.height(24.dp))
        Text("Part ${section.number} of 3", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        Text(section.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(section.blurb, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Takes ${section.minutes}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.weight(1.3f))
        PrimaryButton(if (section == Section.ABOUT) "Let's begin" else "Continue", onClick = onStart)
    }
}

@Composable
private fun QuestionPage(vm: MindTrackViewModel, step: Step.Ask) {
    val q = step.question
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var advancing by remember(q.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Question ${step.indexInSection + 1} of ${step.sectionSize}",
            style = MaterialTheme.typography.labelMedium, color = cs.primary,
        )
        q.helper?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(q.prompt, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        when (q) {
            is ChoiceQuestion -> {
                val pick: (Int) -> Unit = { code ->
                    if (!advancing) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.answer(q.id, code)
                        advancing = true
                        scope.launch { delay(260); vm.nextStep() }
                    }
                }
                if (q.compact) {
                    q.options.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { o ->
                                OptionCard(o.label, vm.answers[q.id] == o.code, { pick(o.code) }, Modifier.weight(1f), centered = true)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        q.options.forEach { o -> OptionCard(o.label, vm.answers[q.id] == o.code, { pick(o.code) }) }
                    }
                }
            }
            is NumberQuestion -> NumberPicker(vm, q)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberPicker(vm: MindTrackViewModel, q: NumberQuestion) {
    val cs = MaterialTheme.colorScheme
    val value = vm.answers[q.id] ?: q.default
    LaunchedEffect(q.id) { if (q.id !in vm.answers) vm.answer(q.id, q.default) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = { vm.answer(q.id, (value - 1).coerceAtLeast(q.min)) }, modifier = Modifier.size(56.dp)) {
                    Text("−", style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$value", style = MaterialTheme.typography.displaySmall, color = cs.primary)
                    Text(q.unit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { vm.answer(q.id, (value + 1).coerceAtMost(q.max)) }, modifier = Modifier.size(56.dp)) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Range ${q.min}–${q.max}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Next", onClick = { vm.nextStep() })
    }
}

@Composable
private fun FinishPage(vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    val complete = vm.answeredCount == vm.questionCount
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 220f)) }
        Box(contentAlignment = Alignment.Center) {
            if (complete) {
                SparkleBurst(trigger = 1, colors = listOf(cs.primary, cs.secondary, cs.tertiary), modifier = Modifier.size(96.dp), particles = 18, reach = 100.dp)
            }
            Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                IconBadge(MtIcons.Check, tint = cs.onSecondary, background = cs.secondary, size = 96.dp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (complete) "All done. Thank you." else "Almost there", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            if (complete) "Taking a moment to reflect on how you feel is a good habit. Your result is ready."
            else "A few questions were skipped. Tap below and we'll take you to the first one.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.3f))
        PrimaryButton(if (complete) "See my result" else "Answer the rest", onClick = { vm.submit() }, loading = vm.submitting)
    }
}
