package com.example.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.tasks.await

object AdvancedExerciseRepository {
    private var exercises: List<RefinedExercise> = emptyList()
    private var availableEquipment: Set<String> = emptySet()

    var isInitialized: Boolean = false
        private set

    fun getAllExercises(): List<RefinedExercise> = exercises
    fun getAllEquipment(): Set<String> = availableEquipment

    suspend fun init(jsonString: String) {
        if (exercises.isNotEmpty()) {
            isInitialized = true
            return
        }

        withContext(Dispatchers.Default) {
            try {

                val jsonElement = Json.parseToJsonElement(jsonString)
                val jsonArray = jsonElement.jsonArray

                val parsedList = mutableListOf<RefinedExercise>()
                val equipmentSet = mutableSetOf<String>()

                for (item in jsonArray) {
                    val obj = item.jsonObject
                    parsedList.add(parseExercise(obj))

                    val eqRaw = obj["equipment"]?.jsonPrimitive?.content ?: "bodyweight"
                    eqRaw.lowercase().split(",").map { it.trim() }.forEach {
                        if (it.isNotEmpty()) equipmentSet.add(it)
                    }
                }

                exercises = parsedList
                availableEquipment = equipmentSet.toSortedSet()
                isInitialized = true

                com.example.myapplication.domain.Logger.d("AdvancedExerciseRepo", "Loaded ${exercises.size} exercises and ${availableEquipment.size} equipment types.")
            } catch (e: Exception) {
                com.example.myapplication.domain.Logger.e("AdvancedExerciseRepo", "Error loading exercises: ${e.message}")
            }
        }
    }

    private fun parseExercise(obj: JsonObject): RefinedExercise {
        val muscleIntensities = mutableMapOf<String, MuscleIntensity>()

        // Dinamično parsanje "muscle_intensity_*" ključev
        for ((key, valueElement) in obj) {
            if (key.startsWith("muscle_intensity_")) {
                val value = valueElement.jsonPrimitive.content
                if (value.isNotEmpty()) {
                    val muscleName = key.removePrefix("muscle_intensity_").trim()
                    val roleChar = value.firstOrNull()?.uppercaseChar()
                    val levelChar = value.drop(1).toIntOrNull() ?: 1

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

        val secMuscles = obj["secondary_muscles"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val executionTips = obj["execution_tips"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

        val rawName = obj["name"]?.jsonPrimitive?.content ?: "Unknown"
        val typicalSetsReps = obj["typical_sets_reps"]?.jsonPrimitive?.content ?: "3x12"
        val difficulty = obj["difficulty"]?.jsonPrimitive?.intOrNull ?: 1

        val restSeconds = when (difficulty) {
            in 8..10 -> 90
            in 4..7 -> 60
            else -> 45
        }

        var finalSets = 3
        var finalReps = 12
        var repsDisplayString = "12"
        var cleanName = rawName

        val setsRepsRegex = Regex("""\s*-\s*(\d+)\s*sets?\s*of\s*([\d-]+)""", RegexOption.IGNORE_CASE)
        val match = setsRepsRegex.find(rawName)
        if (match != null) {
            finalSets = match.groupValues[1].toIntOrNull() ?: 3
            repsDisplayString = match.groupValues[2].trim()
            finalReps = if (repsDisplayString.contains("-")) {
                val parts = repsDisplayString.split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size == 2) (parts[0] + parts[1]) / 2 else parts.firstOrNull() ?: 12
            } else {
                repsDisplayString.toIntOrNull() ?: 12
            }
            cleanName = rawName.replace(match.value, "").trim()
        } else {
            if (typicalSetsReps.contains("x")) {
                val parts = typicalSetsReps.split("x")
                val setsPart = parts[0].trim().toIntOrNull()
                val repsPart = parts.getOrNull(1)?.trim()
                if (setsPart != null) finalSets = setsPart
                if (repsPart != null) {
                    val isTimedExercise = repsPart.lowercase().contains("sekund") ||
                        repsPart.lowercase().contains("sec") ||
                        repsPart.lowercase().endsWith("s")
                    if (isTimedExercise) {
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
            description = obj["description"]?.jsonPrimitive?.content ?: "",
            difficulty = difficulty,
            category = obj["category"]?.jsonPrimitive?.content ?: "strength",
            equipment = obj["equipment"]?.jsonPrimitive?.content ?: "bodyweight",
            notes = obj["notes"]?.jsonPrimitive?.content ?: "",
            caloriesPerKgPerHour = obj["calories_per_kg_per_hour"]?.jsonPrimitive?.doubleOrNull ?: 3.0,
            primaryMuscle = obj["primary_muscle"]?.jsonPrimitive?.content ?: "",
            secondaryMuscles = secMuscles,
            typicalSetsReps = typicalSetsReps,
            executionTips = executionTips,
            videoUrl = obj["video_url"]?.jsonPrimitive?.content ?: "",
            muscleIntensities = muscleIntensities,
            parsedSets = finalSets,
            parsedReps = finalReps,
            repsDisplay = repsDisplayString,
            recommendedRestSeconds = restSeconds
        )
    }

    suspend fun saveExerciseLog(
        name: String,
        sets: Int,
        reps: Int,
        durationSeconds: Int?,
        caloriesKcal: Int
    ) {
        withContext(Dispatchers.IO) {
            try {
                val docRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                val log = hashMapOf(
                    "name" to name,
                    "date" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                    "caloriesKcal" to caloriesKcal,
                    "sets" to sets,
                    "reps" to reps,
                    "durationSeconds" to durationSeconds
                )
                docRef.collection("exerciseLogs").add(log).await()
            } catch (e: Exception) {
                com.example.myapplication.domain.Logger.e("AdvancedExerciseRepo", "Failed to save exercise log: ${e.message}")
            }
        }
    }
}
