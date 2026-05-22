package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.model.Streak
import com.example.myapplication.domain.model.UserDayStatus

/**
 * UpdateStreakUseCase — Faza 21: poenostavljen zgoraj moveToNextDay() SSOT.
 *
 * workout() in restDayStretching() delegirata na repository.moveToNextDay().
 * Brez android odvisnosti — iOS-ready.
 */
class UpdateStreakUseCase(
    private val repository: GamificationRepository
) {
    /** Posodobi streak kot "Workout Done" dan. */
    suspend fun workout(): Streak {
        val newDays = repository.moveToNextDay(
            newStatus        = UserDayStatus.WORKOUT_DONE,
            incrementPlanDay = true
        )
        return Streak(days = newDays, todayStatus = UserDayStatus.WORKOUT_DONE)
    }

    /** Posodobi streak kot "Stretching Done" (rest dan opravil). */
    suspend fun restDayStretching(): Streak {
        val newDays = repository.moveToNextDay(
            newStatus        = UserDayStatus.REST_DAY_DONE,
            xpToBeAwarded    = 10,
            xpReason         = "REST_DAY",
            incrementPlanDay = false
        )
        return Streak(days = newDays, todayStatus = UserDayStatus.REST_DAY_DONE)
    }

    /** Preveri polnočni streak — kliče se iz WeeklyStreakWorker. */
    suspend fun runMidnightCheck() = repository.runMidnightStreakCheck()

    /** Označi danes kot REST_DAY_PENDING. */
    suspend fun markRestDayPending() = repository.markRestDayPending()

    /** Vrni trenutni streak iz Firestore-a. */
    suspend fun getCurrentStreak(): Streak =
        Streak(days = repository.getCurrentStreak())
}
