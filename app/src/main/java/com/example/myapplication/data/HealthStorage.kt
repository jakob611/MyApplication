package com.example.myapplication.data

import android.util.Log
import java.util.Calendar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.ktx.Firebase
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    suspend fun getTodayAppExercisesCalories(): Int {
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.time

            val workouts = FirestoreHelper.getCurrentUserDocRef()
                .collection("workoutSessions")
                .whereGreaterThanOrEqualTo("date", startOfDay)
                .get()
                .await()
            val workoutCals = workouts.documents.sumOf { (it.get("totalKcal") as? Number)?.toInt() ?: 0 }

            val logs = FirestoreHelper.getCurrentUserDocRef()
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
        try {
            FirestoreHelper.getCurrentUserDocRef()
                .collection("daily_health")
                .document(stats.date)
                .set(stats)
                .await()
            Log.d(TAG, "Saved daily stats for ${stats.date}: $stats")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving daily stats", e)
        }
    }

    suspend fun getDailyStats(date: String): DailyHealthStats? {
        return try {
            val doc = FirestoreHelper.getCurrentUserDocRef()
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
        return try {
            val snap = FirestoreHelper.getCurrentUserDocRef()
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
        try {
            FirestoreHelper.getCurrentUserDocRef()
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
        return try {
            val doc = FirestoreHelper.getCurrentUserDocRef()
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

    private suspend fun doStoreDailyData(
        steps: Long, distanceMeters: Double, activeTimeMin: Long, date: String
    ) {
        try {
            // VARNO: Zapis skozi DailyLogRepository → Firestore Transaction.
            // Ni več .set(updates, SetOptions.merge()) izven transakcije na dailyLogs.
            com.example.myapplication.data.daily.DailyLogRepository().updateDailyLog(date) { data ->
                data["steps"]           = steps
                data["distanceMeters"]  = distanceMeters
                data["activeTimeMins"]  = activeTimeMin
                data["lastHealthSync"]  = Clock.System.now().toEpochMilliseconds()
            }

            // Update achievement progress for Steps
            val goals = getHealthGoals()
            val stepsGoal = goals.stepsGoal
            if (steps >= stepsGoal) {
                // Award achievement — zahteva injekcijo UseCase-a.
                // Backlog (Faza 10): Zamenjaj z ManageGamificationUseCase.recordStepsAchievement()
                // ko bo HealthStorage prenehal biti singleton in bo podpiral DI.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error storing daily data", e)
        }
    }
}
