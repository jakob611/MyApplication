package com.example.myapplication.domain.repository

/**
 * Domenski model za workout statistike.
 * KMP-ready: čista Kotlin data class.
 *
 * todayIsRest: data layer implementacija izračuna iz plan_day + plana.
 * todayStatus: vrednosti "WORKOUT_DONE"|"STRETCHING_DONE"|"PENDING_STRETCHING"|"FROZEN"|"MISSED"|""
 */
data class WorkoutStats(
    val streakDays: Int = 0,
    val streakFreezes: Int = 0,
    val weeklyDone: Int = 0,
    val weeklyTarget: Int = 3,
    val planDay: Int = 1,
    val totalWorkoutsCompleted: Int = 0,
    val lastWorkoutEpoch: Long = 0L,
    val todayStatus: String = "",
    val todayIsRest: Boolean = false,
    val dailyKcal: Int = 0
)

/**
 * Repository interface za branje workout statistik.
 *
 * Nadomešča direktno klicanje UserProfileManager in UserPreferencesRepository
 * iz domain layer-a (Clean Architecture: UseCase komunicira samo z interfejsi).
 *
 * KMP-ready: brez Android odvisnosti.
 * Implementacija živi v data/workout/UserWorkoutStatsRepository.kt.
 */
interface WorkoutStatsRepository {
    /**
     * Vrni workout statistike za danega uporabnika.
     * @param email Email za Firestore iskanje
     * @return WorkoutStats ali null, če podatki niso na voljo
     */
    suspend fun getWorkoutStats(email: String): WorkoutStats?

    /**
     * Lokalni fallback: ali je trening opravljen danes (SharedPrefs)?
     */
    suspend fun isWorkoutDoneToday(): Boolean

    /**
     * Lokalni fallback: trenutni plan day (SharedPrefs).
     */
    suspend fun getPlanDay(): Int

    /**
     * Lokalni fallback: dnevni kalorični cilj (SharedPrefs).
     */
    suspend fun getDailyCalories(): Int
}
