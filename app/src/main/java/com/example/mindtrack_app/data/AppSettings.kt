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
