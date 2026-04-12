package com.example.myapplication.data

import com.example.myapplication.domain.WorkoutGenerationParams
import com.example.myapplication.domain.WorkoutGoal
import com.russhwolf.settings.Settings

object AlgorithmPreferences {
    private const val PREFS_NAME = "algorithm_prefs"
    private const val KEY_LEVEL = "user_level"
    private const val KEY_GOAL = "user_goal"
    private const val KEY_FOCUS = "user_focus"
    private const val KEY_EQUIPMENT = "user_equipment"
    private const val KEY_LEVEL_OFFSET = "level_offset"

    // Naraščajoča težavnost — float, raste za 0.5 vsak teden
    private const val KEY_CURRENT_DIFFICULTY = "current_difficulty_level"

    // Recovery mode
    private const val KEY_IS_RECOVERY = "is_recovery_week"
    private const val KEY_RECOVERY_START_DIFF = "recovery_start_difficulty"
    private const val KEY_RECOVERY_TARGET_DIFF = "recovery_target_difficulty"
    private const val KEY_RECOVERY_MISSED_DAYS = "recovery_missed_days"
    private const val KEY_CATCHUP_DAYS_LEFT = "catchup_days_left"
    private const val KEY_CATCHUP_PLAN_DAY = "catchup_from_plan_day" // kateri planDay nadoknaditi

    private val DEFAULT_LEVEL = 5f
    private val DEFAULT_GOAL = WorkoutGoal.GENERAL_FITNESS.name
    private val DEFAULT_FOCUS = setOf("Full Body")
    private val DEFAULT_EQUIPMENT = setOf("bodyweight")

    // ─── Začetna težavnost glede na experience ────────────────────────────
    fun getBaseDifficultyForExperience(experience: String?): Float = when (experience) {
        "Beginner"     -> 4.0f
        "Intermediate" -> 7.0f
        "Advanced"     -> 9.0f
        else           -> 4.0f
    }

    // ─── Nastavi začetno težavnost (pokliči ko se naredi plan) ───────────
    fun initDifficultyForPlan(settings: Settings, experience: String?) {
        // Nastavi samo če še ni bila nastavljena ali je 0
        val current = settings.getFloat(KEY_CURRENT_DIFFICULTY, 0f)
        if (current <= 0f) {
            settings.putFloat(KEY_CURRENT_DIFFICULTY, getBaseDifficultyForExperience(experience))
        }
    }

    // ─── Vrni trenutno težavnost (za generiranje workoutov) ──────────────
    fun getCurrentDifficulty(settings: Settings, experience: String? = null): Float {
        val stored = settings.getFloat(KEY_CURRENT_DIFFICULTY, 0f)
        return if (stored > 0f) stored else getBaseDifficultyForExperience(experience)
    }

    // ─── Povečaj težavnost za 0.5 ob koncu tedna ─────────────────────────
    fun incrementWeeklyDifficulty(settings: Settings) {
        val current = settings.getFloat(KEY_CURRENT_DIFFICULTY, 4.0f)
        val newVal = (current + 0.5f).coerceAtMost(10.0f)
        settings.putFloat(KEY_CURRENT_DIFFICULTY, newVal)
    }

    // ─── Recovery mode ────────────────────────────────────────────────────
    fun startRecoveryMode(settings: Settings, missedDays: Int, planDayBeforeBreak: Int) {
        val currentDiff = settings.getFloat(KEY_CURRENT_DIFFICULTY, 4.0f)
        settings.putBoolean(KEY_IS_RECOVERY, true)
        settings.putFloat(KEY_RECOVERY_START_DIFF, currentDiff * 0.5f) // Začnemo na polovici težavnosti
        settings.putFloat(KEY_RECOVERY_TARGET_DIFF, currentDiff)       // Ciljamo nazaj na prejšnjo težavnost
        settings.putInt(KEY_RECOVERY_MISSED_DAYS, missedDays)
        settings.putInt(KEY_CATCHUP_DAYS_LEFT, missedDays)
        settings.putInt(KEY_CATCHUP_PLAN_DAY, planDayBeforeBreak)
    }

    fun isRecoveryMode(settings: Settings): Boolean {
        return settings.getBoolean(KEY_IS_RECOVERY, false)
    }

    /** Vrni težavnost za recovery dan. Index je od 0 (prvi dan) do weeklyTarget-1 (zadnji dan).
     *  Linearno narašča od startDiff do targetDiff. */
    fun getRecoveryDayDifficulty(settings: Settings, dayIndex: Int, totalDays: Int): Float {
        val startDiff = settings.getFloat(KEY_RECOVERY_START_DIFF, 2.0f)
        val targetDiff = settings.getFloat(KEY_RECOVERY_TARGET_DIFF, 4.0f)
        if (totalDays <= 1) return targetDiff
        val progress = dayIndex.toFloat() / (totalDays - 1).toFloat()
        return startDiff + (targetDiff - startDiff) * progress
    }

    fun endRecoveryMode(settings: Settings) {
        settings.putBoolean(KEY_IS_RECOVERY, false)
    }

    /** Vrne koliko catch-up dni je še ostalo in od katerega planDay-a */
    fun getCatchupState(settings: Settings): Pair<Int, Int> {
        return Pair(
            settings.getInt(KEY_CATCHUP_DAYS_LEFT, 0),
            settings.getInt(KEY_CATCHUP_PLAN_DAY, 1)
        )
    }

    fun decrementCatchupDay(settings: Settings) {
        val left = settings.getInt(KEY_CATCHUP_DAYS_LEFT, 0)
        settings.putInt(KEY_CATCHUP_DAYS_LEFT, (left - 1).coerceAtLeast(0))
    }

    // ─── Legacy params save/load ──────────────────────────────────────────
    fun saveParams(settings: Settings, params: WorkoutGenerationParams) {
        settings.putFloat(KEY_LEVEL, params.userExperienceLevel.toFloat())
        settings.putString(KEY_GOAL, params.goal.name)
        // Multiplatform settings doesn't support String Set directly, fallback to comma separated
        settings.putString(KEY_FOCUS, params.focusAreas.joinToString(","))
        settings.putString(KEY_EQUIPMENT, params.availableEquipment.joinToString(","))
    }

    fun loadParams(settings: Settings): WorkoutGenerationParams {
        val baseLevel = settings.getFloat(KEY_LEVEL, DEFAULT_LEVEL)
        val offset = settings.getFloat(KEY_LEVEL_OFFSET, 0f)
        val finalLevel = (baseLevel + offset).coerceIn(1f, 10f).toInt()

        val goalStr = settings.getString(KEY_GOAL, DEFAULT_GOAL)
        val goal = try { WorkoutGoal.valueOf(goalStr) } catch (_: Exception) { WorkoutGoal.GENERAL_FITNESS }

        val focusStr = settings.getString(KEY_FOCUS, DEFAULT_FOCUS.joinToString(","))
        val focus = if (focusStr.isBlank()) DEFAULT_FOCUS else focusStr.split(",").map { it.trim() }.toSet()

        val equipmentStr = settings.getString(KEY_EQUIPMENT, DEFAULT_EQUIPMENT.joinToString(","))
        val equipment = if (equipmentStr.isBlank()) DEFAULT_EQUIPMENT else equipmentStr.split(",").map { it.trim() }.toSet()

        val currentDiff = settings.getFloat(KEY_CURRENT_DIFFICULTY, finalLevel.toFloat())

        return WorkoutGenerationParams(
            userExperienceLevel = finalLevel,
            targetDifficultyLevel = currentDiff,
            goal = goal,
            focusAreas = focus,
            availableEquipment = equipment
        )
    }

    // ─── User feedback ────────────────────────────────────────────────────
    fun saveGlobalFeedback(settings: Settings, adjustment: Int) {
        val currentOffset = settings.getFloat(KEY_LEVEL_OFFSET, 0f)
        val newOffset = (currentOffset + (adjustment * 0.5f)).coerceIn(-3f, 3f)
        settings.putFloat(KEY_LEVEL_OFFSET, newOffset)
    }

    fun saveExerciseFeedback(settings: Settings, exerciseName: String, difficulty: Int) {
        val key = "ex_mult_${exerciseName.hashCode()}"
        val currentMult = settings.getFloat(key, 1.0f)
        val newMult = when (difficulty) {
            0 -> currentMult * 1.1f
            2 -> currentMult * 0.9f
            else -> currentMult
        }.coerceIn(0.5f, 2.0f)
        settings.putFloat(key, newMult)
    }

    fun getExerciseMultiplier(settings: Settings, exerciseName: String): Float {
        return settings.getFloat("ex_mult_${exerciseName.hashCode()}", 1.0f)
    }

    fun saveExerciseMultiplier(settings: Settings, exerciseName: String, multiplier: Float) {
        settings.putFloat("ex_mult_${exerciseName.hashCode()}", multiplier.coerceIn(0.5f, 2.0f))
    }
}
