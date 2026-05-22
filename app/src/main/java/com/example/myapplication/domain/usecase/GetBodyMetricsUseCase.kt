package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMetrics
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GetBodyMetricsUseCase — čisti domenski use case za branje fitness metrik.
 *
 * Faza 21: todayStatus je zdaj UserDayStatus (tipsko-varni enum).
 * isWorkoutDoneToday se izračuna direktno iz UserDayStatus.isDoneToday.
 */
class GetBodyMetricsUseCase(
    private val statsRepo: WorkoutStatsRepository
) {
    fun invoke(email: String): Flow<BodyMetrics> = flow {
        emit(BodyMetrics(isLoading = true))

        try {
            val stats = statsRepo.getWorkoutStats(email)
            if (stats != null) {
                emit(BodyMetrics(
                    streakDays             = stats.streakDays,
                    streakFreezes          = stats.streakFreezes,
                    weeklyDone             = stats.weeklyDone,
                    weeklyTarget           = stats.weeklyTarget,
                    planDay                = stats.planDay,
                    totalWorkoutsCompleted = stats.totalWorkoutsCompleted,
                    // UserDayStatus.isDoneToday nadomešča staro večpogojno when() logiko
                    isWorkoutDoneToday     = stats.todayStatus.isDoneToday,
                    dailyKcal              = stats.dailyKcal.takeIf { it > 0 } ?: statsRepo.getDailyCalories(),
                    todayIsRest            = stats.todayIsRest,
                    todayStatus            = stats.todayStatus,
                    isLoading              = false
                ))
            } else {
                // Fallback: lokalni SharedPrefs podatki
                emit(BodyMetrics(
                    planDay            = statsRepo.getPlanDay(),
                    weeklyTarget       = 3,
                    isWorkoutDoneToday = statsRepo.isWorkoutDoneToday(),
                    dailyKcal          = statsRepo.getDailyCalories(),
                    todayStatus        = if (statsRepo.isWorkoutDoneToday())
                        UserDayStatus.WORKOUT_DONE else UserDayStatus.WORKOUT_PENDING,
                    isLoading          = false
                ))
            }
        } catch (e: Exception) {
            emit(BodyMetrics(errorMessage = e.message, isLoading = false))
        }
    }
}