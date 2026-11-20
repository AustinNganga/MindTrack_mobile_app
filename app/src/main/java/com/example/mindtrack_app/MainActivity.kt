package com.example.mindtrack_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.ui.AboutScreen
import com.example.mindtrack_app.ui.AccountScreen
import com.example.mindtrack_app.ui.CheckInScreen
import com.example.mindtrack_app.ui.ConsentScreen
import com.example.mindtrack_app.ui.ErrorScreen
import com.example.mindtrack_app.ui.HistoryScreen
import com.example.mindtrack_app.ui.HomeScreen
import com.example.mindtrack_app.ui.InsightsScreen
import com.example.mindtrack_app.ui.MtIcons
import com.example.mindtrack_app.ui.NameScreen
import com.example.mindtrack_app.ui.ProfileScreen
import com.example.mindtrack_app.ui.ResultScreen
import com.example.mindtrack_app.ui.WelcomeScreen
import com.example.mindtrack_app.ui.theme.Mindtrack_appTheme

class MainActivity : ComponentActivity() {
    private val vm: MindTrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Mindtrack_appTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MindTrackApp(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check "Usage access" after the student returns from Settings, and refresh today's screen time.
        if (vm.screen != Screen.LOADING) vm.refreshScreenTime()
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.HOME, "Home", MtIcons.Home),
    Tab(Screen.INSIGHTS, "Insights", MtIcons.Insights),
    Tab(Screen.HISTORY, "History", MtIcons.History),
    Tab(Screen.ABOUT, "About", MtIcons.Info),
    Tab(Screen.PROFILE, "Profile", MtIcons.Person),
)

@Composable
private fun MindTrackApp(vm: MindTrackViewModel) {
    BackHandler(enabled = vm.screen !in listOf(Screen.HOME, Screen.WELCOME, Screen.LOADING, Screen.ERROR)) { vm.back() }

    val isTab = vm.screen in tabs.map { it.screen }
    if (isTab) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = vm.screen == tab.screen,
                            onClick = { vm.go(tab.screen) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "tabs",
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        profile = vm.profile, checkIns = vm.checkIns, questionCount = vm.questionCount, contentPadding = padding,
                        onStart = vm::startCheckIn, onOpen = vm::openCheckIn, onSeeHistory = { vm.go(Screen.HISTORY) },
                        todayMood = vm.todayMood?.mood, onLogMood = { vm.logMood(it) },
                        onOpenInsights = { vm.go(Screen.INSIGHTS) },
                    )
                    Screen.INSIGHTS -> InsightsScreen(vm, padding)
                    Screen.HISTORY -> HistoryScreen(vm.checkIns, vm.threshold, padding, onOpen = vm::openCheckIn, onStart = vm::startCheckIn)
                    Screen.ABOUT -> AboutScreen(padding)
                    Screen.PROFILE -> ProfileScreen(
                        profile = vm.profile, checkInCount = vm.checkIns.size, contentPadding = padding,
                        cloudEmail = vm.cloudEmail, cloudBusy = vm.cloudBusy, cloudMessage = vm.cloudMessage,
                        onSaveName = vm::updateName,
                        onTurnOnBackup = { vm.openAccount(AccountMode.SIGN_UP, fromWelcome = false) },
                        onSyncNow = vm::syncNow, onSignOut = vm::signOutOfBackup, onDeleteAll = vm::deleteAllData,
                    )
                    else -> Unit
                }
            }
        }
        return
    }

    when (vm.screen) {
        Screen.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(48.dp))
        }
        Screen.ERROR -> ErrorScreen(vm.errorMessage ?: "Unknown error")
        Screen.WELCOME -> WelcomeScreen(
            onStart = { vm.go(Screen.NAME) },
            onRestore = { vm.openAccount(AccountMode.SIGN_IN, fromWelcome = true) },
        )
        Screen.ACCOUNT -> AccountScreen(vm)
        Screen.NAME -> NameScreen(
            name = vm.pendingName, onNameChange = { vm.pendingName = it },
            onContinue = { vm.go(Screen.CONSENT) }, onBack = { vm.back() },
        )
        Screen.CONSENT -> ConsentScreen(onAgree = vm::finishOnboarding, onBack = { vm.back() })
        Screen.CHECK_IN -> CheckInScreen(vm)
        Screen.RESULT -> vm.viewing?.let { c ->
            ResultScreen(c, vm.threshold, vm.viewingIsNew, onDone = { vm.back() })
        }
        else -> Unit
    }
}
