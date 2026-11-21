package com.example.mindtrack_app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One day's total screen time, saved so the history outlives Android's own (short) usage log. */
data class DailyScreenTime(val date: String, val totalMillis: Long, val unlocks: Int?)

/** Stores daily screen-time totals in the `screen_time_daily` table (added in database version 2). */
class ScreenTimeRepository(context: Context) {
    private val helper = MindTrackDatabase(context)

    /**
     * Saves days read from Android. Today's value is always replaced (it is still growing).
     * For past days we keep the larger value, because Android deletes old usage events after
     * a few days and a later re-read could otherwise overwrite a full day with a partial one.
     */
    suspend fun save(days: List<DailyScreenTime>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val today = MoodRepository.today()
        db.beginTransaction()
        try {
            days.forEach { d ->
                val stored = db.rawQuery(
                    "SELECT total_ms FROM ${MindTrackDatabase.TABLE_SCREEN} WHERE date = ?", arrayOf(d.date),
                ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                if (d.date != today && stored != null && stored >= d.totalMillis) return@forEach
                val values = ContentValues().apply {
                    put("date", d.date)
                    put("total_ms", d.totalMillis)
                    if (d.unlocks != null) put("unlocks", d.unlocks) else putNull("unlocks")
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict(MindTrackDatabase.TABLE_SCREEN, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** The last [days] days, oldest first; days with no record are left out. */
    suspend fun lastDays(days: Int): List<DailyScreenTime> = withContext(Dispatchers.IO) {
        val from = MoodRepository.lastDays(days).first()
        helper.readableDatabase.rawQuery(
            "SELECT date, total_ms, unlocks FROM ${MindTrackDatabase.TABLE_SCREEN} WHERE date >= ? ORDER BY date ASC",
            arrayOf(from),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(DailyScreenTime(c.getString(0), c.getLong(1), if (c.isNull(2)) null else c.getInt(2)))
            }
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(MindTrackDatabase.TABLE_SCREEN, null, null)
        Unit
    }
}

/** Compares mood with screen time on the days that have both. All maths on the phone. */
data class MoodScreenPattern(
    val pairedDays: Int,
    val medianMillis: Long,
    val moodOnHigherDays: Double,
    val moodOnLowerDays: Double,
    val correlation: Double?, // Pearson r, only with 7+ paired days
)

object MoodScreenAnalysis {
    const val MIN_DAYS = 4

    fun pattern(moods: List<MoodEntry>, screen: List<DailyScreenTime>): MoodScreenPattern? {
        val moodByDate = moods.associate { it.date to it.mood }
        val pairs = screen.mapNotNull { s -> moodByDate[s.date]?.let { s.totalMillis to it } }
        if (pairs.size < MIN_DAYS) return null
        val sorted = pairs.map { it.first }.sorted()
        val median = sorted[sorted.size / 2]
        val higher = pairs.filter { it.first >= median }.map { it.second }
        val lower = pairs.filter { it.first < median }.map { it.second }
        if (higher.isEmpty() || lower.isEmpty()) return null // every day had the same screen time
        return MoodScreenPattern(
            pairedDays = pairs.size,
            medianMillis = median,
            moodOnHigherDays = higher.average(),
            moodOnLowerDays = lower.average(),
            correlation = if (pairs.size >= 7) pearson(pairs.map { it.first.toDouble() }, pairs.map { it.second.toDouble() }) else null,
        )
    }

    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        val mx = x.average(); val my = y.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in x.indices) {
            val dx = x[i] - mx; val dy = y[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / Math.sqrt(sxx * syy)
    }
}
