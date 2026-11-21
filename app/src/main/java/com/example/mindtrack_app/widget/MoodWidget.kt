package com.example.mindtrack_app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.mindtrack_app.MainActivity
import com.example.mindtrack_app.R
import com.example.mindtrack_app.data.AppSettings
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.Moods
import com.example.mindtrack_app.reminders.MoodActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget: today's mood, the mood streak, and one-tap mood buttons.
 * The buttons reuse [MoodActionReceiver], so the widget and the reminder notification share one code path.
 * When the app lock is on, the widget hides which mood was logged (privacy).
 */
class MoodWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every MindTrack widget (call after a mood is logged or a setting changes). */
        suspend fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, MoodWidget::class.java))
            if (ids.isNotEmpty()) render(app, manager, ids)
        }

        /** Same as [refresh], for callers that are not in a coroutine. */
        fun updateAll(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { runCatching { refresh(app) } }
        }

        private suspend fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val moods = MoodRepository(ctx).recent(120)
            val today = moods.firstOrNull()?.takeIf { it.date == MoodRepository.today() }
            val streak = MoodRepository.streak(moods.map { it.date })
            val locked = AppSettings(ctx).lockEnabled

            val title = if (!locked && streak > 0) "MindTrack · 🔥 $streak-day streak" else "MindTrack"
            val status = when {
                today == null -> "How are you feeling today?"
                locked -> "Today's mood saved ✓"
                else -> "Today: ${Moods.face(today.mood)} ${Moods.label(today.mood)}. Tap again to change it."
            }

            val views = RemoteViews(ctx.packageName, R.layout.widget_mood).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_status, status)
                setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
                setOnClickPendingIntent(R.id.widget_mood_low, logMood(ctx, 2))
                setOnClickPendingIntent(R.id.widget_mood_okay, logMood(ctx, 3))
                setOnClickPendingIntent(R.id.widget_mood_good, logMood(ctx, 4))
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun openApp(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(ctx, 3100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun logMood(ctx: Context, mood: Int): PendingIntent {
            val intent = Intent(ctx, MoodActionReceiver::class.java).apply {
                action = MoodActionReceiver.ACTION_LOG_MOOD
                putExtra(MoodActionReceiver.EXTRA_MOOD, mood)
                putExtra(MoodActionReceiver.EXTRA_FROM_WIDGET, true)
            }
            return PendingIntent.getBroadcast(ctx, 3000 + mood, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
