package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

object AdvancedExerciseRepository {
    private var exercises: List<RefinedExercise> = emptyList()
    private var availableEquipment: Set<String> = emptySet()

    // Muscle name mapping if needed (e.g. "Trebuh / Core" -> "Abs")
    // For now we use the keys directly.

    fun init(context: Context) {
        if (exercises.isNotEmpty()) return // Already loaded

        try {
            // Updated to use the new standard JSON file
            val inputStream = context.assets.open("exercises.json")
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            reader.close()

            val jsonArray = JSONArray(jsonString)
            val parsedList = mutableListOf<RefinedExercise>()
            val equipmentSet = mutableSetOf<String>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                parsedList.add(parseExercise(obj))

                // Add equipment
                val eqRaw = obj.optString("equipment", "bodyweight").lowercase()
                eqRaw.split(",").map { it.trim() }.forEach {
                    if (it.isNotEmpty()) equipmentSet.add(it)
                }
            }

            exercises = parsedList
            availableEquipment = equipmentSet.toSortedSet()

            android.util.Log.d("AdvancedExerciseRepo", "Loaded ${exercises.size} exercises and ${availableEquipment.size} equipment types.")
        } catch (e: Exception) {
            android.util.Log.e("AdvancedExerciseRepo", "Error loading exercises: ${e.message}")
        }
    }

    private fun parseExercise(obj: JSONObject): RefinedExercise {
        val muscleIntensities = mutableMapOf<String, MuscleIntensity>()

        // Iterate all keys to find muscle_intensity_*
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith("muscle_intensity_")) {
                val value = obj.optString(key, "")
                if (value.isNotEmpty()) {
                    val muscleName = key.removePrefix("muscle_intensity_").trim()
                    // Parse "P9", "S2" etc.
                    val roleChar = value.firstOrNull()?.uppercaseChar()
                    val levelChar = value.drop(1).toIntOrNull() ?: 1 // Default to 1 if parsing fails

                    val role = when (roleChar) {
                        'P' -> MuscleRole.PRIMARY
                        'S' -> MuscleRole.SECONDARY
                        'T' -> MuscleRole.TERTIARY
                        else -> MuscleRole.NONE
                    }

                    if (role != MuscleRole.NONE) {
                        muscleIntensities[muscleName] = MuscleIntensity(muscleName, role, levelChar)
                    }
                }
            }
        }

        // Parse list strings
        val secMuscles = parseJsonStringArray(obj.optJSONArray("secondary_muscles"))
        val executionTips = parseJsonStringArray(obj.optJSONArray("execution_tips"))

        // --- Execute Parsing Logic ---
        val rawName = obj.optString("name", "Unknown")
        val typicalSetsReps = obj.optString("typical_sets_reps", "3x12")
        val difficulty = obj.optInt("difficulty", 1)

        // 1. Parse Rest Time based on Difficulty
        val restSeconds = when (difficulty) {
            in 8..10 -> 90
            in 4..7 -> 60
            else -> 45
        }

        // 2. Parse Sets/Reps from Name suffix (e.g., "- 3 sets of 10")
        var finalSets = 3
        var finalReps = 12
        var repsDisplayString = "12"
        var cleanName = rawName

        // Check for specific pattern " - X sets of Y"
        val setsRepsRegex = Regex("""\s*-\s*(\d+)\s*sets?\s*of\s*([\d-]+)""", RegexOption.IGNORE_CASE)
        val match = setsRepsRegex.find(rawName)
        if (match != null) {
            finalSets = match.groupValues[1].toIntOrNull() ?: 3
            repsDisplayString = match.groupValues[2].trim()
            // Parse numeric reps (take average if range like "10-12")
            finalReps = if (repsDisplayString.contains("-")) {
                val parts = repsDisplayString.split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size == 2) (parts[0] + parts[1]) / 2 else parts.firstOrNull() ?: 12
            } else {
                repsDisplayString.toIntOrNull() ?: 12
            }
            cleanName = rawName.replace(match.value, "").trim()
        } else {
            // Fallback: Parsing typicalSetsReps string "3x12" or "3x10-12" or "3x45-60 sekund"
            if (typicalSetsReps.contains("x")) {
                val parts = typicalSetsReps.split("x")
                val setsPart = parts[0].trim().toIntOrNull()
                val repsPart = parts.getOrNull(1)?.trim()
                if (setsPart != null) finalSets = setsPart
                if (repsPart != null) {
                    // Zazna timed vaje: vsebuje "sekund", "sec", "s " ali konča z "s"
                    val isTimedExercise = repsPart.lowercase().contains("sekund") ||
                        repsPart.lowercase().contains("sec") ||
                        repsPart.lowercase().endsWith("s")
                    if (isTimedExercise) {
                        // Timed vaja — ohrani range string za prikaz
                        finalReps = 0
                        repsDisplayString = repsPart.replace(Regex("[^0-9-]"), "").ifBlank { "30" }
                    } else {
                        repsDisplayString = repsPart.replace(Regex("[^0-9-]"), "").ifBlank { "12" }
                        finalReps = if (repsDisplayString.contains("-")) {
                            val nums = repsDisplayString.split("-").mapNotNull { it.replace(Regex("[^0-9]"), "").toIntOrNull() }
                            if (nums.size == 2) (nums[0] + nums[1]) / 2 else nums.firstOrNull() ?: 12
                        } else {
                            repsDisplayString.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 12
                        }
                    }
                }
            }
        }

        return RefinedExercise(
            name = cleanName,
            description = obj.optString("description", ""),
            difficulty = difficulty,
            category = obj.optString("category", "strength"),
            equipment = obj.optString("equipment", "bodyweight"),
            notes = obj.optString("notes", ""),
            caloriesPerKgPerHour = obj.optDouble("calories_per_kg_per_hour", 3.0),
            primaryMuscle = obj.optString("primary_muscle", ""),
            secondaryMuscles = secMuscles,
            typicalSetsReps = typicalSetsReps,
            executionTips = executionTips,
            videoUrl = obj.optString("video_url", ""),
            muscleIntensities = muscleIntensities,
            parsedSets = finalSets,
            parsedReps = finalReps,
            repsDisplay = repsDisplayString,
            recommendedRestSeconds = restSeconds
        )
    }

    private fun parseJsonStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    fun getAllExercises(): List<RefinedExercise> = exercises

    fun getAllEquipment(): Set<String> = availableEquipment

    fun getExerciseByName(name: String): RefinedExercise? {
        return exercises.find { it.name.equals(name, ignoreCase = true) }
    }
}
