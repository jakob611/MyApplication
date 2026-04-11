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
     */
    suspend fun updateStreak(isWorkoutSuccess: Boolean)

    /**
     * Ozadičen worker (polnoč) reče repositoryju: "Preveri in resetiraj, če manjkajo workouti".
     */
    suspend fun runMidnightStreakCheck()

    /**
     * Za uporabo Streak Freeze, če je na voljo.
     */
    suspend fun consumeStreakFreeze(): Boolean
}

