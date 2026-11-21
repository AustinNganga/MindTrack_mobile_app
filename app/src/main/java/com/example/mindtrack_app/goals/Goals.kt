package com.example.mindtrack_app.goals

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mindtrack_app.data.AppSettings
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.ScreenTimeReader
import com.example.mindtrack_app.data.formatDuration
import com.example.mindtrack_app.reminders.Reminders
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Screen-time goal and bedtime nudges. A WorkManager job checks real usage every 15 minutes
 * (Android's minimum for periodic work) and sends at most one nudge of each kind per day.
 */
object Goals {
    private const val WORK = "mindtrack_goal_check"
    internal const val KEY_TEST = "test"
    const val GOAL_NOTIFICATION_ID = 1003
    const val BEDTIME_NOTIFICATION_ID = 1004

    /** Starts or stops the background check to match the saved settings. */
    fun apply(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        val needed = (s.screenGoalMinutes > 0 && s.goalNudges) || s.bedtimeNudge
        if (needed) {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GoalWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    /** Sends a nudge right now with today's real numbers (for testing and demos). */
    fun sendTest(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<GoalWorker>().setInputData(workDataOf(KEY_TEST to true)).build(),
        )
    }

    /** True from bedtime until 05:00. Handles bedtimes after midnight too (e.g. 00:30). */
    internal fun isLateNight(s: AppSettings, now: Calendar): Boolean {
        val bed = s.bedtimeHour * 60 + s.bedtimeMinute
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val early = 5 * 60
        return if (bed >= 12 * 60) cur >= bed || cur < early else cur in bed until early
    }

    /** The date of the evening a late night belongs to (so 01:00 counts as the previous night). */
    internal fun nightKey(now: Calendar): String {
        val c = now.clone() as Calendar
        if (c.get(Calendar.HOUR_OF_DAY) < 5) c.add(Calendar.DAY_OF_YEAR, -1)
        return MoodRepository.dateOf(c.timeInMillis)
    }
}

class GoalWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val s = AppSettings(ctx)
        val reader = ScreenTimeReader(ctx)
        if (!reader.hasPermission()) return Result.success()

        val now = System.currentTimeMillis()
        val today = MoodRepository.today()
        val used = reader.usageMillis(reader.startOfToday(), now)
        val goal = s.screenGoalMinutes * 60_000L

        if (inputData.getBoolean(Goals.KEY_TEST, false)) {
            val text = if (goal > 0) "Today so far: ${formatDuration(used)} of your ${formatDuration(goal)} goal."
            else "Today so far: ${formatDuration(used)} of screen time."
            Reminders.show(ctx, Goals.GOAL_NOTIFICATION_ID, "Screen-time check (test)", text, Reminders.OPEN_MOOD)
            return Result.success()
        }

        // Daily goal: 80% and 100% nudges, each at most once per day.
        if (goal > 0 && s.goalNudges) {
            when {
                used >= goal && s.goalNudged100 != today -> {
                    s.goalNudged100 = today
                    s.goalNudged80 = today
                    Reminders.show(
                        ctx, Goals.GOAL_NOTIFICATION_ID, "You've reached today's screen-time goal",
                        "${formatDuration(used)} so far. A short walk, a stretch or a chat could be a nice break. 🚶",
                        Reminders.OPEN_MOOD,
                    )
                }
                used >= goal * 8 / 10 && s.goalNudged80 != today -> {
                    s.goalNudged80 = today
                    Reminders.show(
                        ctx, Goals.GOAL_NOTIFICATION_ID, "Heads up: 80% of your goal",
                        "You've used ${formatDuration(used)} of your ${formatDuration(goal)} goal today.",
                        Reminders.OPEN_MOOD,
                    )
                }
            }
        }

        // Bedtime wind-down: only if the phone is in real use late at night (10+ min in the last 30).
        val cal = Calendar.getInstance()
        if (s.bedtimeNudge && Goals.isLateNight(s, cal)) {
            val key = Goals.nightKey(cal)
            if (s.bedtimeNudged != key) {
                val recent = reader.usageMillis(now - 30 * 60_000L, now)
                if (recent >= 10 * 60_000L) {
                    s.bedtimeNudged = key
                    Reminders.show(
                        ctx, Goals.BEDTIME_NOTIFICATION_ID, "Time to wind down 🌙",
                        "It's late. Putting the phone away now helps you sleep, and good sleep helps your mood.",
                        Reminders.OPEN_MOOD,
                    )
                }
            }
        }
        return Result.success()
    }
}
