package com.example.myapplication.domain.model

/**
 * Domenski model za algoritemske podatke treninškega načrta.
 * Preseljen iz data.store → domain.model v Fazi 41 (Clean Architecture — Anomaly 3).
 * UI in ViewModel smeta samo uvažati iz domain.model, ne iz data.store.
 */
data class AlgorithmData(
    val bmi: Double? = null,
    val bmr: Double? = null,
    val tdee: Double? = null,
    val proteinPerKg: Double? = null,
    val caloriesPerKg: Double? = null,
    val caloricStrategy: String? = null,
    val detailedTips: List<String>? = null,
    val macroBreakdown: String? = null,
    val trainingStrategy: String? = null
)

