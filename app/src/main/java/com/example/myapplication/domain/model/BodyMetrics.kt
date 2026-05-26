package com.example.myapplication.domain.model

import androidx.compose.runtime.Immutable

/**
 * Čisti domenski model za stanje telesa/treninga.
 *
 * Faza 38 — Unified UI State refactoring:
 * `isLoading` in `errorMessage` sta premeščena v [BodyUiState] (presentation sloj).
 * Ta model vsebuje IZKLJUČNO domenske podatke — brez UI/loading stanja.
 *
 * KMP-ready: brez Android, brez viewmodels, brez data layer odvisnosti.
 * ViewModel je odgovoren za mapiranje BodyMetrics → BodyUiState.metrics.
 *
 * todayStatus je tipsko-varni [UserDayStatus] namesto String.
 *
 * Faza 35 — @Immutable: vse lastnosti so val + primitivni tipi → Compose compiler
 * ne bo zgrešeno označil tega razreda kot "nestabilnega" ob morebitni Compose
 * integraciji domenskega sloja.
 */
@Immutable
data class BodyMetrics(
    val streakDays: Int = 0,
    val streakFreezes: Int = 0,
    val weeklyDone: Int = 0,
    val weeklyTarget: Int = 3,
    val planDay: Int = 1,
    val totalWorkoutsCompleted: Int = 0,
    val isWorkoutDoneToday: Boolean = false,
    val dailyKcal: Int = 0,
    val todayIsRest: Boolean = false,
    /** Tipsko-varni status današnjega dne */
    val todayStatus: UserDayStatus = UserDayStatus.WORKOUT_PENDING
    // ODSTRANJENO: isLoading → BodyUiState.isLoading (UI zadeva)
    // ODSTRANJENO: errorMessage → BodyUiState.errorMessage (UI zadeva)
)
