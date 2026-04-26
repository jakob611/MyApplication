package com.example.myapplication.domain.workout

import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.viewmodels.BodyHomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GetBodyMetricsUseCase(private val workoutRepo: WorkoutRepository, private val settingsRepo: UserPreferencesRepository) {
    fun invoke(email: String): Flow<BodyHomeUiState> = flow {
        // Emit loading state initially
        emit(BodyHomeUiState(isLoading = true))

        var state = BodyHomeUiState(isLoading = false)

        try {
            // Firestore Sync: Preberi osvežene podatke in posodobi lokalno bazo
            val stats = UserProfileManager.getWorkoutStats(email)
            if (stats != null) {
                val streak = stats["streak_days"] as? Int ?: 0
                val total = stats["total_workouts_completed"] as? Int ?: 0
                val weeklyDone = stats["weekly_done"] as? Int ?: 0
                val weeklyTarget = stats["weekly_target"] as? Int ?: 3
                val planDay = stats["plan_day"] as? Int ?: 1
                val lastEpoch = stats["last_workout_epoch"] as? Long ?: 0L
                // Faza 13.3: preberi število zamrznitev
                val streakFreezes = stats["streak_freezes"] as? Int ?: 0

                val isDoneToday = if (lastEpoch == 0L) false else {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val lastDate = kotlinx.datetime.LocalDate.fromEpochDays(lastEpoch.toInt())
                    lastDate == now
                }

                // [Global Audit — Faza 13.3]: settingsRepo.updateWorkoutStats() klic odstranjen.
                // bm_prefs lokalni cache ni več potreben — Firestore je SSOT za streak/planDay.

                state = state.copy(
                    streakDays = streak,
                    streakFreezes = streakFreezes,
                    weeklyDone = weeklyDone,
                    weeklyTarget = weeklyTarget,
                    planDay = planDay,
                    totalWorkoutsCompleted = total,
                    isWorkoutDoneToday = isDoneToday,
                    dailyKcal = settingsRepo.getDailyCalories().toInt()
                )
            } else {
                // Če ni v Firestore (prvi zagon brez oblaka), preberi začasno to kar imamo preden prepiše vse na null
                // Ker je ta koda tu fallback, uporabimo zasilne vrednosti dokler se ne uredi
                val isDoneLocally = settingsRepo.isWorkoutDoneToday()
                state = state.copy(
                    planDay = settingsRepo.getPlanDay(),
                    weeklyTarget = settingsRepo.getWeeklyTarget(),
                    isWorkoutDoneToday = isDoneLocally,
                    dailyKcal = settingsRepo.getDailyCalories().toInt()
                )
            }
        } catch (e: Exception) {
            state = state.copy(errorMessage = e.message)
        }

        emit(state)
    }
}
