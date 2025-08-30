package com.example.myapplication.ui.screens

data class AlgorithmData(
    val bmi: Double,
    val bmr: Double,
    val tdee: Double,
    val proteinPerKg: Double,
    val caloriesPerKg: Double,
    val caloricStrategy: String,
    val detailedTips: List<String>,
    val macroBreakdown: String,
    val trainingStrategy: String
)