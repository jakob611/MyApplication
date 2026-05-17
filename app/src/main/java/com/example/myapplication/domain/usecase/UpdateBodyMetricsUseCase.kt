package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import com.example.myapplication.domain.repository.WorkoutRepository
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
        focusAreas: List<String> = emptyList(),
        /** FIX: Extra workout na rest dan = samo bonus XP, brez streak posodobitve. */
        isRestDay: Boolean = false
    ): Result<WorkoutCompletionResult?> {
        return try {
            val tz = TimeZone.Companion.currentSystemDefault()
            val now = Clock.System.now()
            val hour = now.toLocalDateTime(tz).hour
            val timestamp = now.toEpochMilliseconds()

            // 1. Pridobivanje nagrad (XP, Early Bird, Critical Hit) + Faza 8: Unified Streak Engine
            // isRestDay = isExtra && isRestDay → extra workout na rest dnevu = samo XP, brez streak
            // incrementPlanDay = !isExtra → extra workout ne napreduje plan_day
            val res = gamificationUseCase.recordWorkoutCompletion(
                caloriesBurned = totalKcal.toDouble(),
                hour = hour,
                isRestDay = isRestDay && isExtra,
                incrementPlanDay = !isExtra
            )

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

            // 3b. [Faza 8 — Unified Streak Engine] Streak je zdaj v celoti v ManageGamificationUseCase
            //     → recordWorkoutCompletion() → repository.processWorkoutCompletion()
            //     UserProfileManager.updateUserProgressAfterWorkout() je DEPRECATED no-op.

            // 4–6. Ostalo nespremenjeno (XP, burnedCalories bridge so v ManageGamificationUseCase)

            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}