package com.example.mindtrack_app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mindtrack_app.data.MoodEntry
import com.example.mindtrack_app.data.MoodRepository
import com.example.mindtrack_app.data.Moods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the mood buttons on the daily reminder notification: saves today's mood to the
 * on-device database without opening the app, then swaps the notification for a short confirmation.
 */
class MoodActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mood = intent.getIntExtra(EXTRA_MOOD, 0)
        if (mood !in 1..5) return
        val app = context.applicationContext
        val pending = goAsync() // keep the receiver alive while the database write finishes
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MoodRepository(app)
                val today = MoodRepository.today()
                // Keep any note already written today; only the mood changes.
                val note = repo.recent(1).firstOrNull()?.takeIf { it.date == today }?.note.orEmpty()
                repo.save(MoodEntry(today, mood, note, System.currentTimeMillis()))
                Reminders.showMoodSaved(app, "${Moods.face(mood)} ${Moods.label(mood)}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG_MOOD = "com.example.mindtrack_app.LOG_MOOD"
        const val EXTRA_MOOD = "mood"
    }
}
