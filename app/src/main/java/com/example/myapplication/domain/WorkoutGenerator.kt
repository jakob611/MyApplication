package com.example.myapplication.domain

import com.example.myapplication.data.AdvancedExerciseRepository
import com.example.myapplication.data.RefinedExercise
import com.example.myapplication.data.MuscleRole
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import java.time.LocalDate

data class WorkoutGenerationParams(
    val userExperienceLevel: Int,
    val targetDifficultyLevel: Float = userExperienceLevel.toFloat(),
    val availableEquipment: Set<String>,
    val goal: WorkoutGoal,
    val focusAreas: Set<String>,
    val exerciseCount: Int = 10,
    val durationMinutes: Int = 45,
    /** Spol uporabnika ("male" / "female" / ""). Vpliva na score bonuse v calculateScore(). */
    val gender: String = "",
    /** Trenutni dan plana — uporablja se za izračun deterministične naključnosti (seed). */
    val planDay: Int = 1
)

/**
 * Zapis zadnje izvedene vaje iz Firestore (za progresijo volumna).
 * @param exerciseName Ime vaje (case-insensitive primerjava)
 * @param reps Ponovitve zadnje seje
 * @param sets Seti zadnje seje
 * @param weightKg Teža (0f = samo telesna teža)
 */
data class LastExerciseRecord(
    val exerciseName: String,
    val reps: Int,
    val sets: Int,
    val weightKg: Float = 0f
)

enum class WorkoutGoal {
    MUSCLE_GAIN, WEIGHT_LOSS, STRENGTH, ENDURANCE, GENERAL_FITNESS
}

class WorkoutGenerator {

    // ─────────────────────────────────────────────────────────────────────────
    // Faza 17: Mišični ključi za klasifikacijo zgornjega/spodnjega dela telesa.
    // Uporablja se v resolveWeightIncrement() za določitev primernega inkrementa.
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        /** Spodnji del telesa → večji inkrement (+5.0 kg) */
        private val LOWER_BODY_KEYWORDS = setOf(
            "leg", "quad", "hamstring", "glute", "calf", "calves", "hip", "adductor", "tibial"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DETERMINISTIČNA NAKLJUČNOST
    // Seed = (danes kot EpochDay) * 1000 + planDay
    // Rezultat: isti nabor vaj cel dan, ne glede na to koliko krat generiramo.
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildRandom(planDay: Int): Random {
        val epochDay = LocalDate.now().toEpochDay()
        val seed = epochDay * 1000L + planDay.toLong()
        android.util.Log.d("WorkoutGenerator", "Deterministic seed=$seed (epochDay=$epochDay, planDay=$planDay)")
        return Random(seed)
    }

    /**
     * Mapa: fokus iz kviza → seznam ključev mišic točno kot so v exercises.json.
     * UPDATED to English keys (2026-03-15) because exercises.json was replaced with English version.
     * Keys in JSON are e.g. "muscle_intensity_Abs / Core", "muscle_intensity_Legs – Quads".
     * Returns lowercase keys for comparison.
     */
    private fun focusToMuscleKeys(focus: String): List<String> = when (focus.lowercase().trim()) {
        // Kviz fokusi (English categories)
        "upper body"  -> listOf("chest", "back", "shoulders", "biceps", "triceps", "forearms (front)")
        "lower body"  -> listOf("legs – quads", "legs – hamstrings", "glutes", "calves")
        "core"        -> listOf("abs / core")
        "cardio"      -> listOf("cardio")
        "flexibility" -> listOf("stretching")

        // GenerateWorkoutScreen fokusi (English target areas)
        "legs"        -> listOf("legs – quads", "legs – hamstrings", "glutes", "calves")
        "arms"        -> listOf("biceps", "triceps", "forearms (front)")
        "chest"       -> listOf("chest")
        "back"        -> listOf("back")
        "abs"         -> listOf("abs / core")
        "shoulders"   -> listOf("shoulders")

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
        val rng = buildRandom(params.planDay)

        android.util.Log.d("WorkoutGenerator",
            "Generating: focusAreas=${params.focusAreas}, equipment=${params.availableEquipment}, goal=${params.goal}, difficulty=${params.targetDifficultyLevel}, gender=${params.gender}")

        // 1. Filter po opremi (znatno razširjen z fallback opcijami in aliasi)
        val userEquipment = params.availableEquipment.map { it.trim().lowercase() }.toSet()

        var availableExercises = allExercises.filter { exercise ->
            val requiredList = exercise.equipment.split(",").map { it.trim().lowercase() }
            requiredList.all { req ->
                if (req == "bodyweight" || req == "none" || req.isBlank() || req == "body" || req == "body weight" || req == "assisted") return@all true

                userEquipment.any { userEq ->
                    // Direkten match ali startsWith
                    userEq == req || userEq.startsWith(req) || req.startsWith(userEq) ||
                    // Aliases
                    (userEq.contains("dumbbell") && req.contains("dumbbell")) ||
                    (userEq.contains("kettlebell") && req.contains("kettlebell")) ||
                    (userEq.contains("barbell") && req.contains("barbell")) ||
                    (userEq.contains("band") && req.contains("band")) ||
                    (userEq.contains("machine") && req.contains("machine")) ||
                    (userEq.contains("cable") && (req.contains("cable") || req.contains("machine"))) ||
                    (userEq.contains("bench") && req.contains("bench")) ||
                    (userEq.contains("plate") && req.contains("plate")) ||
                    (userEq.contains("ball") && (req.contains("medicine ball") || req.contains("bosu ball") || req.contains("ball")))
                }
            }
        }

        if (availableExercises.isEmpty()) {
            android.util.Log.w("WorkoutGenerator", "No exercises match equipment ${params.availableEquipment}, falling back to bodyweight.")
            // Fallback na bodyweight vaje
            availableExercises = allExercises.filter { exercise ->
                val requiredList = exercise.equipment.split(",").map { it.trim().lowercase() }
                requiredList.all { req ->
                    req == "bodyweight" || req == "none" || req.isBlank() || req == "body" || req == "body weight"
                }
            }
        }

        android.util.Log.d("WorkoutGenerator",
            "After equipment filter: ${availableExercises.size}/${allExercises.size} exercises")

        if (availableExercises.isEmpty()) return emptyList()

        // 2. Izračunaj score za vsako vajo
        val scoredExercises = availableExercises.map { exercise ->
            exercise to calculateScore(exercise, params, rng)
        }

        // Ohrani samo vaje s score > 0 (fokus ujemanje)
        // NIKOLI ne vzemi vaj s score 0 — to pomeni da so izven fokusa
        val focusMatchExercises = scoredExercises.filter { it.second > 0.0 }

        // Če ni nobene vaje v fokusu (npr. fokus ključi ne ujemajo exercises.json),
        // logiraj napako in vzemi vaje s katerokoli score > 0
        val pool = if (focusMatchExercises.isNotEmpty()) {
            // Vzemi samo tiste z score > 1.0 (dobro ujemanje), ali vse s score > 0 kot fallback
            val goodMatches = focusMatchExercises.filter { it.second > 1.0 }
            if (goodMatches.isNotEmpty()) goodMatches else focusMatchExercises
        } else {
            // Absolutni fallback: če izbrana bodyweight fallback opcija ali originalna nima pravih mišic
            android.util.Log.w("WorkoutGenerator",
                "WARNING: No exercises matched focus ${params.focusAreas} — falling back to all available exercises")
            scoredExercises.filter { it.second > 0.0 }.ifEmpty { scoredExercises }
        }

        // Log top 5
        pool.sortedByDescending { it.second }.take(5).forEach { (ex, score) ->
            android.util.Log.d("WorkoutGenerator",
                "  TOP: ${ex.name} score=${"%.2f".format(score)} diff=${ex.difficulty} muscles=${ex.muscleIntensities.keys}")
        }

        val count = params.exerciseCount.coerceAtMost(15)

        val selected = selectExercisesWeighted(pool, count, rng)

        // 3. APPLY PROGRESSION (Intensity & Level scaling)
        return selected.map { exercise ->
            applyProgression(exercise, params)
        }
    }

    private fun applyProgression(exercise: RefinedExercise, params: WorkoutGenerationParams): RefinedExercise {
        // Base values from model
        var sets = exercise.parsedSets.coerceAtLeast(1)
        var reps = exercise.parsedReps.coerceAtLeast(1)

        // Compute float delta between target desired difficulty and the base exercise difficulty
        val difficultyDelta = params.targetDifficultyLevel - exercise.difficulty

        if (difficultyDelta > 0) {
            // Uporabnik je na višjem nivoju kot vaja -> povečaj volumen

            // Reps: Dodamo približno 1-3 reps na stopnjo razlike (max +8 reps)
            val addedReps = (difficultyDelta * 1.5f).toInt().coerceIn(0, 8)
            reps += addedReps

            // Sets: Dodamo 1 set če je razlika med 3.0 in 6.0, ali 2 seta če je > 6.0
            if (difficultyDelta >= 3.0f && sets < 5) sets += 1
            if (difficultyDelta >= 6.0f && sets < 6) sets += 1

            // Max limits based on goals
            val (maxSets, maxReps) = when(params.goal) {
                WorkoutGoal.STRENGTH -> Pair(6, 12)
                WorkoutGoal.MUSCLE_GAIN -> Pair(5, 15)
                WorkoutGoal.ENDURANCE -> Pair(4, 30)
                WorkoutGoal.WEIGHT_LOSS -> Pair(5, 20)
                WorkoutGoal.GENERAL_FITNESS -> Pair(4, 15)
            }

            if (reps > maxReps) {
                // Če smo prekoračili reps limit, poskusimo dodati 1 set na račun volumnske pretvorbe (če dovoljeno)
                if (sets < maxSets) sets += 1
                reps = maxReps
            }

            sets = sets.coerceAtMost(maxSets)
        } else if (difficultyDelta < -2.0f) {
            // Uporabnik je nižjega nivoja kot zahteva vaja (zelo težka vaja)
            // Znižaj volumen
            reps = (reps - 2).coerceAtLeast(1)
            if (sets > 2 && difficultyDelta < -4.0f) sets -= 1
        }

        // Formiramo nov display string za reps
        val repsDisplay = if (reps == exercise.parsedReps) {
            exercise.repsDisplay // Ohranimo original če nespremenjen (npr. "10-12")
        } else {
            reps.toString()
        }

        return exercise.copy(
            parsedSets = sets,
            parsedReps = reps,
            repsDisplay = repsDisplay
        )
    }

    private fun calculateScore(exercise: RefinedExercise, params: WorkoutGenerationParams, rng: Random): Double {
        var score = 10.0

        // A. Ujemanje težavnosti — bliže targetDifficulty = višji score
        val diffDelta = abs(params.targetDifficultyLevel - exercise.difficulty)
        val diffMultiplier = (1.0 - (diffDelta * 0.1)).coerceAtLeast(0.1)
        score *= diffMultiplier

        // B. Fokus ujemanje — s pravilnim mapiranjem na JSON ključe
        var focusMultiplier = 0.0  // default: vaja NI v fokusu → score 0 → ne bo izbrana

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

        // D. GENDER BONUS
        // - female: spodnji del telesa (glutes, legs) → +15 % base score
        // - male: zgornji del telesa (chest, shoulders) → +10 % base score
        if (params.gender.isNotBlank()) {
            val lowerBodyKeys = setOf("glute", "leg", "quad", "hamstring", "calf", "calves")
            val upperBodyKeys = setOf("chest", "shoulder", "pectoral")

            val muscleNames = exercise.muscleIntensities.keys.map { it.lowercase() }

            when (params.gender.lowercase()) {
                "female" -> {
                    val hasLowerBody = muscleNames.any { m -> lowerBodyKeys.any { k -> m.contains(k) } }
                    if (hasLowerBody) {
                        score *= 1.15
                        android.util.Log.d("WorkoutGenerator", "  GENDER female bonus +15%: ${exercise.name}")
                    }
                }
                "male" -> {
                    val hasUpperBody = muscleNames.any { m -> upperBodyKeys.any { k -> m.contains(k) } }
                    if (hasUpperBody) {
                        score *= 1.10
                        android.util.Log.d("WorkoutGenerator", "  GENDER male bonus +10%: ${exercise.name}")
                    }
                }
            }
        }

        // E. Deterministični jitter ±10% — ohranja raznolikost, a je ponovljiv (isti RNG za isti dan/planDay)
        score *= rng.nextDouble(0.9, 1.1)

        return score
    }

    /**
     * Faza 17: Weighted roulette wheel z STATEFUL diversity penaltiji.
     *
     * Za vsako iteracijo:
     *  1. Vzame pre-izračunane base score-e (deterministični jitter že vključen).
     *  2. Pomnoži vsak score z getDiversityMultiplier() → penalizira ponavljanje
     *     istih PRIMARY mišičnih skupin.
     *  3. Zavrti kolo sreče in izbere vajo.
     *  4. Posodobi selectedPrimaryMuscles tracker.
     *
     * Penalty lestvica (na PRIMARY mišico):
     *   1. pojavitev → ×1.0  (brez penaltija)
     *   2. pojavitev → ×0.5  (50 % manj verjetna)
     *   3. pojavitev → ×0.2  (80 % manj verjetna)
     *   4.+ pojavitev → ×0.1 (90 % manj verjetna — ni blokirana, da ohranimo raznolikost)
     *
     * Primer: Po chest vaji dobi vsaka naslednja chest vaja 50 % manjšo verjetnost →
     *   naravno se premakne na shoulders ali back.
     *
     * @param scored Pre-izračunani base score-i (vključno z jitterjem).
     * @param count  Število vaj, ki jih izberemo.
     * @param rng    Deterministični Random (isti seed za isti dan/planDay).
     */
    private fun selectExercisesWeighted(
        scored: List<Pair<RefinedExercise, Double>>,
        count: Int,
        rng: Random
    ): List<RefinedExercise> {
        val selected = mutableListOf<RefinedExercise>()
        val pool = scored.toMutableList()

        // Faza 17: Sledimo koliko krat je bila katera PRIMARY mišica že izbrana danes.
        // Key = lowercase mišično ime (npr. "chest", "legs – quads")
        val selectedPrimaryMuscles = mutableMapOf<String, Int>()

        android.util.Log.d("WorkoutGenerator", "Weighted selection from ${pool.size} exercises, need $count")

        repeat(count) {
            if (pool.isEmpty()) return@repeat

            // Izračunaj efektivni score za vsako vajo v aktualnem pool-u.
            // Base score je stabilen (jitter je že vključen), diversity multiplier
            // se posodablja glede na aktualno stanje selectedPrimaryMuscles.
            val totalEffectiveScore = pool.sumOf { (ex, baseScore) ->
                (baseScore * getDiversityMultiplier(ex, selectedPrimaryMuscles)).coerceAtLeast(0.0)
            }

            if (totalEffectiveScore <= 0.0) {
                android.util.Log.e("WorkoutGenerator",
                    "ERROR: totalEffectiveScore=0 in pool of ${pool.size} — skipping")
                return@repeat
            }

            // Zavrti kolo sreče z diversity-penaliziranimi score-i
            var rand = rng.nextDouble(0.0, totalEffectiveScore)
            var pickedIndex = pool.size - 1  // fallback: zadnja vaja
            for (i in pool.indices) {
                val (ex, baseScore) = pool[i]
                rand -= (baseScore * getDiversityMultiplier(ex, selectedPrimaryMuscles)).coerceAtLeast(0.0)
                if (rand <= 0.0) { pickedIndex = i; break }
            }

            val pickedExercise = pool[pickedIndex].first
            selected.add(pickedExercise)
            pool.removeAt(pickedIndex)  // O(N) shift, sprejemljivo za pool ~20–100

            // Posodobi diversity tracker: beleži vsako PRIMARY mišico izbrane vaje
            val primaryKeys = if (pickedExercise.muscleIntensities.isNotEmpty()) {
                pickedExercise.muscleIntensities.entries
                    .filter { it.value.role == MuscleRole.PRIMARY }
                    .map { it.key.lowercase() }
            } else if (pickedExercise.primaryMuscle.isNotBlank()) {
                listOf(pickedExercise.primaryMuscle.lowercase())
            } else emptyList()

            primaryKeys.forEach { key ->
                selectedPrimaryMuscles[key] = (selectedPrimaryMuscles[key] ?: 0) + 1
            }

            android.util.Log.d("WorkoutGenerator",
                "  PICKED: ${pickedExercise.name} | diversity state: $selectedPrimaryMuscles")
        }

        android.util.Log.d("WorkoutGenerator", "=== SELECTED (weighted + diversity) ===")
        selected.forEachIndexed { i, ex ->
            android.util.Log.d("WorkoutGenerator",
                "  ${i+1}. ${ex.name} muscles=${ex.muscleIntensities.keys}")
        }

        return selected
    }

    /**
     * Faza 17: Diversity penalty multiplier.
     *
     * Vrne faktor, ki se pomnoži z base score-om vaje glede na to,
     * kolikokrat je njena PRIMARY mišica bila že izbrana v tej seji.
     *
     * @param exercise             Kandidatna vaja.
     * @param selectedPrimaryMuscles Mapa: lowercase mišično ime → število izbir do sedaj.
     */
    private fun getDiversityMultiplier(
        exercise: RefinedExercise,
        selectedPrimaryMuscles: Map<String, Int>
    ): Double {
        if (selectedPrimaryMuscles.isEmpty()) return 1.0

        // Določi primary muscle ključe — preferiramo muscleIntensities (richer data),
        // fallback na primaryMuscle string polje
        val primaryKeys: List<String> = if (exercise.muscleIntensities.isNotEmpty()) {
            exercise.muscleIntensities.entries
                .filter { it.value.role == MuscleRole.PRIMARY }
                .map { it.key.lowercase() }
        } else if (exercise.primaryMuscle.isNotBlank()) {
            listOf(exercise.primaryMuscle.lowercase())
        } else {
            return 1.0  // Neznane mišice → brez penaltija
        }

        // Najdi maksimalno število izbir katere koli primary mišice te vaje
        val maxSelectCount = primaryKeys.maxOf { selectedPrimaryMuscles.getOrDefault(it, 0) }

        return when (maxSelectCount) {
            0    -> 1.0   // Prva izbira te mišice — brez penaltija
            1    -> 0.5   // Drugi hit — 50 % manj verjetna
            2    -> 0.2   // Tretji hit — 80 % manj verjetna
            else -> 0.1   // 4.+ hit — 90 % manj verjetna (ni blokirana)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VOLUME PROGRESSION — The Memory Bridge (Faza 17: True Progressive Overload)
    //
    // Hierarhija napredka (v prednosti):
    //   1. REPS FIRST: povečaj ponovitve za +5 % (min +1) do varnostnega stropa 1.5×
    //   2. WEIGHT SHIFT: ko so reps na stropu, povečaj targetWeightKg:
    //        - Spodnji del telesa (noge, glutes):  +5.0 kg na 0.5 kg zaokroženo
    //        - Zgornji del telesa (prsni, hrbet…): +2.5 kg na 0.5 kg zaokroženo
    //        - Telesna teža (weightKg == 0f):      samo ohranjamo rep cap
    //
    // Razlika od prejšnje verzije:
    //   PREJ: reps+5% IN weight+5% hkrati — vedno oboje
    //   ZDAJ: reps DOKLER je prostor, nato weight — nikoli oboje hkrati
    // ─────────────────────────────────────────────────────────────────────────
    fun applyVolumeProgression(
        exercises: List<RefinedExercise>,
        lastSession: List<LastExerciseRecord>?
    ): List<RefinedExercise> {
        if (lastSession.isNullOrEmpty()) {
            android.util.Log.d("WorkoutGenerator", "applyVolumeProgression: ni podatkov iz zadnje seje — preskakujem")
            return exercises
        }

        // O(1) lookup po lowercase imenu — gradimo enkrat, iščemo N-krat
        val lastSessionIndex: Map<String, LastExerciseRecord> =
            lastSession.associateBy { it.exerciseName.trim().lowercase() }

        return exercises.map { exercise ->
            val key = exercise.name.trim().lowercase()
            val last = lastSessionIndex[key]
                ?: return@map exercise  // vaja ni bila v zadnji seji → brez spremembe

            // ── Varnostni strop: ne presegi 1.5× originalnih parsedReps ─────────
            val repsCap = (exercise.parsedReps * 1.5f).roundToInt()

            // +5 % reps, zaokroženo navzgor, min +1
            val proposedReps = (last.reps * 1.05f).roundToInt()
                .coerceAtLeast(last.reps + 1)

            if (proposedReps <= repsCap) {
                // ── Koridor 1: Reps progresija (prostor še obstaja) ───────────────
                android.util.Log.d("WorkoutGenerator",
                    "  Progression[REPS] ${exercise.name}: ${last.reps}→$proposedReps reps")
                exercise.copy(
                    parsedReps  = proposedReps,
                    repsDisplay = proposedReps.toString(),
                    // Ohrani obstoječo težo brez spremembe (ne zwibujo oboje hkrati)
                    targetWeightKg = if (last.weightKg > 0f) last.weightKg.toDouble() else null
                )
            } else {
                // ── Koridor 2: Weight progresija (reps jsou na stropu) ───────────
                val lastWeightKg = last.weightKg.toDouble()
                val newTargetWeight: Double? = when {
                    lastWeightKg <= 0.0 -> null  // Telesna teža → ni weight progression
                    else -> {
                        val increment = resolveWeightIncrement(exercise)
                        val raw = lastWeightKg + increment
                        roundToNearestHalf(raw)  // zaokroži na 0.5 kg (standard plošče)
                    }
                }

                android.util.Log.d("WorkoutGenerator",
                    "  Progression[WEIGHT] ${exercise.name}: reps capped@$repsCap, " +
                    "weight ${last.weightKg} kg → $newTargetWeight kg " +
                    "(+${resolveWeightIncrement(exercise)} kg)")

                exercise.copy(
                    parsedReps     = repsCap,
                    repsDisplay    = repsCap.toString(),
                    targetWeightKg = newTargetWeight
                )
            }
        }
    }

    /**
     * Faza 17: Določi standardni weight increment glede na tip vaje.
     *
     * Logika:
     *   - Spodnji del telesa (quad, glute, hamstring…): +5.0 kg
     *     (večje mišice, večje plošče, hitrejša adaptacija)
     *   - Vse ostalo (zgornji del, core, cardio):       +2.5 kg
     *     (manjše mišice, standardni dumbbell/barbell korak)
     *
     * Podatkovni vir: muscleIntensities (PRIMARY mišice) — isti ključi kot v
     * focusToMuscleKeys() in calculateScore().
     */
    private fun resolveWeightIncrement(exercise: RefinedExercise): Double {
        val primaryMuscles: List<String> = if (exercise.muscleIntensities.isNotEmpty()) {
            exercise.muscleIntensities.entries
                .filter { it.value.role == MuscleRole.PRIMARY }
                .map { it.key.lowercase() }
        } else if (exercise.primaryMuscle.isNotBlank()) {
            listOf(exercise.primaryMuscle.lowercase())
        } else {
            return 2.5  // konservativen fallback za neznane vaje
        }

        val isLowerBody = primaryMuscles.any { muscle ->
            LOWER_BODY_KEYWORDS.any { keyword -> muscle.contains(keyword) }
        }
        return if (isLowerBody) 5.0 else 2.5
    }

    /**
     * Zaokroži double vrednost na najbližnih 0.5 kg — standard Olympic barbell
     * plošče in večina dumbbell setov.
     *
     * Primeri: 22.3 → 22.5 | 22.7 → 23.0 | 100.1 → 100.0
     */
    private fun roundToNearestHalf(kg: Double): Double =
        kotlin.math.round(kg * 2.0) / 2.0
}
