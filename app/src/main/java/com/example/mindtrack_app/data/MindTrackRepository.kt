package com.example.mindtrack_app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val name: String, val consentedAt: Long)

enum class RiskLevel(val label: String) { LOW("Low"), ELEVATED("Elevated") }

data class CheckIn(
    val id: String,
    val timestamp: Long,
    val riskScore: Double,
    val level: RiskLevel,
    val tmdTotal: Int,
    val answers: Map<String, Int>,
    val modelVersion: String,
)

/**
 * Storage boundary for the app. [LocalRepository] keeps everything on the device.
 * To move to Firebase later, write a FirebaseRepository implementing this interface
 * (e.g. Firestore collection users/{uid}/checkIns) and swap it in MindTrackViewModel.
 */
interface MindTrackRepository {
    suspend fun getProfile(): Profile?
    suspend fun saveProfile(profile: Profile)
    suspend fun getCheckIns(): List<CheckIn>
    suspend fun addCheckIn(checkIn: CheckIn)
    suspend fun deleteAll()
}

class LocalRepository(context: Context) : MindTrackRepository {
    private val prefs = context.applicationContext.getSharedPreferences("mindtrack_v2", Context.MODE_PRIVATE)

    override suspend fun getProfile(): Profile? = withContext(Dispatchers.IO) {
        if (!prefs.contains(KEY_CONSENT)) null
        else Profile(prefs.getString(KEY_NAME, "") ?: "", prefs.getLong(KEY_CONSENT, 0L))
    }

    override suspend fun saveProfile(profile: Profile) = withContext(Dispatchers.IO) {
        prefs.edit {
            putString(KEY_NAME, profile.name)
            putLong(KEY_CONSENT, profile.consentedAt)
        }
    }

    override suspend fun getCheckIns(): List<CheckIn> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_CHECKINS, null) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    override suspend fun addCheckIn(checkIn: CheckIn) {
        replaceCheckIns(getCheckIns() + checkIn)
    }

    /** Overwrites the saved list (used when merging check-ins restored from the cloud). */
    suspend fun replaceCheckIns(list: List<CheckIn>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        list.distinctBy { it.id }.forEach { arr.put(toJson(it)) }
        prefs.edit { putString(KEY_CHECKINS, arr.toString()) }
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        prefs.edit { clear() }
    }

    private fun toJson(c: CheckIn) = JSONObject().apply {
        put("id", c.id)
        put("timestamp", c.timestamp)
        put("riskScore", c.riskScore)
        put("level", c.level.name)
        put("tmdTotal", c.tmdTotal)
        put("modelVersion", c.modelVersion)
        put("answers", JSONObject().apply { c.answers.forEach { (k, v) -> put(k, v) } })
    }

    private fun fromJson(o: JSONObject): CheckIn {
        val a = o.getJSONObject("answers")
        val answers = a.keys().asSequence().associateWith { a.getInt(it) }
        return CheckIn(
            id = o.getString("id"),
            timestamp = o.getLong("timestamp"),
            riskScore = o.getDouble("riskScore"),
            level = RiskLevel.valueOf(o.getString("level")),
            tmdTotal = o.getInt("tmdTotal"),
            answers = answers,
            modelVersion = o.optString("modelVersion", ""),
        )
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_CONSENT = "consented_at"
        const val KEY_CHECKINS = "check_ins"
    }
}
