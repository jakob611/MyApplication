package com.example.myapplication.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RefinedExercise(
    val name: String,
    val description: String = "",
    val difficulty: Int = 1,
    val category: String = "strength",
    val equipment: String = "bodyweight",
    val notes: String? = null,
    @SerialName("calories_per_kg_per_hour") val caloriesPerKgPerHour: Double = 3.0,
    @SerialName("primary_muscle") val primaryMuscle: String = "",
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    @SerialName("typical_sets_reps") val typicalSetsReps: String = "3x12",
    @SerialName("execution_tips") val executionTips: List<String> = emptyList(),
    @SerialName("video_url") val videoUrl: String = "",

    // Pre-parsed execution parameters
    val parsedSets: Int = 3,
    val parsedReps: Int = 12,
    val repsDisplay: String = "12", // Original string preserving dashes, e.g., "10-12"
    val recommendedRestSeconds: Int = 60,

    val muscleIntensities: Map<String, MuscleIntensity> = emptyMap()
)

@Serializable
data class MuscleIntensity(
    val muscleName: String,
    val role: MuscleRole, // P, S, T
    val level: Int // 1-10
)

@Serializable
enum class MuscleRole {
    PRIMARY, SECONDARY, TERTIARY, NONE
}
