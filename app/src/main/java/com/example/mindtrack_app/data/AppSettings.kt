package com.example.mindtrack_app.data

import android.content.Context
import androidx.core.content.edit

/** Small on-device settings store (SharedPreferences) for reminders and the app lock. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mindtrack_settings", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK, false)
        set(v) = prefs.edit { putBoolean(KEY_LOCK, v) }

    var moodReminder: Boolean
        get() = prefs.getBoolean(KEY_MOOD_REMINDER, false)
        set(v) = prefs.edit { putBoolean(KEY_MOOD_REMINDER, v) }

    var checkInReminder: Boolean
        get() = prefs.getBoolean(KEY_CHECKIN_REMINDER, false)
        set(v) = prefs.edit { putBoolean(KEY_CHECKIN_REMINDER, v) }

    /** Time of day for both reminders (24-hour clock). Default 20:00. */
    var reminderHour: Int
        get() = prefs.getInt(KEY_HOUR, 20)
        set(v) = prefs.edit { putInt(KEY_HOUR, v) }

    var reminderMinute: Int
        get() = prefs.getInt(KEY_MINUTE, 0)
        set(v) = prefs.edit { putInt(KEY_MINUTE, v) }

    /** 7 = weekly, 14 = every two weeks. */
    var checkInIntervalDays: Int
        get() = prefs.getInt(KEY_INTERVAL, 7)
        set(v) = prefs.edit { putInt(KEY_INTERVAL, v) }

    /** True once the first-run tutorial has been seen or skipped. */
    var tourSeen: Boolean
        get() = prefs.getBoolean(KEY_TOUR, false)
        set(v) = prefs.edit { putBoolean(KEY_TOUR, v) }

    // ---- screen-time goal & bedtime ----
    /** Daily screen-time goal in minutes; 0 = no goal. */
    var screenGoalMinutes: Int
        get() = prefs.getInt("screen_goal_min", 0)
        set(v) = prefs.edit { putInt("screen_goal_min", v) }

    var goalNudges: Boolean
        get() = prefs.getBoolean("goal_nudges", true)
        set(v) = prefs.edit { putBoolean("goal_nudges", v) }

    var bedtimeNudge: Boolean
        get() = prefs.getBoolean("bedtime_nudge", false)
        set(v) = prefs.edit { putBoolean("bedtime_nudge", v) }

    var bedtimeHour: Int
        get() = prefs.getInt("bedtime_hour", 23)
        set(v) = prefs.edit { putInt("bedtime_hour", v) }

    var bedtimeMinute: Int
        get() = prefs.getInt("bedtime_minute", 0)
        set(v) = prefs.edit { putInt("bedtime_minute", v) }

    /** Dates (yyyy-MM-dd) of the last nudges, so each is sent at most once a day/night. */
    var goalNudged80: String
        get() = prefs.getString("goal_nudged_80", "") ?: ""
        set(v) = prefs.edit { putString("goal_nudged_80", v) }

    var goalNudged100: String
        get() = prefs.getString("goal_nudged_100", "") ?: ""
        set(v) = prefs.edit { putString("goal_nudged_100", v) }

    var bedtimeNudged: String
        get() = prefs.getString("bedtime_nudged", "") ?: ""
        set(v) = prefs.edit { putString("bedtime_nudged", v) }

    /** The safety-net card stays hidden up to and including this date (yyyy-MM-dd) after "Not now". */
    var safetyHiddenUntil: String
        get() = prefs.getString("safety_hidden_until", "") ?: ""
        set(v) = prefs.edit { putString("safety_hidden_until", v) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_LOCK = "app_lock"
        const val KEY_MOOD_REMINDER = "mood_reminder"
        const val KEY_CHECKIN_REMINDER = "checkin_reminder"
        const val KEY_HOUR = "reminder_hour"
        const val KEY_MINUTE = "reminder_minute"
        const val KEY_INTERVAL = "checkin_interval_days"
        const val KEY_TOUR = "tour_seen"
    }
}
