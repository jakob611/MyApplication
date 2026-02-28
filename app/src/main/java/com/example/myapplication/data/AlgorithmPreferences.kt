package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.domain.WorkoutGenerationParams
import com.example.myapplication.domain.WorkoutGoal

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
    fun initDifficultyForPlan(context: Context, experience: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Nastavi samo če še ni bila nastavljena ali je 0
        val current = prefs.getFloat(KEY_CURRENT_DIFFICULTY, 0f)
        if (current <= 0f) {
            prefs.edit().putFloat(KEY_CURRENT_DIFFICULTY, getBaseDifficultyForExperience(experience)).apply()
        }
    }

    // ─── Vrni trenutno težavnost (za generiranje workoutov) ──────────────
    fun getCurrentDifficulty(context: Context, experience: String? = null): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getFloat(KEY_CURRENT_DIFFICULTY, 0f)
        return if (stored > 0f) stored else getBaseDifficultyForExperience(experience)
    }

    // ─── Povečaj težavnost za 0.5 ob koncu tedna ─────────────────────────
    fun incrementWeeklyDifficulty(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getFloat(KEY_CURRENT_DIFFICULTY, 4.0f)
        val newVal = (current + 0.5f).coerceAtMost(10.0f)
        prefs.edit().putFloat(KEY_CURRENT_DIFFICULTY, newVal).apply()
    }

    // ─── Recovery mode ────────────────────────────────────────────────────
    fun startRecoveryMode(context: Context, missedDays: Int, planDayBeforeBreak: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDiff = prefs.getFloat(KEY_CURRENT_DIFFICULTY, 4.0f)
        prefs.edit()
            .putBoolean(KEY_IS_RECOVERY, true)
            .putFloat(KEY_RECOVERY_START_DIFF, currentDiff * 0.5f) // Začnemo na polovici težavnosti
            .putFloat(KEY_RECOVERY_TARGET_DIFF, currentDiff)       // Ciljamo nazaj na prejšnjo težavnost
            .putInt(KEY_RECOVERY_MISSED_DAYS, missedDays)
            .putInt(KEY_CATCHUP_DAYS_LEFT, missedDays)
            .putInt(KEY_CATCHUP_PLAN_DAY, planDayBeforeBreak)
            .apply()
    }

    fun isRecoveryMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_RECOVERY, false)
    }

    /** Vrni težavnost za recovery dan. Index je od 0 (prvi dan) do weeklyTarget-1 (zadnji dan).
     *  Linearno narašča od startDiff do targetDiff. */
    fun getRecoveryDayDifficulty(context: Context, dayIndex: Int, totalDays: Int): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startDiff = prefs.getFloat(KEY_RECOVERY_START_DIFF, 2.0f)
        val targetDiff = prefs.getFloat(KEY_RECOVERY_TARGET_DIFF, 4.0f)
        if (totalDays <= 1) return targetDiff
        val progress = dayIndex.toFloat() / (totalDays - 1).toFloat()
        return startDiff + (targetDiff - startDiff) * progress
    }

    fun endRecoveryMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_RECOVERY, false).apply()
    }

    /** Vrne koliko catch-up dni je še ostalo in od katerega planDay-a */
    fun getCatchupState(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Pair(
            prefs.getInt(KEY_CATCHUP_DAYS_LEFT, 0),
            prefs.getInt(KEY_CATCHUP_PLAN_DAY, 1)
        )
    }

    fun decrementCatchupDay(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val left = prefs.getInt(KEY_CATCHUP_DAYS_LEFT, 0)
        prefs.edit().putInt(KEY_CATCHUP_DAYS_LEFT, (left - 1).coerceAtLeast(0)).apply()
    }

    // ─── Legacy params save/load ──────────────────────────────────────────
    fun saveParams(context: Context, params: WorkoutGenerationParams) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(KEY_LEVEL, params.userExperienceLevel.toFloat())
            putString(KEY_GOAL, params.goal.name)
            putStringSet(KEY_FOCUS, params.focusAreas)
            putStringSet(KEY_EQUIPMENT, params.availableEquipment)
            apply()
        }
    }

    fun loadParams(context: Context): WorkoutGenerationParams {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val baseLevel = prefs.getFloat(KEY_LEVEL, DEFAULT_LEVEL)
        val offset = prefs.getFloat(KEY_LEVEL_OFFSET, 0f)
        val finalLevel = (baseLevel + offset).coerceIn(1f, 10f).toInt()

        val goalStr = prefs.getString(KEY_GOAL, DEFAULT_GOAL) ?: DEFAULT_GOAL
        val goal = try { WorkoutGoal.valueOf(goalStr) } catch (_: Exception) { WorkoutGoal.GENERAL_FITNESS }

        val focus = prefs.getStringSet(KEY_FOCUS, DEFAULT_FOCUS) ?: DEFAULT_FOCUS
        val equipment = prefs.getStringSet(KEY_EQUIPMENT, DEFAULT_EQUIPMENT) ?: DEFAULT_EQUIPMENT
        val currentDiff = prefs.getFloat(KEY_CURRENT_DIFFICULTY, finalLevel.toFloat())

        return WorkoutGenerationParams(
            userExperienceLevel = finalLevel,
            targetDifficultyLevel = currentDiff,
            goal = goal,
            focusAreas = focus,
            availableEquipment = equipment
        )
    }

    // ─── User feedback ────────────────────────────────────────────────────
    fun saveGlobalFeedback(context: Context, adjustment: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentOffset = prefs.getFloat(KEY_LEVEL_OFFSET, 0f)
        val newOffset = (currentOffset + (adjustment * 0.5f)).coerceIn(-3f, 3f)
        prefs.edit().putFloat(KEY_LEVEL_OFFSET, newOffset).apply()
    }

    fun saveExerciseFeedback(context: Context, exerciseName: String, difficulty: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "ex_mult_${exerciseName.hashCode()}"
        val currentMult = prefs.getFloat(key, 1.0f)
        val newMult = when (difficulty) {
            0 -> currentMult * 1.1f
            2 -> currentMult * 0.9f
            else -> currentMult
        }.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(key, newMult).apply()
    }

    fun getExerciseMultiplier(context: Context, exerciseName: String): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("ex_mult_${exerciseName.hashCode()}", 1.0f)
    }

    fun saveExerciseMultiplier(context: Context, exerciseName: String, multiplier: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("ex_mult_${exerciseName.hashCode()}", multiplier.coerceIn(0.5f, 2.0f)).apply()
    }
}
