package com.example.myapplication.ui.screens

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