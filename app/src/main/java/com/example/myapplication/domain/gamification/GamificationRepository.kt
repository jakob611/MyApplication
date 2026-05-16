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
     * Faza 8 — Unified Streak Engine.
     * NADOMEŠČA UserProfileManager.updateUserProgressAfterWorkout().
     *
     * V ENI Firestore transakciji:
     * - Epoch-based streak izračun (dayDiff z Streak Freeze podporo)
     * - Zapiše dailyHistory.$today = "WORKOUT_DONE"
     * - Posodobi streak_days, last_workout_epoch, plan_day
     *
     * iOS-ready: vsa logika v domain/data layer, brez Android odvisnosti.
     *
     * @param incrementPlanDay true za redne workouty, false za extra treninge
     */
    suspend fun processWorkoutCompletion(incrementPlanDay: Boolean)

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
