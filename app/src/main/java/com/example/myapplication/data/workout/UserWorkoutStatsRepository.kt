package com.example.myapplication.data.workout

import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.domain.workout.WorkoutStats
import com.example.myapplication.domain.workout.WorkoutStatsRepository
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
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

            val todayStr = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            @Suppress("UNCHECKED_CAST")
            val dailyHistory = (doc.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
            val todayStatus = dailyHistory[todayStr]?.toString() ?: ""

            WorkoutStats(
                streakDays             = doc.getLong("streak_days")?.toInt() ?: 0,
                streakFreezes          = doc.getLong("streak_freezes")?.toInt() ?: 0,
                weeklyDone             = doc.getLong("weekly_done")?.toInt() ?: 0,
                weeklyTarget           = doc.getLong("weekly_target")?.toInt() ?: 3,
                planDay                = doc.getLong("plan_day")?.toInt() ?: 1,
                totalWorkoutsCompleted = doc.getLong("total_workouts_completed")?.toInt() ?: 0,
                lastWorkoutEpoch       = doc.getLong("last_workout_epoch") ?: 0L,
                todayStatus            = todayStatus,
                todayIsRest            = false,  // ViewModel izračuna iz plan modela
                dailyKcal              = prefs.getDailyCalories().toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun isWorkoutDoneToday(): Boolean = prefs.isWorkoutDoneToday()

    override suspend fun getPlanDay(): Int = prefs.getPlanDay()

    override suspend fun getDailyCalories(): Int = prefs.getDailyCalories().toInt()
}

