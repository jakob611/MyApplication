package com.example.myapplication.domain.model

/**
 * Faza 29.8 — Domain model za prehranske cilje.
 * Preseljeno iz ui.nutrition.NutritionViewModel → domain sloj.
 * KMP-ready: brez Android/Firebase odvisnosti.
 */
data class NutritionTargets(
    val calories: Int = 2000,
    val protein: Int = 100,
    val carbs: Int = 200,
    val fat: Int = 60
)
