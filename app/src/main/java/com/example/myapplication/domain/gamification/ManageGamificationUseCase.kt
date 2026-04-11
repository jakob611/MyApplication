package com.example.myapplication.domain.gamification

/**
 * UseCase (KMP) razred za logiko določanja gamifikacije na čisto Kotlin strukturo.
 * Predstavlja most med UI-jem/Workerji in Repozitorijem,
 * brez zlorabe ali napačnega pomnenja v prejšnjih SharedPreferences.
 */
class ManageGamificationUseCase(
    private val repository: GamificationRepository
) {
    /** Uporabnik je uspešno zaključil workout */
    suspend fun completeWorkoutSession(calKcal: Int) {
        // UI sedaj klice samo TO!
        repository.updateStreak(isWorkoutSuccess = true)

        // Dodeli XP na podlagi kalorij iz baze (v Repositoryju), tu samo pravilo "Kcal -> XP"
        val workoutXp = 100 + (calKcal / 10)
        repository.awardXP(workoutXp, "WORKOUT_COMPLETE")
    }

    /** Uporabnik si vzame prosto, zato dobimo nekaj XP, ampak streak ohranimo. */
    suspend fun restDayInitiated() {
        repository.updateStreak(isWorkoutSuccess = true) // Rest Day je veljaven uspeh dneva
        repository.awardXP(10, "REST_DAY")
    }

    /** Worker (ob polnoči) pozove, da naj bi bil tisti dan odtreniran ali zamujen. */
    suspend fun executeMidnightStreakCheck() {
        repository.runMidnightStreakCheck()
        // Repository bo morda preveril uporabo Freeze in zamujenega stanja
        // in ponastavil na 0 neposredno v skupni bazi (v resnici brez uničujočih SharedPreferences).
    }
}

