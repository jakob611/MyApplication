package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.model.Streak

/**
 * UpdateStreakUseCase — posamezna, focused operacija za posodabljanje streaka.
 *
 * Clean Architecture: UseCase je most med UI/ViewModel in Repository.
 * Nadomešča direktne klice repository.updateStreak() razpršene po kodi.
 *
 * Lokacija: domain/usecase/ (skupna mapa za vse use cases, neodvisno od poddomen)
 *
 * iOS-ready: brez Android odvisnosti.
 */
class UpdateStreakUseCase(
    private val repository: GamificationRepository
) {
    /**
     * Posodobi streak kot "Workout Done" dan.
     * @return posodobljeni Streak domenski model
     */
    suspend fun workout(): Streak {
        val newDays = repository.updateStreak(
            isWorkoutSuccess = true,
            activityType = "WORKOUT_DONE"
        )
        return Streak(days = newDays, todayStatus = "WORKOUT_DONE")
    }

    /**
     * Posodobi streak kot "Stretching Done" (rest dan opravil).
     * @return posodobljeni Streak domenski model
     */
    suspend fun restDayStretching(): Streak {
        val newDays = repository.updateStreak(
            isWorkoutSuccess = true,
            activityType = "STRETCHING_DONE"
        )
        return Streak(days = newDays, todayStatus = "STRETCHING_DONE")
    }

    /**
     * Preveri polnočni streak (zamrznjena / zamujeni dnevi).
     * Kliče se iz WeeklyStreakWorker.
     */
    suspend fun runMidnightCheck() {
        repository.runMidnightStreakCheck()
    }

    /**
     * Vrni trenutni streak iz Firestore-a.
     */
    suspend fun getCurrentStreak(): Streak {
        val days = repository.getCurrentStreak()
        return Streak(days = days)
    }
}

