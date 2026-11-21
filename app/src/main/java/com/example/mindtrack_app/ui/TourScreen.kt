package com.example.mindtrack_app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class TourPage(val emoji: String, val title: String, val body: String)

private val tourPages = listOf(
    TourPage(
        "😊", "Log your mood in one tap",
        "Pick a face on Home each day. It takes two seconds and builds a streak 🔥. You can add a short note in Insights, or log straight from a reminder or the home-screen widget.",
    ),
    TourPage(
        "📝", "Check in every week or two",
        "A 4-minute questionnaire about your phone habits and how you've been feeling. MindTrack's AI model gives a Low or Elevated indicator: a prompt to reflect, not a diagnosis.",
    ),
    TourPage(
        "📊", "See your real phone habits",
        "Insights shows your actual screen time, unlocks and most-used apps, and how your mood moves with your screen time. Allow \"Usage access\" when asked.",
    ),
    TourPage(
        "🔒", "Private, and here for you",
        "Everything stays on your phone unless you turn on backup. Set reminders and a PIN lock in Profile. Feeling low? Try the 1-minute breather, or tap ⋮ → Get support now.",
    ),
)

/** Short first-run tutorial: four swipeable cards. Can be replayed from the ⋮ menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourScreen(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState(pageCount = { tourPages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == tourPages.lastIndex

    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onDone()
    }

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("How MindTrack works", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text("Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            TourPageView(tourPages[i], selected = pager.currentPage == i)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            tourPages.indices.forEach { i ->
                val selected = pager.currentPage == i
                val w by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                Box(
                    Modifier.padding(4.dp).height(8.dp).width(w).clip(CircleShape)
                        .background(if (selected) cs.primary else cs.outlineVariant),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            if (last) "Get started" else "Next",
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
        )
    }
}

@Composable
private fun TourPageView(page: TourPage, selected: Boolean) {
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(
        -8f, 8f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y",
    )
    val pop by animateFloatAsState(if (selected) 1f else 0.6f, spring(dampingRatio = 0.45f, stiffness = 200f), label = "pop")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(180.dp)
                .graphicsLayer { translationY = bob.value.dp.toPx(); scaleX = pop; scaleY = pop }
                .clip(CircleShape).background(HeroBrush),
            contentAlignment = Alignment.Center,
        ) { Text(page.emoji, fontSize = 72.sp) }
        Spacer(Modifier.height(40.dp))
        Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = cs().onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun cs() = MaterialTheme.colorScheme
