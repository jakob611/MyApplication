package com.example.myapplication.ui.screens

// =====================================================================
// NutritionModels.kt
// Vsebuje: vse podatkovne modele, enume in pomožne funkcije za Nutrition.
// =====================================================================

import kotlin.math.roundToInt

// ---------- Enumi ----------

internal enum class MealType(val title: String) {
    Breakfast("Breakfast"),
    Lunch("Lunch"),
    Dinner("Dinner"),
    Snacks("Snacks")
}

// ---------- Podatkovni modeli ----------

internal data class TrackedFood(
    val id: String,
    val name: String,
    val meal: MealType,
    val amount: Double,
    val unit: String,
    val caloriesKcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val saturatedFatG: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val cholesterolMg: Double? = null,
    val barcode: String? = null
)

data class SavedCustomMeal(
    val id: String,
    val name: String,
    val items: List<Map<String, Any>>
)

// Javni tip za parsane tarče (uporablja NutritionScreen)
data class NutritionTargets(
    val calories: Int?,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?
)

// ---------- Pomožne funkcije ----------

internal fun parseMacroBreakdown(text: String?): NutritionTargets {
    if (text.isNullOrBlank()) return NutritionTargets(null, null, null, null)
    val proteinTotalRe = Regex("""Protein:\s*[\d.]+g/kg\s*\(([\d.]+)g total\)""", RegexOption.IGNORE_CASE)
    val proteinSimpleRe = Regex("""Protein:\s*([\d.]+)g(?:\b|,)""", RegexOption.IGNORE_CASE)
    val carbsRe = Regex("""Carbs:\s*([\d.]+)g""", RegexOption.IGNORE_CASE)
    val fatRe = Regex("""Fat:\s*([\d.]+)g""", RegexOption.IGNORE_CASE)
    val caloriesRe = Regex("""Calories:\s*([\d.]+)\s*kcal""", RegexOption.IGNORE_CASE)
    val protein = proteinTotalRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
        ?: proteinSimpleRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val carbs = carbsRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val fat = fatRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val calories = caloriesRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    return NutritionTargets(calories, protein, carbs, fat)
}

internal fun formatMacroWeight(grams: Double?, unitPreference: String): String {
    val g = grams ?: 0.0
    return if (unitPreference == "lbs" || unitPreference == "lb") {
        val oz = g / 28.3495
        "${oz.roundToInt()} oz"
    } else {
        "${g.roundToInt()} g"
    }
}

internal fun macroLabel(label: String, consumed: Double, target: Int, unitPreference: String): String {
    val emoji = when (label) {
        "Protein" -> "🥩"
        "Fat" -> "🥑"
        "Carbs" -> "🍞"
        else -> ""
    }
    val unit = if (unitPreference == "lbs" || unitPreference == "lb") "oz" else "g"
    val cVal = if (unit == "oz") (consumed / 28.3495).roundToInt() else consumed.roundToInt()
    val tVal = if (unit == "oz") (target.toDouble() / 28.3495).roundToInt() else target
    return if (target > 0) "$emoji $label: $cVal/$tVal $unit" else "$emoji $label: $cVal $unit"
}
