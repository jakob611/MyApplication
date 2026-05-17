package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMetrics
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * GetBodyMetricsUseCase — čisti domenski use case za branje fitness metrik.
 *
 * Clean Architecture:
 * - Komunicira SAMO z domain/workout/WorkoutStatsRepository (interface)
 * - Vrača domenski model BodyMetrics (ne BodyHomeUiState!)
 * - ViewModel (BodyModuleHomeViewModel) je odgovoren za mapiranje BodyMetrics → BodyHomeUiState
 *
 * KMP-ready: brez Android, viewmodels ali data.settings odvisnosti.
 *
 * @param statsRepo Interface za workout statistike (data/workout/UserWorkoutStatsRepository implementira)
 */
class GetBodyMetricsUseCase(
    private val statsRepo: WorkoutStatsRepository
) {
    /**
     * @param email Email za Firestore lookup
     */
    fun invoke(email: String): Flow<BodyMetrics> = flow {
        emit(BodyMetrics(isLoading = true))

        try {
            val stats = statsRepo.getWorkoutStats(email)
            if (stats != null) {
                val isDoneToday = when {
                    stats.todayStatus == "WORKOUT_DONE" || stats.todayStatus == "STRETCHING_DONE" -> true
                    stats.todayStatus.isNotEmpty() -> false  // PENDING_STRETCHING, FROZEN, MISSED
                    // Fallback: epoch check za backward compatibility
                    stats.lastWorkoutEpoch == 0L -> false
                    else -> {
                        val now = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                        val lastDate = LocalDate.fromEpochDays(stats.lastWorkoutEpoch.toInt())
                        lastDate == now
                    }
                }

                emit(
                    BodyMetrics(
                    streakDays = stats.streakDays,
                    streakFreezes = stats.streakFreezes,
                    weeklyDone = stats.weeklyDone,
                    weeklyTarget = stats.weeklyTarget,
                    planDay = stats.planDay,
                    totalWorkoutsCompleted = stats.totalWorkoutsCompleted,
                    isWorkoutDoneToday = isDoneToday,
                    dailyKcal = stats.dailyKcal.takeIf { it > 0 } ?: statsRepo.getDailyCalories(),
                    todayIsRest = stats.todayIsRest,
                    todayStatus = stats.todayStatus,
                    isLoading = false
                ))
            } else {
                // Fallback: lokalni SharedPrefs podatki
                emit(
                    BodyMetrics(
                        planDay = statsRepo.getPlanDay(),
                        weeklyTarget = 3,
                        isWorkoutDoneToday = statsRepo.isWorkoutDoneToday(),
                        dailyKcal = statsRepo.getDailyCalories(),
                        isLoading = false
                    )
                )
            }
        } catch (e: Exception) {
            emit(BodyMetrics(errorMessage = e.message, isLoading = false))
        }
    }
}