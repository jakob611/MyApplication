package com.example.myapplication.utils

/**
 * Nutrition Calculations - Utility functions for calculating BMR, TDEE, calories, and macros
 * These functions are used by both BodyModule (plan creation) and NutritionPlanStore (recalculation)
 */

fun calculateAdvancedBMR(weight: Double, height: Double, age: Int, isMale: Boolean, bodyFat: Double?): Double {
    return if (bodyFat != null && bodyFat > 0) {
        // Katch-McArdle formula (more accurate with body fat)
        val leanBodyMass = weight * (1 - bodyFat / 100)
        370 + (21.6 * leanBodyMass)
    } else {
        // Enhanced Mifflin-St Jeor with age adjustments
        val baseBMR = if (isMale) {
            10 * weight + 6.25 * height - 5 * age + 5
        } else {
            10 * weight + 6.25 * height - 5 * age - 161
        }

        // Age-based metabolic adjustments (more precise)
        when {
            age < 18 -> baseBMR * 1.12
            age in 18..25 -> baseBMR * 1.05
            age in 26..35 -> baseBMR * 1.0
            age in 36..45 -> baseBMR * 0.97
            age in 46..55 -> baseBMR * 0.94
            age in 56..65 -> baseBMR * 0.91
            else -> baseBMR * 0.87
        }
    }
}

fun calculateEnhancedTDEE(bmr: Double, frequency: String?, experience: String?, age: Int, limitations: List<String>, sleep: String?): Double {
    // Base activity multiplier (more nuanced)
    val baseMultiplier = when (frequency) {
        "2x" -> 1.375
        "3x" -> 1.55
        "4x" -> 1.725
        "5x" -> 1.9
        "6x" -> 2.0
        else -> 1.2
    }

    // Experience efficiency factor
    val experienceMultiplier = when (experience) {
        "Beginner" -> 1.08  // Higher energy expenditure due to inefficiency
        "Intermediate" -> 1.0
        "Advanced" -> 0.96  // More efficient movement patterns
        else -> 1.0
    }

    // Age-based activity adjustment
    val ageMultiplier = when {
        age < 25 -> 1.02
        age in 25..35 -> 1.0
        age in 36..50 -> 0.98
        age > 50 -> 0.95
        else -> 1.0
    }

    // Sleep quality significantly affects metabolism
    val sleepMultiplier = when (sleep) {
        "Less than 6" -> 0.90  // Poor recovery, reduced metabolism
        "6-7" -> 0.97
        "7-8" -> 1.0  // Optimal
        "8-9" -> 1.02
        "9+" -> 1.01  // Diminishing returns
        else -> 1.0
    }

    // Medical limitations adjustment
    val limitationMultiplier = when {
        limitations.contains("Asthma") -> 0.92
        limitations.any { it in listOf("High blood pressure", "Diabetes") } -> 0.94
        limitations.any { it in listOf("Knee injury", "Shoulder injury", "Back pain") } -> 0.96
        else -> 1.0
    }

    return bmr * baseMultiplier * experienceMultiplier * ageMultiplier * sleepMultiplier * limitationMultiplier
}

fun calculateSmartCalories(tdee: Double, goal: String?, experience: String?, bmi: Double, age: Int, isMale: Boolean, bodyFat: Double?, limitations: List<String>): Double {
    val baseCalories = when (goal) {
        "Build muscle" -> {
            val baseSurplus = when (experience) {
                "Beginner" -> 450
                "Intermediate" -> 350
                "Advanced" -> 250
                else -> 350
            }

            // Age and body fat adjustments for muscle building
            val ageFactor = when {
                age < 25 -> 1.0
                age in 25..35 -> 0.95
                age in 36..45 -> 0.85
                age in 46..55 -> 0.75
                else -> 0.65
            }

            val bodyFatFactor = if (bodyFat != null) {
                when {
                    bodyFat < 10 && isMale -> 1.1  // Very lean, can gain more aggressively
                    bodyFat < 18 && !isMale -> 1.1
                    bodyFat > 20 && isMale -> 0.8   // Higher body fat, smaller surplus
                    bodyFat > 28 && !isMale -> 0.8
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
                else -> 350
            }

            // Gender-specific fat loss adjustments
            val genderFactor = if (isMale) 1.0 else 0.85

            // Age-based metabolic considerations
            val ageFactor = when {
                age > 50 -> 0.85  // Slower fat loss for older adults
                age < 25 -> 1.1   // Faster metabolism in younger people
                else -> 1.0
            }

            val adjustedDeficit = baseDeficit * genderFactor * ageFactor
            val minCalories = if (isMale) 1500.0 else 1200.0

            maxOf(tdee - adjustedDeficit, minCalories)
        }

        "Recomposition" -> {
            when {
                experience == "Beginner" && bmi < 25 -> tdee + 150  // Slight surplus for beginners
                bmi > 25 -> tdee - 200  // Slight deficit for overweight
                bodyFat != null && bodyFat > (if (isMale) 15 else 25) -> tdee - 150
                else -> tdee  // Maintenance
            }
        }

        "Improve endurance" -> {
            val baseSurplus = when (experience) {
                "Advanced" -> 300  // Advanced endurance athletes need more fuel
                "Intermediate" -> 250
                else -> 200
            }
            tdee + baseSurplus
        }

        "General health" -> {
            when {
                bmi > 25 -> tdee - 250  // Gentle deficit for overweight
                bmi < 20 -> tdee + 200  // Slight surplus for underweight
                else -> tdee  // Maintenance for healthy weight
            }
        }

        else -> tdee
    }

    // Additional adjustments for medical conditions
    return when {
        limitations.contains("Diabetes") -> baseCalories * 0.98
        limitations.contains("High blood pressure") -> baseCalories * 0.97
        else -> baseCalories
    }
}

fun calculateOptimalMacros(calories: Double, weight: Double, goal: String?, experience: String?, age: Int, isMale: Boolean, bodyFat: Double?, nutrition: String?, limitations: List<String>): Triple<Int, Int, Int> {

    // Protein calculation with multiple factors
    val baseProteinPerKg = when (goal) {
        "Build muscle" -> when (experience) {
            "Beginner" -> 1.8
            "Intermediate" -> 2.0
            "Advanced" -> 2.2
            else -> 1.9
        }
        "Lose fat" -> when {
            bodyFat != null && bodyFat > (if (isMale) 20 else 30) -> 2.4  // Higher protein for aggressive cuts
            else -> 2.0
        }
        "Recomposition" -> 2.2
        "Improve endurance" -> 1.4
        "General health" -> 1.6
        else -> 1.7
    }

    // Age and gender protein adjustments
    val ageProteinFactor = when {
        age < 25 -> 1.0
        age in 25..40 -> 1.05
        age in 41..55 -> 1.15
        age in 56..70 -> 1.25
        else -> 1.35  // Increased protein needs for elderly
    }

    val genderProteinFactor = if (isMale) 1.0 else 0.95

    // Nutrition style adjustments
    val nutritionProteinFactor = when (nutrition) {
        "Vegetarian", "Vegan" -> 1.15  // Higher total to ensure complete amino acids
        "Keto/LCHF" -> 1.1
        else -> 1.0
    }

    val totalProtein = (baseProteinPerKg * weight * ageProteinFactor * genderProteinFactor * nutritionProteinFactor).toInt()

    // Fat calculation based on goals and health
    val fatPerKg = when {
        nutrition == "Keto/LCHF" -> when (goal) {
            "Build muscle" -> 1.8
            "Lose fat" -> 1.5
            else -> 1.6
        }
        goal == "Lose fat" && bodyFat != null && bodyFat > 25 -> 0.7  // Lower fat for aggressive cuts
        limitations.contains("High blood pressure") -> 0.8  // Lower saturated fat
        isMale -> when {
            age < 30 -> 0.9
            age < 50 -> 1.0
            else -> 1.1
        }
        else -> when {  // Female
            age < 30 -> 1.1  // Higher fat needs for hormones
            age < 50 -> 1.2
            else -> 1.3
        }
    }

    val totalFat = (fatPerKg * weight).toInt()

    // Carbohydrate calculation (remaining calories)
    val proteinCalories = totalProtein * 4
    val fatCalories = totalFat * 9
    val remainingCalories = calories - proteinCalories - fatCalories

    val totalCarbs = when (nutrition) {
        "Keto/LCHF" -> minOf(50, (remainingCalories / 4).toInt())  // Very low carb
        "Intermittent fasting" -> maxOf(100, (remainingCalories / 4).toInt())  // Moderate carb
        else -> maxOf(80, (remainingCalories / 4).toInt())  // Minimum for brain function
    }

    return Triple(totalProtein, totalCarbs, totalFat)
}
