package com.example.myapplication.data

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

data class DailyHealthStats(
    val date: String = "",
    val steps: Long = 0,
    val calories: Int = 0,
    val exerciseMinutes: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class HealthGoals(
    val stepsGoal: Long = 7000,
    val caloriesGoal: Int = 500,
    val exerciseMinutesGoal: Int = 30
)

object HealthStorage {
    private const val TAG = "HealthStorage"
    private val db get() = Firebase.firestore
    private val auth get() = Firebase.auth

    private fun getUserDocId(): String? {
        val user = auth.currentUser ?: return null
        return user.email?.takeIf { it.isNotBlank() } ?: user.uid
    }

    suspend fun getTodayAppExercisesCalories(): Int {
        val docId = getUserDocId() ?: return 0
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.time

            val workouts = db.collection("users")
                .document(docId)
                .collection("workoutSessions")
                .whereGreaterThanOrEqualTo("date", startOfDay)
                .get()
                .await()
            val workoutCals = workouts.documents.sumOf { (it.get("totalKcal") as? Number)?.toInt() ?: 0 }

            val logs = db.collection("users")
                .document(docId)
                .collection("exerciseLogs")
                .whereGreaterThanOrEqualTo("date", startOfDay)
                .get()
                .await()
            val logCals = logs.documents.sumOf { (it.get("caloriesKcal") as? Number)?.toInt() ?: 0 }

            Log.d(TAG, "App calories today: Workouts=$workoutCals, Logs=$logCals")
            workoutCals + logCals
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app calories", e)
            0
        }
    }

    suspend fun saveDailyStats(stats: DailyHealthStats) {
        val docId = getUserDocId() ?: return
        try {
            db.collection("users")
                .document(docId)
                .collection("daily_health")
                .document(stats.date)
                .set(stats)
                .await()
            Log.d(TAG, "Saved daily stats for ${stats.date} under $docId: $stats")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving daily stats", e)
        }
    }

    suspend fun getDailyStats(date: String): DailyHealthStats? {
        val docId = getUserDocId() ?: return null
        return try {
            val doc = db.collection("users")
                .document(docId)
                .collection("daily_health")
                .document(date)
                .get()
                .await()

            doc.toObject(DailyHealthStats::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daily stats", e)
            null
        }
    }

    suspend fun getAllDailyStats(): List<DailyHealthStats> {
        val docId = getUserDocId() ?: return emptyList()
        return try {
            val snap = db.collection("users")
                .document(docId)
                .collection("daily_health")
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(DailyHealthStats::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all daily stats", e)
            emptyList()
        }
    }

    suspend fun saveHealthGoals(goals: HealthGoals) {
        val docId = getUserDocId() ?: return
        try {
            db.collection("users")
                .document(docId)
                .collection("settings")
                .document("healthGoals")
                .set(goals)
                .await()
            Log.d(TAG, "Saved health goals: $goals")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving health goals", e)
        }
    }

    suspend fun getHealthGoals(): HealthGoals {
        val docId = getUserDocId() ?: return HealthGoals()
        return try {
            val doc = db.collection("users")
                .document(docId)
                .collection("settings")
                .document("healthGoals")
                .get()
                .await()
            doc.toObject(HealthGoals::class.java) ?: HealthGoals()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting health goals", e)
            HealthGoals()
        }
    }
}
