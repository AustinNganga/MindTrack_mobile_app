package com.example.mindtrack_app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One mood log per calendar day (logging again on the same day updates it). */
data class MoodEntry(val date: String, val mood: Int, val note: String, val updatedAt: Long)

object Moods {
    val faces = listOf("😞", "🙁", "😐", "🙂", "😄")
    val labels = listOf("Very low", "Low", "Okay", "Good", "Great")
    fun face(mood: Int) = faces[(mood - 1).coerceIn(0, 4)]
    fun label(mood: Int) = labels[(mood - 1).coerceIn(0, 4)]
}

/**
 * MindTrack's on-device SQLite database (Android's built-in SQLite, no extra library).
 * Version 1 has one table; add new tables or columns in [onUpgrade] by bumping DB_VERSION.
 */
class MindTrackDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_MOOD (
                $COL_DATE       TEXT    PRIMARY KEY NOT NULL,          -- yyyy-MM-dd, one row per day
                $COL_MOOD       INTEGER NOT NULL CHECK ($COL_MOOD BETWEEN 1 AND 5),
                $COL_NOTE       TEXT    NOT NULL DEFAULT '',
                $COL_UPDATED_AT INTEGER NOT NULL                       -- epoch milliseconds
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes go here, e.g. if (oldVersion < 2) db.execSQL("ALTER TABLE ...")
    }

    companion object {
        const val DB_NAME = "mindtrack.db"
        const val DB_VERSION = 1
        const val TABLE_MOOD = "mood_entries"
        const val COL_DATE = "date"
        const val COL_MOOD = "mood"
        const val COL_NOTE = "note"
        const val COL_UPDATED_AT = "updated_at"
    }
}

class MoodRepository(context: Context) {
    private val helper = MindTrackDatabase(context)

    /** Inserts today's mood, or replaces it if the student already logged one today. */
    suspend fun save(entry: MoodEntry) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MindTrackDatabase.COL_DATE, entry.date)
            put(MindTrackDatabase.COL_MOOD, entry.mood)
            put(MindTrackDatabase.COL_NOTE, entry.note.trim())
            put(MindTrackDatabase.COL_UPDATED_AT, entry.updatedAt)
        }
        helper.writableDatabase.insertWithOnConflict(
            MindTrackDatabase.TABLE_MOOD, null, values, SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    /** Most recent entries first. */
    suspend fun recent(limit: Int = 30): List<MoodEntry> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT date, mood, note, updated_at FROM ${MindTrackDatabase.TABLE_MOOD} ORDER BY date DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(MoodEntry(c.getString(0), c.getInt(1), c.getString(2), c.getLong(3)))
                }
            }
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(MindTrackDatabase.TABLE_MOOD, null, null)
        Unit
    }

    companion object {
        private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun today(): String = fmt().format(Date())

        /** Dates (yyyy-MM-dd) of the last [days] days, oldest first, ending today. */
        fun lastDays(days: Int): List<String> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
            return List(days) {
                val d = fmt().format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                d
            }
        }
    }
}
