package com.example.myapplication.domain.health

data class BodyMetricsResult(
    val bmi: Double,
    val bmiCategory: String,
    val targetCalories: Double,
    val tdee: Double,
    val bmr: Double
)

class CalculateBodyMetricsUseCase {
    fun calculate(
        gender: String,
        age: Int,
        heightCm: Float,
        weightKg: Float,
        goal: String,
        activityLevel: String,
        bodyFatPct: Int? = null
    ): BodyMetricsResult {
        // BMI Calculation
        val heightM = heightCm / 100.0
        val bmi = if (heightM > 0) weightKg / (heightM * heightM) else 0.0
        val bmiCategory = when {
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Normal"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }

        // BMR Calculation (Mifflin-St Jeor)
        val isMale = gender == "Male" || gender == "Boy"
        var bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + (if (isMale) 5 else -161)

        // Katch-McArdle using Body Fat Alternative
        if (bodyFatPct != null && bodyFatPct in 5..50) {
            val leanMass = weightKg * (1 - bodyFatPct / 100.0)
            bmr = 370 + (21.6 * leanMass)
        }

        // Multiplier based on typical freq/duration values
        val modifier = when {
            activityLevel.contains("1-2") -> 1.375
            activityLevel.contains("3-4") -> 1.55
            activityLevel.contains("5-6") -> 1.725
            activityLevel.contains("Daily") -> 1.9
            else -> 1.2
        }

        val tdee = bmr * modifier

        val targetCalories = when {
            goal.contains("Lose", ignoreCase = true) || goal.contains("Cut", ignoreCase = true) -> tdee - 500
            goal.contains("Build", ignoreCase = true) || goal.contains("Gain", ignoreCase = true) -> tdee + 300
            else -> tdee
        }

        return BodyMetricsResult(bmi, bmiCategory, targetCalories, tdee, bmr)
    }

    /**
     * Faza 4 — Dinamični TDEE:
     * Izračuna sedentarno bazo (BMR × 1.2) brez vključenega treninga.
     * Trening se prišteje dinamično prek burnedCalories iz dailyLogs.
     *
     * @param bmr            Bazalna presnova (kcal/dan)
     * @param burnedCalories Kalorije porabljene danes (HC + Workout, real-time)
     * @param goal           Cilj za prilagoditev (+300 mišice / -500 hujšanje / 0 vzdrž.)
     * @return               Dinamični dnevni kalorični limit
     */
    fun calculateDynamicTdee(bmr: Double, burnedCalories: Int, goal: String): Double {
        val baseTdee = bmr * 1.2  // Sedentarna baza — popolno mirovanje + NEAT
        val goalAdjustment = when {
            goal.contains("Lose", ignoreCase = true) ||
            goal.contains("Cut",  ignoreCase = true) -> -500.0
            goal.contains("Build", ignoreCase = true) ||
            goal.contains("Gain",  ignoreCase = true) -> 300.0
            else -> 0.0
        }
        return (baseTdee + burnedCalories + goalAdjustment).coerceAtLeast(1200.0)
    }
}

