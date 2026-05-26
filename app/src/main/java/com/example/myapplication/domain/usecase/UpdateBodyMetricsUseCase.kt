package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Faza 34 — CRIT-03 Refaktor: workoutRepo odvisnost ODSTRANJENA.
 *
 * Workout session dokument se zdaj zapisuje znotraj iste Firestore transakcije
 * kot gamification posodobitev (streak, XP, plan_day) prek
 * `gamificationUseCase.recordWorkoutCompletion(workoutSessionDoc = ...)`.
 *
 * Zagotavlja all-or-nothing atomarnost: ali gamification + seja uspeta skupaj,
 * ali nobena — odpravlja delno korupcijo stanja (gamification OK, seja izgubljena).
 */
class UpdateBodyMetricsUseCase(
    private val gamificationUseCase: ManageGamificationUseCase
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
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val hour = now.toLocalDateTime(tz).hour
            val timestamp = now.toEpochMilliseconds()

            // Faza 34 — CRIT-03: Workout session dokument sestavimo PRED klicem gamification,
            // da ga posredujemo v isto Firestore transakcijo (atomarni zapis).
            val workoutDoc = mutableMapOf<String, Any>(
                "timestamp"      to timestamp,
                "type"           to if (isExtra) "extra" else "regular",
                "totalKcal"      to totalKcal,
                "totalTimeMin"   to totalTimeMin,
                "exercisesCount" to exercisesCount,
                "planDay"        to planDay,
                "exercises"      to exerciseResults,
                // Faza 12: focusAreas za fetchLastSessionForFocus() iskanje
                "focusAreas"     to focusAreas
            )

            // 1. Gamification (XP, streak, plan_day) + atomarni zapis seje v isti transakciji.
            //    isRestDay = isExtra && isRestDay → extra workout na rest dnevu = samo XP, brez streak
            //    incrementPlanDay = !isExtra → extra workout ne napreduje plan_day
            val res = gamificationUseCase.recordWorkoutCompletion(
                caloriesBurned    = totalKcal.toDouble(),
                hour              = hour,
                isRestDay         = isRestDay && isExtra,
                incrementPlanDay  = !isExtra,
                currentPlanDay    = planDay,          // Faza 23: za optimistični newPlanDay izračun
                workoutSessionDoc = workoutDoc        // Faza 34: atomarni zapis v isti transakciji
            )

            // 2. Ločeni workoutRepo.saveWorkoutSession() klic ODSTRANJEN — seja je atomarno
            //    zapisana znotraj moveToNextDay() transakcije zgoraj (Faza 34 — CRIT-03).

            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}