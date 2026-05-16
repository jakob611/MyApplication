package com.example.myapplication.domain.gamification

/**
 * Ključni repozitorij za gamification.
 *
 * Ta interface se nahaja v DOMAIN sloju (brez Android odvisnosti).
 * Definira contract "kaj se da narediti glede streaka ali XP-jev"
 * in zagotavlja SAMO EN Vir Resnice.
 */
interface GamificationRepository {

    /**
     * Shrani XP (s Firebase batch logiko).
     * @param amount Količina XP (npr. 10).
     * @param reason Razlog prejema ("Workout complete", "Daily Login").
     */
    suspend fun awardXP(amount: Int, reason: String)

    /**
     * Vrni trenutno število streaka iz enotnega vira podatkov.
     */
    suspend fun getCurrentStreak(): Int

    /**
     * Povečaj ali ohrani streak. Zažene se, ko je uspešno zabeleženo,
     * in pod kapuco reši tisti "Heisenbug".
     * @param isWorkoutSuccess Je bil trening ali rest day uspešno obkljukan danes?
     * @param activityType Tip aktivnosti za dailyHistory mapo: "WORKOUT_DONE" ali "STRETCHING_DONE"
     * @return Novi streak po posodobitvi (0 ob napaki)
     */
    suspend fun updateStreak(isWorkoutSuccess: Boolean, activityType: String = "WORKOUT_DONE"): Int

    /**
     * Označi TODAY kot "PENDING_STRETCHING" v dailyHistory mapi.
     * Kliče se, ko app ugotovi da je danes rest day — a uporabnik
     * še NI opravil raztezanja. Status ne pripiše streak +1.
     * Prehod v "STRETCHING_DONE" se zgodi SAMO prek [updateStreak] ob
     * eksplicitni uporabnikovi akciji.
     *
     * ⛔ runMidnightCheck() NE sme klicati te metode — polnočni check
     * preverja VČERAJ (ne danes) in ne sme avtokonkludirati rest dnevov.
     */
    suspend fun markRestDayPending()

    /**
     * Ozadičen worker (polnoč) reče repositoryju: "Preveri in resetiraj, če manjkajo workouti".
     * ⚠️ Ta metoda analizira SAMO VČERAJŠNJI dan — nikoli ne auto-complete todayja.
     */
    suspend fun runMidnightStreakCheck()

    /**
     * Za uporabo Streak Freeze, če je na voljo.
     */
    suspend fun consumeStreakFreeze(): Boolean

    /**
     * Faza 12b — Unified Activity Completion Engine (nadomešča processWorkoutCompletion).
     *
     * ENA atomarna Firestore transakcija za VSAKO aktivnost — redni trening ALI extra rest-day workout:
     * - Epoch-based streak izračun z isRestDay matriko (dayDiff z Streak Freeze podporo)
     * - Vedno zapiše dailyHistory.$today ("WORKOUT_DONE" ali "REST_WORKOUT_DONE")
     * - Vedno posodobi last_activity_epoch → midnight check nikoli ne vidi praznega dne
     * - Atomarno dodeli XP in beleži xp_history
     * - Zapiše porabljene kalorije v dailyLogs
     *
     * Streak matrika:
     *   oldLastEpoch == 0L → 1 (prvi zapis)
     *   dayDiff == 0L      → oldStreak (de-dup)
     *   dayDiff == 1L      → if (isRestDay) oldStreak else oldStreak + 1
     *   dayDiff > 1L       → freeze? ohrani : 1
     *
     * @param isRestDay        true = extra workout na rest dnevu (streak se NE poveča, a se zapiše)
     * @param incrementPlanDay true za redne workouty (plan_day +1), false za extra
     * @param xpToBeAwarded    skupni XP za to aktivnost
     * @param xpReason         razlog za XP log
     * @param caloriesBurned   porabljene kalorije za Nutrition bridge (0.0 = preskoči)
     */
    suspend fun processActivityCompletion(
        isRestDay: Boolean,
        incrementPlanDay: Boolean,
        xpToBeAwarded: Int,
        xpReason: String,
        caloriesBurned: Double
    )

    /**
     * Vrni status današnjega dne iz dailyHistory mape.
     * Možne vrednosti: "WORKOUT_DONE", "STRETCHING_DONE", "PENDING_STRETCHING",
     * "FROZEN", "MISSED", ali null (dan ni bil zabeležen).
     */
    suspend fun getTodayStatus(): String?

    /**
     * Zapiši porabljene kalorije v dnevni log (dailyLogs/{todayStr}).
     * Nadomešča direktno klicanje DailyLogRepository iz domain layer-a.
     *
     * @param todayStr Datum v formatu "YYYY-MM-DD"
     * @param calories Porabljene kalorije (dodane k obstoječim)
     */
    suspend fun logBurnedCalories(todayStr: String, calories: Double)

    /**
     * Vrni trenutno gamification stanje (weeklyTarget, workoutDoneToday).
     * Nadomešča workoutDoneProvider/weeklyTargetProvider lambde v ManageGamificationUseCase.
     * KMP-ready: suspending namesto Flow-based lambda injection.
     */
    suspend fun getGamificationState(): com.example.myapplication.domain.gamification.GamificationState
}
