package com.example.myapplication.debug

/**
 * Singleton za shranjevanje surovih TDEE vrednosti za Debug Dashboard.
 * Posodablja se iz NutritionViewModel.setUserMetrics() in observeDailyTotals().
 * Ni namenjen produkcijski uporabi — samo za razvijalce.
 */
object NutritionDebugStore {
    @Volatile var lastBmr: Double = 0.0
    @Volatile var lastGoal: String = "—"
    /** Vedno 1.2 (sedentarni NEAT multiplier) */
    val activityMultiplier: Double = 1.2
    @Volatile var lastGoalAdjustment: Int = 0
    @Volatile var lastBurnedCalories: Int = 0
    @Volatile var lastConsumedCalories: Int = 0
    @Volatile var lastWaterMl: Int = 0
}

