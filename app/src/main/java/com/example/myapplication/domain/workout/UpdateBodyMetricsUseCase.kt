package com.example.myapplication.domain.workout

import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import com.example.myapplication.data.settings.UserPreferencesRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.tasks.await

class UpdateBodyMetricsUseCase(
    private val workoutRepo: WorkoutRepository,
    private val gamificationUseCase: ManageGamificationUseCase,
    private val settingsRepo: UserPreferencesRepository
) {
    suspend operator fun invoke(
        email: String,
        totalKcal: Int,
        totalTimeMin: Double,
        exercisesCount: Int,
        planDay: Int,
        isExtra: Boolean,
        exerciseResults: List<Map<String, Any>>
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
                "timestamp" to timestamp, // Čist Long tip, repozitorij naj ga pretvori
                "type" to if (isExtra) "extra" else "regular",
                "totalKcal" to totalKcal,
                "totalTimeMin" to totalTimeMin,
                "exercisesCount" to exercisesCount,
                "planDay" to planDay,
                "exercises" to exerciseResults
            )

            // 3. Shranjevanje v bazo
            workoutRepo.saveWorkoutSession(email, workoutDoc)

            // 4. Posodobitev lokalnega stanja (Streaki in zadnji trening)
            if (!isExtra) {
                settingsRepo.updateWorkoutStats(
                    completedDay = planDay,
                    timestamp = timestamp
                )
            }

            // 5. Posodobi dnevne kalorije v repozitoriju posameznikovih nastavitev
            settingsRepo.updateDailyCalories(totalKcal.toDouble(), timestamp)

            // 6. Uskladi burnedCalories s tabelo dailyLogs (da takoj osveži graf NutritionScreen)
            val todayStr = now.toLocalDateTime(tz).date.toString()
            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                .collection("dailyLogs").document(todayStr)
                .set(mapOf("burnedCalories" to com.google.firebase.firestore.FieldValue.increment(totalKcal.toDouble())), com.google.firebase.firestore.SetOptions.merge())
                .await()

            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}