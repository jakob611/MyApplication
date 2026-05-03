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
     * ⚠️ Preverja IZKLJUČNO včerajšnji dan — nikoli ne auto-complete rest dnevov za danes.
     * Kliče se iz WeeklyStreakWorker.
     */
    suspend fun runMidnightCheck() {
        repository.runMidnightStreakCheck()
    }

    /**
     * Označi današnji dan kot REST DAY v čakanju (PENDING_STRETCHING).
     *
     * Status ostane PENDING_STRETCHING, DOKLER uporabnik ne klikne
     * "Done" na Stretching kartici → takrat se kliče [restDayStretching()].
     *
     * Kliče se iz BodyModuleHomeScreen ob odprtju, ko je today rest day
     * in ni bilo raztezanja. Idempotentno — že zaključenih dni ne prepiše.
     *
     * iOS-ready: brez Android odvisnosti.
     */
    suspend fun markRestDayPending() {
        repository.markRestDayPending()
    }

    /**
     * Vrni trenutni streak iz Firestore-a.
     */
    suspend fun getCurrentStreak(): Streak {
        val days = repository.getCurrentStreak()
        return Streak(days = days)
    }
}


