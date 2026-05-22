package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.model.AchievementProfile
import com.example.myapplication.domain.model.UserDayStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
 * Faza 21: recordWorkoutCompletion() in restDayInitiated() sedaj oba kličeta
 * repository.moveToNextDay() — en SSOT za vse aktivnostne zaključke.
 *
 * Clean Architecture: komunicira SAMO z GamificationRepository (domain interface).
 * KMP-ready: brez Android, data.settings, data.UserProfile odvisnosti.
 */
class ManageGamificationUseCase(
    private val repository: GamificationRepository
) {

    fun getGamificationStateFlow(): Flow<GamificationState> = flow {
        try { emit(repository.getGamificationState()) }
        catch (_: Exception) { emit(GamificationState()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordWorkoutCompletion — izračuna XP in delegira na moveToNextDay()
    //
    // @param isRestDay      true = extra workout na rest dnevu → REST_WORKOUT_DONE
    // @param incrementPlanDay true = redni workout → plan_day +1
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun recordWorkoutCompletion(
        caloriesBurned: Double,
        hour: Int,
        isRestDay: Boolean = false,
        incrementPlanDay: Boolean = true
    ): WorkoutCompletionResult {

        val calorieXP   = (caloriesBurned / 8).toInt()
        val baseXP      = 50
        val isCritical  = kotlin.random.Random.nextFloat() < 0.1f
        val finalBaseXP = if (isCritical) baseXP * 2 else baseXP
        val totalXP     = finalBaseXP + calorieXP

        val newStatus = if (isRestDay) UserDayStatus.REST_WORKOUT_DONE else UserDayStatus.WORKOUT_DONE
        val xpReason  = if (isRestDay) "REST_WORKOUT_COMPLETE" else "WORKOUT_COMPLETE"

        repository.moveToNextDay(
            newStatus        = newStatus,
            xpToBeAwarded    = totalXP,
            xpReason         = xpReason,
            caloriesBurned   = caloriesBurned,
            incrementPlanDay = incrementPlanDay && !isRestDay
        )

        return WorkoutCompletionResult(emptyList(), totalXP, isCritical)
    }

    suspend fun awardXP(amount: Int, reason: String) = repository.awardXP(amount, reason)

    suspend fun recordPlanCreation() = repository.awardXP(100, "PLAN_CREATED")

    suspend fun recordLoginOnly() = repository.awardXP(10, "DAILY_LOGIN")

    fun getBadgeProgress(badgeId: String, profile: AchievementProfile): Int {
        return when (badgeId) {
            "first_workout", "committed_10", "committed_50",
            "committed_100", "committed_250", "committed_500" ->
                profile.totalWorkoutsCompleted
            "calorie_crusher_1k", "calorie_crusher_5k", "calorie_crusher_10k" ->
                profile.totalCaloriesBurned.toInt()
            "level_5", "level_10", "level_25", "level_50" ->
                profile.level
            "first_follower", "social_butterfly", "influencer", "celebrity" ->
                profile.followers
            "early_bird"  -> profile.earlyBirdWorkouts
            "night_owl"   -> profile.nightOwlWorkouts
            "week_warrior", "month_master", "year_champion" ->
                profile.currentLoginStreak
            "first_plan", "plan_master" ->
                profile.totalPlansCreated
            else -> 0
        }
    }

    suspend fun checkAndSyncBadgesOnStartup(): List<String> = emptyList()

    /**
     * Faza 4b / 21: Uporabnik je opravil raztezanje na rest dnevu.
     *
     * Server-side guard: če je danes že "WORKOUT_DONE" ali "REST_WORKOUT_DONE",
     * akcija ni dovoljena — vrne obstoječi streak brez spremembe.
     *
     * Faza 21: Kliče moveToNextDay(REST_DAY_DONE) namesto starih updateStreak() + awardXP().
     *
     * @return Novi streak po posodobitvi (ali obstoječi če akcija ni dovoljena).
     */
    suspend fun restDayInitiated(): Int {
        val todayStatus = runCatching { repository.getTodayStatus() }
            .getOrDefault(UserDayStatus.WORKOUT_PENDING)

        // Guard: redni trening je danes že opravljen → raztezanje ni dovoljeno
        if (todayStatus == UserDayStatus.WORKOUT_DONE || todayStatus == UserDayStatus.REST_WORKOUT_DONE) {
            return runCatching { repository.getCurrentStreak() }.getOrDefault(0)
        }

        // REST_DAY_DONE = streak+1, plan_day nespremenjen, +10 XP
        val newStreak = repository.moveToNextDay(
            newStatus      = UserDayStatus.REST_DAY_DONE,
            xpToBeAwarded  = 10,
            xpReason       = "REST_DAY",
            caloriesBurned = 0.0,
            incrementPlanDay = false
        )
        return if (newStreak > 0) newStreak else runCatching { repository.getCurrentStreak() }.getOrDefault(0)
    }

    /** Worker (ob polnoči) pozove streak check za včerajšnji dan. */
    suspend fun executeMidnightStreakCheck() = repository.runMidnightStreakCheck()
}