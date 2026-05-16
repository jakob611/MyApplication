package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.model.AchievementProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
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
 * Faza 12: recordWorkoutCompletion() izračuna XP pred klicem atomarnega
 * processWorkoutCompletion(). completeWorkoutSession() IZBRISAN (legacy stub).
 */
class ManageGamificationUseCase(
    private val repository: GamificationRepository
) {

    fun getGamificationStateFlow(): Flow<GamificationState> = flow {
        try { emit(repository.getGamificationState()) }
        catch (_: Exception) { emit(GamificationState()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordWorkoutCompletion — Faza 12: XP izračun + en atomaren klic
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Zaključi vadbo: izračuna skupni XP, nato pokliče atomarni engine.
     *
     * Za normalni workout (isRestDay=false):
     *   → pokliče repository.processWorkoutCompletion(streak + XP + kalorije v 1 tx)
     *
     * Za extra workout na rest dnevu (isRestDay=true):
     *   → samo XP + kalorije, brez streak posodobitve
     *
     * XP formula:
     *   calorieXP  = caloriesBurned / 8   (1 XP na 8 kcal)
     *   baseXP     = 50
     *   Critical   = 10% verjetnost → baseXP × 2
     *   totalXP    = finalBaseXP + calorieXP
     *
     * @param caloriesBurned kcal porabljene med vadbo
     * @param hour           ura vadbe (rezervirano za Early Bird / Night Owl bonus)
     * @param isRestDay      true = extra vadba na rest dnevu → samo XP, brez streak
     * @param incrementPlanDay true = redni workout → plan_day +1 v Firestore
     */
    suspend fun recordWorkoutCompletion(
        caloriesBurned: Double,
        hour: Int,
        isRestDay: Boolean = false,
        incrementPlanDay: Boolean = true
    ): WorkoutCompletionResult {

        // ── XP izračun (pred klicem repozitorija) ────────────────────────────
        val calorieXP   = (caloriesBurned / 8).toInt()                      // 1 XP = 8 kcal
        val baseXP      = 50
        val isCritical  = kotlin.random.Random.nextFloat() < 0.1f           // 10% verjetnost
        val finalBaseXP = if (isCritical) baseXP * 2 else baseXP            // Critical = 2× base
        val totalXP     = finalBaseXP + calorieXP
        // ─────────────────────────────────────────────────────────────────────

        if (!isRestDay) {
            // Normalni workout: atomarna transakcija za streak + XP + kalorije
            repository.processWorkoutCompletion(
                incrementPlanDay = incrementPlanDay,
                xpToBeAwarded    = totalXP,
                xpReason         = "WORKOUT_COMPLETE",
                caloriesBurned   = caloriesBurned
            )
        } else {
            // Extra workout na rest dnevu: samo XP + kalorije, brez streak posodobitve
            repository.awardXP(totalXP, "WORKOUT_COMPLETE")
            if (caloriesBurned > 0.0) {
                try {
                    val todayStr = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date.toString()
                    repository.logBurnedCalories(todayStr, caloriesBurned)
                } catch (_: Exception) { /* tiha napaka — ne sesuj UI */ }
            }
        }

        return WorkoutCompletionResult(emptyList(), totalXP, isCritical)
    }

    suspend fun awardXP(amount: Int, reason: String) = repository.awardXP(amount, reason)

    suspend fun recordPlanCreation() = repository.awardXP(100, "PLAN_CREATED")

    suspend fun recordLoginOnly() = repository.awardXP(10, "DAILY_LOGIN")

    /**
     * Vrni badge napredek za danega uporabnika.
     * @param profile AchievementProfile domenski model (ViewModel ga konvertira iz data.UserProfile)
     */
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
     * Faza 4b: Uporabnik je opravil raztezanje na rest dnevu.
     * Streak +1 prek epoch-based updateStreak(), XP +10.
     * Status "STRETCHING_DONE" → dailyHistory.
     * @return Novi streak (za Toast "Daily Goal Met! Streak: X days")
     */
    suspend fun restDayInitiated(): Int {
        val newStreak = repository.updateStreak(
            isWorkoutSuccess = true,
            activityType = "STRETCHING_DONE"
        )
        repository.awardXP(10, "REST_DAY")
        return newStreak
    }

    /** Worker (ob polnoči) pozove streak check za včerajšnji dan. */
    suspend fun executeMidnightStreakCheck() = repository.runMidnightStreakCheck()
}