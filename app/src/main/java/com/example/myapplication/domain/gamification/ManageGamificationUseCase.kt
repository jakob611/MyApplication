package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.Badge
import com.example.myapplication.data.BadgeDefinitions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.toLocalDateTime

data class WorkoutCompletionResult(
    val unlockedBadges: List<Badge>,
    val xpAwarded: Int,
    val isCritical: Boolean
)

data class GamificationState(
    val weeklyTarget: Int = 0,
    val workoutDoneToday: Boolean = false
)

/**
 * UseCase (KMP) razred za logiko doloŤanja gamifikacije na Ťisto Kotlin strukturo.
 * Predstavlja most med UI-jem/Workerji in Repozitorijem,
 * brez zlorabe ali napačnega pomnenja v prejšnjih SharedPreferences.
 */
class ManageGamificationUseCase(
    private val repository: GamificationRepository,
    private val workoutDoneProvider: () -> Boolean = { false },
    private val weeklyTargetProvider: () -> Flow<Int> = { flowOf(0) }
) {

    fun getGamificationStateFlow(): Flow<GamificationState> {
        return weeklyTargetProvider().combine(flowOf(workoutDoneProvider())) { target, _ ->
            GamificationState(
                weeklyTarget = target,
                workoutDoneToday = workoutDoneProvider()
            )
        }
    }

    /** Uporabnik je uspešno zaključil workout */
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

        // ── Workout-Nutrition Bridge ─────────────────────────────────────────────
        // Piše burned calories v dailyLogs → NutritionScreen prikaže pravilni net balance.
        // To je edino mesto pisanja (UpdateBodyMetricsUseCase step 6 odstranjen).
        if (caloriesBurned > 0.0) {
            try {
                val todayStr = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                    .date.toString()
                com.example.myapplication.data.daily.DailyLogRepository().updateDailyLog(todayStr) { data ->
                    val existingBurned = (data["burnedCalories"] as? Number)?.toDouble() ?: 0.0
                    data["burnedCalories"] = existingBurned + caloriesBurned
                }
                // ℹ️ Log je dostopen prek DailyLogRepository.lastTransactions (iOS-ready)
            } catch (_: Exception) {
                // Napaka je tiha — DailyLogRepository.lastTransactions zabeleži neuspeh
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

    /**
     * Faza 4b: Uporabnik je opravil raztezanje na rest dnevu.
     * Streak +1, XP +10. Shrani status "STRETCHING_DONE" v dailyHistory mapo.
     * @return Novi streak (za Toast "Daily Goal Met! Streak: X days 🔥")
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
        // Repository bo morda preveril uporabo Freeze in zamujenega stanja
        // in ponastavil na 0 neposredno v skupni bazi (v resnici brez uničujočih SharedPreferences).
    }
}
