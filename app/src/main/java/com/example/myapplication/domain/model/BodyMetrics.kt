package com.example.myapplication.domain.model

/**
 * Čisti domenski model za stanje telesa/treninga.
 * Nadomešča BodyHomeUiState kot return tip GetBodyMetricsUseCase.
 *
 * KMP-ready: brez Android, brez viewmodels, brez data layer odvisnosti.
 * ViewModel je odgovoren za mapiranje BodyMetrics → BodyHomeUiState (za UI).
 */
data class BodyMetrics(
    val streakDays: Int = 0,
    val streakFreezes: Int = 0,
    val weeklyDone: Int = 0,
    val weeklyTarget: Int = 3,
    val planDay: Int = 1,
    val totalWorkoutsCompleted: Int = 0,
    val isWorkoutDoneToday: Boolean = false,
    val dailyKcal: Int = 0,
    val todayIsRest: Boolean = false,
    /** Vrednosti: "WORKOUT_DONE" | "STRETCHING_DONE" | "PENDING_STRETCHING" | "FROZEN" | "MISSED" | "" */
    val todayStatus: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
