package com.example.mindtrack_app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Optional cloud backup using Firebase Authentication (email/password) and Cloud Firestore.
 *
 * Firestore layout (one private folder per student):
 *   users/{uid}                      -> { name, consentedAt, updatedAt }
 *   users/{uid}/checkIns/{checkInId} -> { timestamp, riskScore, level, tmdTotal, modelVersion }
 *
 * Data minimisation: the individual questionnaire answers are NOT uploaded. They stay on the phone.
 * Only what the History and Result screens need is backed up.
 */
class CloudBackup(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** Email of the signed-in student, or null when backup is off. */
    val signedInEmail: String? get() = auth.currentUser?.email

    private val uid: String? get() = auth.currentUser?.uid

    // ---------------- accounts ----------------

    suspend fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    // ---------------- profile ----------------

    suspend fun uploadProfile(profile: Profile) {
        val id = uid ?: return
        db.collection("users").document(id).set(
            mapOf(
                "name" to profile.name,
                "consentedAt" to profile.consentedAt,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun downloadProfile(): Profile? {
        val id = uid ?: return null
        val doc = db.collection("users").document(id).get().await()
        if (!doc.exists()) return null
        return Profile(
            name = doc.getString("name").orEmpty(),
            consentedAt = doc.getLong("consentedAt") ?: System.currentTimeMillis(),
        )
    }

    // ---------------- check-ins ----------------

    private fun checkInsRef(id: String) = db.collection("users").document(id).collection("checkIns")

    suspend fun uploadCheckIn(c: CheckIn) {
        val id = uid ?: return
        checkInsRef(id).document(c.id).set(
            mapOf(
                "timestamp" to c.timestamp,
                "riskScore" to c.riskScore,
                "level" to c.level.name,
                "tmdTotal" to c.tmdTotal,
                "modelVersion" to c.modelVersion,
            ),
        ).await()
    }

    /** Uploads several check-ins in one atomic batch (Firestore allows up to 500 writes per batch). */
    suspend fun uploadCheckIns(list: List<CheckIn>) {
        val id = uid ?: return
        list.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { c ->
                batch.set(
                    checkInsRef(id).document(c.id),
                    mapOf(
                        "timestamp" to c.timestamp,
                        "riskScore" to c.riskScore,
                        "level" to c.level.name,
                        "tmdTotal" to c.tmdTotal,
                        "modelVersion" to c.modelVersion,
                    ),
                )
            }
            batch.commit().await()
        }
    }

    suspend fun downloadCheckIns(): List<CheckIn> {
        val id = uid ?: return emptyList()
        val snapshot = checkInsRef(id).get().await()
        return snapshot.documents.mapNotNull { d ->
            runCatching {
                CheckIn(
                    id = d.id,
                    timestamp = d.getLong("timestamp")!!,
                    riskScore = d.getDouble("riskScore")!!,
                    level = RiskLevel.valueOf(d.getString("level")!!),
                    tmdTotal = (d.getLong("tmdTotal") ?: 0L).toInt(),
                    answers = emptyMap(), // answers are never uploaded
                    modelVersion = d.getString("modelVersion").orEmpty(),
                )
            }.getOrNull()
        }
    }

    /** Deletes this student's backup (all check-ins and the profile document). */
    suspend fun deleteCloudData() {
        val id = uid ?: return
        val docs = checkInsRef(id).get().await().documents
        docs.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        db.collection("users").document(id).delete().await()
    }

    companion object {
        /** Turns Firebase errors into short messages a student can understand. */
        fun friendlyMessage(e: Throwable): String = when (e) {
            is FirebaseAuthWeakPasswordException -> "Please use a password with at least 6 characters."
            is FirebaseAuthUserCollisionException -> "An account with this email already exists. Try signing in instead."
            is FirebaseAuthInvalidUserException -> "We couldn't find an account with that email."
            is FirebaseAuthInvalidCredentialsException -> "The email or password is incorrect."
            is FirebaseNetworkException -> "No internet connection. Please check your connection and try again."
            is FirebaseFirestoreException ->
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    "The cloud refused access. Check the Firestore security rules."
                else "Cloud error: ${e.code.name.lowercase().replace('_', ' ')}."
            else -> e.localizedMessage ?: "Something went wrong. Please try again."
        }
    }
}
