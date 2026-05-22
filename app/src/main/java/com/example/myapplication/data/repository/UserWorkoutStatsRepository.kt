package com.example.myapplication.data.repository

import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.repository.WorkoutStats
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import com.example.myapplication.data.store.FirestoreHelper
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Data-layer implementacija WorkoutStatsRepository.
 *
 * Bere podatke iz Firestore (prek FirestoreHelper) in lokalne SharedPrefs (fallback).
 * Wrappira UserProfileManager.getWorkoutStats() logiko v domenski interface.
 *
 * todayIsRest je vedno false — ViewModel izračuna isRestDay iz plan modela
 * (ki je presentation concern, ne domenski concern).
 *
 * @param prefs UserPreferencesRepository za lokalni fallback (SharedPrefs)
 */
class UserWorkoutStatsRepository(
    private val prefs: UserPreferencesRepository
) : WorkoutStatsRepository {

    override suspend fun getWorkoutStats(email: String): WorkoutStats? {
        return try {
            val docRef = FirestoreHelper.getCurrentUserDocRef() ?: return null
            val doc = docRef.get().await()
            if (!doc.exists()) return null

            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayStr = today.toString()

            @Suppress("UNCHECKED_CAST")
            val dailyHistory = (doc.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
            // Faza 21: pretvori surovi Firestore string v tipsko-varni UserDayStatus
            val todayStatus = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

            // ✅ FIX: weeklyDone se izračuna dinamično iz dailyHistory tekočega tedna (pon–danes).
            // S tem se števec samodejno ponastavi ob novem tednu — ni potrebe po ločenem reset polju.
            val daysFromMonday = today.dayOfWeek.value - 1 // Monday.value=1, Sunday.value=7 → pon=0
            val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
            val weeklyDoneCalc = dailyHistory.entries.count { (dateStr, status) ->
                val s = status.toString()
                if (s != "WORKOUT_DONE" && s != "REST_WORKOUT_DONE") return@count false
                try { val d = LocalDate.parse(dateStr); d in monday..today } catch (_: Exception) { false }
            }

            WorkoutStats(
                streakDays = doc.getLong("streak_days")?.toInt() ?: 0,
                streakFreezes = doc.getLong("streak_freezes")?.toInt() ?: 0,
                weeklyDone = weeklyDoneCalc,
                weeklyTarget = doc.getLong("weekly_target")?.toInt() ?: 3,
                planDay = doc.getLong("plan_day")?.toInt() ?: 1,
                totalWorkoutsCompleted = doc.getLong("total_workouts_completed")?.toInt() ?: 0,
                lastWorkoutEpoch = doc.getLong("last_workout_epoch") ?: 0L,
                todayStatus = todayStatus,
                todayIsRest = false,  // ViewModel izračuna iz plan modela
                dailyKcal = prefs.getDailyCalories().toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun isWorkoutDoneToday(): Boolean = prefs.isWorkoutDoneToday()

    override suspend fun getPlanDay(): Int = prefs.getPlanDay()

    override suspend fun getDailyCalories(): Int = prefs.getDailyCalories().toInt()
}