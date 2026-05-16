package com.example.myapplication.domain.usecase

import kotlin.math.roundToInt

/**
 * CalculateDailyCalorieTargetUseCase — SSOT za izračun dnevnega kaloričnega cilja.
 *
 * ## Algoritem (Hibridni kalorični motor — Faza 10):
 *  1. BMR:
 *     - Katch-McArdle: če je `bodyFatPercentage` podan (0 < BF% < 60)
 *       → lbm = weight × (1 − BF%/100); BMR = 370 + 21.6 × lbm
 *     - Mifflin-St Jeor fallback (ločeno za M/Ž + starostna korekcija)
 *  2. TDEE = BMR × faktor_aktivnosti (konzervativni FAO/WHO faktorji)
 *  3. Dnevni cilj = TDEE + prilagoditev_cilja (−500 / 0 / +300)
 *
 * ## Aktivnostni faktorji (Faza 10 — konzervativni):
 *  null/sedentarno = 1.20 | 2x (LIGHT) = 1.375 | 3x (MODERATE) = 1.45 |
 *  4x (ACTIVE) = 1.65 | 5x (EXTREME) = 1.85 | 6x = 2.0
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
        /**
         * Odstotek telesne maščobe (0–59).
         * Če je podan → Katch-McArdle BMR; sicer → Mifflin-St Jeor fallback.
         */
        val bodyFatPercentage: Double? = null,
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
        val bmr = calculateBmr(
            weightKg          = input.weightKg,
            heightCm          = input.heightCm,
            ageYears          = input.ageYears,
            isMale            = input.isMale,
            bodyFatPercentage = input.bodyFatPercentage
        )
        val factor = activityFactor(input.activityLevel)
        val tdee = bmr * factor
        val goalAdj = goalAdjustment(input.goal)
        val target = (tdee + goalAdj).coerceAtLeast(1200.0).roundToInt()

        return Result(
            bmr               = bmr,
            tdee              = tdee,
            dailyCalorieTarget = target,
            goalAdjustment    = goalAdj
        )
    }

    /**
     * Prikladen vstop ko je BMR že izračunan (npr. shranjen v Firestore planu).
     * Kliče se iz NutritionViewModel.setUserMetrics().
     *
     * @param bmr              Predhodno izračunana bazalna presnova (kcal/dan)
     * @param goal             Cilj (besedilo)
     * @param activityLevel    Frekvenca treningov ("2x"–"6x") ali null
     * @param bodyFatPercentage Opcijsko BF% za beleženje/debug (BMR ni preračunan)
     */
    fun fromBmr(
        bmr: Double,
        goal: String,
        activityLevel: String? = null,
        bodyFatPercentage: Double? = null
    ): Result {
        val factor = activityFactor(activityLevel)
        val tdee = bmr * factor
        val goalAdj = goalAdjustment(goal)
        val target = (tdee + goalAdj).coerceAtLeast(1200.0).roundToInt()

        return Result(
            bmr               = bmr,
            tdee              = tdee,
            dailyCalorieTarget = target,
            goalAdjustment    = goalAdj
        )
    }

    // ── Interna BMR logika (hibridni motor) ────────────────────────────────────

    /**
     * Hibridni BMR izračun:
     * - Katch-McArdle: če je BF% podan, > 0 in < 60
     * - Mifflin-St Jeor + starostna korekcija: fallback
     */
    private fun calculateBmr(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        isMale: Boolean,
        bodyFatPercentage: Double?
    ): Double {
        // ── Katch-McArdle pogoj ──────────────────────────────────────────────
        if (bodyFatPercentage != null && bodyFatPercentage > 0.0 && bodyFatPercentage < 60.0) {
            val lbm = weightKg * (1.0 - bodyFatPercentage / 100.0)
            return 370.0 + (21.6 * lbm)
        }

        // ── Mifflin-St Jeor fallback ─────────────────────────────────────────
        val baseBmr = if (isMale) {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears - 161.0
        }
        // Starostna metabolna korekcija
        return when {
            ageYears < 18          -> baseBmr * 1.12
            ageYears in 18..25     -> baseBmr * 1.05
            ageYears in 26..35     -> baseBmr * 1.00
            ageYears in 36..45     -> baseBmr * 0.97
            ageYears in 46..55     -> baseBmr * 0.94
            ageYears in 56..65     -> baseBmr * 0.91
            else                   -> baseBmr * 0.87
        }
    }

    // ── Pomožne funkcije (companion-level, testabilne) ──────────────────────────

    companion object {
        /**
         * Pretvori frekvenco treningov v TDEE množilnik.
         *
         * Faza 10 — Konzervativni FAO/WHO faktorji (preprečevanje overshoot):
         *  null  → SEDENTARY  = 1.20
         *  "2x"  → LIGHT      = 1.375
         *  "3x"  → MODERATE   = 1.45  (−0.10 vs. standard 1.55)
         *  "4x"  → ACTIVE     = 1.65  (−0.075 vs. standard 1.725)
         *  "5x"  → EXTREME    = 1.85  (−0.05 vs. standard 1.9)
         *  "6x"  → MAX        = 2.00  (nepremenjeno)
         */
        fun activityFactor(activityLevel: String?): Double = when (activityLevel) {
            "2x" -> 1.375   // LIGHT — lahka aktivnost
            "3x" -> 1.45    // MODERATE — znižano z 1.55 (FAO/WHO konzervativna ocena)
            "4x" -> 1.65    // ACTIVE — znižano z 1.725 (realnejša ocena za rekreativce)
            "5x" -> 1.85    // EXTREME — znižano z 1.9 (zgornja meja varnosti)
            "6x" -> 2.00    // MAX — intenziven profesionalni trening
            else -> 1.20    // SEDENTARY — sedeč življenjski slog (fallback)
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
