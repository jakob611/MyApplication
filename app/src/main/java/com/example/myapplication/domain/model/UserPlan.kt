package com.example.myapplication.domain.model

/**
 * Domenski model za aktiven trening plan.
 * Vsebuje samo podatke ki so relevantni za domain logiko.
 * Full plan (WeekPlan, DayPlan struktura) ostane v data.PlanResult.
 *
 * iOS-ready: brez Android odvisnosti.
 */
data class UserPlan(
    val id: String = "",
    val name: String = "My Plan",
    /** Skupno število treningov na teden (npr. 4) */
    val trainingDays: Int = 3,
    /** Trenutni dan v planu (1-based, max 28) */
    val planDay: Int = 1,
    /** Treningi opravljeni ta teden */
    val weeklyDone: Int = 0,
    /** Cilj treningov ta teden */
    val weeklyTarget: Int = 3,
    /** Datum nastanka plana (epoch ms) */
    val createdAt: Long = 0L
) {
    /** Teden v 4-tedenskem ciklu (1–4) */
    val currentWeek: Int get() = ((planDay - 1) / 7) + 1

    /** Dan v tednu (1–7) */
    val dayInWeek: Int get() = ((planDay - 1) % 7) + 1

    /** Napredek tega tedna (0.0–1.0) */
    val weeklyProgress: Float
        get() = if (weeklyTarget > 0) (weeklyDone.toFloat() / weeklyTarget).coerceIn(0f, 1f) else 0f
}

