package com.example.myapplication.domain.workout

import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class UpdateBodyMetricsUseCase(
    private val workoutRepo: WorkoutRepository,
    private val gamificationUseCase: ManageGamificationUseCase
    // settingsRepo odstranjen — Global Audit Faza 13.3: bm_prefs ni več SSOT za streak/planDay
) {
    suspend operator fun invoke(
        email: String,
        totalKcal: Int,
        totalTimeMin: Double,
        exercisesCount: Int,
        planDay: Int,
        isExtra: Boolean,
        exerciseResults: List<Map<String, Any>>,
        /** Fokus mišic te seje — za fetchLastSessionForFocus() v Fazi 12. */
        focusAreas: List<String> = emptyList()
    ): Result<WorkoutCompletionResult?> {
        return try {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val hour = now.toLocalDateTime(tz).hour
            val timestamp = now.toEpochMilliseconds()

            // 1. Pridobivanje nagrad (XP, Early Bird, Critical Hit)
            val res = gamificationUseCase.recordWorkoutCompletion(totalKcal.toDouble(), hour)

            // 2. Priprava podatkov (BREZ FIREBASE IMPORTA)
            val workoutDoc = mutableMapOf(
                "timestamp" to timestamp,
                "type" to if (isExtra) "extra" else "regular",
                "totalKcal" to totalKcal,
                "totalTimeMin" to totalTimeMin,
                "exercisesCount" to exercisesCount,
                "planDay" to planDay,
                "exercises" to exerciseResults,
                // Faza 12: focusAreas za fetchLastSessionForFocus() iskanje
                "focusAreas" to focusAreas
            )

            // 3. Shranjevanje v bazo
            workoutRepo.saveWorkoutSession(email, workoutDoc)

            // 3b. Faza 13.3: Streak Engine + Plan Progression + Streak Freeze (atomarna transakcija)
            // Posodobi streak_days, plan_day, last_workout_epoch in streak_freezes v users/{uid}
            // isExtra workouts -> incrementPlanDay=false (streak se posodobi, plan_day pa ne)
            val progressResult = com.example.myapplication.data.settings.UserProfileManager
                .updateUserProgressAfterWorkout(incrementPlanDay = !isExtra)
            android.util.Log.d("UpdateBodyMetrics",
                " Streak Engine result: planDay=${progressResult.newPlanDay}, " +
                "streak=${progressResult.newStreakDays}, freezes=${progressResult.newStreakFreezes}, " +
                "freezeUsed=${progressResult.freezeUsed}, isExtra=$isExtra")

            // 4. [REMOVED — Faza 13.3 Global Audit] settingsRepo.updateWorkoutStats() je bil DEPRECATED.
            //    plan_day, streak_days, last_workout_epoch — vse piše updateUserProgressAfterWorkout() v Firestore.
            //    bm_prefs ni več relevanten za te podatke.

            // 5. [DEPRECATED — SSOT je dailyLogs] Stari SharedPrefs zapis kalorij
            // TODO: Odstrani ko bo bm_prefs.daily_calories popolnoma nadomeščen z DailyLogRepository
            // settingsRepo.updateDailyCalories(totalKcal.toDouble(), timestamp)

            // 6. [MOVED to ManageGamificationUseCase.recordWorkoutCompletion] burnedCalories → dailyLogs
            // Pisanje je centralizirano v Workout-Nutrition Bridgeu. Tu ni duplikata.

            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}