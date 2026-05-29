package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.model.AchievementProfile
import com.example.myapplication.domain.model.UserDayStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Rezultat atomarne Firestore transakcije moveToNextDay().
 *
 * Vsebuje DEJANSKE vrednosti, ki so bile zapisane v Firestore — ne lokalne ocene.
 * ViewModel mora zaupati IZKLJUČNO tem vrednostim za optimistično UI posodobitev.
 *
 * BP-3 / BP-4 Fix: Odpravlja lokalni newPlanDay izračun v UseCase in tiho -1 napako v repozitoriju.
 */
data class GamificationUpdateResult(
    /** Novi streak po transakciji (kot ga je Firestore dejansko zapisal). */
    val newStreak: Int,
    /** Novi plan_day po transakciji (kot ga je Firestore dejansko zapisal). */
    val newPlanDay: Int
)

/**
 * Rezultat zaključenega workout-a.
 * unlockedBadges: seznam badge ID-jev (ne Badge objektov — brez data layer odvisnosti).
 *
 * Faza 23: newStreakDays propagiran iz moveToNextDay() → ViewModel ne rabi dodatnega Firestore read-a.
 * newPlanDay: novi plan_day po zaključku (dejanska vrednost iz Firestore transakcije, ne lokalna ocena).
 */
data class WorkoutCompletionResult(
    val unlockedBadges: List<String> = emptyList(),
    val xpAwarded: Int,
    val isCritical: Boolean,
    /** Novi streak po transakciji (0 = transakcija spodleti ali de-dup). */
    val newStreakDays: Int = 0,
    /** Novi plan_day po zaključku (ali nespremenjen za extra/rest). */
    val newPlanDay: Int = 0
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
        // Faza 34 — CRIT-02 Fix: Ne lovimo izjem tihoma z emitiranjem praznega stanja.
        // Propagiraj napako navzgor — klicatelji (ViewModel catch bloki) upravljajo UI napake.
        emit(repository.getGamificationState())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordWorkoutCompletion — izračuna XP in delegira na moveToNextDay()
    //
    // @param isRestDay      true = extra workout na rest dnevu → REST_WORKOUT_DONE
    // @param incrementPlanDay true = redni workout → plan_day +1
    // @param workoutSessionDoc Faza 34 — CRIT-03: opcionalni workout session dokument
    //                          za atomarni zapis v isti Firestore transakciji kot gamification.
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun recordWorkoutCompletion(
        caloriesBurned: Double,
        hour: Int,
        isRestDay: Boolean = false,
        incrementPlanDay: Boolean = true,
        currentPlanDay: Int = 0,
        workoutSessionDoc: Map<String, Any>? = null
    ): WorkoutCompletionResult {

        val calorieXP      = (caloriesBurned / 8).toInt()
        val baseXP         = 50
        val isCritical     = kotlin.random.Random.nextFloat() < 0.1f
        val finalBaseXP    = if (isCritical) baseXP * 2 else baseXP
        val totalXP        = finalBaseXP + calorieXP

        val newStatus      = if (isRestDay) UserDayStatus.REST_WORKOUT_DONE else UserDayStatus.WORKOUT_DONE
        val xpReason       = if (isRestDay) "REST_WORKOUT_COMPLETE" else "WORKOUT_COMPLETE"
        val shouldIncrement = incrementPlanDay && !isRestDay

        // BP-3 / BP-4 Fix: getOrElse { throw it } propagira Firestore napako navzgor (ne vrne tihega -1).
        // gamResult vsebuje DEJANSKI newStreak in newPlanDay, ki ju je Firestore zapisal —
        // brez lokalnega ugibanja (oldPlanDay + 1).
        val gamResult = repository.moveToNextDay(
            newStatus        = newStatus,
            xpToBeAwarded    = totalXP,
            xpReason         = xpReason,
            caloriesBurned   = caloriesBurned,
            incrementPlanDay = shouldIncrement,
            workoutSessionDoc = workoutSessionDoc
        ).getOrElse { throw it }  // remeče → ujeto v UpdateBodyMetricsUseCase.invoke() → Result.failure

        return WorkoutCompletionResult(
            unlockedBadges = emptyList(),
            xpAwarded      = totalXP,
            isCritical     = isCritical,
            newStreakDays  = gamResult.newStreak,
            newPlanDay     = gamResult.newPlanDay  // dejanska vrednost iz Firestore, ne lokalni izračun
        )
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
        // Faza 32.9 — BUG-04 Fix: getTodayStatus napaka propagira navzgor.
        // Ne defaultiramo na WORKOUT_PENDING ob auth/network napaki — to bi
        // dovolilo raztezanje ko je bil workout danes morda že opravljen.
        val todayStatus = repository.getTodayStatus()

        // Guard: redni trening je danes že opravljen → raztezanje ni dovoljeno
        if (todayStatus == UserDayStatus.WORKOUT_DONE || todayStatus == UserDayStatus.REST_WORKOUT_DONE) {
            // Faza 32.9 — BUG-04 Fix: Odstranjeno runCatching { }.getOrDefault(0).
            // Če getCurrentStreak() spodleti, izjema propagira do CompleteRestDay
            // catch bloka v ViewModel-u → ShowSnackbar namesto streak=0 v UI.
            return repository.getCurrentStreak()
        }

        // BP-4 Fix: getOrElse { throw it } propagira Firestore napako navzgor → CompleteRestDay catch
        // blok v ViewModel-u prikaže Snackbar. Ni več tihega -1 / 0 return-a ob napaki.
        return repository.moveToNextDay(
            newStatus      = UserDayStatus.REST_DAY_DONE,
            xpToBeAwarded  = 10,
            xpReason       = "REST_DAY",
            caloriesBurned = 0.0,
            incrementPlanDay = false
        ).getOrElse { throw it }.newStreak
    }

    /** Worker (ob polnoči) pozove streak check za včerajšnji dan.
     *
     * Faza 54 — Anomaly #4 Fix: yesterdayWasRestDay=true preprečuje napačen streak reset
     * na počitniških dnevih (REST_DAY_PENDING ali WORKOUT_PENDING → ni kazni).
     */
    suspend fun executeMidnightStreakCheck(yesterdayWasRestDay: Boolean = false) =
        repository.runMidnightStreakCheck(yesterdayWasRestDay)

    /**
     * Faza 54 — Anomaly #4 Fix: Označi danes kot REST_DAY_PENDING v dailyHistory.
     *
     * Kliče se iz BodyModuleHomeViewModel, ko ViewModel ugotovi da je danes počitniški dan
     * (na podlagi plan modela) in status še ni bil nastavljen. S tem midnight worker
     * vidi REST_DAY_PENDING v dailyHistory in ne penalizira streaka.
     *
     * Operacija je idempotentna — ne prepiše zaključenih statusov (WORKOUT_DONE, REST_DAY_DONE…).
     */
    suspend fun markRestDayPending() = repository.markRestDayPending()
}