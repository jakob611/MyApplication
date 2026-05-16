package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.model.AchievementProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.toLocalDateTime

/**
 * Rezultat zaključenega workout-a.
 * unlockedBadges: seznam badge ID-jev (ne Badge objektov — brez data layer odvisnosti).
 */
data class WorkoutCompletionResult(
    val unlockedBadges: List<String> = emptyList(),
    val xpAwarded: Int,
    val isCritical: Boolean
)

data class GamificationState(
    val weeklyTarget: Int = 0,
    val workoutDoneToday: Boolean = false
)

/**
 * UseCase za gamification logiko.
 *
 * Clean Architecture: komunicira SAMO z GamificationRepository (domain interface).
 * KMP-ready: brez Android, data.settings, data.UserProfile odvisnosti.
 *
 * @param repository GamificationRepository (implementira FirestoreGamificationRepository)
 */
class ManageGamificationUseCase(
    private val repository: GamificationRepository
) {

    /**
     * Flow za GamificationState (weeklyTarget, workoutDoneToday).
     * Podatke bere prek repository (ne prek SharedPrefs lambda-jev).
     */
    fun getGamificationStateFlow(): Flow<GamificationState> = flow {
        try {
            emit(repository.getGamificationState())
        } catch (_: Exception) {
            emit(GamificationState())
        }
    }

    /** @deprecated Používaj recordWorkoutCompletion() — ta funkcija je legacy stub */
    suspend fun completeWorkoutSession(calKcal: Int) {
        // ⚠️ AUDIT: Ne kliče več repository.updateStreak() — streak posodobi UpdateBodyMetricsUseCase.
        val workoutXp = 100 + (calKcal / 10)
        repository.awardXP(workoutXp, "WORKOUT_COMPLETE")
    }

    /**
     * Faza 8 — Unified Streak Engine:
     * @param incrementPlanDay true = redni workout (plan_day +1), false = extra workout
     * @param isRestDay true = extra workout na rest dnevu → samo XP, brez streak
     */
    suspend fun recordWorkoutCompletion(
        caloriesBurned: Double,
        hour: Int,
        isRestDay: Boolean = false,
        incrementPlanDay: Boolean = true
    ): WorkoutCompletionResult {
        // Faza 8: Unified Streak Engine — kliči processWorkoutCompletion SAMO če ni extra-na-rest-dnevu
        if (!isRestDay) {
            repository.processWorkoutCompletion(incrementPlanDay)
        }
        val calorieXP = (caloriesBurned / 8).toInt()
        val baseXP = 50
        val isCritical = kotlin.random.Random.nextFloat() < 0.1f
        val finalBaseXP = if (isCritical) baseXP * 2 else baseXP

        repository.awardXP(finalBaseXP, "WORKOUT_COMPLETE")
        if (calorieXP > 0) {
            repository.awardXP(calorieXP, "CALORIES_BURNED")
        }

        // ── Workout-Nutrition Bridge ─────────────────────────────────────────────
        // Piše burned calories v dailyLogs → NutritionScreen prikaže pravilni net balance.
        // Prek repository interface (ne direktna DailyLogRepository instantiacija).
        if (caloriesBurned > 0.0) {
            try {
                val todayStr = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                    .date.toString()
                repository.logBurnedCalories(todayStr, caloriesBurned)
            } catch (_: Exception) {
                // Napaka je tiha
            }
        }
        // ────────────────────────────────────────────────────────────────────────

        return WorkoutCompletionResult(emptyList(), finalBaseXP + calorieXP, isCritical)
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

    /**
     * Vrni badge napredek za danega uporabnika.
     * @param profile AchievementProfile domenski model (ViewModel ga konvertira iz data.UserProfile)
     */
    fun getBadgeProgress(badgeId: String, profile: AchievementProfile): Int {
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

    suspend fun checkAndSyncBadgesOnStartup(): List<String> {
        return emptyList() // delegated to future KMP sync logic
    }

    /**
     * Faza 4b: Uporabnik je opravil raztezanje na rest dnevu.
     * Streak +1, XP +10. Shrani status "STRETCHING_DONE" v dailyHistory mapo.
     * @return Novi streak (za Toast "Daily Goal Met! Streak: X days ")
     */
    suspend fun restDayInitiated(): Int {
        val newStreak = repository.updateStreak(
            isWorkoutSuccess = true,
            activityType = "STRETCHING_DONE"
        )
        repository.awardXP(10, "REST_DAY")
        return newStreak
    }

    /** Worker (ob polnoči) pozove, da naj bi bil tisti dan odtreniran ali zamujen. */
    suspend fun executeMidnightStreakCheck() {
        repository.runMidnightStreakCheck()
    }
}