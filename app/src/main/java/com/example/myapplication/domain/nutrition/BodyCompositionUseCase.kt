package com.example.myapplication.domain.nutrition
import kotlin.math.pow
/**
 * Zlata KMP koda za ra�unanje telesne sestave in metabolizma.
 * Brez kakr�nihkoli Android odvisnosti (Brez Contexta, Android SDK in uporabni�kega vmesnika).
 */
class BodyCompositionUseCase {
    /**
     * Ra�una indeks telesne mase (BMI).
     * @param weightKg te�a v kilogramih
     * @param heightCm vi�ina v centimetrih
     */
    fun calculateBMI(weightKg: Double, heightCm: Double): Double {
        if (heightCm <= 0) return 0.0
        val heightM = heightCm / 100.0
        return weightKg / heightM.pow(2)
    }
    /**
     * Evalvira BMI glede na kategorijo (npr. Normal, Overweight).
     */
    fun evaluateBMICategory(bmi: Double): String {
        return when {
            bmi < 18.5 -> "Underweight"
            bmi in 18.5..24.9 -> "Normal Profile"
            bmi in 25.0..29.9 -> "Overweight"
            else -> "Obese"
        }
    }
    /**
     * Zastareli ro�ni algoritmi iz 'BodyOverviewViewmodel' in 'Progress.kt'
     * zamenjani z enotnim ocenjevanjem dele�a ma��obe.
     */
    fun estimateBodyFatPercentage(
        weightKg: Double, 
        waistCm: Double, 
        neckCm: Double, 
        heightCm: Double, 
        isMale: Boolean
    ): Double {
        if (waistCm <= 0 || neckCm <= 0 || heightCm <= 0) return 0.0
        return if (isMale) {
            495.0 / (1.0324 - 0.19077 * kotlin.math.log10(waistCm - neckCm) + 0.15456 * kotlin.math.log10(heightCm)) - 450.0
        } else {
            // Potrebovali bi �e obseg bokov za popolnost, vendar uporabimo standardno napoved
            val hipsCm = waistCm * 1.15 // aproximacija �e ga nimamo.
            495.0 / (1.29579 - 0.35004 * kotlin.math.log10(waistCm + hipsCm - neckCm) + 0.22100 * kotlin.math.log10(heightCm)) - 450.0
        }.coerceIn(2.0, 60.0) // Smiselne meje
    }
}
