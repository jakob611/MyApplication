package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class RefinedExercise(
    val name: String,
    val description: String,
    val difficulty: Int,
    val category: String,
    val equipment: String,
    val notes: String?,
    @SerializedName("calories_per_kg_per_hour") val caloriesPerKgPerHour: Double,
    @SerializedName("primary_muscle") val primaryMuscle: String,
    @SerializedName("secondary_muscles") val secondaryMuscles: List<String>,
    @SerializedName("typical_sets_reps") val typicalSetsReps: String,
    @SerializedName("execution_tips") val executionTips: List<String>,
    @SerializedName("video_url") val videoUrl: String,

    // Pre-parsed execution parameters
    val parsedSets: Int = 3,
    val parsedReps: Int = 12,
    val repsDisplay: String = "12", // Original string preserving dashes, e.g., "10-12"
    val recommendedRestSeconds: Int = 60,

    // Muscle intensities are dynamic keys in JSON, so we might need custom deserialization
    // or just map them manually if using a library like Gson with a custom adapter.
    // simpler approach: load as Map<String, Any> first, then convert.
    // OR: just add the known ones as nullable fields if they are finite,
    // but the prompt implies dynamic "muscle_intensity_*" keys.
    // I'll use a custom deserializer or just parse the whole object as a Map for flexibility in the repository.
    val muscleIntensities: Map<String, MuscleIntensity> = emptyMap()
)

data class MuscleIntensity(
    val muscleName: String,
    val role: MuscleRole, // P, S, T
    val level: Int // 1-10
)

enum class MuscleRole {
    PRIMARY, SECONDARY, TERTIARY, NONE
}
