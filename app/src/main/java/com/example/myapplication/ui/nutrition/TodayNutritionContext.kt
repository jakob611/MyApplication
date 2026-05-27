package com.example.myapplication.ui.nutrition

import com.example.myapplication.ui.screens.SavedCustomMeal

/**
 * Faza 47 — Presentation-layer kontekst za dnevno prehransko stanje.
 *
 * Deriviran v [NutritionViewModel.todayNutritionContext] prek combine() iz:
 *   - _planResultFlow   (workout / rest day izračun)
 *   - _internalProfile  (teža, spol, cilj → voda + kalorije)
 *   - nutritionTargets  (kalorični cilj tega dne)
 *   - parsedCustomMeals (shranjene kombinacije obrokov)
 *
 * NutritionScreen samo bere ta objekt — nič ne izračunava samo.
 */
data class TodayNutritionContext(
    val isWorkoutDay: Boolean = false,
    val adjustedWaterTargetMl: Int = 2000,
    val adjustedCalorieTarget: Int = 2000,
    val customMeals: List<SavedCustomMeal> = emptyList()
)

