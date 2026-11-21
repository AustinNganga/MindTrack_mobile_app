package com.example.mindtrack_app

import com.example.mindtrack_app.goals.Goals
import com.example.mindtrack_app.widget.MoodWidget
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindtrack_app.data.AppSettings
import com.example.mindtrack_app.data.CheckIn
import com.example.mindtrack_app.data.CloudBackup
import com.example.mindtrack_app.data.LocalRepository
import com.example.mindtrack_app.data.MindTrackRepository
import com.example.mindtrack_app.data.MoodEntry
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.ScreenTimeReader
import com.example.mindtrack_app.data.ScreenTimeSummary
import com.example.mindtrack_app.data.DailyScreenTime
import com.example.mindtrack_app.data.ScreenTimeRepository
import com.example.mindtrack_app.data.Profile
import com.example.mindtrack_app.data.Question
import com.example.mindtrack_app.data.QuestionBank
import com.example.mindtrack_app.data.RiskLevel
import com.example.mindtrack_app.data.Section
import com.example.mindtrack_app.model.MindTrackModel
import com.example.mindtrack_app.reminders.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { LOADING, ERROR, WELCOME, NAME, CONSENT, HOME, INSIGHTS, HISTORY, ABOUT, PROFILE, CHECK_IN, RESULT, ACCOUNT }

enum class AccountMode { SIGN_UP, SIGN_IN }

/** One page of the check-in flow. */
sealed interface Step {
    data class Intro(val section: Section) : Step
    data class Ask(val question: Question, val indexInSection: Int, val sectionSize: Int) : Step
    data object Finish : Step
}

class MindTrackViewModel(app: Application) : AndroidViewModel(app) {

    private val local = LocalRepository(app)
    private val repository: MindTrackRepository = local
    private val cloud = CloudBackup()
    private val moodRepo = MoodRepository(app)
    private val screenTimeRepo = ScreenTimeRepository(app)
    private val screenTimeReader = ScreenTimeReader(app)
    private val settings = AppSettings(app)
    private var model: MindTrackModel? = null

    var screen by mutableStateOf(Screen.LOADING)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var checkIns by mutableStateOf<List<CheckIn>>(emptyList())
        private set

    /** Check-in currently shown on the result screen. */
    var viewing by mutableStateOf<CheckIn?>(null)
        private set
    var viewingIsNew by mutableStateOf(false)
        private set

    var pendingName by mutableStateOf("")

    // ---------- mood journal state ----------
    /** Most recent mood entries, newest first. */
    var moods by mutableStateOf<List<MoodEntry>>(emptyList())
        private set
    val todayMood: MoodEntry? get() = moods.firstOrNull { it.date == MoodRepository.today() }

    // ---------- screen-time state ----------
    var screenTimeAllowed by mutableStateOf(false)
        private set
    var screenTime by mutableStateOf<ScreenTimeSummary?>(null)
        private set
    var screenTimeLoading by mutableStateOf(false)
        private set
    /** Saved daily screen time for the last 14 days (oldest first), for the mood vs screen-time chart. */
    var screenTimeHistory by mutableStateOf<List<DailyScreenTime>>(emptyList())
        private set

    // ---------- app lock state ----------
    var lockEnabled by mutableStateOf(settings.lockEnabled)
        private set
    /** True while the lock screen should cover the app. Starts locked if the lock is on. */
    var locked by mutableStateOf(settings.lockEnabled)
        private set
    var lockMessage by mutableStateOf<String?>(null)

    /** True while the "share a summary" (PDF) screen is open. */
    var showReport by mutableStateOf(false)

    /** True while the 1-minute breathing exercise is open. */
    var showBreather by mutableStateOf(false)

    /** Days in a row with a mood logged (see MoodRepository.streak). */
    val moodStreak: Int get() = MoodRepository.streak(moods.map { it.date })

    // ---------- screen-time goal & bedtime ----------
    var screenGoalMinutes by mutableIntStateOf(settings.screenGoalMinutes)
        private set
    var goalNudgesOn by mutableStateOf(settings.goalNudges)
        private set
    var bedtimeNudgeOn by mutableStateOf(settings.bedtimeNudge)
        private set
    var bedtimeHour by mutableIntStateOf(settings.bedtimeHour)
        private set
    var bedtimeMinute by mutableIntStateOf(settings.bedtimeMinute)
        private set

    fun setScreenGoal(minutes: Int) {
        settings.screenGoalMinutes = minutes; screenGoalMinutes = minutes
        Goals.apply(getApplication<Application>(), settings)
    }

    fun setGoalNudges(on: Boolean) {
        settings.goalNudges = on; goalNudgesOn = on
        Goals.apply(getApplication<Application>(), settings)
    }

    fun setBedtimeNudge(on: Boolean) {
        settings.bedtimeNudge = on; bedtimeNudgeOn = on
        Goals.apply(getApplication<Application>(), settings)
    }

    fun setBedtime(hour: Int, minute: Int) {
        settings.bedtimeHour = hour; settings.bedtimeMinute = minute
        bedtimeHour = hour; bedtimeMinute = minute
    }

    fun sendTestNudge() = Goals.sendTest(getApplication<Application>())

    /** A suggested goal: 15% under the 7-day average, rounded down to 15 minutes (30 min to 8 h). */
    val suggestedGoalMinutes: Int?
        get() {
            val avg = screenTime?.weekAverageMillis ?: return null
            if (avg <= 0) return null
            return ((avg / 60_000.0 * 0.85) / 15).toInt().times(15).coerceIn(30, 480)
        }

    /** (days goal met, days counted) over up to the last 7 finished days. */
    val goalDaysMet: Pair<Int, Int>?
        get() {
            if (screenGoalMinutes <= 0) return null
            val today = MoodRepository.today()
            val past = screenTimeHistory.filter { it.date != today }.takeLast(7)
            if (past.isEmpty()) return null
            return past.count { it.totalMillis <= screenGoalMinutes * 60_000L } to past.size
        }

    // ---------- safety net ----------
    private var safetyHiddenUntil by mutableStateOf(settings.safetyHiddenUntil)

    /**
     * Why the caring support card should show on Home, or null. Shows after three low moods in a row
     * (🙁 or 😞), or an Elevated check-in in the last 7 days, unless hidden with "Not now".
     */
    val safetyReason: String?
        get() {
            if (profile == null) return null
            val today = MoodRepository.today()
            if (safetyHiddenUntil.isNotEmpty() && safetyHiddenUntil >= today) return null
            val byDate = moods.associate { it.date to it.mood }
            val days = MoodRepository.lastDays(4) // oldest first, ending today
            val window = if (days[3] in byDate) days.subList(1, 4) else days.subList(0, 3)
            if (window.all { (byDate[it] ?: 5) <= 2 }) return "You've logged a low mood three days in a row."
            val last = checkIns.firstOrNull()
            if (last != null && last.level == RiskLevel.ELEVATED &&
                System.currentTimeMillis() - last.timestamp < 7 * 24 * 60 * 60 * 1000L
            ) return "Your latest check-in came back Elevated."
            return null
        }

    /** "Not now": hide the support card for 3 days (today and the next two). */
    fun hideSafetyCard() {
        val until = MoodRepository.dateOf(System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000L)
        settings.safetyHiddenUntil = until
        safetyHiddenUntil = until
    }

    /** True while the first-run tutorial is showing. */
    var showTour by mutableStateOf(!settings.tourSeen)

    fun finishTour() {
        settings.tourSeen = true
        showTour = false
    }

    // ---------- reminder settings state ----------
    var moodReminderOn by mutableStateOf(settings.moodReminder)
        private set
    var checkInReminderOn by mutableStateOf(settings.checkInReminder)
        private set
    var reminderHour by mutableIntStateOf(settings.reminderHour)
        private set
    var reminderMinute by mutableIntStateOf(settings.reminderMinute)
        private set
    var checkInIntervalDays by mutableIntStateOf(settings.checkInIntervalDays)
        private set

    /** Screen requested by a tapped notification while the app was still loading. */
    private var pendingOpen: String? = null

    // ---------- cloud backup state ----------
    /** Email of the signed-in account, or null when cloud backup is off. */
    var cloudEmail by mutableStateOf<String?>(null)
        private set
    var cloudBusy by mutableStateOf(false)
        private set
    /** Short status or error message shown on the account and profile screens. */
    var cloudMessage by mutableStateOf<String?>(null)
    var accountMode by mutableStateOf(AccountMode.SIGN_UP)
    /** True when the account screen was opened from Welcome ("Restore from a backup"). */
    var accountFromWelcome by mutableStateOf(false)
        private set

    val answers = mutableStateMapOf<String, Int>()
    var stepIndex by mutableIntStateOf(0)
        private set
    var submitting by mutableStateOf(false)
        private set

    val steps: List<Step> = buildList {
        Section.entries.forEach { section ->
            val qs = QuestionBank.all.filter { it.section == section }
            add(Step.Intro(section))
            qs.forEachIndexed { i, q -> add(Step.Ask(q, i, qs.size)) }
        }
        add(Step.Finish)
    }
    val questionCount = QuestionBank.all.size

    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.Default) {
                    val json = app.assets.open("mindtrack_model.json").bufferedReader().use { it.readText() }
                    MindTrackModel.fromJson(json)
                }
                val missing = QuestionBank.uncovered(loaded.features)
                check(missing.isEmpty()) { "The app has no question for model inputs: $missing" }
                model = loaded
                profile = repository.getProfile()
                checkIns = repository.getCheckIns()
                cloudEmail = cloud.signedInEmail
                moods = moodRepo.recent()
                screenTimeHistory = runCatching { screenTimeRepo.lastDays(14) }.getOrDefault(emptyList())
                refreshScreenTime()
                Goals.apply(getApplication<Application>(), settings)
                screen = if (profile == null) Screen.WELCOME else Screen.HOME
                if (profile == null) locked = false
                pendingOpen?.let { applyOpen(it) }
                pendingOpen = null
            } catch (e: Exception) {
                errorMessage = e.message ?: e.toString()
                screen = Screen.ERROR
            }
        }
    }

    // ---------- navigation ----------
    fun go(target: Screen) { screen = target }

    /** Returns true if the back press was handled. */
    fun back(): Boolean = when (screen) {
        Screen.NAME -> { screen = Screen.WELCOME; true }
        Screen.CONSENT -> { screen = Screen.NAME; true }
        Screen.INSIGHTS, Screen.HISTORY, Screen.ABOUT, Screen.PROFILE -> { screen = Screen.HOME; true }
        Screen.RESULT -> { screen = if (viewingIsNew) Screen.HOME else Screen.HISTORY; true }
        Screen.CHECK_IN -> { previousStep(); true }
        Screen.ACCOUNT -> { screen = if (accountFromWelcome) Screen.WELCOME else Screen.PROFILE; true }
        else -> false
    }

    // ---------- onboarding ----------
    fun finishOnboarding() {
        val p = Profile(pendingName.trim(), System.currentTimeMillis())
        profile = p
        viewModelScope.launch {
            repository.saveProfile(p)
            if (cloudEmail != null) runCatching { cloud.uploadProfile(p) }
        }
        screen = Screen.HOME
    }

    fun updateName(name: String) {
        val p = (profile ?: return).copy(name = name.trim())
        profile = p
        viewModelScope.launch {
            repository.saveProfile(p)
            if (cloudEmail != null) runCatching { cloud.uploadProfile(p) }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            if (cloudEmail != null) {
                // Remove the cloud copy too, then sign out.
                runCatching { cloud.deleteCloudData() }.onFailure { cloudMessage = CloudBackup.friendlyMessage(it) }
                cloud.signOut()
                cloudEmail = null
            }
            repository.deleteAll()
            moodRepo.deleteAll()
            screenTimeRepo.deleteAll()
            Reminders.cancelAll(getApplication<Application>())
            settings.clear()
            lockEnabled = false; locked = false
            moodReminderOn = false; checkInReminderOn = false
            Goals.cancel(getApplication<Application>())
            screenGoalMinutes = 0; goalNudgesOn = true; bedtimeNudgeOn = false
            bedtimeHour = 23; bedtimeMinute = 0; safetyHiddenUntil = ""
            reminderHour = settings.reminderHour; reminderMinute = settings.reminderMinute
            checkInIntervalDays = settings.checkInIntervalDays
            moods = emptyList()
            screenTimeHistory = emptyList()
            showTour = true
            MoodWidget.updateAll(getApplication<Application>())
            profile = null
            checkIns = emptyList()
            answers.clear()
            pendingName = ""
            screen = Screen.WELCOME
        }
    }

    // ---------- check-in ----------
    fun startCheckIn() {
        answers.clear()
        stepIndex = 0
        submitting = false
        screen = Screen.CHECK_IN
    }

    fun answer(questionId: String, code: Int) { answers[questionId] = code }

    fun nextStep() { if (stepIndex < steps.lastIndex) stepIndex++ }

    fun previousStep() {
        if (stepIndex > 0) stepIndex-- else screen = Screen.HOME
    }

    fun abandonCheckIn() {
        answers.clear()
        stepIndex = 0
        screen = Screen.HOME
    }

    val answeredCount: Int get() = QuestionBank.all.count { it.id in answers }

    fun submit() {
        val m = model ?: return
        if (submitting) return
        val firstMissing = steps.indexOfFirst { it is Step.Ask && it.question.id !in answers }
        if (firstMissing >= 0) { stepIndex = firstMissing; return }
        submitting = true
        viewModelScope.launch {
            val snapshot = answers.toMap()
            val prediction = withContext(Dispatchers.Default) {
                m.predict(QuestionBank.modelInputs(snapshot, m.features))
            }
            val checkIn = CheckIn(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                riskScore = prediction.probability,
                level = if (prediction.elevated) RiskLevel.ELEVATED else RiskLevel.LOW,
                tmdTotal = QuestionBank.tmdIds.sumOf { snapshot.getValue(it) },
                answers = snapshot,
                modelVersion = m.version,
            )
            repository.addCheckIn(checkIn)
            checkIns = listOf(checkIn) + checkIns
            if (cloudEmail != null) {
                // Back up in the background. If the phone is offline, Firestore queues the write
                // and sends it automatically when the connection returns.
                viewModelScope.launch {
                    runCatching { cloud.uploadCheckIn(checkIn) }
                        .onFailure { cloudMessage = CloudBackup.friendlyMessage(it) }
                }
            }
            viewing = checkIn
            viewingIsNew = true
            submitting = false
            screen = Screen.RESULT
        }
    }

    fun openCheckIn(checkIn: CheckIn) {
        viewing = checkIn
        viewingIsNew = false
        screen = Screen.RESULT
    }

    val threshold: Double get() = model?.elevatedThreshold ?: 0.5

    // ---------- mood journal ----------

    /** Saves today's mood (replacing any earlier entry from today). */
    fun logMood(mood: Int, note: String = todayMood?.note.orEmpty()) {
        val entry = MoodEntry(MoodRepository.today(), mood, note, System.currentTimeMillis())
        moods = listOf(entry) + moods.filterNot { it.date == entry.date }
        viewModelScope.launch {
            moodRepo.save(entry)
            MoodWidget.refresh(getApplication<Application>()) // keep the home-screen widget in sync
        }
    }

    /** Re-reads moods from the database (a mood may have been logged from a notification). */
    fun reloadMoods() {
        viewModelScope.launch { moods = moodRepo.recent() }
    }

    // ---------- screen time ----------

    fun screenTimePermissionIntent() = screenTimeReader.permissionIntent()

    /** Re-checks the permission and reloads usage data. Called on start and whenever the app returns to the foreground. */
    fun refreshScreenTime() {
        screenTimeAllowed = screenTimeReader.hasPermission()
        if (!screenTimeAllowed || screenTimeLoading) return
        screenTimeLoading = true
        viewModelScope.launch {
            val summary = runCatching { screenTimeReader.summary() }.getOrNull()
            screenTime = summary
            if (summary != null) {
                // Save each day to the database, then read back 14 days for the chart.
                runCatching {
                    screenTimeRepo.save(summary.week.map { DailyScreenTime(MoodRepository.dateOf(it.dayStart), it.totalMillis, it.unlocks) })
                    screenTimeHistory = screenTimeRepo.lastDays(14)
                }
            }
            screenTimeLoading = false
        }
    }

    // ---------- app lock ----------

    fun changeAppLock(on: Boolean) {
        settings.lockEnabled = on
        lockEnabled = on
        MoodWidget.updateAll(getApplication<Application>()) // widget hides mood details while locked
        lockMessage = null
    }

    /** Called when the app returns from the background after a while. */
    fun lockIfEnabled() {
        if (lockEnabled && profile != null) locked = true
    }

    fun unlock() {
        locked = false
        lockMessage = null
    }

    // ---------- reminders ----------

    fun setMoodReminder(on: Boolean) {
        settings.moodReminder = on; moodReminderOn = on
        Reminders.apply(getApplication<Application>(), settings)
    }

    fun setCheckInReminder(on: Boolean) {
        settings.checkInReminder = on; checkInReminderOn = on
        Reminders.apply(getApplication<Application>(), settings)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        settings.reminderHour = hour; settings.reminderMinute = minute
        reminderHour = hour; reminderMinute = minute
        Reminders.apply(getApplication<Application>(), settings)
    }

    fun setCheckInInterval(days: Int) {
        settings.checkInIntervalDays = days; checkInIntervalDays = days
        Reminders.apply(getApplication<Application>(), settings)
    }

    fun sendTestReminder(type: String) = Reminders.sendTest(getApplication<Application>(), type)

    /** Opens the screen a notification asked for (or remembers it until the app has loaded). */
    fun handleOpen(action: String?) {
        if (action == null) return
        if (screen == Screen.LOADING) pendingOpen = action else applyOpen(action)
    }

    private fun applyOpen(action: String) {
        if (profile == null) return
        when (action) {
            Reminders.OPEN_MOOD -> screen = Screen.INSIGHTS
            Reminders.OPEN_CHECKIN -> if (screen != Screen.CHECK_IN) startCheckIn()
        }
    }

    // ---------- cloud backup actions ----------

    fun openAccount(mode: AccountMode, fromWelcome: Boolean) {
        accountMode = mode
        accountFromWelcome = fromWelcome
        cloudMessage = null
        screen = Screen.ACCOUNT
    }

    /** Creates an account or signs in, then merges this phone's check-ins with the cloud. */
    fun submitAccount(email: String, password: String) {
        if (cloudBusy) return
        cloudBusy = true
        cloudMessage = null
        viewModelScope.launch {
            try {
                if (accountMode == AccountMode.SIGN_UP) cloud.createAccount(email, password)
                else cloud.signIn(email, password)
                cloudEmail = cloud.signedInEmail

                val remoteProfile = cloud.downloadProfile()
                val localProfile = profile
                if (localProfile == null && remoteProfile != null) {
                    // New phone: restore the profile from the backup.
                    profile = remoteProfile
                    repository.saveProfile(remoteProfile)
                } else if (localProfile != null) {
                    cloud.uploadProfile(localProfile)
                }

                val (sent, restored) = mergeWithCloud()
                cloudMessage = when {
                    restored > 0 -> "Backup on. Restored $restored check-in${if (restored == 1) "" else "s"} from the cloud."
                    sent > 0 -> "Backup on. $sent check-in${if (sent == 1) "" else "s"} backed up."
                    else -> "Backup on. New check-ins will be backed up automatically."
                }
                screen = when {
                    profile == null -> Screen.NAME      // restored account had no profile yet: finish onboarding
                    accountFromWelcome -> Screen.HOME
                    else -> Screen.PROFILE
                }
            } catch (e: Exception) {
                cloudMessage = CloudBackup.friendlyMessage(e)
            } finally {
                cloudBusy = false
            }
        }
    }

    /** Uploads check-ins that only exist on this phone and downloads ones that only exist in the cloud. */
    private suspend fun mergeWithCloud(): Pair<Int, Int> {
        val remote = cloud.downloadCheckIns()
        val localList = repository.getCheckIns()
        val localIds = localList.map { it.id }.toSet()
        val remoteIds = remote.map { it.id }.toSet()
        val toUpload = localList.filter { it.id !in remoteIds }
        val toRestore = remote.filter { it.id !in localIds }
        cloud.uploadCheckIns(toUpload)
        val merged = (localList + toRestore).sortedByDescending { it.timestamp }
        local.replaceCheckIns(merged)
        checkIns = merged
        return toUpload.size to toRestore.size
    }

    fun syncNow() {
        if (cloudBusy || cloudEmail == null) return
        cloudBusy = true
        cloudMessage = null
        viewModelScope.launch {
            try {
                val (sent, restored) = mergeWithCloud()
                cloudMessage = "Synced: $sent uploaded, $restored restored."
            } catch (e: Exception) {
                cloudMessage = CloudBackup.friendlyMessage(e)
            } finally {
                cloudBusy = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) { cloudMessage = "Type your email above first."; return }
        viewModelScope.launch {
            cloudMessage = try {
                cloud.sendPasswordReset(email)
                "If an account exists for that email, a reset link has been sent."
            } catch (e: Exception) { CloudBackup.friendlyMessage(e) }
        }
    }

    fun signOutOfBackup() {
        cloud.signOut()
        cloudEmail = null
        cloudMessage = "Backup turned off. Your check-ins are still on this phone."
    }
}
