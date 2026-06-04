package com.example.myapplication.ui.progress

/**
 * Podatki za prikaz napovedi teže v UI kartici.
 * UI-only model — ne gre v domain layer.
 */
data class WeightPredictionDisplay(
    val emaWeightKg: Double,
    val avgDailyBalanceKcal: Double,
    val predictedWeightIn30Days: Double,
    val goalWeightKg: Double?,
    val daysToGoal: Int?,
    val goalDateStr: String?,
    val activeDaysInLastWeek: Int,
    val confidenceFactor: Double = 0.0
)

/**
 * Popolni izračun napovedi — vsebuje vrednosti za WeightPredictorStore.
 * Ločen od [WeightPredictionDisplay] — ne vsebuje Compose stanja.
 * Izračuna se v [ProgressViewModel] na Dispatchers.Default.
 */
data class WeightPredictionFull(
    val display: WeightPredictionDisplay,
    val emaWeightKg: Double,
    val avgDailyBalanceKcal: Double,
    val predictedWeightIn30Days: Double,
    val goalWeightKg: Double?,
    val goalDateStr: String?,
    val daysToGoal: Int?,
    val activeDaysCount: Int,
    val hybridTDEE: Int,
    val adaptiveTDEE: Int,
    val confidenceFactor: Double
)

/** Pomožna funkcija za ime meseca — premaknjeno iz Progress.kt. */
fun monthName(month: Int): String = when (month) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
    7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
}

