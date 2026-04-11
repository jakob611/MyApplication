package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.Badge
import com.example.myapplication.data.BadgeDefinitions

data class WorkoutCompletionResult(
    val unlockedBadges: List<Badge>,
    val xpAwarded: Int,
    val isCritical: Boolean
)

/**
 * UseCase (KMP) razred za logiko doloŤanja gamifikacije na Ťisto Kotlin strukturo.
 * Predstavlja most med UI-jem/Workerji in Repozitorijem,
 * brez zlorabe ali napačnega pomnenja v prejšnjih SharedPreferences.
 */
class ManageGamificationUseCase(
    private val repository: GamificationRepository
) {
    /** Uporabnik je uspeĹˇno zakljuŤil workout */
    suspend fun completeWorkoutSession(calKcal: Int) {
        // UI sedaj klice samo TO!
        repository.updateStreak(isWorkoutSuccess = true)

        // Dodeli XP na podlagi kalorij iz baze (v Repositoryju), tu samo pravilo "Kcal -> XP"
        val workoutXp = 100 + (calKcal / 10)
        repository.awardXP(workoutXp, "WORKOUT_COMPLETE")
    }

    suspend fun recordWorkoutCompletion(caloriesBurned: Double, hour: Int): WorkoutCompletionResult {
        repository.updateStreak(isWorkoutSuccess = true)
        val calorieXP = (caloriesBurned / 8).toInt()
        val baseXP = 50
        val isCritical = kotlin.random.Random.nextFloat() < 0.1f
        val finalBaseXP = if (isCritical) baseXP * 2 else baseXP

        repository.awardXP(finalBaseXP, "WORKOUT_COMPLETE")
        if (calorieXP > 0) {
            repository.awardXP(calorieXP, "CALORIES_BURNED")
        }
        return WorkoutCompletionResult(emptyList(), finalBaseXP + calorieXP, isCritical) // emptyList is fine to bypass legacy logic safely
    }

    suspend fun awardXP(amount: Int, reason: String) {
        repository.awardXP(amount, reason)
    }

    suspend fun recordPlanCreation() {
        repository.awardXP(100, "PLAN_CREATED")
    }

    suspend fun recordLoginOnly() {
        repository.awardXP(10, "DAILY_LOGIN")
    }

    fun getBadgeProgress(badgeId: String, profile: UserProfile): Int {
        return when (badgeId) {
            "first_workout", "committed_10", "committed_50", "committed_100",
            "committed_250", "committed_500" ->
                profile.totalWorkoutsCompleted
            "calorie_crusher_1k", "calorie_crusher_5k", "calorie_crusher_10k" ->
                profile.totalCaloriesBurned.toInt()
            "level_5", "level_10", "level_25", "level_50" ->
                profile.level
            "first_follower", "social_butterfly", "influencer", "celebrity" ->
                profile.followers
            "early_bird" -> profile.earlyBirdWorkouts
            "night_owl" -> profile.nightOwlWorkouts
            "week_warrior", "month_master", "year_champion" ->
                profile.currentLoginStreak
            "first_plan", "plan_master" ->
                profile.totalPlansCreated
            else -> 0
        }
    }

    suspend fun checkAndSyncBadgesOnStartup(): List<Badge> {
        return emptyList() // delegated to future KMP sync logic
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
