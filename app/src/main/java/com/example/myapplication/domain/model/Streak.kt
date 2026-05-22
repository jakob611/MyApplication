package com.example.myapplication.domain.model

/**
 * Čisti domenski model za streak stanje.
 * Brez Android in Firestore odvisnosti — primeren za KMP.
 *
 * todayStatus je zdaj tipsko-varni [UserDayStatus] namesto String.
 */
data class Streak(
    val days: Int = 0,
    val freezes: Int = 0,
    /** Tipsko-varni status današnjega dne */
    val todayStatus: UserDayStatus = UserDayStatus.WORKOUT_PENDING
) {
    /** Streak je aktiven, če je vsaj 1 dan */
    val isActive: Boolean get() = days > 0

    /** Streak Freeze je na voljo */
    val isFreezeAvailable: Boolean get() = freezes > 0

    /** Danes je cilj že opravljen (workout ali stretching) */
    val isTodayCompleted: Boolean get() = todayStatus.isDoneToday

    /** Danes je bil porabljen freeze */
    val isTodayFrozen: Boolean get() = todayStatus == UserDayStatus.FROZEN

    /** Danes je bil zamrznjen streak (reset) */
    val isTodayMissed: Boolean get() = todayStatus == UserDayStatus.MISSED
}
