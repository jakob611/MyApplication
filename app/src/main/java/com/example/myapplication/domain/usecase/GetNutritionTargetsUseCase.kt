package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.NutritionTargets

/**
 * Faza 29.8 — Clean Architecture: Poslovna logika za izračun prehranskih ciljev.
 *
 * Preseljeno iz NutritionViewModel.nutritionTargets combine bloka → Domain sloj.
 * ViewModel je zdaj samo koordinator — ne vsebuje več poslovne logike.
 *
 * Stroga prioritetna hierarhija:
 *   1. frozenCalories (zamrznjeni Firestore snapshot tega dne)
 *   2. hybridTDEE > 800 (izmerjeni TDEE iz ProgressViewModel)
 *   3. planCalories > 0 (real-time plan iz Firestore)
 *   4. 2000 kcal (varni fallback)
 *
 * KMP-ready: brez Android/Firebase odvisnosti.
 */
class GetNutritionTargetsUseCase {

    operator fun invoke(
        frozenCalories: Int?,
        frozenProtein: Int?,
        frozenCarbs: Int?,
        frozenFat: Int?,
        planCalories: Int,
        planProtein: Int,
        planCarbs: Int,
        planFat: Int,
        hybridTDEE: Int
    ): NutritionTargets {

        // Kalorije — stroga prioriteta + zaokrožitev navzdol na 100
        val rawCalories = when {
            frozenCalories != null   -> frozenCalories
            hybridTDEE > 800         -> hybridTDEE
            planCalories > 0         -> planCalories
            else                     -> 2000
        }
        val calories = (rawCalories / 100) * 100

        // Makri — frozen → plan → fallback (brez !! operatorja)
        val protein = when {
            (frozenProtein ?: 0) > 0 -> frozenProtein ?: 100
            planProtein > 0          -> planProtein
            else                     -> 100
        }
        val carbs = when {
            (frozenCarbs ?: 0) > 0   -> frozenCarbs ?: 200
            planCarbs > 0            -> planCarbs
            else                     -> 200
        }
        val fat = when {
            (frozenFat ?: 0) > 0     -> frozenFat ?: 60
            planFat > 0              -> planFat
            else                     -> 60
        }

        return NutritionTargets(
            calories = calories,
            protein  = protein,
            carbs    = carbs,
            fat      = fat
        )
    }
}
