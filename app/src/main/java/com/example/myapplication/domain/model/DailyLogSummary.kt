package com.example.myapplication.domain.model

import kotlinx.datetime.LocalDate

/**
 * Dnevni povzetek vnosa hrane, vode in porabljenih kalorij.
 * Pridobi se iz Firestore dailyLogs kolekcije.
 *
 * @param burnedCalories  Porabljene kalorije iz kardio aktivnosti (iz dailyLogs.burnedCalories).
 */
data class DailyLogSummary(
    val date: LocalDate,
    val calories: Double,
    val waterMl: Int,
    val burnedCalories: Double = 0.0
)

