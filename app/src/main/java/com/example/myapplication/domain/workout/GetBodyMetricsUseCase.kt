package com.example.myapplication.domain.workout

import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.viewmodels.BodyHomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.toLocalDateTime

class GetBodyMetricsUseCase(private val workoutRepo: WorkoutRepository, private val settingsRepo: UserPreferencesRepository) {
    /**
     * @param email Email za Firestore lookup
     * @param plan  Trenutni plan (za določitev todayIsRest) — Faza 8
     */
    fun invoke(email: String, plan: com.example.myapplication.data.PlanResult? = null): Flow<BodyHomeUiState> = flow {
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
                val streakFreezes = stats["streak_freezes"] as? Int ?: 0
                // Faza 8: today_status iz dailyHistory (za stretching button vidnost)
                val todayStatus = stats["today_status"] as? String ?: ""

                // Faza 8: isWorkoutDoneToday = WORKOUT_DONE ali STRETCHING_DONE v dailyHistory
                // (prej: samo epoch check, ki ni deloval za stretching)
                val isDoneToday = when {
                    todayStatus == "WORKOUT_DONE" || todayStatus == "STRETCHING_DONE" -> true
                    todayStatus.isNotEmpty() -> false  // PENDING_STRETCHING, FROZEN, MISSED → ni done
                    // Fallback: epoch check za backward compatibility
                    lastEpoch == 0L -> false
                    else -> {
                        val now = kotlinx.datetime.Clock.System.now()
                            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                        val lastDate = kotlinx.datetime.LocalDate.fromEpochDays(lastEpoch.toInt())
                        lastDate == now
                    }
                }

                // Faza 8: todayIsRest — iz plana (ali je planDay rest day?)
                val todayIsRest = plan?.weeks
                    ?.flatMap { it.days }
                    ?.firstOrNull { it.dayNumber == planDay }
                    ?.isRestDay ?: false

                state = state.copy(
                    streakDays = streak,
                    streakFreezes = streakFreezes,
                    weeklyDone = weeklyDone,
                    weeklyTarget = weeklyTarget,
                    planDay = planDay,
                    totalWorkoutsCompleted = total,
                    isWorkoutDoneToday = isDoneToday,
                    todayIsRest = todayIsRest,
                    todayStatus = todayStatus,
                    dailyKcal = settingsRepo.getDailyCalories().toInt()
                )
            } else {
                // Fallback: začasne vrednosti dokler Firestore ne odgovori
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
