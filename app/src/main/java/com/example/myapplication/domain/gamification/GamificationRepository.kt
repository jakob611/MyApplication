package com.example.myapplication.domain.gamification

import com.example.myapplication.domain.model.UserDayStatus

/**
 * SSOT za gamification logiko.
 *
 * Faza 21: processActivityCompletion() in updateStreak() so zamenjani z
 * moveToNextDay() — eno atomarno Firestore transakcijo za VSE poti.
 *
 * KMP-ready interface brez Android odvisnosti.
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
     * Označi TODAY kot [UserDayStatus.REST_DAY_PENDING] v dailyHistory mapi.
     * Kliče se, ko app ugotovi da je danes rest day — a uporabnik
     * še NI opravil raztezanja. Status ne pripiše streak +1.
     *
     * ⛔ runMidnightCheck() NE sme klicati te metode.
     */
    suspend fun markRestDayPending()

    /**
     * Ozadičen worker (polnoč) reče repositoryju: "Preveri in resetiraj, če manjkajo workouti".
     * ⚠️ Ta metoda analizira SAMO VČERAJŠNJI dan — nikoli ne auto-complete todayja.
     */
    suspend fun runMidnightStreakCheck()

    /**
     * Porabi Streak Freeze, če je na voljo.
     */
    suspend fun consumeStreakFreeze(): Boolean

    /**
     * Vrni tipsko-varni [UserDayStatus] za danes iz dailyHistory mape.
     * Vrne [UserDayStatus.WORKOUT_PENDING] če dan ni bil zabeležen.
     */
    suspend fun getTodayStatus(): UserDayStatus

    /**
     * Zapiši porabljene kalorije v dnevni log (dailyLogs/{todayStr}).
     * Uporablja se za ručno beleženje (RunTracker, ManualExercise) izven moveToNextDay().
     */
    suspend fun logBurnedCalories(todayStr: String, calories: Double)

    /**
     * Vrni trenutno gamification stanje (weeklyTarget, workoutDoneToday).
     */
    suspend fun getGamificationState(): GamificationState

    // ─────────────────────────────────────────────────────────────────────────
    // moveToNextDay — SSOT za VSE aktivnostne zaključke (Faza 21)
    //
    // Nadomešča oba stara klica:
    //   • processActivityCompletion(isRestDay, incrementPlanDay, xp, reason, cals)
    //   • updateStreak(isWorkoutSuccess, activityType)
    //
    // ENA atomarna Firestore transakcija za:
    //   ① Preveritev in de-duplikacija (ne prepiše višje prioritete)
    //   ② Streak izračun (epoch-based, Freeze podpora)
    //   ③ Posodobitev plan_day (samo za WORKOUT_DONE z incrementPlanDay=true)
    //   ④ XP + Level posodobitev
    //   ⑤ Zapis v dailyHistory z UserDayStatus.firestoreValue
    //   ⑥ Porabljene kalorije v dailyLogs (Nutrition bridge)
    //
    // Logika glede na [newStatus]:
    //   WORKOUT_DONE     → streak+1, plan_day+1 (če incrementPlanDay=true)
    //   REST_WORKOUT_DONE → streak ohranjen, plan_day nespremenjen
    //   REST_DAY_DONE    → streak+1, plan_day nespremenjen
    //
    // @return Novi streak po transakciji (0 ob napaki ali de-dup preskoček).
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun moveToNextDay(
        newStatus: UserDayStatus,
        xpToBeAwarded: Int = 0,
        xpReason: String = "",
        caloriesBurned: Double = 0.0,
        /** true = plan_day +1. Privzeto: samo za WORKOUT_DONE. */
        incrementPlanDay: Boolean = newStatus.shouldIncrementPlanDay
    ): Int
}
