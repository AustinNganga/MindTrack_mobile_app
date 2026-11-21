package com.example.mindtrack_app

import com.example.mindtrack_app.ui.TourScreen
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.mindtrack_app.ui.BreatherDialog
import com.example.mindtrack_app.ui.LocalStagger
import com.example.mindtrack_app.ui.StaggerCounter
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.ui.AboutScreen
import com.example.mindtrack_app.ui.AccountScreen
import com.example.mindtrack_app.ui.AppLockCard
import com.example.mindtrack_app.ui.LockScreen
import com.example.mindtrack_app.ui.MainTopBar
import com.example.mindtrack_app.ui.SupportDialog
import com.example.mindtrack_app.ui.ReminderSettingsCard
import com.example.mindtrack_app.reminders.Reminders
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

// FragmentActivity (a ComponentActivity) is required by the system BiometricPrompt used for the app lock.
class MainActivity : FragmentActivity() {
    private val vm: MindTrackViewModel by viewModels()

    /** When the app last went to the background, for re-locking after [RELOCK_AFTER_MS]. */
    private var backgroundedAt = 0L
    /** True while the PIN/fingerprint prompt is showing (the PIN screen briefly stops this activity). */
    private var authInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.createChannel(this)
        vm.handleOpen(intent?.getStringExtra(Reminders.EXTRA_OPEN))
        setContent {
            Mindtrack_appTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MindTrackApp(vm, authenticate = ::authenticate, canUseLock = ::canUseLock, setSecure = ::setSecure)
                }
            }
        }
    }

    /** A reminder notification was tapped while the app was already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleOpen(intent.getStringExtra(Reminders.EXTRA_OPEN))
    }

    override fun onStop() {
        super.onStop()
        if (!authInProgress) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (!authInProgress && backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) {
            vm.lockIfEnabled()
        }
    }

    /**
     * While the app lock is on, hide MindTrack's content in the Recents switcher and block
     * screenshots/screen recording (FLAG_SECURE). Turning the lock off allows screenshots again.
     */
    private fun setSecure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Fingerprint/face if enrolled, otherwise the phone's PIN, pattern or password. */
    private fun canUseLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate(onSuccess: () -> Unit) {
        if (authInProgress) return
        authInProgress = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!cancelled) vm.lockMessage = errString.toString()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MindTrack")
            .setSubtitle("Use your PIN, fingerprint or face")
            .setAllowedAuthenticators(AUTHENTICATORS) // no negative button allowed when DEVICE_CREDENTIAL is included
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val RELOCK_AFTER_MS = 30_000L
    }

    override fun onResume() {
        super.onResume()
        // Re-check "Usage access" after the student returns from Settings, and refresh today's screen time.
        if (vm.screen != Screen.LOADING) {
            vm.refreshScreenTime()
            vm.reloadMoods() // picks up a mood logged from the reminder notification
        }
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.HOME, "Home", MtIcons.Home),
    Tab(Screen.INSIGHTS, "Insights", MtIcons.Insights),
    Tab(Screen.HISTORY, "History", MtIcons.History),
    Tab(Screen.PROFILE, "Profile", MtIcons.Person),
)

private val screenOrder = listOf(Screen.HOME, Screen.INSIGHTS, Screen.HISTORY, Screen.PROFILE, Screen.ABOUT)

private fun titleFor(screen: Screen): String = when (screen) {
    Screen.HOME -> "MindTrack"
    Screen.INSIGHTS -> "Insights"
    Screen.HISTORY -> "History"
    Screen.PROFILE -> "Profile & settings"
    Screen.ABOUT -> "About MindTrack"
    else -> ""
}

@Composable
private fun MindTrackApp(
    vm: MindTrackViewModel,
    authenticate: (onSuccess: () -> Unit) -> Unit,
    canUseLock: () -> Boolean,
    setSecure: (Boolean) -> Unit,
) {
    LaunchedEffect(vm.lockEnabled) { setSecure(vm.lockEnabled) }

    // The tab the student was on, so "back" from About returns there.
    var lastTab by rememberSaveable { mutableStateOf(Screen.HOME) }
    LaunchedEffect(vm.screen) { if (vm.screen in tabs.map { it.screen }) lastTab = vm.screen }
    var showSupport by rememberSaveable { mutableStateOf(false) }
    if (showSupport) SupportDialog(onDismiss = { showSupport = false })
    if (vm.showBreather) BreatherDialog(onClose = { vm.showBreather = false })

    // App lock: cover everything until the student unlocks.
    if (vm.locked && vm.screen != Screen.LOADING && vm.screen != Screen.ERROR && vm.profile != null) {
        LockScreen(message = vm.lockMessage, onUnlock = { authenticate { vm.unlock() } })
        return
    }

    // First-run tutorial (also replayable from the menu).
    if (vm.showTour && vm.screen == Screen.HOME) {
        TourScreen(onDone = vm::finishTour)
        return
    }

    BackHandler(enabled = vm.screen !in listOf(Screen.HOME, Screen.WELCOME, Screen.LOADING, Screen.ERROR)) {
        if (vm.screen == Screen.ABOUT) vm.go(lastTab) else vm.back()
    }

    val isTab = vm.screen in tabs.map { it.screen }
    if (isTab || vm.screen == Screen.ABOUT) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MainTopBar(
                    title = titleFor(vm.screen),
                    onBack = if (vm.screen == Screen.ABOUT) ({ vm.go(lastTab) }) else null,
                    lockEnabled = vm.lockEnabled,
                    onLockNow = vm::lockIfEnabled,
                    onNewCheckIn = vm::startCheckIn,
                    onLogMood = { vm.go(Screen.INSIGHTS) },
                    onSettings = { vm.go(Screen.PROFILE) },
                    onSupport = { showSupport = true },
                    onAbout = { vm.go(Screen.ABOUT) },
                    onBreathe = { vm.showBreather = true },
                    onTour = { vm.go(Screen.HOME); vm.showTour = true },
                )
            },
            bottomBar = {
                if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                transitionSpec = {
                    // Slide towards the tab you tapped (right for tabs further along, left for earlier ones).
                    val dir = if (screenOrder.indexOf(targetState) >= screenOrder.indexOf(initialState)) 1 else -1
                    (slideInHorizontally(tween(300)) { w -> dir * w / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(200)) { w -> -dir * w / 6 } + fadeOut(tween(150)))
                },
                label = "tabs",
            ) { screen ->
                // A fresh stagger counter per visit, so cards rise in one after another.
                CompositionLocalProvider(LocalStagger provides remember { StaggerCounter() }) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        profile = vm.profile, checkIns = vm.checkIns, questionCount = vm.questionCount, contentPadding = padding,
                        onStart = vm::startCheckIn, onOpen = vm::openCheckIn, onSeeHistory = { vm.go(Screen.HISTORY) },
                        todayMood = vm.todayMood?.mood, onLogMood = { vm.logMood(it) },
                        onOpenInsights = { vm.go(Screen.INSIGHTS) },
                        moodStreak = vm.moodStreak, onBreathe = { vm.showBreather = true },
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
                        settingsContent = {
                            ReminderSettingsCard(vm)
                            AppLockCard(vm, canUseLock = canUseLock, authenticate = authenticate)
                        },
                    )
                    else -> Unit
                }
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
