package com.example.myapplication.domain.model

/**
 * Čisti domenski model za streak stanje.
 * Brez Android in Firestore odvisnosti — primeren za KMP.
 *
 * todayStatus vrednosti: "WORKOUT_DONE" | "STRETCHING_DONE" | "FROZEN" | "MISSED" | null
 */
data class Streak(
    val days: Int = 0,
    val freezes: Int = 0,
    /** Kakšen status je danes vpisan v dailyHistory mapo */
    val todayStatus: String? = null
) {
    /** Streak je aktiven, če je vsaj 1 dan */
    val isActive: Boolean get() = days > 0

    /** Streak Freeze je na voljo */
    val isFreezeAvailable: Boolean get() = freezes > 0

    /** Danes je cilj že opravljen (workout ali stretching) */
    val isTodayCompleted: Boolean
        get() = todayStatus == "WORKOUT_DONE" || todayStatus == "STRETCHING_DONE"

    /** Danes je bil porabljen freeze */
    val isTodayFrozen: Boolean get() = todayStatus == "FROZEN"

    /** Danes je bil zamrznjen streak (reset) */
    val isTodayMissed: Boolean get() = todayStatus == "MISSED"
}

