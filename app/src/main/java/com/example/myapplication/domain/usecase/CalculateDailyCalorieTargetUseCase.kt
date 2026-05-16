package com.example.myapplication.domain.usecase

import com.example.myapplication.utils.calculateAdvancedBMR
import com.example.myapplication.utils.calculateEnhancedTDEE
import kotlin.math.roundToInt

/**
 * CalculateDailyCalorieTargetUseCase — SSOT za izračun dnevnega kaloričnega cilja.
 *
 * ## Algoritem (Mifflin-St Jeor + faktor aktivnosti + konzervativna ciljna prilagoditev):
 *  1. BMR = Mifflin-St Jeor (z opcijskim Katch-McArdle, če je body fat podan)
 *  2. TDEE = BMR × faktor_aktivnosti (iz frekvence treningov)
 *  3. Dnevni cilj = TDEE + prilagoditev_cilja (−500 / 0 / +300)
 *
 * ## Zakaj ne calculateSmartCalories() (kviz)?
 *  `calculateSmartCalories()` uporablja agresivne BMI-dependent suficite/deficite
 *  (npr. −750 kcal za BMI > 35), kar povzroča pretirane ocene pri kalibraciji.
 *  Ta UseCase uporablja konzervativno ±500/300 kcal metodo, ki je v skladu z
 *  NutritionScreen pristopom "kalorični minimum + aktivnost".
 *
 * ## Kje se kliče (Faza 9):
 *  - `NutritionViewModel.setUserMetrics()` → prek [fromBmr]
 *  - `BodyModule.kt algorithmData` → prek [invoke] z vsemi vhodnimi podatki
 *
 * KMP-ready: brez Android odvisnosti.
 */
class CalculateDailyCalorieTargetUseCase {

    /**
     * Popolni vhodni podatki za izračun od začetka.
     * Kliče se iz BodyModule.kt (kviz) ko so vsi biometrični podatki na voljo.
     */
    data class Input(
        val weightKg: Double,
        val heightCm: Double,
        val ageYears: Int,
        val isMale: Boolean,
        /** Frekvenca treningov: "2x", "3x", "4x", "5x", "6x" ali null = sedentarno */
        val activityLevel: String? = null,
        /** Cilj: "Lose fat", "Build muscle", "General health", "Recomposition", … */
        val goal: String = "",
        val bodyFatPercent: Double? = null,
        val experience: String? = null,
        val limitations: List<String> = emptyList(),
        val sleep: String? = null
    )

    /**
     * Rezultat izračuna.
     */
    data class Result(
        /** Bazalna presnova (kcal/dan) */
        val bmr: Double,
        /** Skupna dnevna poraba energije s faktorjem aktivnosti */
        val tdee: Double,
        /** Končni dnevni kalorični cilj (zaokrožen na celo število) */
        val dailyCalorieTarget: Int,
        /** Prilagoditev glede na cilj: −500 / 0 / +300 */
        val goalAdjustment: Int
    )

    /**
     * Izračuna dnevni kalorični cilj iz popolnih biometričnih vhodnih podatkov.
     * Kliče se iz BodyModule.kt (kviz) pri ustvarjanju novega plana.
     */
    fun invoke(input: Input): Result {
        val bmr = calculateAdvancedBMR(
            weight = input.weightKg,
            height = input.heightCm,
            age = input.ageYears,
            isMale = input.isMale,
            bodyFat = input.bodyFatPercent
        )
        val tdee = calculateEnhancedTDEE(
            bmr = bmr,
            frequency = input.activityLevel,
            experience = input.experience,
            age = input.ageYears,
            limitations = input.limitations,
            sleep = input.sleep
        )
        val goalAdj = goalAdjustment(input.goal)
        val target = (tdee + goalAdj).coerceAtLeast(1200.0).roundToInt()

        return Result(
            bmr = bmr,
            tdee = tdee,
            dailyCalorieTarget = target,
            goalAdjustment = goalAdj
        )
    }

    /**
     * Prikladen vstop ko je BMR že izračunan (npr. shranjen v Firestore planu).
     * Kliče se iz NutritionViewModel.setUserMetrics().
     *
     * @param bmr  Predhodno izračunana bazalna presnova (kcal/dan)
     * @param goal Cilj (besedilo)
     * @param activityLevel Frekvenca treningov ("2x"–"6x") ali null
     */
    fun fromBmr(bmr: Double, goal: String, activityLevel: String? = null): Result {
        val factor = activityFactor(activityLevel)
        val tdee = bmr * factor
        val goalAdj = goalAdjustment(goal)
        val target = (tdee + goalAdj).coerceAtLeast(1200.0).roundToInt()

        return Result(
            bmr = bmr,
            tdee = tdee,
            dailyCalorieTarget = target,
            goalAdjustment = goalAdj
        )
    }

    // ── Pomožne funkcije (companion-level, testabilne) ──────────────────────────

    companion object {
        /**
         * Pretvori frekvenco treningov v TDEE množilnik.
         * Skladno z Mifflin-St Jeor TDEE tabelami.
         */
        fun activityFactor(activityLevel: String?): Double = when (activityLevel) {
            "2x" -> 1.375   // Lightly active
            "3x" -> 1.55    // Moderately active
            "4x" -> 1.725   // Very active
            "5x" -> 1.9     // Extra active
            "6x" -> 2.0     // Extreme active
            else -> 1.2     // Sedentary (fallback)
        }

        /**
         * Konzervativna ciljna prilagoditev (ne-agresivni deficit/suficit).
         * −500 kcal za hujšanje | +300 kcal za naraščanje mišic | 0 za vzdrževanje.
         */
        fun goalAdjustment(goal: String): Int = when {
            goal.contains("Lose", ignoreCase = true) ||
            goal.contains("Cut",  ignoreCase = true) -> -500

            goal.contains("Build", ignoreCase = true) ||
            goal.contains("Gain",  ignoreCase = true) -> 300

            else -> 0
        }
    }
}

