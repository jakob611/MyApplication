package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.model.Streak
import com.example.myapplication.domain.model.UserDayStatus

/**
 * ⚠️ DEAD CODE — Faza 23: Ta razred ni klican nikjer v produkcijski kodi.
 *
 * Vse delegate klice (workout, restDayStretching, runMidnightCheck, markRestDayPending,
 * getCurrentStreak) pokrije DIREKTNO ManageGamificationUseCase.
 *
 * TODO: Zbriši to datoteko ročno (AI ne brisati datotek).
 * WeeklyStreakWorker kliče ManageGamificationUseCase.executeMidnightStreakCheck() direktno.
 */
@Deprecated(
    "Dead code — Faza 23. Zamenjaj z ManageGamificationUseCase.",
    replaceWith = ReplaceWith("ManageGamificationUseCase(repository)")
)
class UpdateStreakUseCase(
    private val repository: GamificationRepository
) {
    /** @see com.example.myapplication.domain.gamification.ManageGamificationUseCase.recordWorkoutCompletion */
    suspend fun workout(): Streak {
        val newDays = repository.moveToNextDay(
            newStatus        = UserDayStatus.WORKOUT_DONE,
            incrementPlanDay = true
        )
        return Streak(days = newDays, todayStatus = UserDayStatus.WORKOUT_DONE)
    }

    /** @see com.example.myapplication.domain.gamification.ManageGamificationUseCase.restDayInitiated */
    suspend fun restDayStretching(): Streak {
        val newDays = repository.moveToNextDay(
            newStatus        = UserDayStatus.REST_DAY_DONE,
            xpToBeAwarded    = 10,
            xpReason         = "REST_DAY",
            incrementPlanDay = false
        )
        return Streak(days = newDays, todayStatus = UserDayStatus.REST_DAY_DONE)
    }

    /** @see com.example.myapplication.domain.gamification.ManageGamificationUseCase.executeMidnightStreakCheck */
    suspend fun runMidnightCheck() = repository.runMidnightStreakCheck()

    /** @see com.example.myapplication.domain.gamification.ManageGamificationUseCase */
    suspend fun markRestDayPending() = repository.markRestDayPending()

    /** @see com.example.myapplication.domain.gamification.GamificationRepository.getCurrentStreak */
    suspend fun getCurrentStreak(): Streak =
        Streak(days = repository.getCurrentStreak())
}
