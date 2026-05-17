package com.example.myapplication.domain.nutrition

/**
 * Nutrition Calculations — Faza 13: Arhitekturna in matematična prenova
 *
 * Popravljene napake:
 *  1. calculateAdvancedBMR: starostni faktor za mladostnike (<18) apliciran GLOBALNO
 *     na obe formuli (Katch-McArdle in Mifflin-St Jeor).
 *  2. calculateEnhancedTDEE: experience/sleep/limitations vplivajo SAMO na aktivnostni
 *     del metabolizma (delta = TDEE − BMR), ne na bazalni metabolizem organov.
 *  3. calculateOptimalMacros: vsi makrohranili zaščiteni s coerceAtLeast(0).
 *     Keto/LCHF karbohidrati imajo fiksno spodnjo mejo 20g (ne negativne vrednosti).
 *  4. calculateAdaptiveTDEE: emaWeightChangeDelta delimo vedno z 7.0 (dejanski
 *     časovni interval telesne mase), ne z activeDays.
 */

// ══════════════════════════════════════════════════════════════════════════════
// 1. BMR — Basal Metabolic Rate
// ══════════════════════════════════════════════════════════════════════════════
fun calculateAdvancedBMR(
    weight: Double,
    height: Double,
    age: Int,
    isMale: Boolean,
    bodyFat: Double?
): Double {
    val rawBMR = if (bodyFat != null && bodyFat > 0) {
        val leanBodyMass = weight * (1.0 - bodyFat / 100.0)
        370.0 + (21.6 * leanBodyMass)
    } else {
        if (isMale)
            10.0 * weight + 6.25 * height - 5.0 * age + 5.0
        else
            10.0 * weight + 6.25 * height - 5.0 * age - 161.0
    }
    return when {
        age < 18 -> rawBMR * 1.12
        age > 65 -> rawBMR * 0.87
        else     -> rawBMR
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. TDEE — Total Daily Energy Expenditure
// ══════════════════════════════════════════════════════════════════════════════
fun calculateEnhancedTDEE(
    bmr: Double,
    frequency: String?,
    experience: String?,
    age: Int,
    limitations: List<String>,
    sleep: String?
): Double {
    val baseMultiplier = when (frequency) {
        "2x" -> 1.375
        "3x" -> 1.55
        "4x" -> 1.725
        "5x" -> 1.9
        "6x" -> 2.0
        else -> 1.2
    }
    val experienceMultiplier = when (experience) {
        "Beginner"     -> 1.08
        "Intermediate" -> 1.0
        "Advanced"     -> 0.96
        else           -> 1.0
    }
    val sleepMultiplier = when (sleep) {
        "Less than 6" -> 0.90
        "6-7"         -> 0.97
        "7-8"         -> 1.0
        "8-9"         -> 1.02
        "9+"          -> 1.01
        else          -> 1.0
    }
    val limitationMultiplier = when {
        limitations.contains("Asthma") -> 0.92
        limitations.any { it in listOf("High blood pressure", "Diabetes") } -> 0.94
        limitations.any { it in listOf("Knee injury", "Shoulder injury", "Back pain") } -> 0.96
        else -> 1.0
    }
    val ageMultiplier = when {
        age < 25      -> 1.02
        age in 25..35 -> 1.0
        age in 36..50 -> 0.98
        age > 50      -> 0.95
        else          -> 1.0
    }
    val baseTDEE      = bmr * baseMultiplier
    val activityDelta = baseTDEE - bmr
    val adjustedDelta = activityDelta * experienceMultiplier * sleepMultiplier * limitationMultiplier
    return (bmr + adjustedDelta) * ageMultiplier
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. Kalorijski cilj (Smart Calories)
// ══════════════════════════════════════════════════════════════════════════════
fun calculateSmartCalories(
    tdee: Double,
    goal: String?,
    experience: String?,
    bmi: Double,
    age: Int,
    isMale: Boolean,
    bodyFat: Double?,
    limitations: List<String>
): Double {
    val baseCalories = when (goal) {
        "Build muscle" -> {
            val baseSurplus = when (experience) {
                "Beginner"     -> 450
                "Intermediate" -> 350
                "Advanced"     -> 250
                else           -> 350
            }
            val ageFactor = when {
                age < 25          -> 1.0
                age in 25..35     -> 0.95
                age in 36..45     -> 0.85
                age in 46..55     -> 0.75
                else              -> 0.65
            }
            val bodyFatFactor = if (bodyFat != null) {
                when {
                    bodyFat < 10 && isMale   -> 1.1
                    bodyFat < 18 && !isMale  -> 1.1
                    bodyFat > 20 && isMale   -> 0.8
                    bodyFat > 28 && !isMale  -> 0.8
                    else -> 1.0
                }
            } else 1.0
            tdee + (baseSurplus * ageFactor * bodyFatFactor)
        }
        "Lose fat" -> {
            val baseDeficit = when {
                bmi > 35 -> 750
                bmi > 30 -> 650
                bmi > 27 -> 550
                bmi > 25 -> 450
                else     -> 350
            }
            val genderFactor = if (isMale) 1.0 else 0.85
            val ageFactor = when {
                age > 50 -> 0.85
                age < 25 -> 1.1
                else     -> 1.0
            }
            val minCalories = if (isMale) 1500.0 else 1200.0
            maxOf(tdee - baseDeficit * genderFactor * ageFactor, minCalories)
        }
        "Recomposition" -> when {
            experience == "Beginner" && bmi < 25 -> tdee + 150
            bmi > 25 -> tdee - 200
            bodyFat != null && bodyFat > (if (isMale) 15 else 25) -> tdee - 150
            else     -> tdee
        }
        "Improve endurance" -> {
            val baseSurplus = when (experience) {
                "Advanced"     -> 300
                "Intermediate" -> 250
                else           -> 200
            }
            tdee + baseSurplus
        }
        "General health" -> when {
            bmi > 25 -> tdee - 250
            bmi < 20 -> tdee + 200
            else     -> tdee
        }
        else -> tdee
    }
    return when {
        limitations.contains("Diabetes")             -> baseCalories * 0.98
        limitations.contains("High blood pressure")  -> baseCalories * 0.97
        else -> baseCalories
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 4. Dnevna voda
// ══════════════════════════════════════════════════════════════════════════════
fun calculateDailyWaterMl(
    weightKg: Double,
    isMale: Boolean,
    activityLevel: String,
    isWorkoutDay: Boolean
): Float {
    var water = weightKg * 35.0
    if (isMale) water *= 1.1
    water += when (activityLevel) {
        "Sedentary"          -> 0.0
        "Lightly active"     -> 250.0
        "Moderately active"  -> 500.0
        "Very active"        -> 750.0
        else                 -> 250.0
    }
    if (isWorkoutDay) water += 500.0
    water = (water / 100.0).let { kotlin.math.round(it) * 100.0 }
    return water.coerceIn(1500.0, 5000.0).toFloat()
}

// ══════════════════════════════════════════════════════════════════════════════
// 5. Rest day kalorije
// ══════════════════════════════════════════════════════════════════════════════
fun calculateRestDayCalories(tdee: Double, goal: String?, isMale: Boolean): Int {
    val adjustment = when (goal) {
        "Lose fat"      -> -250
        "Build muscle"  -> 100
        "Recomposition" -> -150
        else            -> 0
    }
    val minCalories = if (isMale) 1500 else 1200
    return (tdee + adjustment).coerceAtLeast(minCalories.toDouble()).toInt()
}

// ══════════════════════════════════════════════════════════════════════════════
// 6. Makrohranila — Triple<protein, carbs, fat> v gramih
// ══════════════════════════════════════════════════════════════════════════════
fun calculateOptimalMacros(
    calories: Double,
    weight: Double,
    goal: String?,
    experience: String?,
    age: Int,
    isMale: Boolean,
    bodyFat: Double?,
    nutrition: String?,
    limitations: List<String>
): Triple<Int, Int, Int> {
    val baseProteinPerKg = when (goal) {
        "Build muscle" -> when (experience) {
            "Beginner"     -> 1.8
            "Intermediate" -> 2.0
            "Advanced"     -> 2.2
            else           -> 1.9
        }
        "Lose fat" -> if (bodyFat != null && bodyFat > (if (isMale) 20 else 30)) 2.4 else 2.0
        "Recomposition"     -> 2.2
        "Improve endurance" -> 1.4
        "General health"    -> 1.6
        else                -> 1.7
    }
    val ageProteinFactor = when {
        age < 25      -> 1.0
        age in 25..40 -> 1.05
        age in 41..55 -> 1.15
        age in 56..70 -> 1.25
        else          -> 1.35
    }
    val genderProteinFactor    = if (isMale) 1.0 else 0.95
    val nutritionProteinFactor = when (nutrition) {
        "Vegetarian", "Vegan" -> 1.15
        "Keto/LCHF"           -> 1.1
        else                  -> 1.0
    }
    val totalProtein = (baseProteinPerKg * weight * ageProteinFactor * genderProteinFactor * nutritionProteinFactor)
        .toInt().coerceAtLeast(0)

    val fatPerKg = when {
        nutrition == "Keto/LCHF" -> when (goal) {
            "Build muscle" -> 1.8
            "Lose fat"     -> 1.5
            else           -> 1.6
        }
        goal == "Lose fat" && bodyFat != null && bodyFat > 25 -> 0.7
        limitations.contains("High blood pressure") -> 0.8
        isMale -> when { age < 30 -> 0.9; age < 50 -> 1.0; else -> 1.1 }
        else   -> when { age < 30 -> 1.1; age < 50 -> 1.2; else -> 1.3 }
    }
    val totalFat = (fatPerKg * weight).toInt().coerceAtLeast(0)

    val proteinCalories   = totalProtein * 4
    val fatCalories       = totalFat * 9
    val remainingCalories = (calories - proteinCalories - fatCalories).coerceAtLeast(0.0)

    val totalCarbs = when (nutrition) {
        "Keto/LCHF"            -> (remainingCalories / 4).toInt().coerceIn(20, 50)
        "Intermittent fasting" -> (remainingCalories / 4).toInt().coerceAtLeast(100)
        else                   -> (remainingCalories / 4).toInt().coerceAtLeast(80)
    }
    return Triple(totalProtein, totalCarbs, totalFat)
}

// ══════════════════════════════════════════════════════════════════════════════
// 7. EMA — Exponential Moving Average
// ══════════════════════════════════════════════════════════════════════════════
fun calculateEMA(weights: List<Double>, period: Int = 7): Double {
    if (weights.isEmpty()) return 0.0
    val alpha = 2.0 / (period + 1)
    var ema = weights[0]
    for (i in 1 until weights.size) {
        ema = alpha * weights[i] + (1.0 - alpha) * ema
    }
    return ema
}

/**
 * Rezultat hibridnega TDEE izračuna.
 */
data class AdaptiveTDEEResult(
    val hybridTDEE: Int,
    val adaptiveTDEE: Int,
    val confidenceFactor: Double
)

// ══════════════════════════════════════════════════════════════════════════════
// 8. Adaptivni TDEE — Hibridni izračun iz opazovane telesne mase
// ══════════════════════════════════════════════════════════════════════════════
fun calculateAdaptiveTDEE(
    last7DaysCalories: List<Int>,
    emaWeightChangeDelta: Double,
    theoreticalTDEE: Int = 0
): AdaptiveTDEEResult {
    val activeDays = last7DaysCalories.size
    val confidence = when {
        activeDays < 3  -> 0.0
        activeDays <= 5 -> 0.5
        else            -> 1.0
    }
    val adaptiveTDEE: Int = if (activeDays == 0) {
        theoreticalTDEE.coerceAtLeast(800)
    } else {
        val avgCalories          = last7DaysCalories.average()
        val observedDailyBalance = (emaWeightChangeDelta * 7700.0) / 7.0
        (avgCalories - observedDailyBalance).toInt().coerceAtLeast(800)
    }
    val hybrid = if (theoreticalTDEE <= 0) {
        adaptiveTDEE
    } else {
        (confidence * adaptiveTDEE + (1.0 - confidence) * theoreticalTDEE)
            .toInt().coerceAtLeast(800)
    }
    return AdaptiveTDEEResult(
        hybridTDEE       = hybrid,
        adaptiveTDEE     = adaptiveTDEE,
        confidenceFactor = confidence
    )
}
