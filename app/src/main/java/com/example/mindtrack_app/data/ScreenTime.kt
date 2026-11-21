package com.example.mindtrack_app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsage(val packageName: String, val label: String, val millis: Long)

/** [unlocks] is null on Android 8 and older, where unlock events are not available. */
data class DayUsage(val dayStart: Long, val totalMillis: Long, val unlocks: Int?)

data class ScreenTimeSummary(
    val today: DayUsage,
    val week: List<DayUsage>,          // last 7 days, oldest first, ending today
    val topAppsToday: List<AppUsage>,
) {
    val weekAverageMillis: Long get() = if (week.isEmpty()) 0 else week.sumOf { it.totalMillis } / week.size
}

/**
 * Reads the phone's real app-usage history with Android's UsageStatsManager.
 * Needs the special "Usage access" permission, which the student grants in system Settings.
 * Everything is computed on the device; nothing is uploaded.
 */
class ScreenTimeReader(context: Context) {
    private val ctx = context.applicationContext
    private val pm: PackageManager = ctx.packageManager

    fun hasPermission(): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the system screen where the student switches on "Usage access" for MindTrack. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Total foreground time of user apps between two moments (used by the goal nudges). */
    suspend fun usageMillis(start: Long, end: Long): Long = withContext(Dispatchers.Default) {
        usageBetween(start, end).first.values.sum()
    }

    fun startOfToday(): Long = startOfDay(0)

    suspend fun summary(): ScreenTimeSummary = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val week = (6 downTo 0).map { daysAgo ->
            val start = startOfDay(daysAgo)
            val end = if (daysAgo == 0) now else startOfDay(daysAgo - 1)
            val (perApp, unlocks) = usageBetween(start, end)
            DayUsage(start, perApp.values.sum(), unlocks)
        }
        val (todayPerApp, _) = usageBetween(startOfDay(0), now)
        val top = todayPerApp.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { AppUsage(it.key, labelOf(it.key), it.value) }
        ScreenTimeSummary(today = week.last(), week = week, topAppsToday = top)
    }

    /**
     * Adds up foreground time per app between [start] and [end] from the raw event stream, and counts unlocks.
     *
     * Only ONE app can be in the foreground at a time, so we walk the events as a single timeline:
     * when an app comes to the front, whatever was in front before is closed at that moment; when the
     * screen turns off or locks, everything is closed. This stops a missing "went to background" event
     * (common after the emulator resumes from a snapshot, or after a crash) from counting for hours,
     * and guarantees a day can never add up to more time than has actually passed.
     */
    private fun usageBetween(start: Long, end: Long): Pair<Map<String, Long>, Int?> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val totals = HashMap<String, Long>()
        var unlocks = 0
        var current: String? = null // app in the foreground right now
        var since = 0L              // when it came to the foreground
        var anyEvent = false
        fun add(pkg: String, ms: Long) { if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms }
        fun close(at: Long) {
            current?.let { add(it, at - since) }
            current = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp
            val pkg = event.packageName
            when (event.eventType) {
                EVENT_RESUMED -> if (pkg != null && pkg != current) {
                    close(ts)
                    current = pkg
                    since = ts
                }
                EVENT_PAUSED -> when {
                    pkg == current -> close(ts)
                    // The very first event is an app leaving: it was already open when the window began.
                    current == null && !anyEvent && pkg != null -> add(pkg, ts - start)
                }
                EVENT_SCREEN_OFF, EVENT_KEYGUARD_SHOWN, EVENT_SHUTDOWN -> close(ts)
                EVENT_KEYGUARD_HIDDEN -> unlocks++
            }
            anyEvent = true
        }
        close(end) // the app still open at the end of the window

        // Keep only apps a person can open from the app drawer (skips system services).
        val userApps = totals.filterKeys { pm.getLaunchIntentForPackage(it) != null }
        val unlockCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) unlocks else null
        return userApps to unlockCount
    }

    private fun labelOf(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }

    private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val EVENT_RESUMED = 1          // UsageEvents.Event.ACTIVITY_RESUMED (MOVE_TO_FOREGROUND)
        const val EVENT_PAUSED = 2           // UsageEvents.Event.ACTIVITY_PAUSED (MOVE_TO_BACKGROUND)
        const val EVENT_SCREEN_OFF = 16      // UsageEvents.Event.SCREEN_NON_INTERACTIVE (Android 9+)
        const val EVENT_KEYGUARD_SHOWN = 17  // UsageEvents.Event.KEYGUARD_SHOWN (Android 9+): the phone was locked
        const val EVENT_KEYGUARD_HIDDEN = 18 // UsageEvents.Event.KEYGUARD_HIDDEN (Android 9+): the phone was unlocked
        const val EVENT_SHUTDOWN = 26        // UsageEvents.Event.DEVICE_SHUTDOWN
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
