package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.model.AchievementProfile
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
    // recordWorkoutCompletion — Faza 12b: XP izračun + VEDNO en atomaren klic
    //
    // KRITIČNA FIX: isRestDay NE zaobide klica repozitorija!
    // Brez vpisa v dailyHistory in last_activity_epoch midnight worker vidi prazen dan
    // → porabi Streak Freeze ali resetira streak na 0, čeprav je uporabnik treniral.
    //
    // processActivityCompletion pokrije VSE scenarije v ENI transakciji:
    //   isRestDay=false → "WORKOUT_DONE" + streak+1
    //   isRestDay=true  → "REST_WORKOUT_DONE" + streak ohranjen (ne pade!)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Zaključi vadbo: izračuna skupni XP, nato pokliče atomarni engine za vse scenarije.
     *
     * XP formula:
     *   calorieXP  = caloriesBurned / 8   (1 XP na 8 kcal)
     *   baseXP     = 50
     *   Critical   = 10% verjetnost → baseXP × 2
     *   totalXP    = finalBaseXP + calorieXP
     *
     * @param caloriesBurned kcal porabljene med vadbo
     * @param hour           ura vadbe (rezervirano za Early Bird / Night Owl bonus)
     * @param isRestDay      true = extra vadba na rest dnevu → streak se ohrani (ne poveča, ne pade)
     * @param incrementPlanDay true = redni workout → plan_day +1 v Firestore
     */
    suspend fun recordWorkoutCompletion(
        caloriesBurned: Double,
        hour: Int,
        isRestDay: Boolean = false,
        incrementPlanDay: Boolean = true
    ): WorkoutCompletionResult {

        // ── XP izračun (pred klicem repozitorija) ────────────────────────────
        val calorieXP   = (caloriesBurned / 8).toInt()         // 1 XP = 8 kcal
        val baseXP      = 50
        val isCritical  = kotlin.random.Random.nextFloat() < 0.1f
        val finalBaseXP = if (isCritical) baseXP * 2 else baseXP
        val totalXP     = finalBaseXP + calorieXP
        // ─────────────────────────────────────────────────────────────────────

        // VEDNO pokliči atomarni engine — isRestDay=true NE sme zaobiti tega klica!
        repository.processActivityCompletion(
            isRestDay        = isRestDay,
            incrementPlanDay = incrementPlanDay,
            xpToBeAwarded    = totalXP,
            xpReason         = if (isRestDay) "REST_WORKOUT_COMPLETE" else "WORKOUT_COMPLETE",
            caloriesBurned   = caloriesBurned
        )

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
     *
     * FIX #3 — Koledarski zaklep:
     * Preverimo DEJANSKI DATUM in status iz repozitorija. Če je na isti
     * kalendarski dan dailyHistory že vseboval "WORKOUT_DONE" ali
     * "REST_WORKOUT_DONE", raztezanje NI dovoljeno — vrnemo obstoječi streak
     * brez spremembe in brez zapisa.
     *
     * @return Novi streak (ali obstoječi streak če akcija ni dovoljena)
     */
    suspend fun restDayInitiated(): Int {
        // FIX: server-side guard — preveri aktualni datum v bazi
        val todayStatus = runCatching { repository.getTodayStatus() }.getOrNull()
        if (todayStatus == "WORKOUT_DONE" || todayStatus == "REST_WORKOUT_DONE") {
            // Redni trening je bil danes že opravljen — raztezanje ni dovoljeno na isti dan.
            // Vrni obstoječi streak brez spremembe.
            return runCatching { repository.getCurrentStreak() }.getOrDefault(0)
        }

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