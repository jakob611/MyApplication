package com.example.myapplication.domain

import com.example.myapplication.data.AdvancedExerciseRepository
import com.example.myapplication.data.RefinedExercise
import com.example.myapplication.data.MuscleRole
import kotlin.math.abs
import kotlin.random.Random

data class WorkoutGenerationParams(
    val userExperienceLevel: Int,
    val targetDifficultyLevel: Float = userExperienceLevel.toFloat(),
    val availableEquipment: Set<String>,
    val goal: WorkoutGoal,
    val focusAreas: Set<String>,
    val exerciseCount: Int = 10,
    val durationMinutes: Int = 45
)

enum class WorkoutGoal {
    MUSCLE_GAIN, WEIGHT_LOSS, STRENGTH, ENDURANCE, GENERAL_FITNESS
}

class WorkoutGenerator {

    /**
     * Mapa: fokus iz kviza → seznam ključev mišic točno kot so v exercises.json.
     * Lowercase primerjava — JSON ključi so npr. "Trebuh / Core", "Noge – Quads" itd.
     * Vrnemo lowercase različice ki jih primerjamo z lowercase imeni mišic.
     */
    private fun focusToMuscleKeys(focus: String): List<String> = when (focus.lowercase().trim()) {
        // Kviz fokusi (slovensko orientirani)
        "upper body"  -> listOf("prsni", "hrbet", "ramena", "biceps", "triceps", "prednje podlakti")
        "lower body"  -> listOf("noge – quads", "noge – hamstrings", "zadnjica", "meča")
        "core"        -> listOf("trebuh / core")
        "cardio"      -> listOf("kardio")
        "flexibility" -> listOf("raztezanje")

        // GenerateWorkoutScreen fokusi (angleško)
        "legs"        -> listOf("noge – quads", "noge – hamstrings", "zadnjica", "meča")
        "arms"        -> listOf("biceps", "triceps", "prednje podlakti")
        "chest"       -> listOf("prsni")
        "back"        -> listOf("hrbet")
        "abs"         -> listOf("trebuh / core")
        "shoulders"   -> listOf("ramena")

        // Balance / Full Body → posebna obravnava (prazno = vse vaje OK)
        "balance", "full body" -> emptyList()

        // Neznani fokus — poskusi direktno ujemanje
        else -> listOf(focus.lowercase().trim())
    }

    /** Vrne true če je fokus tipa "vse mišice" (Full Body / Balance / prazen) */
    private fun isFullBodyFocus(focusAreas: Set<String>): Boolean =
        focusAreas.isEmpty() ||
        focusAreas.any { it.equals("Full Body", ignoreCase = true) } ||
        focusAreas.any { it.equals("Balance", ignoreCase = true) }

    fun generateWorkout(params: WorkoutGenerationParams): List<RefinedExercise> {
        val allExercises = AdvancedExerciseRepository.getAllExercises()

        android.util.Log.d("WorkoutGenerator",
            "Generating: focusAreas=${params.focusAreas}, equipment=${params.availableEquipment}, goal=${params.goal}, difficulty=${params.targetDifficultyLevel}")

        // 1. Filter po opremi
        val userEquipment = params.availableEquipment.map { it.trim().lowercase() }.toSet()

        val availableExercises = allExercises.filter { exercise ->
            val requiredList = exercise.equipment.split(",").map { it.trim().lowercase() }
            requiredList.all { req ->
                if (req == "bodyweight" || req == "none" || req.isBlank()) return@all true
                userEquipment.any { userEq ->
                    userEq == req || userEq.startsWith(req) || req.startsWith(userEq) ||
                    (userEq.contains("dumbbell") && req.contains("dumbbell")) ||
                    (userEq.contains("kettlebell") && req.contains("kettlebell")) ||
                    (userEq.contains("barbell") && req.contains("barbell")) ||
                    (userEq.contains("band") && req.contains("band"))
                }
            }
        }

        android.util.Log.d("WorkoutGenerator",
            "After equipment filter: ${availableExercises.size}/${allExercises.size} exercises")

        if (availableExercises.isEmpty()) return emptyList()

        // 2. Izračunaj score za vsako vajo
        val scoredExercises = availableExercises.map { exercise ->
            exercise to calculateScore(exercise, params)
        }

        // Ohrani samo vaje s score > 1.0 (eliminira popolnoma neskladne)
        val validExercises = scoredExercises.filter { it.second > 1.0 }

        // Log top 5
        validExercises.sortedByDescending { it.second }.take(5).forEach { (ex, score) ->
            android.util.Log.d("WorkoutGenerator",
                "  TOP: ${ex.name} score=${"%.2f".format(score)} diff=${ex.difficulty} muscles=${ex.muscleIntensities.keys}")
        }

        val pool = if (validExercises.isNotEmpty()) validExercises else scoredExercises
        val count = params.exerciseCount.coerceAtMost(15)

        return selectExercisesWeighted(pool, count)
    }

    private fun calculateScore(exercise: RefinedExercise, params: WorkoutGenerationParams): Double {
        var score = 10.0

        // A. Ujemanje težavnosti — bliže targetDifficulty = višji score
        val diffDelta = abs(params.targetDifficultyLevel - exercise.difficulty)
        val diffMultiplier = (1.0 - (diffDelta * 0.1)).coerceAtLeast(0.1)
        score *= diffMultiplier

        // B. Fokus ujemanje — s pravilnim mapiranjem na JSON ključe
        var focusMultiplier = 0.1  // default: vaja ni v fokusu

        if (isFullBodyFocus(params.focusAreas)) {
            // Full Body / Balance → vse vaje so primerne, bonus za tiste s PRIMARY mišico
            focusMultiplier = 1.0
            if (exercise.muscleIntensities.values.any { it.role == MuscleRole.PRIMARY }) {
                focusMultiplier = 1.2
            }
        } else {
            // Zberi vse target muscle ključe za vse fokuse
            val targetMuscleKeys = params.focusAreas
                .flatMap { focusToMuscleKeys(it) }
                .toSet()  // lowercase

            // Preveri ali katera mišica vaje ustreza kateri od target ključev
            val matchingIntensities = exercise.muscleIntensities.entries.filter { (muscleName, _) ->
                val mLower = muscleName.lowercase()
                targetMuscleKeys.any { key -> mLower.contains(key) || key.contains(mLower) }
            }

            if (matchingIntensities.isNotEmpty()) {
                focusMultiplier = 5.0  // baza za fokus ujemanje

                // Bonus glede na primarnost (P/S/T) IN intenziteto (1-10)
                val bestBonus = matchingIntensities.maxOf { (_, intensity) ->
                    val roleMultiplier = when (intensity.role) {
                        MuscleRole.PRIMARY   -> 3.0
                        MuscleRole.SECONDARY -> 1.5
                        MuscleRole.TERTIARY  -> 0.7
                        MuscleRole.NONE      -> 0.0
                    }
                    val levelBonus = intensity.level / 10.0  // 0.1 – 1.0
                    roleMultiplier * levelBonus
                }
                focusMultiplier += bestBonus  // max +3.0 pri P10
            }
        }
        score *= focusMultiplier

        // C. Cilj (goal)
        when (params.goal) {
            WorkoutGoal.WEIGHT_LOSS -> {
                score *= (exercise.caloriesPerKgPerHour / 5.0).coerceAtLeast(0.5)
            }
            WorkoutGoal.STRENGTH -> {
                if (exercise.category == "strength") score *= 1.2
            }
            WorkoutGoal.MUSCLE_GAIN -> {
                val maxPrimaryLevel = exercise.muscleIntensities.values
                    .filter { it.role == MuscleRole.PRIMARY }
                    .maxOfOrNull { it.level } ?: 0
                score *= (1.0 + maxPrimaryLevel * 0.03)
            }
            WorkoutGoal.ENDURANCE -> {
                score *= (exercise.caloriesPerKgPerHour / 7.0).coerceAtLeast(0.3)
            }
            WorkoutGoal.GENERAL_FITNESS -> { /* brez spremembe */ }
        }

        // D. Naključni jitter ±10% — ohranja raznolikost med ponovitvami
        score *= Random.nextDouble(0.9, 1.1)

        return score
    }

    /**
     * Weighted random (kolo sreče):
     * Vsaka vaja ima verjetnost izbire sorazmerno svojemu score-u.
     * Vaja z score 8.0 ima ~4x večjo verjetnost kot vaja s score 2.0,
     * ampak vaja s score 2.0 ima še vedno realno možnost → raznolikost.
     */
    private fun selectExercisesWeighted(
        scored: List<Pair<RefinedExercise, Double>>,
        count: Int
    ): List<RefinedExercise> {
        val selected = mutableListOf<RefinedExercise>()
        val pool = scored.toMutableList()

        android.util.Log.d("WorkoutGenerator", "Weighted selection from ${pool.size} exercises, need $count")

        repeat(count) {
            if (pool.isEmpty()) return@repeat

            // Seštej vse pozitivne score-e
            val totalScore = pool.sumOf { (_, s) -> s.coerceAtLeast(0.0) }
            if (totalScore <= 0.0) {
                // Fallback: uniform random
                val picked = pool.random()
                selected.add(picked.first)
                pool.remove(picked)
                return@repeat
            }

            // Zavrti kolo sreče
            var rand = Random.nextDouble(0.0, totalScore)
            var pickedPair = pool.last()  // fallback
            for (pair in pool) {
                rand -= pair.second.coerceAtLeast(0.0)
                if (rand <= 0.0) {
                    pickedPair = pair
                    break
                }
            }

            selected.add(pickedPair.first)
            pool.remove(pickedPair)
        }

        android.util.Log.d("WorkoutGenerator", "=== SELECTED (weighted) ===")
        selected.forEachIndexed { i, ex ->
            android.util.Log.d("WorkoutGenerator", "  ${i+1}. ${ex.name} muscles=${ex.muscleIntensities.keys}")
        }

        return selected
    }
}
