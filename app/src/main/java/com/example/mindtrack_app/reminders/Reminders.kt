package com.example.mindtrack_app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mindtrack_app.MainActivity
import com.example.mindtrack_app.R
import com.example.mindtrack_app.data.AppSettings
import com.example.mindtrack_app.data.LocalRepository
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.Moods
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Gentle reminders, scheduled with WorkManager so they survive app restarts and phone reboots.
 *  - Daily mood reminder: skipped automatically if today's mood is already logged.
 *  - Check-in reminder (weekly or fortnightly): skipped if a check-in was done recently.
 */
object Reminders {
    const val CHANNEL_ID = "mindtrack_reminders"
    const val EXTRA_OPEN = "mindtrack_open"
    const val OPEN_MOOD = "mood"
    const val OPEN_CHECKIN = "checkin"

    internal const val KEY_TYPE = "type"
    internal const val KEY_FORCE = "force"
    private const val WORK_MOOD = "mindtrack_mood_reminder"
    private const val WORK_CHECKIN = "mindtrack_checkin_reminder"

    /** Re-applies the schedule from the saved settings (call after any reminder setting changes). */
    fun apply(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (settings.moodReminder) {
            wm.enqueueUniquePeriodicWork(WORK_MOOD, ExistingPeriodicWorkPolicy.UPDATE, periodic(OPEN_MOOD, 1, settings))
        } else wm.cancelUniqueWork(WORK_MOOD)

        if (settings.checkInReminder) {
            wm.enqueueUniquePeriodicWork(
                WORK_CHECKIN, ExistingPeriodicWorkPolicy.UPDATE,
                periodic(OPEN_CHECKIN, settings.checkInIntervalDays.toLong(), settings),
            )
        } else wm.cancelUniqueWork(WORK_CHECKIN)
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_MOOD)
        wm.cancelUniqueWork(WORK_CHECKIN)
    }

    /** Shows a reminder right now (used by the "Send a test reminder" button for demos). */
    fun sendTest(context: Context, type: String) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf(KEY_TYPE to type, KEY_FORCE to true))
                .build(),
        )
    }

    private fun periodic(type: String, everyDays: Long, s: AppSettings): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<ReminderWorker>(everyDays, TimeUnit.DAYS)
            .setInitialDelay(millisUntil(s.reminderHour, s.reminderMinute), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TYPE to type))
            .build()

    /** Milliseconds from now until the next hh:mm (today if still ahead, otherwise tomorrow). */
    private fun millisUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle reminders to log your mood and do a check-in"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission") // permission is checked in canPostNotifications()
    internal fun show(context: Context, id: Int, title: String, text: String, open: String, moodButtons: Boolean = false) {
        if (!canPostNotifications(context)) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, open)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mindtrack)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // One-tap mood buttons (Android shows at most 3 buttons on a notification).
        if (moodButtons) {
            QUICK_MOODS.forEach { mood ->
                builder.addAction(0, "${Moods.face(mood)} ${Moods.label(mood)}", moodPendingIntent(context, mood))
            }
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** Moods offered on the notification: Low, Okay, Good (2, 3, 4 on the 1–5 scale). */
    private val QUICK_MOODS = listOf(2, 3, 4)
    const val MOOD_NOTIFICATION_ID = 1001

    private fun moodPendingIntent(context: Context, mood: Int): PendingIntent {
        val intent = Intent(context, MoodActionReceiver::class.java).apply {
            action = MoodActionReceiver.ACTION_LOG_MOOD
            putExtra(MoodActionReceiver.EXTRA_MOOD, mood)
        }
        // A different request code per mood, so each button keeps its own extra.
        return PendingIntent.getBroadcast(
            context, 2000 + mood, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Replaces the mood reminder with a short "saved" confirmation that disappears by itself. */
    @SuppressLint("MissingPermission")
    internal fun showMoodSaved(context: Context, moodText: String) {
        if (!canPostNotifications(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, OPEN_MOOD)
        }
        val pending = PendingIntent.getActivity(
            context, MOOD_NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mindtrack)
            .setContentTitle("Mood saved: $moodText")
            .setContentText("Thanks! Tap to add a note or see your week.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(8_000) // disappears after 8 seconds
            .build()
        NotificationManagerCompat.from(context).notify(MOOD_NOTIFICATION_ID, notification)
    }
}

/** Runs in the background when a reminder is due and decides whether to show it. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val type = inputData.getString(Reminders.KEY_TYPE) ?: return Result.success()
        val force = inputData.getBoolean(Reminders.KEY_FORCE, false)

        when (type) {
            Reminders.OPEN_MOOD -> {
                val loggedToday = MoodRepository(ctx).recent(1).firstOrNull()?.date == MoodRepository.today()
                if (force || !loggedToday) {
                    Reminders.show(
                        ctx, Reminders.MOOD_NOTIFICATION_ID, "How are you feeling today?",
                        "Tap a mood below to log it, or open MindTrack to add a note.", Reminders.OPEN_MOOD,
                        moodButtons = true,
                    )
                }
            }
            Reminders.OPEN_CHECKIN -> {
                val days = AppSettings(ctx).checkInIntervalDays
                val last = LocalRepository(ctx).getCheckIns().firstOrNull()?.timestamp
                val due = last == null || System.currentTimeMillis() - last > (days - 1) * DAY_MS
                if (force || due) {
                    Reminders.show(
                        ctx, 1002, "Time for your MindTrack check-in",
                        "About 4 minutes. See how you're doing compared with last time.", Reminders.OPEN_CHECKIN,
                    )
                }
            }
        }
        return Result.success()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
